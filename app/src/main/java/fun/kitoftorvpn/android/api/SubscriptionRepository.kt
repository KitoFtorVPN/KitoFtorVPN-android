package `fun`.kitoftorvpn.android.api

import android.content.Context
import android.util.Log
import `fun`.kitoftorvpn.android.notifications.SubNotificationScheduler
import `fun`.kitoftorvpn.android.settings.AppPreferences
import `fun`.kitoftorvpn.android.storage.ConfigStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Репозиторий подписки: дёргает /api/sub каждые 5 минут пока жив поллинг,
 * хранит последнее значение в StateFlow. При получении данных:
 *   - сохраняет expiresAtMs в AppPreferences (живой таймер + реплан алармов)
 *   - перепланирует уведомления через SubNotificationScheduler
 */
object SubscriptionRepository {

    private const val TAG = "SubRepo"
    private const val POLL_INTERVAL_MS = 5 * 60 * 1000L

    private val _subInfo = MutableStateFlow<SubscriptionApi.SubInfo?>(null)
    val subInfo: StateFlow<SubscriptionApi.SubInfo?> = _subInfo.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollJob: Job? = null

    fun startPolling(context: Context) {
        val ctx = context.applicationContext
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                refreshOnce(ctx)
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        _subInfo.value = null
    }

    /** Принудительное обновление (вне расписания). */
    fun refreshNow(context: Context) {
        val ctx = context.applicationContext
        scope.launch { refreshOnce(ctx) }
    }

    private suspend fun refreshOnce(ctx: Context) {
        val token = ConfigStore.loadToken(ctx)
        if (token.isNullOrBlank()) {
            Log.d(TAG, "no token — guest mode, skipping /api/sub")
            _subInfo.value = null
            return
        }
        val info = SubscriptionApi.fetchSubInfo(token)
        Log.d(TAG, "got: $info")
        _subInfo.value = info

        // Обновляем expiresAtMs и перепланируем уведомления.
        val expiresIn = info.expiresInSeconds
        if (info.status == SubscriptionApi.SubInfo.Status.ACTIVE && expiresIn != null && expiresIn > 0) {
            val expiresAtMs = System.currentTimeMillis() + expiresIn * 1000L
            AppPreferences.setSubExpiresAt(ctx, expiresAtMs)
            AppPreferences.setSubType(
                ctx,
                if (info.type == SubscriptionApi.SubInfo.SubType.TEST) "test" else "sub"
            )
            if (AppPreferences.getNotificationsEnabled(ctx)) {
                SubNotificationScheduler.reschedule(ctx, expiresAtMs, info.type)
            } else {
                SubNotificationScheduler.cancelAll(ctx)
            }
        } else if (info.status == SubscriptionApi.SubInfo.Status.EXPIRED ||
            info.status == SubscriptionApi.SubInfo.Status.NONE ||
            info.status == SubscriptionApi.SubInfo.Status.TEST_ENDED
        ) {
            // Подписка закончилась — показываем возвратные уведомления на +3/+7/+14 дней.
            val expiredAt = AppPreferences.getSubExpiresAt(ctx)
                ?: System.currentTimeMillis()
            if (AppPreferences.getNotificationsEnabled(ctx)) {
                SubNotificationScheduler.scheduleExpiredReminders(ctx, expiredAt)
            }
        }
    }
}
