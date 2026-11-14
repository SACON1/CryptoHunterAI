
package gr.cryptohunter.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object BinanceApi {

    private val client = OkHttpClient()

    suspend fun getBitcoinPrice(): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("https://data-api.binance.vision/api/v3/ticker/price?symbol=BTCUSDT")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("Binance error: ${response.code}")
                }

                val json = JSONObject(response.body?.string() ?: "{}")
                json.getString("price")
            }
        }
}
