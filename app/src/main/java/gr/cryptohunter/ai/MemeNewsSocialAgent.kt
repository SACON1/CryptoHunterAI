package gr.cryptohunter.ai

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

internal data class NewsSocialItem(
    val title: String,
    val summary: String,
    val source: String,
    val url: String
)

internal data class NewsSocialPulse(
    val score: Int?,
    val positive: Int,
    val negative: Int,
    val items: List<NewsSocialItem>,
    val sourcesChecked: Int,
    val note: String
)

internal object MemeNewsSocialAgent {
    private data class Cached(val at: Long, val pulse: NewsSocialPulse)
    private val cache = mutableMapOf<String, Cached>()

    suspend fun analyze(coin: MemeCandidate): NewsSocialPulse =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val key = "${coin.chain}:${coin.address}".lowercase(Locale.ROOT)
            synchronized(cache) {
                cache[key]?.takeIf { System.currentTimeMillis() - it.at < 10 * 60 * 1000L }?.let { return@withContext it.pulse }
            }

            val queryName = coin.name.trim().take(60)
            val query = if (coin.symbol.length >= 3) "${queryName} ${coin.symbol} crypto" else "${queryName} crypto"
            val encoded = URLEncoder.encode(query, "UTF-8")
            val found = mutableListOf<NewsSocialItem>()
            var checked = 0

            val xQuery = URLEncoder.encode("$query site:x.com OR site:twitter.com", "UTF-8")
            val feeds = listOf(
                "Google News" to "https://news.google.com/rss/search?q=$encoded&hl=en-US&gl=US&ceid=US:en",
                "X / Twitter (δημόσια indexed posts)" to "https://news.google.com/rss/search?q=$xQuery&hl=en-US&gl=US&ceid=US:en",
                "Reddit" to "https://www.reddit.com/search.rss?q=$encoded&sort=new&t=week"
            )
            for ((source, url) in feeds) {
                try {
                    found += fetchRss(url, source)
                    checked++
                } catch (_: Exception) {
                    // A failed source is reported as unavailable; it is not treated as negative sentiment.
                }
            }

            val unique = found.distinctBy { it.url.ifBlank { it.title } }.take(8)
            val positives = listOf(
                "partnership", "partner", "listing", "listed", "launch", "integration",
                "adoption", "investment", "funding", "support", "airdrop", "collaboration",
                "bullish", "approved", "upgrade", "mainnet"
            )
            val negatives = listOf(
                "rug pull", "rugpull", "scam", "fraud", "exploit", "hacked", "hack",
                "drained", "stolen", "investigation", "lawsuit", "delisted", "dump",
                "honeypot", "malware", "phishing", "exit liquidity"
            )
            var positiveHits = 0
            var negativeHits = 0
            unique.forEach { item ->
                val text = "${item.title} ${item.summary}".lowercase(Locale.ROOT)
                positiveHits += positives.count { text.contains(it) }
                negativeHits += negatives.count { text.contains(it) }
            }

            val score = if (unique.isEmpty()) null else
                (50 + positiveHits * 7 - negativeHits * 12).coerceIn(0, 100)
            val note = when {
                checked == 0 -> "Οι δημόσιες πηγές ειδήσεων/social δεν ήταν διαθέσιμες αυτή τη φορά."
                unique.isEmpty() -> "Δεν βρέθηκαν σχετικές αναφορές. Η απουσία αποτελεσμάτων δεν σημαίνει ότι το token είναι ασφαλές."
                else -> "Βρέθηκαν ${unique.size} αναφορές από Google News, δημόσια indexed posts του X/Twitter και Reddit. Το X δεν παρέχει πλήρη ζωντανή αναζήτηση μέσω αυτής της δωρεάν μεθόδου. Το sentiment βασίζεται σε λέξεις-κλειδιά, όχι σε επιβεβαίωση γεγονότων."
            }
            val result = NewsSocialPulse(score, positiveHits, negativeHits, unique, checked, note)
            synchronized(cache) { cache[key] = Cached(System.currentTimeMillis(), result) }
            result
        }

    private fun fetchRss(url: String, source: String): List<NewsSocialItem> {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 7000
        connection.readTimeout = 7000
        connection.setRequestProperty("User-Agent", "CryptoHunterAI/0.4 (Android; public RSS reader)")
        connection.setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml")
        return try {
            connection.inputStream.use { stream ->
                val parser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(stream, null)
                val results = mutableListOf<NewsSocialItem>()
                var event = parser.eventType
                var inItem = false
                var tag = ""
                var title = ""
                var description = ""
                var link = ""
                while (event != XmlPullParser.END_DOCUMENT && results.size < 6) {
                    when (event) {
                        XmlPullParser.START_TAG -> {
                            tag = parser.name.lowercase(Locale.ROOT)
                            if (tag == "item" || tag == "entry") {
                                inItem = true
                                title = ""
                                description = ""
                                link = ""
                            } else if (inItem && tag == "link") {
                                val href = parser.getAttributeValue(null, "href")
                                if (!href.isNullOrBlank()) link = href
                            }
                        }
                        XmlPullParser.TEXT -> if (inItem) {
                            when (tag) {
                                "title" -> title += parser.text
                                "description", "summary", "content" -> description += parser.text
                                "link" -> link += parser.text
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            val end = parser.name.lowercase(Locale.ROOT)
                            if (inItem && (end == "item" || end == "entry")) {
                                val cleanTitle = clean(title)
                                if (cleanTitle.isNotBlank()) {
                                    results += NewsSocialItem(
                                        cleanTitle.take(180),
                                        clean(description).take(260),
                                        source,
                                        link.trim()
                                    )
                                }
                                inItem = false
                            }
                            tag = ""
                        }
                    }
                    event = parser.next()
                }
                results
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun clean(value: String): String =
        value.replace(Regex("<[^>]*>"), " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("\\s+"), " ")
            .trim()
}
