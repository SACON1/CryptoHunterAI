package gr.cryptohunter.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

internal data class MemeAgentVote(
    val name: String,
    val verdict: String,
    val detail: String,
    val score: Int?,
    val danger: Boolean = false
)

internal data class MemeCouncilReport(
    val score: Int,
    val verdict: String,
    val votes: List<MemeAgentVote>,
    val blocked: Boolean,
    val caveat: String
)

internal suspend fun runMemeAgentCouncil(coin: MemeCandidate): MemeCouncilReport {
    val votes = mutableListOf<MemeAgentVote>()

    val liquidityScore = when {
        coin.liquidity >= 100_000 -> 90
        coin.liquidity >= 50_000 -> 78
        coin.liquidity >= 20_000 -> 65
        coin.liquidity >= 10_000 -> 50
        else -> 20
    }
    votes += MemeAgentVote(
        "Liquidity Agent",
        if (liquidityScore >= 65) "ΘΕΤΙΚΟΣ" else "ΠΡΟΣΟΧΗ",
        "Ρευστότητα ${usdShort(coin.liquidity)}. Χαμηλή ρευστότητα αυξάνει το slippage και τον κίνδυνο εξόδου.",
        liquidityScore,
        liquidityScore < 40
    )

    val volumeRatio = if (coin.liquidity > 0) coin.volume24h / coin.liquidity else 0.0
    val volumeScore = when {
        coin.volume24h >= 100_000 && volumeRatio in 0.5..15.0 -> 88
        coin.volume24h >= 25_000 && volumeRatio in 0.25..20.0 -> 72
        coin.volume24h >= 5_000 -> 55
        else -> 20
    }
    votes += MemeAgentVote(
        "Volume & Activity Agent",
        if (volumeScore >= 65) "ΘΕΤΙΚΟΣ" else "ΟΥΔΕΤΕΡΟΣ",
        "Όγκος 24h ${usdShort(coin.volume24h)} • σχέση όγκου/LP ${String.format(java.util.Locale.US, "%.2f", volumeRatio)}.",
        volumeScore
    )

    val momentumScore = when {
        coin.change24h in 3.0..35.0 && coin.transactions >= 100 -> 82
        coin.change24h in 0.0..60.0 && coin.transactions >= 40 -> 68
        coin.change24h > 100.0 -> 30
        coin.change24h < -25.0 -> 25
        else -> 50
    }
    votes += MemeAgentVote(
        "Momentum Agent",
        if (momentumScore >= 65) "ΘΕΤΙΚΟΣ" else "ΟΥΔΕΤΕΡΟΣ",
        "Μεταβολή 24h ${String.format(java.util.Locale.US, "%.2f", coin.change24h)}% • ${coin.transactions} συναλλαγές. Μεγάλη απότομη άνοδος δεν θεωρείται από μόνη της θετική.",
        momentumScore
    )

    val ageScore = when {
        coin.ageHours == null -> 35
        coin.ageHours < 1 -> 15
        coin.ageHours < 24 -> 35
        coin.ageHours < 168 -> 60
        else -> 75
    }
    votes += MemeAgentVote(
        "Token Age Agent",
        if (ageScore >= 60) "ΘΕΤΙΚΟΣ" else "ΠΡΟΣΟΧΗ",
        "Ηλικία pair: ${coin.ageHours?.let { String.format(java.util.Locale.US, "%.1f ώρες", it) } ?: "άγνωστη"}. Τα πολύ νέα pairs έχουν αυξημένο ρίσκο.",
        ageScore,
        ageScore < 20
    )

    var rugFailed = false
    try {
        val rug = inspectRugPull(coin.chain, coin.address)
        val dangerous = rug.items.count { it.risk == true }
        val unknown = rug.items.count { it.risk == null }
        val securityScore = (100 - dangerous * 22 - unknown * 4).coerceIn(0, 100)
        votes += MemeAgentVote(
            "Rug Pull Hunter",
            if (dangerous > 0) "ΑΠΟΡΡΙΨΗ" else if (unknown > 2) "ΜΗ ΕΠΙΒΕΒΑΙΩΜΕΝΟΣ" else "ΧΩΡΙΣ ΚΡΙΣΙΜΟ FLAG",
            "${dangerous} επικίνδυνα ευρήματα • ${unknown} άγνωστα πεδία • πηγή: ${rug.source}.",
            securityScore,
            dangerous > 0
        )
    } catch (e: Exception) {
        rugFailed = true
        votes += MemeAgentVote(
            "Rug Pull Hunter",
            "ΑΓΝΩΣΤΟ",
            "Ο on-chain έλεγχος απέτυχε: ${e.message ?: "μη διαθέσιμος"}. Δεν θεωρείται καθαρό token.",
            null,
            true
        )
    }

    try {
        val pulse = MemeNewsSocialAgent.analyze(coin)
        fun sourceScore(items: List<NewsSocialItem>): Int? {
            if (items.isEmpty()) return null
            val positiveTerms = listOf("partnership", "listing", "launch", "integration", "adoption", "funding", "airdrop", "bullish", "approved", "upgrade", "mainnet")
            val negativeTerms = listOf("rug pull", "rugpull", "scam", "fraud", "exploit", "hacked", "hack", "drained", "stolen", "lawsuit", "delisted", "dump", "honeypot", "phishing")
            val positive = items.sumOf { item -> positiveTerms.count { "${item.title} ${item.summary}".contains(it, ignoreCase = true) } }
            val negative = items.sumOf { item -> negativeTerms.count { "${item.title} ${item.summary}".contains(it, ignoreCase = true) } }
            return (50 + positive * 7 - negative * 12).coerceIn(0, 100)
        }
        val newsItems = pulse.items.filter { it.source == "Google News" }
        val socialItems = pulse.items.filter { it.source.contains("X / Twitter") || it.source == "Reddit" }
        val newsScore = sourceScore(newsItems)
        val socialScore = sourceScore(socialItems)
        votes += MemeAgentVote(
            "News Agent",
            when {
                newsScore == null -> "ΜΗ ΔΙΑΘΕΣΙΜΕΣ ΑΝΑΦΟΡΕΣ"
                newsScore >= 65 -> "ΘΕΤΙΚΕΣ ΕΝΔΕΙΞΕΙΣ — ΠΡΟΣ ΕΠΙΒΕΒΑΙΩΣΗ"
                newsScore <= 35 -> "ΑΡΝΗΤΙΚΕΣ ΕΝΔΕΙΞΕΙΣ"
                else -> "ΟΥΔΕΤΕΡΕΣ ΑΝΑΦΟΡΕΣ"
            },
            "Google News: ${newsItems.size} σχετικές αναφορές • score ${newsScore ?: "—"}/100. ${pulse.note}",
            newsScore,
            newsScore != null && newsScore <= 20
        )
        votes += MemeAgentVote(
            "Social Agent (X + Reddit)",
            when {
                socialScore == null -> "ΜΗ ΔΙΑΘΕΣΙΜΕΣ ΑΝΑΦΟΡΕΣ"
                socialScore >= 65 -> "ΘΕΤΙΚΟ SOCIAL BUZZ — ΜΗ ΕΠΙΒΕΒΑΙΩΜΕΝΟ"
                socialScore <= 35 -> "ΑΡΝΗΤΙΚΟ SOCIAL BUZZ"
                else -> "ΟΥΔΕΤΕΡΟ SOCIAL BUZZ"
            },
            "X/Twitter indexed: ${socialItems.count { it.source.contains("X / Twitter") }} • Reddit: ${socialItems.count { it.source == "Reddit" }} • score ${socialScore ?: "—"}/100. Δεν είναι πλήρης live κάλυψη του X.",
            socialScore,
            socialScore != null && socialScore <= 20
        )
    } catch (e: Exception) {
        votes += MemeAgentVote(
            "News & Social Agent",
            "ΜΗ ΔΙΑΘΕΣΙΜΟ",
            "Η αναζήτηση ειδήσεων/social απέτυχε: ${e.message ?: "άγνωστο σφάλμα"}. Δεν προσμετράται θετικό sentiment.",
            null,
            false
        )
    }

    val numeric = votes.mapNotNull { it.score }
    val average = if (numeric.isEmpty()) 0 else numeric.average().toInt()
    val blocked = votes.any { it.danger } || rugFailed
    val finalScore = if (blocked) minOf(average, 35) else average
    val verdict = when {
        blocked -> "ΑΠΟΡΡΙΨΗ — ΥΨΗΛΟ / ΑΓΝΩΣΤΟ ΡΙΣΚΟ"
        finalScore >= 75 -> "ΥΠΟΨΗΦΙΟ ΓΙΑ ΠΑΡΑΚΟΛΟΥΘΗΣΗ"
        finalScore >= 55 -> "ΜΟΝΟ ΣΕ WATCHLIST"
        else -> "ΔΕΝ ΠΡΟΚΥΠΤΕΙ ΕΥΚΑΙΡΙΑ"
    }
    return MemeCouncilReport(
        finalScore, verdict, votes, blocked,
        "Rule-based αξιολόγηση πολλών agents με Google News, δημόσια indexed posts του X/Twitter και Reddit. Το sentiment δεν επιβεβαιώνει γεγονότα και το αποτέλεσμα δεν είναι πρόβλεψη κέρδους."
    )
}

