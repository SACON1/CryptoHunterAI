package gr.cryptohunter.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

internal data class RugCheckItem(val title: String, val value: String, val risk: Boolean? = null)
internal data class RugCheckResult(
    val chain: String,
    val address: String,
    val source: String,
    val headline: String,
    val items: List<RugCheckItem>,
    val warning: String
)

private fun rugGet(url: String): JSONObject {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.connectTimeout = 12000
    connection.readTimeout = 12000
    connection.setRequestProperty("Accept", "application/json")
    connection.setRequestProperty("User-Agent", "CryptoHunterAI/0.4")
    return try {
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}: ${body.take(180)}")
        JSONObject(body)
    } finally { connection.disconnect() }
}

private fun jsonFlag(o: JSONObject, key: String): Boolean? {
    if (!o.has(key) || o.isNull(key)) return null
    return when (val value = o.opt(key)) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> value == "1" || value.equals("true", true)
        else -> null
    }
}

internal suspend fun inspectRugPull(chain: String, address: String): RugCheckResult = withContext(Dispatchers.IO) {
    require(address.isNotBlank()) { "Συμπλήρωσε τη διεύθυνση του token." }
    when (chain.lowercase(Locale.US)) {
        "solana" -> {
            val root = rugGet("https://api.rugcheck.xyz/v1/tokens/${java.net.URLEncoder.encode(address, "UTF-8")}/report")
            val token = root.optJSONObject("token") ?: JSONObject()
            val items = mutableListOf<RugCheckItem>()
            val mint = token.optString("mintAuthority", "unknown")
            val freeze = token.optString("freezeAuthority", "unknown")
            items += RugCheckItem("Mint authority", if (mint == "null" || mint == "unknown" || mint.isBlank()) "Δεν επιβεβαιώθηκε από την απόκριση" else "Ενεργή authority: $mint", if (mint != "null" && mint != "unknown" && mint.isNotBlank()) true else null)
            items += RugCheckItem("Freeze authority", if (freeze == "null" || freeze == "unknown" || freeze.isBlank()) "Δεν επιβεβαιώθηκε από την απόκριση" else "Ενεργή authority: $freeze", if (freeze != "null" && freeze != "unknown" && freeze.isNotBlank()) true else null)
            val risks = root.optJSONArray("risks")
            if (risks != null) {
                for (i in 0 until risks.length()) {
                    val r = risks.optJSONObject(i) ?: continue
                    val level = r.optString("level", "unknown")
                    val name = r.optString("name", "Risk")
                    val desc = r.optString("description", "")
                    items += RugCheckItem(name, listOf(level, desc).filter { it.isNotBlank() }.joinToString(" — "), level.equals("danger", true) || level.equals("critical", true))
                }
            }
            val score = root.optInt("score", -1)
            val normalized = root.optInt("score_normalised", -1)
            val danger = items.any { it.risk == true }
            RugCheckResult("Solana", address, "RugCheck.xyz", if (danger) "Εντοπίστηκαν προειδοποιήσεις" else "Ο έλεγχος ολοκληρώθηκε — απαιτείται προσοχή", items,
                "Το αποτέλεσμα προέρχεται από RugCheck. Score: ${if (score >= 0) score else "—"} • Normalized: ${if (normalized >= 0) normalized else "—"}. Δεν αποτελεί εγγύηση ασφάλειας.")
        }
        else -> {
            val chainId = when (chain.lowercase(Locale.US)) {
                "ethereum" -> "1"; "bsc" -> "56"; "base" -> "8453"; "arbitrum" -> "42161"; "polygon" -> "137"
                else -> error("Δεν υποστηρίζεται ακόμη το δίκτυο $chain από τον αυτόματο έλεγχο.")
            }
            val root = rugGet("https://api.gopluslabs.io/api/v1/token_security/$chainId?contract_addresses=${java.net.URLEncoder.encode(address, "UTF-8")}")
            val result = root.optJSONObject("result") ?: JSONObject()
            val token = result.optJSONObject(address.lowercase(Locale.US))
                ?: result.keys().asSequence().mapNotNull { result.optJSONObject(it) }.firstOrNull()
                ?: error("Ο πάροχος δεν επέστρεψε αναφορά για αυτή τη διεύθυνση.")
            val items = mutableListOf<RugCheckItem>()
            fun flag(label: String, key: String, badMeansRisk: Boolean = true) {
                val v = jsonFlag(token, key)
                items += RugCheckItem(label, when (v) { null -> "Άγνωστο / δεν επιστράφηκε"; true -> if (badMeansRisk) "ΝΑΙ — χρειάζεται προσοχή" else "ΝΑΙ"; false -> if (badMeansRisk) "Δεν εντοπίστηκε από αυτόν τον έλεγχο" else "ΟΧΙ" }, if (v == null) null else if (badMeansRisk) v else !v)
            }
            flag("Honeypot", "is_honeypot")
            flag("Μπορεί να αυξήσει supply (mintable)", "is_mintable")
            flag("Blacklist λειτουργία", "is_blacklisted")
            flag("Ανάκτηση ownership", "can_take_back_ownership")
            flag("Κρυφός owner", "hidden_owner")
            flag("Self-destruct", "selfdestruct")
            flag("External calls", "external_call")
            items += RugCheckItem("Buy tax", token.optString("buy_tax", "Άγνωστο"))
            items += RugCheckItem("Sell tax", token.optString("sell_tax", "Άγνωστο"))
            items += RugCheckItem("Αριθμός holders", token.optString("holder_count", "Άγνωστο"))
            items += RugCheckItem("Creator address", token.optString("creator_address", "Άγνωστο"))
            val holders = token.optJSONArray("holders")
            if (holders != null && holders.length() > 0) {
                var top = 0.0
                for (i in 0 until minOf(10, holders.length())) top += holders.optJSONObject(i)?.optString("percent", "0")?.toDoubleOrNull() ?: 0.0
                items += RugCheckItem("Συγκέντρωση top 10 holders", String.format(Locale.US, "%.2f%%", top), if (top >= 50) true else false)
            } else items += RugCheckItem("Συγκέντρωση top 10 holders", "Δεν επιστράφηκαν στοιχεία", null)
            val danger = items.any { it.risk == true }
            RugCheckResult(chain, address, "GoPlus Security", if (danger) "Εντοπίστηκαν σοβαρά red flags" else "Έλεγχος ολοκληρώθηκε — υπάρχουν άγνωστα πεδία", items,
                "Τα δεδομένα προέρχονται από GoPlus. Η ρευστότητα/LP lock και το ιστορικό developer δεν επιβεβαιώνονται πάντα από αυτό το endpoint.")
        }
    }
}

