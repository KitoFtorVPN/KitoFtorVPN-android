package `fun`.kitoftorvpn.android.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import `fun`.kitoftorvpn.android.settings.AppPreferences

/**
 * После перезагрузки устройства все AlarmManager-таймеры сбрасываются.
 * Этот ресивер восстанавливает их из сохранённого expiresAtMs.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON" -> restoreAlarms(context)
        }
    }

    private fun restoreAlarms(context: Context) {
        if (!AppPreferences.getNotificationsEnabled(context)) return
        val expiresAt = AppPreferences.getSubExpiresAt(context) ?: return
        val type = when (AppPreferences.getSubType(context)) {
            "test" -> `fun`.kitoftorvpn.android.api.SubscriptionApi.SubInfo.SubType.TEST
            "sub" -> `fun`.kitoftorvpn.android.api.SubscriptionApi.SubInfo.SubType.SUB
            else -> null
        }
        SubNotificationScheduler.reschedule(context, expiresAt, type)
    }
}
