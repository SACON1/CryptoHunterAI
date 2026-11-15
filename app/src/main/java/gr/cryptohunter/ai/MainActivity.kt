
package gr.cryptohunter.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

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
    private val openedSignalSymbol = mutableStateOf<String?>(null)
    private val openMemeTab = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openedSignalSymbol.value = intent.getStringExtra(EXTRA_SIGNAL_SYMBOL)
        openMemeTab.value = intent.getBooleanExtra(EXTRA_OPEN_MEME_TAB, false)
        scheduleBackgroundMarketScan(applicationContext)
        MemeRadarAlerts.createChannel(applicationContext)
        scheduleMemeRadarWork(applicationContext)
        setContent {
            CryptoHunterApp(
                notificationSymbol = openedSignalSymbol.value,
                onNotificationHandled = { openedSignalSymbol.value = null },
                openMemeTab = openMemeTab.value,
                onMemeTabHandled = { openMemeTab.value = false }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openedSignalSymbol.value = intent.getStringExtra(EXTRA_SIGNAL_SYMBOL)
        openMemeTab.value = intent.getBooleanExtra(EXTRA_OPEN_MEME_TAB, false)
    }

    companion object {
        const val EXTRA_SIGNAL_SYMBOL = "crypto_hunter_signal_symbol"
        const val EXTRA_OPEN_MEME_TAB = "crypto_hunter_open_meme_tab"
    }
}

