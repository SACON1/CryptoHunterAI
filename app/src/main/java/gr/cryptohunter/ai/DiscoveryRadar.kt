package gr.cryptohunter.ai

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

private data class RadarToken(val chain: String, val description: String, val link: String)

private suspend fun fetchRadarTokens(): List<RadarToken> = withContext(Dispatchers.IO) {
    val conn = URL("https://api.dexscreener.com/token-profiles/latest/v1").openConnection() as HttpURLConnection
    conn.connectTimeout = 12000
    conn.readTimeout = 12000
    try {
        val data = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
        (0 until minOf(data.length(), 30)).mapNotNull { i ->
            val o = data.optJSONObject(i) ?: return@mapNotNull null
            val chain = o.optString("chainId")
            val address = o.optString("tokenAddress")
            if (chain.isBlank() || address.isBlank()) null
            else RadarToken(chain, o.optString("description", "Νέο token profile"), o.optString("url"))
        }
    } finally { conn.disconnect() }
}

@Composable
fun DiscoveryRadarScreen() {
    val context = LocalContext.current
    var section by remember { mutableStateOf("Meme Coins") }
    var tokens by remember { mutableStateOf<List<RadarToken>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(refresh) {
        loading = true
        try {
            tokens = fetchRadarTokens()
            error = if (tokens.isEmpty()) "Δεν βρέθηκαν νέα profiles αυτή τη στιγμή." else ""
        } catch (e: Exception) {
            error = "Αποτυχία σύνδεσης: ${e.message ?: "δοκίμασε ξανά"}"
        } finally { loading = false }
    }

    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Text("DISCOVERY RADAR", color = Color(0xFF45D9FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("Νέες ευκαιρίες. Πιο αυστηροί έλεγχοι.", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Text("Meme coins • Developer history • News catalysts • Startups", color = Color(0xFFA7B4D0), fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Meme Coins", "Developer", "News", "Startups").forEach { item ->
                FilterChip(selected = section == item, onClick = { section = item }, label = { Text(item, fontSize = 9.sp) })
            }
        }
        when (section) {
            "Meme Coins" -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Νέα token profiles", color = Color.White, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { refresh++ }) { Text("Ανανέωση", color = Color(0xFF42E6B0)) }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (error.isNotBlank()) Text(error, color = Color(0xFFFFC857), fontSize = 11.sp)
                Text("Πηγή: DexScreener • Τα νέα profiles δεν έχουν ακόμη περάσει έλεγχο ασφάλειας.", color = Color(0xFFA7B4D0), fontSize = 10.sp)
                tokens.forEach { token ->
                    Column(
                        Modifier.fillMaxWidth().background(
                            Brush.linearGradient(listOf(Color(0xD91D2945), Color(0xB3101830))),
                            RoundedCornerShape(16.dp)
                        ).border(1.dp, Color(0x669B75FF), RoundedCornerShape(16.dp)).padding(13.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(token.chain.uppercase(), color = Color(0xFF42E6B0), fontWeight = FontWeight.Bold)
                            Text("ΜΗ ΕΛΕΓΜΕΝΟ", color = Color(0xFFFFC857), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(token.description.take(130), color = Color.White, fontSize = 12.sp)
                        if (token.link.startsWith("https://")) {
                            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(token.link))) }) {
                                Text("Άνοιγμα στο DexScreener ↗", color = Color(0xFF42E6B0))
                            }
                        }
                    }
                }
            }
            "Developer" -> RadarPlaceholder("Έλεγχος Developer", "Επόμενο στάδιο: ιστορικό wallet, προηγούμενα projects, συνδεδεμένα wallets και ενδείξεις rug pull. Θα εμφανίζουμε αποδείξεις και όχι αυθαίρετη ετικέτα scam.")
            "News" -> RadarPlaceholder("News Catalyst Radar", "Επόμενο στάδιο: διασταύρωση ειδήσεων και επίσημων ανακοινώσεων με crypto assets, με ώρα δημοσίευσης και πηγές.")
            else -> RadarPlaceholder("Startup Radar", "Επόμενο στάδιο: νέα crypto/tech startups, ομάδα, χρηματοδότηση, προϊόν, συνεργασίες και ιστορικό ιδρυτών.")
        }
        Text("Προσοχή: η εμφάνιση ενός token δεν σημαίνει ότι είναι νόμιμο ή ασφαλές. Δεν γίνεται αυτόματη αγορά.", color = Color(0xFFA7B4D0), fontSize = 10.sp)
    }
}

@Composable
private fun RadarPlaceholder(title: String, description: String) {
    Column(Modifier.fillMaxWidth().background(Color(0xCC17213A), RoundedCornerShape(18.dp))
        .border(1.dp, Color(0x669B75FF), RoundedCornerShape(18.dp)).padding(16.dp)) {
        Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(description, color = Color(0xFFA7B4D0), fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        Text("ΕΝΣΩΜΑΤΩΣΗ ΣΕ ΕΞΕΛΙΞΗ", color = Color(0xFFFFC857), fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
