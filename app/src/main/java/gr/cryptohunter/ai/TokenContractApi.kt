package gr.cryptohunter.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

data class TokenContract(val network: String, val address: String)

object TokenContractApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun lookup(binanceSymbol: String): List<TokenContract> = withContext(Dispatchers.IO) {
        val asset = binanceSymbol.removeSuffix("USDT").removeSuffix("USDC").uppercase(Locale.US)
        val searchRequest = Request.Builder()
            .url("https://api.coingecko.com/api/v3/search?query=$asset")
            .header("Accept", "application/json")
            .build()
        val coinId = client.newCall(searchRequest).execute().use { response ->
            if (!response.isSuccessful) throw Exception("CoinGecko HTTP ${response.code}")
            val body = response.body?.string() ?: throw Exception("Κενή απάντηση CoinGecko")
            val coins = JSONObject(body).optJSONArray("coins") ?: return@withContext emptyList()
            (0 until coins.length())
                .map { coins.getJSONObject(it) }
                .firstOrNull { it.optString("symbol").equals(asset, ignoreCase = true) }
                ?.optString("id")
                ?.takeIf { it.isNotBlank() }
                ?: return@withContext emptyList()
        }

        val detailRequest = Request.Builder()
            .url("https://api.coingecko.com/api/v3/coins/$coinId?localization=false&tickers=false&market_data=false&community_data=false&developer_data=false&sparkline=false")
            .header("Accept", "application/json")
            .build()
        client.newCall(detailRequest).execute().use { response ->
            if (!response.isSuccessful) throw Exception("CoinGecko HTTP ${response.code}")
            val body = response.body?.string() ?: throw Exception("Κενή απάντηση CoinGecko")
            val platforms = JSONObject(body).optJSONObject("platforms") ?: return@use emptyList()
            val results = mutableListOf<TokenContract>()
            val keys = platforms.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val address = platforms.optString(key).trim()
                if (address.isNotBlank() && address != "null") {
                    val network = when (key.lowercase(Locale.US)) {
                        "ethereum" -> "Ethereum"
                        "binance-smart-chain" -> "BNB Smart Chain"
                        "solana" -> "Solana"
                        "polygon-pos" -> "Polygon"
                        "arbitrum-one" -> "Arbitrum"
                        "optimistic-ethereum" -> "Optimism"
                        "avalanche" -> "Avalanche C-Chain"
                        "base" -> "Base"
                        "fantom" -> "Fantom"
                        else -> key
                    }
                    results.add(TokenContract(network, address))
                }
            }
            results.sortedBy { it.network }
        }
    }
}
