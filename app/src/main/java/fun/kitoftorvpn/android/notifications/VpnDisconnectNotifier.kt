package `fun`.kitoftorvpn.android.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import `fun`.kitoftorvpn.android.MainActivity
import `fun`.kitoftorvpn.android.R
import `fun`.kitoftorvpn.android.settings.AppPreferences

/**
 * Локальное уведомление о неожиданном разрыве VPN. Показываем, когда VPN
 * перешёл в OFF не по команде пользователя (userInitiated=false).
 */
object VpnDisconnectNotifier {

    private const val NOTIFICATION_ID = 201

    fun notifyDropped(context: Context) {
        val ctx = context.applicationContext
        if (!AppPreferences.getNotificationsEnabled(ctx)) return

        // Используем тот же канал что алармы.
        val channelId = SubAlarmReceiver.CHANNEL_ID

        val openIntent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPI = PendingIntent.getActivity(
            ctx, NOTIFICATION_ID, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(ctx, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("KitoFtorVPN")
            .setContentText("Соединение VPN разорвано. Подключитесь заново.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setContentIntent(openPI)
            .build()

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notif)
    }
}
