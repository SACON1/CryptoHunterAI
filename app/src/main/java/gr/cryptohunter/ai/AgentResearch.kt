package gr.cryptohunter.ai

import android.content.Intent
import android.net.Uri
import android.util.Xml
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL

private data class AgentItem(val title:String,val source:String,val url:String,val summary:String,val category:String)
private data class AgentData(val tokens:List<AgentItem>,val boosts:List<AgentItem>,val social:List<AgentItem>,val news:List<AgentItem>,val memes:List<MemeCandidate>,val time:String)

private fun agentGet(url:String):String {
    val c=URL(url).openConnection() as HttpURLConnection
    c.connectTimeout=12000; c.readTimeout=12000
    c.setRequestProperty("User-Agent","CryptoHunterAI/0.2 Android")
    c.setRequestProperty("Accept","application/json, application/rss+xml, application/xml, text/xml")
    return try { c.inputStream.bufferedReader().use{it.readText()} } finally { c.disconnect() }
}

private fun agentRss(xml:String,source:String,category:String):List<AgentItem> {
    val p=Xml.newPullParser(); p.setInput(xml.reader())
    val out=mutableListOf<AgentItem>(); var event=p.eventType
    var active=false; var title=""; var link=""; var desc=""; var itemDepth=0
    while(event!=XmlPullParser.END_DOCUMENT && out.size<15) {
        when(event) {
            XmlPullParser.START_TAG -> {
                val tag=p.name.lowercase()
                if(tag=="item" || tag=="entry") { active=true; title=""; link=""; desc=""; itemDepth=p.depth }
                else if(active && tag=="title") title=p.nextText().trim()
                else if(active && tag in listOf("description","summary","content")) desc=p.nextText().replace(Regex("<[^>]*>")," ").trim()
                else if(active && tag=="link") link=(p.getAttributeValue(null,"href") ?: p.nextText()).trim()
            }
            XmlPullParser.END_TAG -> if(active && p.name.lowercase() in listOf("item","entry") && p.depth==itemDepth) {
                if(title.isNotBlank()) out.add(AgentItem(title,source,link,desc.take(200),category))
                active=false
            }
        }
        event=p.next()
    }
    return out
}

private suspend fun fetchAgentData():AgentData=withContext(Dispatchers.IO) {
    val tokens=runCatching {
        val a=JSONArray(agentGet("https://api.dexscreener.com/token-profiles/latest/v1"))
        (0 until minOf(a.length(),20)).mapNotNull { i ->
            val o=a.optJSONObject(i) ?: return@mapNotNull null
            val addr=o.optString("tokenAddress"); val chain=o.optString("chainId")
            if(addr.isBlank() || chain.isBlank()) null else AgentItem("Νέο token profile • $chain","DexScreener",o.optString("url"),"Address: $addr • ${o.optString("description","Χωρίς περιγραφή").take(100)}","TOKEN")
        }
    }.getOrDefault(emptyList())
    val boosts=runCatching {
        val a=JSONArray(agentGet("https://api.dexscreener.com/token-boosts/latest/v1"))
        (0 until minOf(a.length(),15)).mapNotNull { i ->
            val o=a.optJSONObject(i) ?: return@mapNotNull null
            val addr=o.optString("tokenAddress"); val link=o.optString("url")
            if(addr.isBlank() || link.isBlank()) null else AgentItem("Προωθημένο token • ${o.optString("chainId")}","DexScreener Boosts",link,"Boost: ${o.optString("amount","—")} • $addr. Η πληρωμένη προώθηση δεν είναι ένδειξη αξιοπιστίας.","BOOST")
        }
    }.getOrDefault(emptyList())
    val socialFeeds=listOf("https://www.reddit.com/r/CryptoCurrency/.rss","https://www.reddit.com/r/solana/.rss","https://www.reddit.com/r/memecoins/.rss")
    val social=socialFeeds.flatMapIndexed { i,url -> runCatching { agentRss(agentGet(url),listOf("r/CryptoCurrency","r/solana","r/memecoins")[i],"SOCIAL") }.getOrDefault(emptyList()) }.take(25)
    val newsFeeds=listOf("https://cointelegraph.com/rss","https://www.coindesk.com/arc/outboundfeeds/rss/?outputType=xml","https://www.theblock.co/rss.xml")
    val news=newsFeeds.flatMapIndexed { i,url -> runCatching { agentRss(agentGet(url),listOf("Cointelegraph","CoinDesk","The Block")[i],"NEWS") }.getOrDefault(emptyList()) }.take(30)
    val memeCoins=runCatching { scanMemeCoins().coins }.getOrDefault(emptyList())
    AgentData(tokens,boosts,social,news,memeCoins,java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.getDefault()).format(java.util.Date()))
}

