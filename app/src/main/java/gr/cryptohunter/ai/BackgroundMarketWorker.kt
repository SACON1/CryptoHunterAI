package gr.cryptohunter.ai

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException

/**
 * Periodic background scan. Android WorkManager enforces a minimum 15-minute
 * interval and may defer execution to protect battery.
 */
class BackgroundMarketWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            SignalNotifications.createChannel(applicationContext)
            val markets = BinanceApi.getTopUsdtMarkets()
            if (markets.isEmpty()) return Result.retry()

            // Scan the 50 highest-volume USDT pairs returned by Binance.
            val signals = BinanceTechnicalApi.scan(markets.take(50).map { it.symbol })
            SignalNotifications.notifyStrongSignals(applicationContext, signals)
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
