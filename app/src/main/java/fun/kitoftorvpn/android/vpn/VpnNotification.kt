package `fun`.kitoftorvpn.android.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import `fun`.kitoftorvpn.android.MainActivity
import `fun`.kitoftorvpn.android.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Уведомление в шторке про активный VPN + кнопка "Отключить".
 *
 * Библиотека amneziawg-android сама держит свой сервис как foreground
 * (foregroundServiceType=systemExempted, см. манифест), поэтому мы не делаем
 * собственный foreground — только показываем своё информативное уведомление,
 * которое юзер может использовать для быстрого отключения.
 *
 * Уведомление:
 *   - несъёмное (ongoing), пока VPN активен
 *   - тап по телу — откроет приложение
 *   - кнопка "Отключить" — вызовет VpnControlReceiver → VpnRepository.disconnect()
 */
object VpnNotification {

    private const val CHANNEL_ID = "kitoftorvpn_status"
    private const val CHANNEL_NAME = "Статус подключения"
    private const val NOTIFICATION_ID = 101

    fun show(context: Context) {
        val ctx = context.applicationContext
        ensureChannel(ctx)

        // Тап по уведомлению — открывает приложение.
        val openIntent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPI = PendingIntent.getActivity(
            ctx, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Кнопка "Отключить" → broadcast → VpnControlReceiver.
        val disconnectIntent = Intent(ctx, VpnControlReceiver::class.java).apply {
            action = VpnControlReceiver.ACTION_DISCONNECT
        }
        val disconnectPI = PendingIntent.getBroadcast(
            ctx, 0, disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("KitoFtorVPN")
            .setContentText("Подключён")
            .setOngoing(true)             // несъёмное
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)   // без звука
            .setContentIntent(openPI)
            .addAction(0, "Отключить", disconnectPI)
            .build()

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notif)
    }

    fun hide(context: Context) {
        val nm = context.applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(
            CHANNEL_ID, CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Уведомление о активном подключении"
            setShowBadge(false)
        }
        nm.createNotificationChannel(ch)
    }
}

/**
 * Broadcast-приёмник для кнопки "Отключить" в уведомлении.
 * Зарегистрирован в AndroidManifest.
 */
class VpnControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_DISCONNECT) return
        // goAsync() говорит системе "я ещё работаю" — это продлевает жизнь процесса
        // на ~10 сек, достаточно чтобы завершить setState(DOWN). Без этого процесс
        // может быть убит сразу после onReceive и disconnect не успеет завершиться.
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                VpnRepository.disconnect()
            } catch (e: Exception) {
                android.util.Log.e("VpnControlReceiver", "disconnect failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DISCONNECT = "fun.kitoftorvpn.android.DISCONNECT"
    }
}