private fun agentMood(text:String):Pair<String,Color> {
    val s=text.lowercase()
    val pos=listOf("bullish","breakout","surge","partnership","adoption","launch","listing","growth").count{s.contains(it)}
    val neg=listOf("scam","rug","hack","exploit","dump","fraud","warning","lawsuit","crash").count{s.contains(it)}
    return when { pos>neg -> "Θετικό λεξιλόγιο • όχι πρόβλεψη" to Color(0xFF42E6B0); neg>pos -> "Αρνητικό λεξιλόγιο • προσοχή" to Color(0xFFFF7185); else -> "Μικτό / ουδέτερο" to Color(0xFFFFC857) }
}


private data class MemeAnalysis(
    val mentions: List<AgentItem>,
    val topics: List<String>,
    val tone: String,
    val explanation: String
)

private fun hasTerm(text: String, term: String): Boolean {
    val t=term.lowercase()
    if(t.length<=4) return Regex("(^|[^a-z0-9])"+Regex.escape(t)+"([^a-z0-9]|$)").containsMatchIn(text)
    return text.contains(t)
}

private fun analyzeMemeContext(coin:MemeCandidate, news:List<AgentItem>, social:List<AgentItem>):MemeAnalysis {
    val symbol=coin.symbol.lowercase().trim()
    val name=coin.name.lowercase().trim()
    val all=(news+social).distinctBy { it.url.ifBlank { it.source+it.title } }
    val mentions=all.filter { item ->
        val text=(item.title+" "+item.summary).lowercase()
        (name.length>=4 && text.contains(name)) || (symbol.length>=3 && hasTerm(text,symbol))
    }.take(5)
    val corpus=mentions.joinToString(" ") { it.title+" "+it.summary }.lowercase()
    val topics=buildList {
        if(listOf("trump","president","election","white house","politic","πολιτικ","εκλογ","πρόεδρος").any{hasTerm(corpus,it)}) add("Πολιτική")
        if(listOf("elon","musk","celebrity","influencer","celeb","διάσημ").any{hasTerm(corpus,it)}) add("Πρόσωπα / influencers")
        if(listOf("listing","listed","exchange","binance","coinbase","kraken","εισαγωγ","καταχώρ").any{hasTerm(corpus,it)}) add("Listings / ανταλλακτήρια")
        if(listOf("launch","launched","fair launch","presale","airdrop","κυκλοφορία","λανσάρ").any{hasTerm(corpus,it)}) add("Launch / διανομή")
        if(listOf("hack","exploit","rug pull","scam","fraud","phishing","κλοπή","απάτη").any{hasTerm(corpus,it)}) add("Ασφάλεια / πιθανή απάτη")
        if(listOf("partnership","partner","integration","collaboration","συνεργασία").any{hasTerm(corpus,it)}) add("Συνεργασία")
    }
    val positive=listOf("bullish","surge","rally","adoption","listing","partnership","breakout","άνοδο","θετικ")
        .sumOf { term -> mentions.count { hasTerm((it.title+" "+it.summary).lowercase(),term) } }
    val negative=listOf("scam","rug","hack","exploit","dump","fraud","warning","lawsuit","crash","απάτη","προειδοπ")
        .sumOf { term -> mentions.count { hasTerm((it.title+" "+it.summary).lowercase(),term) } }
    val tone=when {
        mentions.isEmpty()->"Δεν υπάρχει αρκετό σχετικό περιεχόμενο"
        negative>positive->"Αρνητικές ενδείξεις στις αναφορές"
        positive>negative->"Θετικές λέξεις στις αναφορές"
        else->"Μικτό / ουδέτερο περιεχόμενο"
    }
    val explanation=when {
        mentions.isEmpty()->"Δεν βρέθηκε αναφορά που να ταιριάζει καθαρά με το όνομα ή το σύμβολο του token στις πηγές που ελέγχθηκαν."
        topics.isNotEmpty()->"Βρέθηκαν ${mentions.size} σχετικές αναφορές. Θέματα: ${topics.joinToString(", ")}. Άνοιξε τις πηγές για επιβεβαίωση."
        else->"Βρέθηκαν ${mentions.size} αναφορές με αντιστοίχιση ονόματος/συμβόλου. Δεν προκύπτει ακόμη σαφές γεγονός."
    }
    return MemeAnalysis(mentions,topics,tone,explanation)
}


