package gr.cryptohunter.ai

import kotlin.math.abs
import kotlin.math.max

data class Candle(
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

data class TechnicalSignal(
    val symbol: String,
    val direction: String,
    val score: Int,
    val price: Double,
    val timeframe: String,
    val explanation: String,
    val stopLoss: Double?,
    val takeProfit: Double?,
    val riskReward: Double?,
    val indicatorDetails: List<Pair<String, String>> = emptyList()
)

object TechnicalAnalysis {
    fun analyze(symbol: String, candles: List<Candle>): TechnicalSignal? {
        if (candles.size < 55) return null
        val closes = candles.map { it.close }
        val last = closes.last()
        if (last <= 0.0) return null

        val ema20 = ema(closes, 20)
        val ema50 = ema(closes, 50)
        val rsi = rsi(closes, 14)
        val macdLine = ema(closes, 12) - ema(closes, 26)
        val macdSeries = (26..closes.size).map { end ->
            val part = closes.take(end)
            ema(part, 12) - ema(part, 26)
        }
        val macdSignal = ema(macdSeries, 9)
        val recentVolumes = candles.takeLast(21).dropLast(1).map { it.volume }
        val avgVolume = recentVolumes.average().takeIf { it > 0.0 } ?: 0.0
        val volumeRatio = if (avgVolume > 0.0) candles.last().volume / avgVolume else 1.0
        val recent = candles.takeLast(20)
        val atr = atr(candles, 14)
        val support = recent.minOf { it.low }
        val resistance = recent.maxOf { it.high }

        val trend = when {
            last > ema20 && ema20 > ema50 -> 100
            last > ema20 -> 65
            last < ema20 && ema20 < ema50 -> 0
            else -> 35
        }
        val rsiScore = when {
            rsi in 50.0..68.0 -> 100
            rsi in 42.0..72.0 -> 65
            rsi > 78.0 || rsi < 28.0 -> 15
            else -> 35
        }
        val macdScore = if (macdLine > macdSignal && macdLine > 0) 100
            else if (macdLine > macdSignal) 65 else 20
        val volumeScore = when {
            volumeRatio >= 1.5 -> 100
            volumeRatio >= 1.1 -> 75
            else -> 35
        }
        val range = max(resistance - support, last * 0.0001)
        val supportScore = when {
            (last - support) / range < 0.25 -> 90
            (resistance - last) / range < 0.10 -> 75
            else -> 50
        }
        val score = (trend * 0.25 + rsiScore * 0.20 + macdScore * 0.20 +
            volumeScore * 0.15 + supportScore * 0.20).toInt().coerceIn(0, 100)
        val direction = when {
            score >= 75 && trend >= 65 && macdScore >= 65 -> "BUY"
            score <= 30 && trend <= 35 && macdScore <= 35 -> "SELL"
            else -> "WAIT"
        }
        val stopLoss = when (direction) {
            "BUY" -> last - atr * 1.5
            "SELL" -> last + atr * 1.5
            else -> null
        }
        val takeProfit = when (direction) {
            "BUY" -> last + atr * 3.0
            "SELL" -> last - atr * 3.0
            else -> null
        }
        val riskReward = if (stopLoss != null && takeProfit != null) {
            abs(takeProfit - last) / abs(last - stopLoss).coerceAtLeast(1e-12)
        } else null
        val buyMissing = buildList {
            if (score < 75) add("score κάτω από 75")
            if (trend < 65) add("τάση EMA μη επιβεβαιωμένη")
            if (macdScore < 65) add("MACD χωρίς ανοδική επιβεβαίωση")
        }
        val sellMissing = buildList {
            if (score > 30) add("score πάνω από 30")
            if (trend > 35) add("τάση EMA δεν είναι καθοδική")
            if (macdScore > 35) add("MACD δεν επιβεβαιώνει πτώση")
        }
        val decisionNote = when (direction) {
            "BUY" -> "BUY: επιβεβαιώθηκαν score, EMA και MACD"
            "SELL" -> "SELL: επιβεβαιώθηκαν score, EMA και MACD"
            else -> "WAIT • Για BUY: ${buyMissing.joinToString(", ")} • Για SELL: ${sellMissing.joinToString(", ")}"
        }
        val details = listOf(
            "EMA 20/50" to "Τι είναι: εκθετικοί κινητοί μέσοι που δείχνουν την κατεύθυνση της τάσης. Τώρα: EMA20 ${fmt(ema20)}, EMA50 ${fmt(ema50)}, τιμή ${fmt(last)}. ${when { last > ema20 && ema20 > ema50 -> "Θετική διάταξη, ενισχύει την ανοδική τάση."; last < ema20 && ema20 < ema50 -> "Αρνητική διάταξη, δείχνει καθοδική τάση."; else -> "Οι μέσοι δεν έχουν πλήρη ανοδική ή καθοδική συμφωνία." }}",
            "RSI 14" to "Τι είναι: δείκτης ορμής από 0 έως 100. Τώρα: ${fmt(rsi)}. Συνήθως πάνω από 70 θεωρείται υπεραγορασμένο και κάτω από 30 υπερπουλημένο· εδώ η ζώνη 50–68 βαθμολογείται θετικά χωρίς να αποτελεί μόνη της σήμα.",
            "MACD" to "Τι είναι: συγκρίνει εκθετικούς μέσους για να εκτιμήσει ορμή και αλλαγή τάσης. Τώρα: ${if (macdLine > macdSignal) "η γραμμή MACD είναι πάνω από τη γραμμή σήματος" else "η γραμμή MACD είναι κάτω από τη γραμμή σήματος"} και ${if (macdLine > 0) "πάνω από το μηδέν" else "κάτω από το μηδέν"}.",
            "Όγκος" to "Τι είναι: μετρά τη δραστηριότητα συναλλαγών σε σχέση με τον πρόσφατο μέσο όρο. Τώρα: ${fmt(volumeRatio)}x του μέσου όγκου 20 κεριών. Μεγαλύτερος όγκος μπορεί να επιβεβαιώσει μια κίνηση, όχι όμως να εγγυηθεί συνέχειά της.",
            "Στήριξη / Αντίσταση" to "Τι είναι: πρόσφατες ζώνες όπου η τιμή έχει βρει αγοραστές ή πωλητές. Στο τελευταίο παράθυρο 20 κεριών: στήριξη περίπου ${fmt(support)}, αντίσταση περίπου ${fmt(resistance)}. Είναι ενδεικτικά επίπεδα, όχι εγγυημένα όρια.",
            "ATR 14" to "Τι είναι: μέσο πραγματικό εύρος, δηλαδή μέτρο μεταβλητότητας και όχι κατεύθυνσης. Τώρα: ${fmt(atr)}. Χρησιμοποιείται από το πρωτότυπο για ενδεικτικό Stop Loss 1.5×ATR και Take Profit 3×ATR όταν υπάρχει BUY ή SELL."
        )
        val explanation = "$decisionNote\nEMA20 ${fmt(ema20)} • EMA50 ${fmt(ema50)} • RSI ${fmt(rsi)} • MACD ${if (macdLine > macdSignal) "θετικό" else "αρνητικό"} • Όγκος ${fmt(volumeRatio)}x"
        return TechnicalSignal(symbol, direction, score, last, "1H", explanation, stopLoss, takeProfit, riskReward, details)
    }

    private fun ema(values: List<Double>, period: Int): Double {
        if (values.isEmpty()) return 0.0
        val k = 2.0 / (period + 1.0)
        return values.drop(1).fold(values.first()) { previous, value ->
            value * k + previous * (1.0 - k)
        }
    }

    private fun rsi(values: List<Double>, period: Int): Double {
        if (values.size <= period) return 50.0
        val changes = values.zipWithNext { a, b -> b - a }.takeLast(period)
        val gains = changes.filter { it > 0 }.sum() / period
        val losses = -changes.filter { it < 0 }.sum() / period
        if (losses == 0.0) return 100.0
        val rs = gains / losses
        return 100.0 - 100.0 / (1.0 + rs)
    }

    private fun atr(candles: List<Candle>, period: Int): Double {
        val ranges = candles.zipWithNext().map { (previous, current) ->
            max(current.high - current.low, max(abs(current.high - previous.close), abs(current.low - previous.close)))
        }
        return ranges.takeLast(period).average().takeIf { it.isFinite() && it > 0.0 }
            ?: candles.last().close * 0.01
    }

    private fun fmt(value: Double): String = String.format(java.util.Locale.US, "%.6f", value)
}
