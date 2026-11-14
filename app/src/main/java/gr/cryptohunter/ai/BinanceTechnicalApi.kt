package gr.cryptohunter.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

object BinanceTechnicalApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun scan(symbols: List<String>): List<TechnicalSignal> = withContext(Dispatchers.IO) {
        val results = mutableListOf<TechnicalSignal>()
        symbols.distinct().chunked(8).forEach { batch ->
            val batchResults = coroutineScope {
                batch.map { symbol ->
                    async(Dispatchers.IO) {
                        try {
                            val candles = loadCandles(symbol)
                            TechnicalAnalysis.analyze(symbol, candles)
                        } catch (_: Exception) {
                            null
                        }
                    }
                }.awaitAll()
            }
            results.addAll(batchResults.filterNotNull())
        }
        results.sortedByDescending { it.score }
    }

    private suspend fun loadCandles(symbol: String): List<Candle> = withContext(Dispatchers.IO) {
        val hosts = listOf("https://api.binance.com", "https://data-api.binance.vision")
        var lastError: Exception? = null
        for (host in hosts) {
            try {
                val request = Request.Builder()
                    .url("$host/api/v3/klines?symbol=$symbol&interval=1h&limit=100")
                    .header("Accept", "application/json")
                    .build()
                return@withContext client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("Binance HTTP ${response.code}")
                    val body = response.body?.string() ?: throw Exception("Κενή απάντηση")
                    val rows = JSONArray(body)
                    (0 until rows.length()).map { index ->
                        val row = rows.getJSONArray(index)
                        Candle(
                            open = row.getString(1).toDouble(),
                            high = row.getString(2).toDouble(),
                            low = row.getString(3).toDouble(),
                            close = row.getString(4).toDouble(),
                            volume = row.getString(5).toDouble()
                        )
                    }
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: Exception("Δεν φορτώθηκαν candles για $symbol")
    }
}
