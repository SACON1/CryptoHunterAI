package gr.cryptohunter.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object BinanceApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    private val priceUrls = listOf(
        "https://api.binance.com/api/v3/ticker/price?symbol=BTCUSDT",
        "https://data-api.binance.vision/api/v3/ticker/price?symbol=BTCUSDT"
    )

    suspend fun getBitcoinPrice(): String = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        for (url in priceUrls) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .build()
                return@withContext client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw Exception("Binance error: HTTP ${response.code}")
                    }
                    val body = response.body?.string()
                        ?: throw Exception("Κενή απάντηση από Binance")
                    JSONObject(body).getString("price")
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: Exception("Αδυναμία σύνδεσης με Binance")
    }
}
