package gr.cryptohunter.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class MemeCandidate(
    val name: String,
    val symbol: String,
    val chain: String,
    val address: String,
    val url: String,
    val price: String,
    val liquidity: Double,
    val volume24h: Double,
    val change24h: Double,
    val marketCap: Double,
    val ageHours: Double?,
    val transactions: Int
)

internal data class MemeScan(val coins: List<MemeCandidate>, val checkedAt: String, val sources: Int)

private fun memeHttp(url: String): String {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 10000
    c.readTimeout = 10000
    c.setRequestProperty("User-Agent", "CryptoHunterAI/0.3 Android")
    return try { c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() }
}

internal suspend fun scanMemeCoins(): MemeScan = withContext(Dispatchers.IO) {
    val sources = mutableListOf<Pair<String, String>>()
    runCatching {
        val arr = JSONArray(memeHttp("https://api.dexscreener.com/token-profiles/latest/v1"))
        for (i in 0 until minOf(arr.length(), 35)) {
            val o = arr.optJSONObject(i) ?: continue
            val chain = o.optString("chainId")
            val address = o.optString("tokenAddress")
            val url = o.optString("url")
            if (chain.isNotBlank() && address.isNotBlank()) sources.add(chain to address)
        }
    }
    runCatching {
        val arr = JSONArray(memeHttp("https://api.dexscreener.com/token-boosts/latest/v1"))
        for (i in 0 until minOf(arr.length(), 25)) {
            val o = arr.optJSONObject(i) ?: continue
            val chain = o.optString("chainId")
            val address = o.optString("tokenAddress")
            if (chain.isNotBlank() && address.isNotBlank()) sources.add(chain to address)
        }
    }
    val unique = sources.distinct().take(45)
    val candidates = mutableListOf<MemeCandidate>()
    unique.groupBy { it.first }.forEach { (chain, entries) ->
        entries.chunked(25).forEach { chunk ->
            val addresses = chunk.joinToString(",") { it.second }
            runCatching {
                val arr = JSONArray(memeHttp("https://api.dexscreener.com/tokens/v1/$chain/$addresses"))
                for (i in 0 until arr.length()) {
                    val p = arr.optJSONObject(i) ?: continue
                    val base = p.optJSONObject("baseToken") ?: continue
                    val address = base.optString("address")
                    val symbol = base.optString("symbol")
                    val name = base.optString("name", symbol)
                    val liq = p.optJSONObject("liquidity")?.optDouble("usd", 0.0) ?: 0.0
                    val vol = p.optJSONObject("volume")?.optDouble("h24", 0.0) ?: 0.0
                    val change = p.optJSONObject("priceChange")?.optDouble("h24", 0.0) ?: 0.0
                    val tx = p.optJSONObject("txns")?.optJSONObject("h24")
                    val transactions = (tx?.optInt("buys", 0) ?: 0) + (tx?.optInt("sells", 0) ?: 0)
                    val created = p.optLong("pairCreatedAt", 0L)
                    val age = if (created > 0) ((System.currentTimeMillis() - created).coerceAtLeast(0L) / 3600000.0) else null
                    if (address.isBlank() || symbol.isBlank()) continue
                    candidates.add(MemeCandidate(
                        name = name, symbol = symbol, chain = chain, address = address,
                        url = p.optString("url"), price = p.optString("priceUsd", "—"),
                        liquidity = liq, volume24h = vol, change24h = change,
                        marketCap = p.optDouble("marketCap", p.optDouble("fdv", 0.0)),
                        ageHours = age, transactions = transactions
                    ))
                }
            }
        }
    }
    val ranked = candidates.distinctBy { it.chain + ":" + it.address }
        .sortedByDescending { it.volume24h }
        .take(35)
    MemeScan(ranked, SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()), unique.size)
}

internal fun usdShort(value: Double): String = when {
    value >= 1_000_000 -> "\\$" + String.format(Locale.US, "%.2fM", value / 1_000_000)
    value >= 1_000 -> "\\$" + String.format(Locale.US, "%.1fK", value / 1_000)
    else -> "\\$" + String.format(Locale.US, "%.0f", value)
}

