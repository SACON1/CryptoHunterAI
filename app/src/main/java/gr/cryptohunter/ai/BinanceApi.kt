package gr.cryptohunter.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class MarketTicker(
    val symbol: String,
    val price: String,
    val quoteVolume: Double
)

object BinanceApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    private val baseUrls = listOf(
        "https://api.binance.com",
        "https://data-api.binance.vision"
    )

    suspend fun getBitcoinPrice(): String = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        for (base in baseUrls) {
            try {
                val request = Request.Builder()
                    .url("$base/api/v3/ticker/price?symbol=BTCUSDT")
                    .header("Accept", "application/json")
                    .build()
                return@withContext client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("Binance HTTP ${response.code}")
                    val body = response.body?.string() ?: throw Exception("Κενή απάντηση")
                    JSONObject(body).getString("price")
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: Exception("Αδυναμία σύνδεσης με Binance")
    }

    // Top 50 USDT pairs by 24-hour quote volume on Binance (not market-cap ranking).
    suspend fun getTopUsdtMarkets(): List<MarketTicker> = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        for (base in baseUrls) {
            try {
                val request = Request.Builder()
                    .url("$base/api/v3/ticker/24hr")
                    .header("Accept", "application/json")
                    .build()
                return@withContext client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("Binance HTTP ${response.code}")
                    val body = response.body?.string() ?: throw Exception("Κενή απάντηση")
                    val array = JSONArray(body)
                    val markets = mutableListOf<MarketTicker>()
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        val symbol = item.optString("symbol")
                        if (!symbol.endsWith("USDT")) continue
                        if (symbol in setOf("USDCUSDT", "FDUSDUSDT", "TUSDUSDT", "USDPUSDT", "BUSDUSDT")) continue
                        val price = item.optString("lastPrice")
                        val volume = item.optDouble("quoteVolume", 0.0)
                        if (price.isNotBlank() && volume > 0.0) {
                            markets.add(MarketTicker(symbol, price, volume))
                        }
                    }
                    markets.sortedByDescending { it.quoteVolume }.take(50)
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: Exception("Δεν φορτώθηκαν οι αγορές Binance")
    }
}