internal data class MemeMasterPick(
    val coin: MemeCandidate,
    val report: MemeCouncilReport,
    val masterReason: String
)

internal data class MemeMasterResult(
    val picks: List<MemeMasterPick>,
    val watchlist: List<MemeMasterPick>,
    val evaluated: Int,
    val rejected: Int
)

/**
 * Final gatekeeper: unknown or dangerous rug checks never become a recommendation.
 * Incomplete news/social data can appear only in a clearly-labelled watchlist.
 */
internal object MemeMasterAgent {
    fun select(evaluated: List<Pair<MemeCandidate, MemeCouncilReport>>): MemeMasterResult {
        val ranked = evaluated.mapNotNull { (coin, report) ->
            val newsVote = report.votes.firstOrNull { it.name == "News Agent" }
            val socialVote = report.votes.firstOrNull { it.name == "Social Agent (X + Reddit)" }
            val rugVote = report.votes.firstOrNull { it.name == "Rug Pull Hunter" }
            val missingNews = newsVote?.score == null
            val missingSocial = socialVote?.score == null
            val negativeNews = (newsVote?.score ?: 50) <= 25 || (socialVote?.score ?: 50) <= 25
            val rugNotCleared = rugVote == null || rugVote.danger || rugVote.score == null
            val marketTooThin = coin.liquidity < 10_000 || coin.volume24h < 5_000 || coin.transactions < 20
            if (report.blocked || rugNotCleared || marketTooThin || negativeNews || report.score < 55) null
            else {
                val reason = if (missingNews || missingSocial) {
                    "Πέρασε βασικά market/rug φίλτρα, αλλά λείπουν news/social δεδομένα. Παραμένει μόνο σε watchlist."
                } else {
                    "Πέρασε τα διαθέσιμα market, rug και sentiment φίλτρα. Απαιτείται ανθρώπινος έλεγχος πριν από αγορά."
                }
                MemeMasterPick(coin, report, reason)
            }
        }.sortedWith(
            compareByDescending<MemeMasterPick> { it.report.score }
                .thenByDescending { it.coin.liquidity }
                .thenByDescending { it.coin.volume24h }
        )
        val picks = ranked.filter { pick ->
            val newsVote = pick.report.votes.firstOrNull { it.name == "News Agent" }
            val socialVote = pick.report.votes.firstOrNull { it.name == "Social Agent (X + Reddit)" }
            newsVote?.score != null && newsVote.score > 25 && socialVote?.score != null && socialVote.score > 25 && pick.report.score >= 75
        }.take(5)
        val pickKeys = picks.map { it.coin.chain + ":" + it.coin.address }.toSet()
        val watchlist = ranked.filter { it.coin.chain + ":" + it.coin.address !in pickKeys }.take(8)
        return MemeMasterResult(picks, watchlist, evaluated.size, evaluated.size - picks.size - watchlist.size)
    }
}

