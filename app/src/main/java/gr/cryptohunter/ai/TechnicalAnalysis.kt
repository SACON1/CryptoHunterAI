package gr.cryptohunter.ai

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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
    val riskReward: Double?
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
        val explanation = "EMA20 ${fmt(ema20)} • EMA50 ${fmt(ema50)} • RSI ${fmt(rsi)} • MACD ${if (macdLine > macdSignal) "θετικό" else "αρνητικό"} • Όγκος ${fmt(volumeRatio)}x"
        return TechnicalSignal(symbol, direction, score, last, "1H", explanation, stopLoss, takeProfit, riskReward)
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
            max(
                current.high - current.low,
                max(abs(current.high - previous.close), abs(current.low - previous.close))
            )
        }
        return ranges.takeLast(period).average().takeIf { it.isFinite() && it > 0.0 }
            ?: candles.last().close * 0.01
    }

    private fun fmt(value: Double): String = String.format(java.util.Locale.US, "%.2f", value)
}
