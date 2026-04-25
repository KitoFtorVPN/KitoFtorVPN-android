package `fun`.kitoftorvpn.android.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import `fun`.kitoftorvpn.android.api.SubscriptionApi

/**
 * Планирует локальные уведомления о подписке через AlarmManager.
 *
 * События (те же что у Telegram-бота bot.py):
 *   - за 3 дня до окончания подписки
 *   - в момент окончания подписки
 *   - +3, +7, +14 дней после окончания (возвратные)
 *   - за 5 минут до окончания тестового периода
 *
 * Каждое событие = уникальный requestCode, чтобы можно было индивидуально
 * отменять и перезаписывать.
 */
object SubNotificationScheduler {

    private const val TAG = "SubNotifScheduler"

    // Request codes для PendingIntent. Должны быть стабильны между запусками.
    private const val RC_3D_BEFORE = 1001
    private const val RC_AT_EXPIRE = 1002
    private const val RC_3D_AFTER = 1003
    private const val RC_7D_AFTER = 1004
    private const val RC_14D_AFTER = 1005
    private const val RC_TEST_5MIN = 1006

    private val ALL_CODES = listOf(
        RC_3D_BEFORE, RC_AT_EXPIRE, RC_3D_AFTER, RC_7D_AFTER, RC_14D_AFTER, RC_TEST_5MIN
    )

    // Event types для notification body
    const val EVENT_3D_BEFORE = "sub_3d_before"
    const val EVENT_AT_EXPIRE = "sub_at_expire"
    const val EVENT_3D_AFTER = "sub_3d_after"
    const val EVENT_7D_AFTER = "sub_7d_after"
    const val EVENT_14D_AFTER = "sub_14d_after"
    const val EVENT_TEST_5MIN = "sub_test_5min"

    /**
     * Переплан всех алармов для активной подписки. Снимает старые, ставит новые.
     * @param expiresAtMs абсолютный timestamp окончания (UTC ms).
     * @param type тип подписки (SUB — платная, TEST — тест, null — unknown).
     */
    fun reschedule(context: Context, expiresAtMs: Long, type: SubscriptionApi.SubInfo.SubType?) {
        cancelAll(context)
        val now = System.currentTimeMillis()

        if (type == SubscriptionApi.SubInfo.SubType.TEST) {
            // За 5 минут до окончания теста.
            val at = expiresAtMs - 5 * 60_000L
            if (at > now) scheduleAlarm(context, RC_TEST_5MIN, at, EVENT_TEST_5MIN)
        } else {
            // Платная подписка: за 3 дня до окончания.
            val at3d = expiresAtMs - 3 * 86400_000L
            if (at3d > now) scheduleAlarm(context, RC_3D_BEFORE, at3d, EVENT_3D_BEFORE)
        }

        // Момент окончания — для обоих типов.
        if (expiresAtMs > now) scheduleAlarm(context, RC_AT_EXPIRE, expiresAtMs, EVENT_AT_EXPIRE)

        // Возвратные напоминания всегда ставим сразу, расчитав от expiresAtMs.
        scheduleExpiredReminders(context, expiresAtMs)
    }

    /**
     * Ставит возвратные напоминания +3/+7/+14 дней после окончания.
     * Вызывается и при reschedule, и отдельно если подписка уже expired.
     */
    fun scheduleExpiredReminders(context: Context, expiredAtMs: Long) {
        val now = System.currentTimeMillis()
        listOf(
            RC_3D_AFTER to (expiredAtMs + 3 * 86400_000L),
            RC_7D_AFTER to (expiredAtMs + 7 * 86400_000L),
            RC_14D_AFTER to (expiredAtMs + 14 * 86400_000L)
        ).forEach { (rc, at) ->
            if (at > now) {
                val event = when (rc) {
                    RC_3D_AFTER -> EVENT_3D_AFTER
                    RC_7D_AFTER -> EVENT_7D_AFTER
                    else -> EVENT_14D_AFTER
                }
                scheduleAlarm(context, rc, at, event)
            }
        }
    }

    fun cancelAll(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (rc in ALL_CODES) {
            val pi = makePendingIntent(context, rc, "", cancelOnly = true) ?: continue
            am.cancel(pi)
            pi.cancel()
        }
    }

    private fun scheduleAlarm(context: Context, requestCode: Int, triggerAtMs: Long, event: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = makePendingIntent(context, requestCode, event) ?: return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+: нужен SCHEDULE_EXACT_ALARM либо USE_EXACT_ALARM.
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
                } else {
                    // Permission не дан — fallback на неточный alarm.
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
                }
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
            }
            Log.d(TAG, "scheduled $event at $triggerAtMs (rc=$requestCode)")
        } catch (e: SecurityException) {
            Log.e(TAG, "alarm scheduling denied", e)
        }
    }

    private fun makePendingIntent(
        context: Context,
        requestCode: Int,
        event: String,
        cancelOnly: Boolean = false
    ): PendingIntent? {
        val intent = Intent(context, SubAlarmReceiver::class.java).apply {
            action = SubAlarmReceiver.ACTION_FIRE
            putExtra(SubAlarmReceiver.EXTRA_EVENT, event)
        }
        val flags = if (cancelOnly) {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }
}