@Composable
private fun AgentCoreAnimation(isScanning:Boolean) {
    val transition= rememberInfiniteTransition(label="agent-core")
    val pulse by transition.animateFloat(
        initialValue=0.78f, targetValue=1.08f,
        animationSpec=infiniteRepeatable(tween(1500),RepeatMode.Reverse),
        label="core-pulse"
    )
    val rotation by transition.animateFloat(
        initialValue=0f, targetValue=360f,
        animationSpec=infiniteRepeatable(tween(if(isScanning) 5000 else 9000)),
        label="core-rotation"
    )
    val wave by transition.animateFloat(
        initialValue=0.18f,targetValue=0.82f,
        animationSpec=infiniteRepeatable(tween(1100),RepeatMode.Reverse),
        label="core-wave"
    )
    Box(Modifier.fillMaxWidth(), contentAlignment=androidx.compose.ui.Alignment.Center) {
        Canvas(Modifier.size(184.dp)) {
            val c=center
            val radius=size.minDimension*0.43f
            drawCircle(
                brush=Brush.radialGradient(listOf(Color(0x6645D9FF),Color(0x449B75FF),Color.Transparent),center=c,radius=radius*1.5f),
                radius=radius*1.5f,center=c
            )
            drawCircle(Color(0x669B75FF),radius=radius*pulse,center=c,style=Stroke(width=2.dp.toPx()))
            drawCircle(Color(0xAA45D9FF),radius=radius*0.72f,center=c,style=Stroke(width=1.5.dp.toPx()))
            drawCircle(Color(0x8842E6B0),radius=radius*(0.52f+wave*0.08f),center=c,style=Stroke(width=2.dp.toPx()))
            for(i in 0 until 4) {
                val angle=Math.toRadians((rotation+i*90.0))
                val inner=radius*0.78f
                val outer=radius*(0.98f+wave*0.10f)
                val x1=c.x+ kotlin.math.cos(angle).toFloat()*inner
                val y1=c.y+ kotlin.math.sin(angle).toFloat()*inner
                val x2=c.x+ kotlin.math.cos(angle).toFloat()*outer
                val y2=c.y+ kotlin.math.sin(angle).toFloat()*outer
                drawLine(Color(0xFFB69BFF),androidx.compose.ui.geometry.Offset(x1,y1),androidx.compose.ui.geometry.Offset(x2,y2),strokeWidth=3.dp.toPx(),cap=StrokeCap.Round)
            }
            drawCircle(Color(0xFF9B75FF),radius=radius*0.34f*pulse,center=c)
            drawCircle(Color(0xFF45D9FF),radius=radius*0.18f,center=c)
        }
    }
}

