
package gr.cryptohunter.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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

private val Bg = Color(0xFF0B1020)
private val Panel = Color(0xFF151D31)
private val Green = Color(0xFF39D98A)
private val Muted = Color(0xFF9AA8C2)

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

    val demoSignals = listOf(
        Signal("BTC/USDT", "Binance", "Spot", "BUY", 86, "4H", "ΔΕΙΓΜΑ"),
        Signal("ETH/USDT", "OKX", "Futures", "WAIT", 72, "1H", "ΔΕΙΓΜΑ"),
        Signal("SOL/USDT", "Bybit", "Spot", "BUY", 81, "15m", "ΔΕΙΓΜΑ")
    )

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Bg,
            surface = Panel,
            primary = Green,
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Bg)
                .padding(18.dp)
        ) {
            Text(
                "CRYPTO HUNTER AI",
                color = Green,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                "Κυνήγησε ευκαιρίες με κανόνες.",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Spot • Futures • Multi-exchange",
                color = Muted
            )

            Spacer(Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Επισκόπηση", "Σήματα", "Ρυθμίσεις").forEach {
                    FilterChip(
                        selected = tab == it,
                        onClick = { tab = it },
                        label = { Text(it) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            when (tab) {
                "Επισκόπηση" -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Panel
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "BITCOIN LIVE PRICE",
                                color = Muted
                            )

                            Text(
                                "$$btcPrice USDT",
                                color = Green,
                                fontSize = 27.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(Modifier.height(6.dp))

                            Text(
                                connection,
                                color = if (
                                    connection.startsWith("Επιτυχής")
                                ) Green else Color(0xFFFFC857),
                                fontSize = 12.sp
                            )

                            Text(
                                "Ανανέωση κάθε 30 δευτερόλεπτα",
                                color = Muted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        "Στρατηγική ανάλυσης",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        "EMA • RSI • MACD • Όγκος • Support/Resistance",
                        color = Muted,
                        fontSize = 13.sp
                    )

                    Spacer(Modifier.height(16.dp))

                    Text(
                        "Ενδεικτικά σήματα",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )

                    Text(scanStatus, color = Muted, fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    if (technicalSignals.isNotEmpty()) TechnicalSignalList(technicalSignals)
                    else SignalList(demoSignals)
                }

                "Σήματα" -> {
                    Text("Πραγματική τεχνική ανάλυση • Top 10", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(scanStatus, color = Muted, fontSize = 11.sp)
                    if (technicalSignals.isNotEmpty()) TechnicalSignalList(technicalSignals)
                    Spacer(Modifier.height(12.dp))
                    Text("Top 50 Binance • USDT", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Κατάταξη βάσει όγκου 24ώρου, όχι κεφαλαιοποίησης.", color = Muted, fontSize = 11.sp)
                    if (marketsError.isNotBlank()) {
                        Text(marketsError, color = Color(0xFFFFC857), fontSize = 12.sp)
                    }
                    if (markets.isEmpty()) {
                        Text("Δεν υπάρχουν ακόμη δεδομένα αγοράς.", color = Muted)
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(markets) { market ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Panel),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(market.symbol, color = Color.White, fontWeight = FontWeight.Bold)
                                        Text("${market.price} USDT", color = Green)
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    Text(
                        "Ρυθμίσεις",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(10.dp))

                    Text("Αγορές: Spot και Futures", color = Muted)
                    Text("Σύμπαν: Top 50 crypto", color = Muted)
                    Text("Binance: Δημόσια δεδομένα τιμών", color = Muted)
                    Text("Push notifications: Υπό ανάπτυξη", color = Muted)
                    Text("Γλώσσα: Ελληνικά", color = Muted)
                }
            }

            Spacer(Modifier.weight(1f))

            Text(
                "Εκπαιδευτικό πρωτότυπο • Όχι επενδυτική συμβουλή",
                color = Muted,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun SignalList(signals: List<Signal>) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(signals) { s ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Panel
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            s.symbol,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            s.direction,
                            color = if (s.direction == "BUY")
                                Green else Color(0xFFFFC857),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(5.dp))

                    Text(
                        "${s.venue} • ${s.market} • ${s.timeframe}",
                        color = Muted,
                        fontSize = 12.sp
                    )

                    Text(
                        "Βαθμολογία: ${s.score}/100 • ${s.status}",
                        color = Green,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun TechnicalSignalList(signals: List<TechnicalSignal>) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(signals) { signal ->
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(signal.symbol, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(signal.direction, color = when (signal.direction) {
                            "BUY" -> Green
                            "SELL" -> Color(0xFFFF6677)
                            else -> Color(0xFFFFC857)
                        }, fontWeight = FontWeight.Bold)
                    }
                    Text("Βαθμολογία ${signal.score}/100 • ${signal.timeframe}", color = Muted, fontSize = 12.sp)
                    Text("Τιμή: ${signal.price}", color = Green, fontSize = 12.sp)
                    Text(signal.explanation, color = Muted, fontSize = 11.sp)
                }
            }
        }
    }
}
