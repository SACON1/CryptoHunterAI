package gr.cryptohunter.ai

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MemeRadarWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            SignalNotifications.createChannel(applicationContext)
            val scan = scanMemeCoins()
            val prefs = applicationContext.getSharedPreferences("meme_radar_alerts", Context.MODE_PRIVATE)
            for (coin in scan.coins.take(8)) {
                val report = runMemeAgentCouncil(coin)
                if (report.blocked || report.score < 75) continue
                val key = "${coin.chain}:${coin.address}"
                val last = prefs.getLong(key, 0L)
                if (System.currentTimeMillis() - last < 12 * 60 * 60 * 1000L) continue
                MemeRadarAlerts.notify(applicationContext, coin, report.score, report.verdict)
                prefs.edit().putLong(key, System.currentTimeMillis()).apply()
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

object MemeRadarAlerts {
    private const val CHANNEL = "meme_coin_opportunities"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL,
                "Crypto Hunter AI — Meme coin opportunities",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Ευκαιρίες meme coins μετά από αυτόματο έλεγχο agents" }
            context.getSystemService(android.app.NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun notify(context: Context, coin: MemeCandidate, score: Int, verdict: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        createChannel(context)
        val open = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_MEME_TAB, true)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = android.app.PendingIntent.getActivity(
            context, coin.address.hashCode(), open,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Meme opportunity • ${coin.symbol}")
            .setContentText("Agent score ${score}/100 • ${coin.chain}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "${coin.name} (${coin.symbol})\nScore: ${score}/100\n${verdict}\nLP: ${usdShort(coin.liquidity)} • Vol 24h: ${usdShort(coin.volume24h)}\nΑπαιτείται δικός σου έλεγχος πριν από οποιαδήποτε αγορά."
            ))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try { NotificationManagerCompat.from(context).notify(coin.address.hashCode(), notification) }
        catch (_: SecurityException) { }
    }
}

fun scheduleMemeRadarWork(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
    val request = PeriodicWorkRequestBuilder<MemeRadarWorker>(15, TimeUnit.MINUTES)
        .setConstraints(constraints)
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "crypto_hunter_meme_radar",
        ExistingPeriodicWorkPolicy.UPDATE,
        request
    )
}