@Composable
internal fun MemeAgentCouncilCard(coin: MemeCandidate, initialReport: MemeCouncilReport? = null) {
    var loading by remember(coin.address) { mutableStateOf(false) }
    var report by remember(coin.address) { mutableStateOf(initialReport) }
    var error by remember(coin.address) { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxWidth().background(Color(0xCC18233C), RoundedCornerShape(16.dp)).padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text("MEME COIN AGENT COUNCIL", color = Color(0xFF45D9FF), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
        Text("Πολλαπλοί εξειδικευμένοι έλεγχοι πριν από κάθε alert.", color = Color(0xFFA7B4D0), fontSize = 10.sp)
        Button(
            onClick = {
                scope.launch {
                    loading = true
                    error = ""
                    report = null
                    try { report = runMemeAgentCouncil(coin) }
                    catch (e: Exception) { error = e.message ?: "Αποτυχία ανάλυσης" }
                    finally { loading = false }
                }
            },
            enabled = !loading,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9B75FF))
        ) { Text(if (loading) "Οι agents ελέγχουν..." else "Εκτέλεση ελέγχου agents") }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color(0xFF9B75FF))
        if (error.isNotBlank()) Text(error, color = Color(0xFFFFC857), fontSize = 10.sp)
        report?.let { r ->
            Text("${r.score}/100", color = if (r.blocked) Color(0xFFFF7185) else Color(0xFF42E6B0), fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
            Text(r.verdict, color = if (r.blocked) Color(0xFFFF7185) else Color(0xFFFFC857), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            r.votes.forEach { vote ->
                Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = .045f), RoundedCornerShape(10.dp)).padding(9.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(vote.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                        Text(vote.score?.let { "${it}/100" } ?: "—", color = Color(0xFF45D9FF), fontSize = 10.sp)
                    }
                    Text(vote.verdict, color = if (vote.danger) Color(0xFFFF7185) else Color(0xFFFFC857), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(vote.detail, color = Color(0xFFA7B4D0), fontSize = 10.sp)
                }
            }
            Text(r.caveat, color = Color(0xFFFFC857), fontSize = 10.sp)
        }
    }
}
