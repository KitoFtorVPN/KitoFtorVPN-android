package `fun`.kitoftorvpn.android.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import `fun`.kitoftorvpn.android.MainActivity
import `fun`.kitoftorvpn.android.R
import `fun`.kitoftorvpn.android.settings.AppPreferences

/**
 * Локальное уведомление о неожиданном разрыве VPN. Показываем, когда VPN
 * перешёл в OFF не по команде пользователя.
 */
object VpnDisconnectNotifier {

    private const val NOTIFICATION_ID = 201
    private const val CHANNEL_ID = "kitoftorvpn_disconnect"
    private const val CHANNEL_NAME = "Разрыв соединения"

    // Старый канал (уведомления о подписке) — больше не нужен, убираем у тех, кто обновился.
    private const val OLD_CHANNEL_ID = "kitoftorvpn_alerts"

    fun notifyDropped(context: Context) {
        val ctx = context.applicationContext
        if (!AppPreferences.getNotificationsEnabled(ctx)) return

        ensureChannel(ctx)

        val openIntent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPI = PendingIntent.getActivity(
            ctx, NOTIFICATION_ID, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
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

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.deleteNotificationChannel(OLD_CHANNEL_ID)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Уведомление, если VPN неожиданно отключился"
        }
        nm.createNotificationChannel(ch)
    }
}
