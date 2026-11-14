
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
    var btcPrice by remember { mutableStateOf("Φόρτωση...") }
    var connection by remember { mutableStateOf("Σύνδεση...") }

    LaunchedEffect(Unit) {
        while (true) {
            try {
                btcPrice = BinanceApi.getBitcoinPrice()
                connection = "Συνδεδεμένο με Binance"
            } catch (e: Exception) {
                connection = "Αποτυχία σύνδεσης"
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
            Modifier.fillMaxSize().background(Bg).padding(18.dp)
        ) {
            Text("CRYPTO HUNTER AI", color = Green,
                fontSize = 13.sp, fontWeight = FontWeight.Bold)

            Text("Κυνήγησε ευκαιρίες με κανόνες.",
                color = Color.White, fontSize = 24.sp,
                fontWeight = FontWeight.Bold)

            Spacer(Modifier.height(8.dp))
            Text("Spot • Futures • Multi-exchange", color = Muted)

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
                        colors = CardDefaults.cardColors(containerColor = Panel)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("BITCOIN LIVE PRICE", color = Muted)
                            Text(
                                "$$btcPrice USDT",
                                color = Green,
                                fontSize = 27.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(connection, color = Muted, fontSize = 12.sp)
                            Text("Ανανέωση κάθε 30 δευτερόλεπτα",
                                color = Muted, fontSize = 11.sp)
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text("Στρατηγική ανάλυσης",
                        color = Color.White, fontWeight = FontWeight.Bold)
                    Text("EMA • RSI • MACD • Όγκος • Support/Resistance",
                        color = Muted, fontSize = 13.sp)

                    Spacer(Modifier.height(16.dp))
                    Text("Ενδεικτικά σήματα",
                        color = Color.White, fontWeight = FontWeight.Bold)
                    SignalList(demoSignals)
                }

                "Σήματα" -> SignalList(demoSignals)

                else -> {
                    Text("Ρυθμίσεις", color = Color.White,
                        fontWeight = FontWeight.Bold)
                    Text("Αγορές: Spot και Futures", color = Muted)
                    Text("Σύμπαν: Top 50 crypto", color = Muted)
                    Text("Binance: δημόσια σύνδεση τιμών", color = Muted)
                    Text("Push notifications: υπό ανάπτυξη", color = Muted)
                }
            }

            Spacer(Modifier.weight(1f))
            Text("Εκπαιδευτικό πρωτότυπο • Όχι επενδυτική συμβουλή",
                color = Muted, fontSize = 10.sp)
        }
    }
}

@Composable
fun SignalList(signals: List<Signal>) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(signals) { s ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(s.symbol, color = Color.White,
                            fontWeight = FontWeight.Bold)
                        Text(s.direction,
                            color = if (s.direction == "BUY") Green
                            else Color(0xFFFFC857),
                            fontWeight = FontWeight.Bold)
                    }
                    Text("${s.venue} • ${s.market} • ${s.timeframe}",
                        color = Muted, fontSize = 12.sp)
                    Text("Βαθμολογία: ${s.score}/100 • ${s.status}",
                        color = Green, fontSize = 12.sp)
                }
            }
        }
    }
}
