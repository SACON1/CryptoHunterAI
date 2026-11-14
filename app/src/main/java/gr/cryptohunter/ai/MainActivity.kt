
package gr.cryptohunter.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val Bg = Color(0xFF080D1D)
private val Panel = Color(0xCC17213A)
private val Green = Color(0xFF42E6B0)
private val Muted = Color(0xFFA7B4D0)
private val Violet = Color(0xFF9B75FF)
private val Cyan = Color(0xFF45D9FF)

data class Signal(
    val symbol: String,
    val venue: String,
    val market: String,
    val direction: String,
    val score: Int,
    val timeframe: String,
    val status: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CryptoHunterApp() }
    }
}

@Composable
fun CryptoHunterApp() {
    var tab by remember { mutableStateOf("Επισκόπηση") }
    var btcPrice by remember { mutableStateOf("--") }
    var connection by remember { mutableStateOf("Αναμονή σύνδεσης...") }
    var markets by remember { mutableStateOf<List<MarketTicker>>(emptyList()) }
    var marketsError by remember { mutableStateOf("") }
    var technicalSignals by remember { mutableStateOf<List<TechnicalSignal>>(emptyList()) }
    var scanStatus by remember { mutableStateOf("Η τεχνική ανάλυση δεν έχει ξεκινήσει.") }

    LaunchedEffect(Unit) {
        while (true) {
            try {
                val price = BinanceApi.getBitcoinPrice()
                btcPrice = price
                connection = "Επιτυχής σύνδεση Binance"
                try {
                    markets = BinanceApi.getTopUsdtMarkets()
                    marketsError = ""
                    scanStatus = "Ανάλυση EMA, RSI, MACD και όγκου..."
                    technicalSignals = BinanceTechnicalApi.scan(markets.take(10).map { it.symbol })
                    scanStatus = "Ολοκληρώθηκε ανάλυση ${technicalSignals.size} αγορών • 1H"
                } catch (marketException: Exception) {
                    marketsError = marketException.message ?: "Αποτυχία φόρτωσης αγορών"
                    scanStatus = "Δεν ήταν δυνατή η ανάλυση αγοράς."
                }
            } catch (e: Exception) {
                connection = "Σφάλμα: ${e.message ?: "Άγνωστο σφάλμα"}"
            }

            delay(30_000)
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(background = Bg, surface = Panel, primary = Green, onBackground = Color.White, onSurface = Color.White)
    ) {
        Column(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF111A35), Bg, Color(0xFF080B17))))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                androidx.compose.foundation.Image(
                    painter = painterResource(id = R.drawable.ic_crypto_hunter),
                    contentDescription = "Crypto Hunter AI",
                    modifier = Modifier.size(52.dp),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("CRYPTO HUNTER", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    Text("AI  •  SMART MARKET RADAR", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    if (connection.startsWith("Επιτυχής")) "● LIVE" else "● OFFLINE",
                    color = if (connection.startsWith("Επιτυχής")) Green else Color(0xFFFFC857),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(22.dp))
            Text("Η αγορά, με", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
            Text("άλλη ματιά.", color = Violet, fontSize = 29.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Spot  •  Futures  •  Multi-exchange", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Επισκόπηση", "Σήματα", "Ρυθμίσεις").forEach { item ->
                    GlassChip(label = item, selected = tab == item) { tab = item }
                }
            }
            Spacer(Modifier.height(18.dp))
            when (tab) {
                "Επισκόπηση" -> {
                    GlassCard(accent = Cyan) {
                        Text("BITCOIN  /  LIVE PRICE", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(7.dp))
                        Text("$$btcPrice", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                        Text("USDT", color = Green, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("●", color = if (connection.startsWith("Επιτυχής")) Green else Color(0xFFFFC857))
                            Spacer(Modifier.width(7.dp))
                            Text(connection, color = Muted, fontSize = 11.sp)
                        }
                        Text("Αυτόματη ανανέωση κάθε 30 sec", color = Muted, fontSize = 10.sp)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassCard(Modifier.weight(1f), accent = Violet) {
                            Text("TOP 50", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("${markets.size}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Text("Αγορές", color = Muted, fontSize = 11.sp)
                        }
                        GlassCard(Modifier.weight(1f), accent = Green) {
                            Text("TECH SCAN", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("${technicalSignals.size}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Text("Αναλύσεις", color = Muted, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    SectionTitle("Στρατηγική ανάλυσης", "MULTI-INDICATOR ENGINE")
                    Text("EMA 20/50  •  RSI 14  •  MACD  •  Volume  •  ATR", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(18.dp))
                    SectionTitle("Σήματα αγοράς", scanStatus)
                    if (technicalSignals.isNotEmpty()) {
                        TechnicalSignalList(technicalSignals.take(8))
                    } else {
                        GlassCard(accent = Violet, compact = true) {
                            Text("Δεν υπάρχουν ακόμη επιβεβαιωμένα τεχνικά σήματα.", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("Τα σήματα θα εμφανιστούν μόλις ολοκληρωθεί επιτυχώς η ανάλυση δεδομένων.", color = Muted, fontSize = 11.sp)
                        }
                    }
                }
                "Σήματα" -> {
                    SectionTitle("Τεχνικά σήματα", "TOP 10 • BINANCE • 1H")
                    Text(scanStatus, color = Muted, fontSize = 11.sp)
                    Spacer(Modifier.height(10.dp))
                    if (technicalSignals.isNotEmpty()) {
                        TechnicalSignalList(technicalSignals)
                    } else {
                        GlassCard(accent = Violet, compact = true) {
                            Text("Δεν υπάρχουν ακόμη επιβεβαιωμένα τεχνικά σήματα.", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("Έλεγξε τη σύνδεση και δοκίμασε ξανά σε λίγο.", color = Muted, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    SectionTitle("Αγορές Top 50", "Κατάταξη βάσει όγκου 24ώρου")
                    if (marketsError.isNotBlank()) Text(marketsError, color = Color(0xFFFFC857), fontSize = 12.sp)
                    markets.take(50).forEach { market ->
                        GlassCard(accent = Cyan, compact = true) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(market.symbol, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("${market.price} USDT", color = Green, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                else -> {
                    SectionTitle("Ρυθμίσεις", "PERSONALIZE YOUR RADAR")
                    GlassCard(accent = Violet) {
                        Text("Αγορές", color = Muted, fontSize = 11.sp)
                        Text("Spot και Futures", color = Color.White, fontWeight = FontWeight.Bold)
                        Divider(color = Color.White.copy(alpha = 0.10f))
                        Text("Σύμπαν", color = Muted, fontSize = 11.sp)
                        Text("Top 50 crypto", color = Color.White, fontWeight = FontWeight.Bold)
                        Divider(color = Color.White.copy(alpha = 0.10f))
                        Text("Ανταλλακτήριο δεδομένων", color = Muted, fontSize = 11.sp)
                        Text("Binance • Δημόσια δεδομένα", color = Color.White, fontWeight = FontWeight.Bold)
                        Divider(color = Color.White.copy(alpha = 0.10f))
                        Text("Push notifications", color = Muted, fontSize = 11.sp)
                        Text("Υπό ανάπτυξη", color = Color(0xFFFFC857), fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("CRYPTO HUNTER AI  •  Εκπαιδευτικό εργαλείο — όχι επενδυτική συμβουλή", color = Muted, fontSize = 9.sp)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    accent: Color = Violet,
    compact: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier.fillMaxWidth()
            .background(Brush.linearGradient(listOf(Color(0xD91D2945), Color(0xB3101830), Color(0xC91C2540))), RoundedCornerShape(22.dp))
            .border(1.dp, Brush.linearGradient(listOf(accent.copy(alpha = 0.55f), Color.White.copy(alpha = 0.10f), Violet.copy(alpha = 0.25f))), RoundedCornerShape(22.dp))
            .padding(if (compact) 13.dp else 17.dp),
        content = content
    )
}

@Composable
private fun GlassChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) Violet.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.045f),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Violet.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.14f))
    ) {
        Text(label, Modifier.padding(horizontal = 13.dp, vertical = 10.dp), color = if (selected) Color.White else Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    Text(subtitle, color = Muted, fontSize = 9.sp)
    Spacer(Modifier.height(9.dp))
}

@Composable
fun SignalList(signals: List<Signal>) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        signals.forEach { s ->
            GlassCard(accent = if (s.direction == "BUY") Green else Violet, compact = true) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(s.symbol, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(s.direction, color = if (s.direction == "BUY") Green else Color(0xFFFFC857), fontWeight = FontWeight.Bold)
                }
                Text("${s.venue}  •  ${s.market}  •  ${s.timeframe}", color = Muted, fontSize = 11.sp)
                Text("Βαθμολογία: ${s.score}/100  •  ${s.status}", color = Green, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun TechnicalSignalList(signals: List<TechnicalSignal>) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        signals.forEach { signal ->
            GlassCard(accent = when (signal.direction) { "BUY" -> Green; "SELL" -> Color(0xFFFF6677); else -> Violet }, compact = true) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(signal.symbol, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(signal.direction, color = when (signal.direction) { "BUY" -> Green; "SELL" -> Color(0xFFFF6677); else -> Color(0xFFFFC857) }, fontWeight = FontWeight.Bold)
                }
                Text("Βαθμολογία ${signal.score}/100  •  ${signal.timeframe}", color = Muted, fontSize = 11.sp)
                Text("Entry: ${signal.price}", color = Green, fontSize = 11.sp)
                signal.stopLoss?.let { Text("Stop Loss: $it", color = Color(0xFFFF6677), fontSize = 11.sp) }
                signal.takeProfit?.let { Text("Take Profit: $it", color = Green, fontSize = 11.sp) }
                signal.riskReward?.let { Text("Risk / Reward: 1:${String.format(java.util.Locale.US, "%.1f", it)}", color = Color(0xFFFFC857), fontSize = 11.sp) }
                Text(signal.explanation, color = Muted, fontSize = 10.sp)
            }
        }
    }
}