@Composable
fun CryptoHunterApp(notificationSymbol: String? = null, onNotificationHandled: () -> Unit = {}, openMemeTab: Boolean = false, onMemeTabHandled: () -> Unit = {}) {
    val context = LocalContext.current
    var notificationsEnabled by remember { mutableStateOf(SignalNotifications.areNotificationsEnabled(context)) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) SignalNotifications.createChannel(context)
        notificationsEnabled = SignalNotifications.areNotificationsEnabled(context)
    }
    var tab by remember { mutableStateOf(if (openMemeTab) "Meme Coins" else "Επισκόπηση") }
    LaunchedEffect(openMemeTab) { if (openMemeTab) { tab = "Meme Coins"; onMemeTabHandled() } }
    var btcPrice by remember { mutableStateOf("--") }
    var connection by remember { mutableStateOf("Αναμονή σύνδεσης...") }
    var markets by remember { mutableStateOf<List<MarketTicker>>(emptyList()) }
    var marketsError by remember { mutableStateOf("") }
    var technicalSignals by remember { mutableStateOf<List<TechnicalSignal>>(emptyList()) }
    var scanStatus by remember { mutableStateOf("Η τεχνική ανάλυση δεν έχει ξεκινήσει.") }
    var selectedSignal by remember { mutableStateOf<TechnicalSignal?>(null) }
    LaunchedEffect(tab) { notificationsEnabled = SignalNotifications.areNotificationsEnabled(context) }
    LaunchedEffect(technicalSignals, notificationSymbol) {
        val symbol = notificationSymbol
        if (!symbol.isNullOrBlank()) {
            val match = technicalSignals.firstOrNull { it.symbol.equals(symbol, ignoreCase = true) }
            if (match != null) {
                selectedSignal = match
                onNotificationHandled()
            }
        }
    }
    val pulse = rememberInfiniteTransition(label = "live-pulse")
    val liveAlpha by pulse.animateFloat(0.45f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "live-alpha")

    LaunchedEffect(Unit) {
        SignalNotifications.createChannel(context)
        var lastTechnicalScanAt = 0L
        while (true) {
            try {
                val price = BinanceApi.getBitcoinPrice()
                btcPrice = price
                connection = "Επιτυχής σύνδεση Binance"
                try {
                    markets = BinanceApi.getTopUsdtMarkets()
                    marketsError = ""
                    val now = System.currentTimeMillis()
                    if (now - lastTechnicalScanAt >= 5 * 60 * 1000L) {
                        scanStatus = "Ανάλυση και των 50 αγορών με EMA, RSI, MACD και όγκο..."
                        technicalSignals = BinanceTechnicalApi.scan(markets.take(50).map { it.symbol })
                        SignalNotifications.notifyStrongSignals(context, technicalSignals)
                        lastTechnicalScanAt = now
                        val buyCount = technicalSignals.count { it.direction == "BUY" }
                        val sellCount = technicalSignals.count { it.direction == "SELL" }
                        scanStatus = "Ελέγχθηκαν ${markets.take(50).size} αγορές • BUY: ${buyCount} • SELL: ${sellCount} • WAIT: ${technicalSignals.count { it.direction == "WAIT" }}"
                    }
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
        if (selectedSignal != null) {
            SignalDetailScreen(selectedSignal!!, onBack = { selectedSignal = null })
        } else {
        Column(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF172344), Color(0xFF0B1228), Bg, Color(0xFF070B16))))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                androidx.compose.foundation.Image(
                    painter = painterResource(id = R.drawable.ic_crypto_hunter),
                    contentDescription = "Crypto Hunter AI",
                    modifier = Modifier.size(70.dp)
                        .background(Brush.linearGradient(listOf(Color(0xFF243B67), Color(0xFF151C3A))), RoundedCornerShape(22.dp))
                        .border(1.dp, Brush.linearGradient(listOf(Cyan.copy(alpha = 0.75f), Violet.copy(alpha = 0.65f))), RoundedCornerShape(22.dp))
                        .padding(4.dp)
                        .graphicsLayer { scaleX = 0.985f + liveAlpha * 0.025f; scaleY = 0.985f + liveAlpha * 0.025f },
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("CRYPTO HUNTER", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                    Text("AI  •  SMART MARKET RADAR", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    if (connection.startsWith("Επιτυχής")) "● LIVE" else "● OFFLINE",
                    color = (if (connection.startsWith("Επιτυχής")) Green else Color(0xFFFFC857)).copy(alpha = liveAlpha),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(24.dp))
            GlassCard(accent = Violet) {
                Text("AI MARKET INTELLIGENCE", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                Text("Η αγορά, με", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                Text("άλλη ματιά.", color = Violet, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                Text("Spot  •  Futures  •  Meme Coins  •  Multi-agent research", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(Green.copy(alpha = liveAlpha), RoundedCornerShape(50)))
                    Text(if (connection.startsWith("Επιτυχής")) "RADAR ΣΕ ΛΕΙΤΟΥΡΓΙΑ" else "ΣΥΝΔΕΣΗ ΣΕ ΕΛΕΓΧΟ", color = if (connection.startsWith("Επιτυχής")) Green else Color(0xFFFFC857), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Επισκόπηση", "Σήματα", "Meme Coins", "Agent", "Ρυθμίσεις").forEach { item ->
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
                        TechnicalSignalList(technicalSignals.take(8), onOpen = { selectedSignal = it })
                    } else {
                        GlassCard(accent = Violet, compact = true) {
                            Text("Δεν υπάρχουν ακόμη επιβεβαιωμένα τεχνικά σήματα.", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("Τα σήματα θα εμφανιστούν μόλις ολοκληρωθεί επιτυχώς η ανάλυση δεδομένων.", color = Muted, fontSize = 11.sp)
                        }
                    }
                }
                "Meme Coins" -> {
                    MemeCoinRadarScreen()
                }
                "Agent" -> {
                    AgentResearchScreen()
                }
                "Σήματα" -> {
                    SectionTitle("Τεχνικά σήματα", "TOP 50 • BINANCE • 1H • ΑΝΑΝΕΩΣΗ ΑΝΑ 5 ΛΕΠΤΑ")
                    Text(scanStatus, color = Muted, fontSize = 11.sp)
                    Spacer(Modifier.height(10.dp))
                    if (technicalSignals.isNotEmpty()) {
                        TechnicalSignalList(technicalSignals, onOpen = { selectedSignal = it })
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
                        Text("Τοπικές ειδοποιήσεις για ισχυρά σήματα", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        Text(if (notificationsEnabled) "● ΕΙΔΟΠΟΙΗΣΕΙΣ ΕΝΕΡΓΕΣ" else "● ΕΙΔΟΠΟΙΗΣΕΙΣ ΑΝΕΝΕΡΓΕΣ", color = if (notificationsEnabled) Green else Color(0xFFFFC857), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            if (!notificationsEnabled && Build.VERSION.SDK_INT >= 33) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else if (!notificationsEnabled) {
                                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply { putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName) })
                            } else {
                                SignalNotifications.sendTestNotification(context)
                            }
                        }, colors = ButtonDefaults.buttonColors(containerColor = Violet)) {
                            Text(if (notificationsEnabled) "Δοκιμή ειδοποίησης" else "Ενεργοποίηση ειδοποιήσεων", color = Color.White)
                        }
                        OutlinedButton(onClick = {
                            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply { putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName) })
                        }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Muted)) {
                            Text("Ρυθμίσεις ειδοποιήσεων κινητού")
                        }
                        Text("Ειδοποίηση για βαθμολογία ≥80 • έως μία ανά σύμβολο/κατεύθυνση ανά 6 ώρες", color = Muted, fontSize = 10.sp)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("CRYPTO HUNTER AI  •  Εκπαιδευτικό εργαλείο — όχι επενδυτική συμβουλή", color = Muted, fontSize = 9.sp)
            Spacer(Modifier.height(8.dp))
        }
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
            .background(Brush.linearGradient(listOf(Color(0xE6253556), Color(0xD914203B), Color(0xE61A2544))), RoundedCornerShape(26.dp))
            .border(1.dp, Brush.linearGradient(listOf(accent.copy(alpha = 0.72f), Color.White.copy(alpha = 0.13f), Violet.copy(alpha = 0.38f))), RoundedCornerShape(26.dp))
            .padding(if (compact) 14.dp else 19.dp),
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
    Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp)
    Text(subtitle, color = Muted, fontSize = 9.sp, letterSpacing = 1.0.sp)
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
fun TechnicalSignalList(signals: List<TechnicalSignal>, onOpen: (TechnicalSignal) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        signals.forEach { signal ->
            GlassCard(accent = if (signal.direction == "BUY") Green else Violet, compact = true) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(signal.symbol, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("${signal.timeframe} • Βαθμολογία ${signal.score}/100", color = Muted, fontSize = 11.sp)
                    }
                    Text(signal.direction, color = if (signal.direction == "BUY") Green else if (signal.direction == "SELL") Color(0xFFFF6677) else Color(0xFFFFC857), fontWeight = FontWeight.ExtraBold)
                }
                Text(signal.explanation.substringBefore("\n"), color = Muted, fontSize = 10.sp, maxLines = 2)
                Button(onClick = { onOpen(signal) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Violet)) {
                    Text("Άνοιγμα πλήρους ανάλυσης  ↗")
                }
            }
        }
    }
}