@Composable
fun AgentResearchScreen() {
    val context=LocalContext.current
    var data by remember { mutableStateOf<AgentData?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf("MEME") }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresh) {
        loading=true; error=""
        try { data=fetchAgentData(); if(data!!.tokens.isEmpty() && data!!.social.isEmpty() && data!!.news.isEmpty()) error="Οι πηγές δεν απάντησαν." }
        catch(e:Exception) { error=e.message ?: "Σφάλμα σύνδεσης" }
        finally { loading=false }
    }
    LaunchedEffect(Unit) { while(true) { delay(5*60*1000L); refresh++ } }
    val list=when(tab) { "SOCIAL"->data?.social.orEmpty(); "NEWS"->data?.news.orEmpty(); "TOKENS"->data?.tokens.orEmpty(); "BOOSTS"->data?.boosts.orEmpty(); else->emptyList() }
    val memeList=data?.memes.orEmpty()
    Column(verticalArrangement=Arrangement.spacedBy(11.dp)) {
        AgentCoreAnimation(isScanning=loading)
        Text("CRYPTO HUNTER • LIVE AGENT",color=Color(0xFF45D9FF),fontSize=10.sp,fontWeight=FontWeight.Bold)
        Text("Ο ερευνητής της αγοράς σου.",color=Color.White,fontSize=21.sp,fontWeight=FontWeight.ExtraBold)
        Text("Συζητήσεις • Ειδήσεις • Νέα tokens • Προωθήσεις",color=Color(0xFFA7B4D0),fontSize=11.sp)
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xD91D2945),Color(0xB3101830))),RoundedCornerShape(18.dp)).border(1.dp,Color(0x669B75FF),RoundedCornerShape(18.dp)).padding(14.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("AGENT STATUS",color=Color(0xFFA7B4D0),fontSize=10.sp,fontWeight=FontWeight.Bold)
                Text(if(loading)"● SCANNING" else if(data!=null)"● CONNECTED" else "● WAITING",color=if(loading)Color(0xFFFFC857) else Color(0xFF42E6B0),fontSize=10.sp,fontWeight=FontWeight.Bold)
            }
            if(loading) LinearProgressIndicator(Modifier.fillMaxWidth(),color=Color(0xFF9B75FF))
            Text("DexScreener • Reddit RSS • Crypto news RSS",color=Color.White,fontSize=11.sp)
            Text("Τελευταίος έλεγχος: ${data?.time ?: "—"} • ανανέωση ανά 5 λεπτά όταν είναι ανοιχτή η καρτέλα",color=Color(0xFFA7B4D0),fontSize=9.sp)
            TextButton(onClick={refresh++}) { Text("↻ Σάρωση τώρα",color=Color(0xFF45D9FF)) }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            listOf("MEME","SOCIAL","NEWS","TOKENS","BOOSTS").forEach { key ->
                FilterChip(selected=tab==key,onClick={tab=key},label={Text(when(key){"MEME"->"Meme Radar";"SOCIAL"->"Social";"NEWS"->"News";"TOKENS"->"New";else->"Boosts"},fontSize=9.sp)})
            }
        }
        if(error.isNotBlank()) Text(error,color=Color(0xFFFFC857),fontSize=11.sp)
        if(tab=="MEME") Text("Δωρεάν ανάλυση κανόνων: αντιστοίχιση ονόματος/συμβόλου, θεματικές ειδήσεων, θετικό/αρνητικό λεξιλόγιο και market red flags. Δεν είναι ακόμη μεγάλο γλωσσικό μοντέλο και δεν αποδεικνύει αιτιώδη σχέση.",color=Color(0xFFA7B4D0),fontSize=10.sp)
        if(tab=="SOCIAL") Text("Δημόσιες συζητήσεις Reddit μέσω RSS. Η ένδειξη κλίματος είναι απλή ανάλυση λέξεων, όχι AI πρόβλεψη.",color=Color(0xFFA7B4D0),fontSize=10.sp)
        if(tab=="BOOSTS") Text("Τα boosts είναι πληρωμένη προβολή — όχι απόδειξη ποιότητας ή ασφάλειας.",color=Color(0xFFFFC857),fontSize=10.sp)
        if(tab=="MEME") {
            if(memeList.isEmpty() && !loading) Text("Δεν βρέθηκαν live meme coin pairs από τις πηγές αυτή τη στιγμή.",color=Color(0xFFA7B4D0),fontSize=12.sp)
            memeList.forEach { coin ->
                val analysis=analyzeMemeContext(coin,data?.news.orEmpty(),data?.social.orEmpty())
                val related=analysis.mentions.take(3)
                val redFlags=buildList {
                    if(coin.liquidity<10000) add("Χαμηλή ρευστότητα")
                    if(coin.volume24h<5000) add("Χαμηλός όγκος")
                    if(coin.ageHours!=null && coin.ageHours<24) add("Pair κάτω των 24 ωρών")
                    if(coin.transactions<20) add("Λίγες συναλλαγές")
                    if(coin.marketCap<=0) add("Άγνωστο market cap")
                }
                val score=(100-redFlags.size*18).coerceAtLeast(10)
                Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xDD1D2945),Color(0xCC111A30))),RoundedCornerShape(16.dp)).border(1.dp,Color(0x559B75FF),RoundedCornerShape(16.dp)).padding(13.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(coin.name,color=Color.White,fontWeight=FontWeight.Bold,fontSize=13.sp,maxLines=1)
                            Text("${coin.symbol} • ${coin.chain}",color=Color(0xFF45D9FF),fontSize=10.sp)
                        }
                        Text("MARKET FLAGS ${redFlags.size}",color=if(redFlags.size>=3)Color(0xFFFF7185) else Color(0xFFFFC857),fontSize=9.sp,fontWeight=FontWeight.Bold)
                    }
                    Text("LP ${usdShort(coin.liquidity)} • Vol ${usdShort(coin.volume24h)} • 24h ${String.format(java.util.Locale.US,"%.1f",coin.change24h)}%",color=Color.White,fontSize=10.sp)
                    Text(if(redFlags.isEmpty()) "Δεν βρέθηκαν τα βασικά market red flags" else "Έλεγχοι: "+redFlags.joinToString(" • "),color=Color(0xFFFFC857),fontSize=10.sp)
                    Text("Ανάλυση Agent • ${analysis.tone}",color=if(analysis.tone.startsWith("Αρνητικές"))Color(0xFFFF7185) else Color(0xFF45D9FF),fontSize=10.sp,fontWeight=FontWeight.Bold)
                    Text(analysis.explanation,color=Color(0xFFA7B4D0),fontSize=10.sp)
                    if(analysis.topics.isNotEmpty()) Text("Θέματα: ${analysis.topics.joinToString(" • ")}",color=Color(0xFFB69BFF),fontSize=10.sp)
                    Text(if(related.isEmpty()) "Δεν υπάρχουν σχετικές αναφορές." else "Σχετικές πηγές (${related.size})",color=Color(0xFF45D9FF),fontSize=10.sp,fontWeight=FontWeight.Bold)
                    related.forEach { item ->
                        Text("• ${item.source}: ${item.title}",color=Color(0xFFA7B4D0),fontSize=10.sp,maxLines=2)
                        if(item.url.startsWith("https://")) TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(item.url)))}) { Text("Άνοιγμα αναφοράς ↗",color=Color(0xFF42E6B0),fontSize=9.sp) }
                    }
                    if(coin.url.startsWith("https://")) TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(coin.url)))}) { Text("Άνοιγμα coin στο DexScreener ↗",color=Color(0xFF45D9FF),fontSize=10.sp) }
                }
            }
        }
        if(tab!="MEME" && list.isEmpty() && !loading) Text("Δεν υπάρχουν διαθέσιμα αποτελέσματα αυτή τη στιγμή.",color=Color(0xFFA7B4D0),fontSize=12.sp)
        list.forEach { item ->
            val mood=agentMood(item.title+" "+item.summary)
            Column(Modifier.fillMaxWidth().background(Color(0xCC17213A),RoundedCornerShape(16.dp)).border(1.dp,Color(0x339B75FF),RoundedCornerShape(16.dp)).padding(13.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(item.source.uppercase(),color=Color(0xFF45D9FF),fontSize=9.sp,fontWeight=FontWeight.Bold)
                    Text(item.category,color=Color(0xFFA7B4D0),fontSize=8.sp)
                }
                Spacer(Modifier.height(5.dp))
                Text(item.title,color=Color.White,fontSize=13.sp,fontWeight=FontWeight.SemiBold)
                if(item.summary.isNotBlank()) Text(item.summary,color=Color(0xFFA7B4D0),fontSize=10.sp,maxLines=3)
                if(tab=="SOCIAL" || tab=="NEWS") Text(mood.first,color=mood.second,fontSize=9.sp)
                if(item.url.startsWith("https://")) TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(item.url)))}) { Text("Άνοιγμα πηγής ↗",color=Color(0xFF42E6B0),fontSize=10.sp) }
            }
        }
        Text("Το X δεν έχει συνδεθεί ακόμη: απαιτείται επίσημη πρόσβαση API. Για επίσημη και σταθερή πρόσβαση στο Reddit χρειάζεται εγκεκριμένο OAuth. Η ανάλυση είναι δωρεάν λογική κανόνων — όχι LLM, background cloud agent ή αυτόματη αγορά.",color=Color(0xFFA7B4D0),fontSize=10.sp)
    }
}
