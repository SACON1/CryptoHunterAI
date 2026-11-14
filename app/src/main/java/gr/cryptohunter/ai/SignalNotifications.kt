package gr.cryptohunter.ai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Locale

object SignalNotifications {
    private const val CHANNEL_ID = "crypto_hunter_signals"
    private const val PREFS = "crypto_hunter_notification_prefs"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Crypto Hunter AI — Σήματα",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Ειδοποιήσεις για ισχυρά τεχνικά σήματα αγοράς"
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    fun notifyStrongSignals(context: Context, signals: List<TechnicalSignal>) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val manager = NotificationManagerCompat.from(context)

        signals
            .filter { it.score >= 80 && (it.direction == "BUY" || it.direction == "SELL") }
            .forEach { signal ->
                val key = "${signal.symbol}_${signal.direction}"
                if (preferences.getBoolean(key, false)) return@forEach

                val details = buildString {
                    append("Entry: ${format(signal.price)}")
                    signal.stopLoss?.let { append(" • SL: ${format(it)}") }
                    signal.takeProfit?.let { append(" • TP: ${format(it)}") }
                    signal.riskReward?.let {
                        append(" • R/R 1:${String.format(Locale.US, "%.1f", it)}")
                    }
                }

                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Crypto Hunter AI • ${signal.symbol} ${signal.direction}")
                    .setContentText(details)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(
                        "$details\nΒαθμολογία: ${signal.score}/100\n${signal.explanation}"
                    ))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .build()

                try {
                    manager.notify(signal.symbol.hashCode(), notification)
                    preferences.edit().putBoolean(key, true).apply()
                } catch (_: SecurityException) {
                    // Notification permission may have been revoked while the app was open.
                }
            }
    }

    private fun format(value: Double): String =
        String.format(Locale.US, "%.6f", value).trimEnd('0').trimEnd('.')
}