@Composable
private fun SignalDetailScreen(signal: TechnicalSignal, onBack: () -> Unit) {
    var info by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF111A35), Bg)))
            .verticalScroll(rememberScrollState()).padding(18.dp)
    ) {
        TextButton(onClick = onBack) { Text("← Πίσω στα σήματα", color = Cyan) }
        Text("ΑΝΑΛΥΣΗ ΝΟΜΙΣΜΑΤΟΣ", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(signal.symbol, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(12.dp))
        GlassCard(accent = Violet) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("ΑΠΟΤΕΛΕΣΜΑ", color = Muted)
                Text(signal.direction, color = if (signal.direction == "BUY") Green else if (signal.direction == "SELL") Color(0xFFFF6677) else Color(0xFFFFC857), fontWeight = FontWeight.Bold)
            }
            Text("${signal.score}/100", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
            Text("Χρονικό πλαίσιο: ${signal.timeframe}", color = Muted)
            Divider(color = Color.White.copy(alpha = 0.12f))
            Text("Τιμή αναφοράς: ${signal.price}", color = Green)
            signal.stopLoss?.let { Text("Stop Loss: $it", color = Color(0xFFFF6677)) }
            signal.takeProfit?.let { Text("Take Profit: $it", color = Green) }
            signal.riskReward?.let { Text("Risk / Reward: 1:${String.format(java.util.Locale.US, "%.1f", it)}", color = Color(0xFFFFC857)) }
            Text("Ενδεικτικά επίπεδα — όχι εντολή αγοράς.", color = Muted, fontSize = 10.sp)
        }
        Spacer(Modifier.height(18.dp))
        Text("Τεχνικές με απλή εξήγηση", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        signal.indicatorDetails.forEach { (name, description) ->
            GlassCard(accent = Cyan, compact = true) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(name, Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { info = description }) { Text("ⓘ Info", color = Cyan) }
                }
                Text(description.substringAfter("Τώρα:", description), color = Muted, fontSize = 11.sp, maxLines = 3)
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("Εκπαιδευτική ανάλυση — όχι επενδυτική συμβουλή.", color = Muted, fontSize = 10.sp)
    }
    info?.let { text ->
        AlertDialog(onDismissRequest = { info = null }, title = { Text("Τι σημαίνει;") }, text = { Text(text) },
            confirmButton = { TextButton(onClick = { info = null }) { Text("Κατάλαβα") } },
            containerColor = Panel, titleContentColor = Color.White, textContentColor = Muted)
    }
}

private fun scheduleBackgroundMarketScan(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val request = PeriodicWorkRequestBuilder<BackgroundMarketWorker>(
        15, TimeUnit.MINUTES
    )
        .setConstraints(constraints)
        .setBackoffCriteria(
            androidx.work.BackoffPolicy.EXPONENTIAL,
            30,
            TimeUnit.SECONDS
        )
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "crypto_hunter_background_scan",
        ExistingPeriodicWorkPolicy.KEEP,
        request
    )
}
