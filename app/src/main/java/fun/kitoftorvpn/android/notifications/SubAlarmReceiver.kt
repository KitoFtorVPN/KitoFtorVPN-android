package `fun`.kitoftorvpn.android.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import `fun`.kitoftorvpn.android.R
import `fun`.kitoftorvpn.android.settings.AppPreferences

/**
 * Обрабатывает срабатывание aларма от SubNotificationScheduler. Показывает
 * уведомление с соответствующим текстом. Клик ведёт в ЛК (браузер).
 */
class SubAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_FIRE) return
        val event = intent.getStringExtra(EXTRA_EVENT) ?: return

        // Юзер мог выключить уведомления после того как alarm был запланирован.
        if (!AppPreferences.getNotificationsEnabled(context)) return

        val (title, text) = messageFor(event)
        showNotification(context.applicationContext, event, title, text)
    }

    private fun messageFor(event: String): Pair<String, String> = when (event) {
        SubNotificationScheduler.EVENT_3D_BEFORE -> "KitoFtorVPN" to
            "Подписка закончится через 3 дня. Не забудьте продлить."
        SubNotificationScheduler.EVENT_AT_EXPIRE -> "KitoFtorVPN" to
            "Подписка закончилась. Продлите, чтобы продолжить пользоваться VPN."
        SubNotificationScheduler.EVENT_3D_AFTER -> "KitoFtorVPN" to
            "Ваша подписка закончилась 3 дня назад. Оформите новую, и продолжайте пользоваться VPN."
        SubNotificationScheduler.EVENT_7D_AFTER -> "KitoFtorVPN" to
            "Верните защиту своих данных. Подписка от 150 ₽/мес — безопасный интернет каждый день."
        SubNotificationScheduler.EVENT_14D_AFTER -> "KitoFtorVPN" to
            "Последнее напоминание. Если захотите вернуться — мы всегда на связи."
        SubNotificationScheduler.EVENT_TEST_5MIN -> "KitoFtorVPN" to
            "Тестовый доступ закончится через 5 минут. Купите подписку, чтобы продолжить."
        else -> "KitoFtorVPN" to "Уведомление"
    }

    private fun showNotification(ctx: Context, event: String, title: String, text: String) {
        ensureChannel(ctx)

        val notifId = event.hashCode()

        // Клик ведёт в ЛК — страница покупки/продления.
        val openIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://my.kitoftorvpn.fun")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val openPI = PendingIntent.getActivity(
            ctx, notifId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openPI)
            .build()

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notifId, notif)
    }

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(
            CHANNEL_ID, CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Уведомления о подписке и VPN"
        }
        nm.createNotificationChannel(ch)
    }

    companion object {
        const val ACTION_FIRE = "fun.kitoftorvpn.android.SUB_ALARM_FIRE"
        const val EXTRA_EVENT = "event"
        const val CHANNEL_ID = "kitoftorvpn_alerts"
        const val CHANNEL_NAME = "Уведомления о подписке"
    }
}