@Composable
fun MemeCoinRadarScreen() {
    val context = LocalContext.current
    var scan by remember { mutableStateOf<MemeScan?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<MemeCandidate?>(null) }

    LaunchedEffect(refresh) {
        loading = true
        error = ""
        try { scan = scanMemeCoins() }
        catch (e: Exception) { error = e.message ?: "Αποτυχία σύνδεσης" }
        finally { loading = false }
    }

    if (selected != null) {
        val coin = selected!!
        Column(Modifier.fillMaxWidth().background(Color(0xFF0A1022), RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = { selected = null }) { Text("← Πίσω στη λίστα", color = Color(0xFF45D9FF)) }
            Text(coin.name, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
            Text("${coin.symbol} • ${coin.chain}", color = Color(0xFF45D9FF), fontWeight = FontWeight.Bold)
            Text("Τιμή: ${coin.price}", color = Color.White)
            Text("Liquidity: ${usdShort(coin.liquidity)}", color = Color.White)
            Text("Όγκος 24h: ${usdShort(coin.volume24h)}", color = Color.White)
            Text("Μεταβολή 24h: ${String.format(Locale.US, "%.2f", coin.change24h)}%", color = if (coin.change24h >= 0) Color(0xFF42E6B0) else Color(0xFFFF7185))
            Text("Market cap / FDV: ${usdShort(coin.marketCap)}", color = Color.White)
            Text("Ηλικία pair: ${coin.ageHours?.let { String.format(Locale.US, "%.1f ώρες", it) } ?: "Άγνωστη"}", color = Color.White)
            Text("Συναλλαγές 24h: ${coin.transactions}", color = Color.White)
            Divider(color = Color.White.copy(alpha = .15f))
            Text("Έλεγχος κινδύνου", color = Color(0xFFFFC857), fontWeight = FontWeight.Bold)
            val flags = buildList {
                if (coin.liquidity < 10000) add("Πολύ χαμηλή ρευστότητα (<\\$10K)")
                if (coin.volume24h < 5000) add("Χαμηλός όγκος 24h (<\\$5K)")
                if (coin.ageHours != null && coin.ageHours < 24) add("Νέο pair κάτω των 24 ωρών")
                if (coin.transactions < 20) add("Λίγες συναλλαγές 24h")
                if (coin.marketCap <= 0) add("Δεν υπάρχει διαθέσιμο market cap")
            }
            if (flags.isEmpty()) Text("Δεν εντοπίστηκαν τα παραπάνω βασικά red flags στα διαθέσιμα δεδομένα. Αυτό ΔΕΝ αποδεικνύει ότι είναι ασφαλές.", color = Color(0xFFFFC857), fontSize = 12.sp)
            else flags.forEach { Text("⚠ $it", color = Color(0xFFFFC857), fontSize = 12.sp) }
            Text("Δεν ελέγχθηκαν ακόμη owner privileges, mint/freeze authority, holder concentration, developer history ή honeypot με ανεξάρτητο on-chain scanner.", color = Color(0xFFA7B4D0), fontSize = 11.sp)
            RugPullCheckCard(coin.chain, coin.address)
            MemeAgentCouncilCard(coin)
            if (coin.url.startsWith("https://")) {
                Button(onClick = { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(coin.url))) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9B75FF))) {
                    Text("Άνοιγμα DexScreener", color = Color.White)
                }
            }
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("MEME COIN RADAR", color = Color(0xFF45D9FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("Νέα meme coins", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            }
            Text(if (loading) "● SCANNING" else "● RADAR", color = if (loading) Color(0xFFFFC857) else Color(0xFF42E6B0), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Text("Νέα token profiles και boosts από DexScreener • ταξινόμηση βάσει όγκου", color = Color(0xFFA7B4D0), fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { refresh++ }, enabled = !loading, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9B75FF))) {
                Text(if (loading) "Σάρωση..." else "↻ Σάρωση τώρα")
            }
            scan?.let { Text("Έλεγχος ${it.checkedAt}", color = Color(0xFFA7B4D0), fontSize = 10.sp, modifier = Modifier.padding(top = 12.dp)) }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color(0xFF9B75FF))
        if (error.isNotBlank()) Text("Σφάλμα: $error", color = Color(0xFFFFC857), fontSize = 11.sp)
        Text("ΠΡΟΣΟΧΗ: Τα boosts είναι πληρωμένες προωθήσεις. Η εμφάνιση εδώ δεν σημαίνει ότι ένα coin είναι legit ή ασφαλές.", color = Color(0xFFFFC857), fontSize = 11.sp)
        if (scan?.coins.isNullOrEmpty() && !loading) Text("Δεν βρέθηκαν νέα ζεύγη από τις διαθέσιμες πηγές αυτή τη στιγμή.", color = Color.White)
        scan?.coins?.forEach { coin ->
            val flags = (if (coin.liquidity < 10000) 1 else 0) +
                (if (coin.volume24h < 5000) 1 else 0) +
                (if (coin.ageHours != null && coin.ageHours < 24) 1 else 0) +
                (if (coin.transactions < 20) 1 else 0)
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.linearGradient(listOf(Color(0xDD1D2945), Color(0xCC111A30))), RoundedCornerShape(18.dp))
                    .border(1.dp, Color(0x559B75FF), RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(coin.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                        Text("${coin.symbol} • ${coin.chain}", color = Color(0xFF45D9FF), fontSize = 10.sp)
                    }
                    Text(if (flags >= 2) "ΥΨΗΛΟ ΡΙΣΚΟ" else if (flags == 1) "ΠΡΟΣΟΧΗ" else "ΒΑΣΙΚΑ OK", color = if (flags >= 2) Color(0xFFFF7185) else Color(0xFFFFC857), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("LP ${usdShort(coin.liquidity)}", color = Color.White, fontSize = 11.sp)
                    Text("Vol ${usdShort(coin.volume24h)}", color = Color.White, fontSize = 11.sp)
                    Text("${String.format(Locale.US, "%.1f", coin.change24h)}%", color = if (coin.change24h >= 0) Color(0xFF42E6B0) else Color(0xFFFF7185), fontSize = 11.sp)
                }
                Text("Το risk label είναι μόνο αρχικός έλεγχος αγοράς, όχι έλεγχος scam.", color = Color(0xFFA7B4D0), fontSize = 9.sp)
                TextButton(onClick = { selected = coin }) { Text("Πλήρης εικόνα & red flags  →", color = Color(0xFF45D9FF)) }
            }
        }
    }
}