@Composable
internal fun RugPullCheckCard(chain: String, address: String) {
    var loading by remember(address) { mutableStateOf(false) }
    var result by remember(address) { mutableStateOf<RugCheckResult?>(null) }
    var error by remember(address) { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("RUG PULL HUNTER", color = Color(0xFFFFC857), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        Text("Έλεγχος on-chain μέσω ανεξάρτητου παρόχου", color = Color(0xFFA7B4D0), fontSize = 10.sp)
        Button(
            onClick = {
                scope.launch {
                    loading = true; error = ""; result = null
                    try { result = inspectRugPull(chain, address) }
                    catch (e: Exception) { error = e.message ?: "Αποτυχία ελέγχου" }
                    finally { loading = false }
                }
            },
            enabled = !loading && address.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9B75FF))
        ) { Text(if (loading) "Έλεγχος..." else "Έλεγχος για Rug Pull") }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color(0xFF9B75FF))
        if (error.isNotBlank()) Text("Δεν ολοκληρώθηκε ο έλεγχος: $error", color = Color(0xFFFFC857), fontSize = 11.sp)
        result?.let { report ->
            Text(report.headline, color = if (report.items.any { it.risk == true }) Color(0xFFFF7185) else Color(0xFFFFC857), fontWeight = FontWeight.Bold)
            Text("Πηγή: ${report.source}", color = Color(0xFF45D9FF), fontSize = 10.sp)
            report.items.forEach { item ->
                Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = .045f), RoundedCornerShape(10.dp)).padding(9.dp)) {
                    Text(item.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                    Text(item.value, color = when (item.risk) { true -> Color(0xFFFF7185); false -> Color(0xFF42E6B0); null -> Color(0xFFA7B4D0) }, fontSize = 10.sp)
                }
            }
            Text(report.warning, color = Color(0xFFA7B4D0), fontSize = 10.sp)
        }
    }
}

@Composable
fun RugPullHunterScreen() {
    var chain by remember { mutableStateOf("Solana") }
    var address by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<RugCheckResult?>(null) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("RUG PULL HUNTER AI", color = Color(0xFF45D9FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text("Έλεγξε ένα token πριν αλληλεπιδράσεις μαζί του.", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
        Text("Solana • Ethereum • BSC • Base • Arbitrum • Polygon", color = Color(0xFFA7B4D0), fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Solana", "Ethereum", "BSC", "Base").forEach { c ->
                FilterChip(selected = chain == c, onClick = { chain = c; result = null; error = "" }, label = { Text(c) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Arbitrum", "Polygon").forEach { c ->
                FilterChip(selected = chain == c, onClick = { chain = c; result = null; error = "" }, label = { Text(c) })
            }
        }
        OutlinedTextField(value = address, onValueChange = { address = it.trim(); result = null }, modifier = Modifier.fillMaxWidth(),
            label = { Text("Token contract / mint address") }, placeholder = { Text("Επικόλλησε τη διεύθυνση") }, singleLine = true)
        Button(onClick = {
            scope.launch {
                loading = true; error = ""; result = null
                try { result = inspectRugPull(chain, address) }
                catch (e: Exception) { error = e.message ?: "Αποτυχία ελέγχου" }
                finally { loading = false }
            }
        }, enabled = address.isNotBlank() && !loading, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9B75FF))) {
            Text(if (loading) "Γίνεται έλεγχος..." else "Σάρωση για Rug Pull")
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color(0xFF9B75FF))
        if (error.isNotBlank()) Text("Σφάλμα: $error", color = Color(0xFFFFC857), fontSize = 11.sp)
        result?.let { report ->
            Text(report.headline, color = if (report.items.any { it.risk == true }) Color(0xFFFF7185) else Color(0xFFFFC857), fontWeight = FontWeight.Bold)
            Text("Πηγή: ${report.source}", color = Color(0xFF45D9FF), fontSize = 10.sp)
            report.items.forEach { item ->
                Column(Modifier.fillMaxWidth().background(Color(0xCC17213A), RoundedCornerShape(12.dp)).padding(10.dp)) {
                    Text(item.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Text(item.value, color = when (item.risk) { true -> Color(0xFFFF7185); false -> Color(0xFF42E6B0); null -> Color(0xFFA7B4D0) }, fontSize = 11.sp)
                }
            }
            Text(report.warning, color = Color(0xFFA7B4D0), fontSize = 10.sp)
        }
        Text("Σημαντικό: ο έλεγχος δεν εγγυάται ότι ένα token είναι ασφαλές. Άγνωστα πεδία δεν θεωρούνται καθαρά.", color = Color(0xFFFFC857), fontSize = 10.sp)
    }
}
