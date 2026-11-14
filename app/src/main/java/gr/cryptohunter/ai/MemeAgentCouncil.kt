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

    votes += MemeAgentVote(
        "News & Social Agent",
        "ΑΝΑΜΟΝΗ ΣΥΝΔΕΣΗΣ",
        "Δεν έχει συνδεθεί ακόμη αξιόπιστη live ροή ειδήσεων και social sentiment. Δεν προσμετράται θετικό news score.",
        null
    )

    try {
        val pulse = MemeNewsSocialAgent.analyze(coin)
        val newsDetail = if (pulse.score == null) {
            pulse.note
        } else {
            "Sentiment score ${pulse.score}/100 • θετικές λέξεις: ${pulse.positive} • αρνητικές λέξεις: ${pulse.negative}. ${pulse.note}"
        }
        votes += MemeAgentVote(
            "News & Social Agent",
            when {
                pulse.score == null -> "ΜΗ ΔΙΑΘΕΣΙΜΑ ΔΕΔΟΜΕΝΑ"
                pulse.score >= 65 -> "ΘΕΤΙΚΟ SENTIMENT — ΜΗ ΕΠΙΒΕΒΑΙΩΜΕΝΟ"
                pulse.score <= 35 -> "ΑΡΝΗΤΙΚΟ SENTIMENT — ΧΡΕΙΑΖΕΤΑΙ ΕΛΕΓΧΟΣ"
                else -> "ΟΥΔΕΤΕΡΟ SENTIMENT"
            },
            newsDetail + if (pulse.items.isEmpty()) "" else " Πηγές: " + pulse.items.joinToString(" • ") { it.source },
            pulse.score,
            false
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
        "Rule-based αξιολόγηση πολλών agents με δημόσιες αναφορές Google News και Reddit. Το sentiment δεν επιβεβαιώνει γεγονότα και το αποτέλεσμα δεν είναι πρόβλεψη κέρδους."
    )
}

@Composable
internal fun MemeAgentCouncilCard(coin: MemeCandidate) {
    var loading by remember(coin.address) { mutableStateOf(false) }
    var report by remember(coin.address) { mutableStateOf<MemeCouncilReport?>(null) }
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
