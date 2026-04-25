package `fun`.kitoftorvpn.android.settings

import android.content.Context

/**
 * Пользовательские настройки приложения. Хранятся в обычных SharedPreferences
 * (не шифрованных — данные несекретные, в отличие от конфига и токена).
 */
object AppPreferences {
    private const val PREFS = "kitoftorvpn_settings"

    // User-visible toggles
    private const val KEY_NOTIFICATIONS = "notifications_enabled"

    // Auth/session state (для восстановления стартового экрана)
    private const val KEY_IS_GUEST = "is_guest"

    // Subscription cache (для живого таймера и планирования алармов)
    private const val KEY_SUB_EXPIRES_AT = "sub_expires_at"
    private const val KEY_SUB_TYPE = "sub_type"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ─── Guest mode flag ─────────────────────────────

    fun getGuestMode(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_IS_GUEST, false)

    fun setGuestMode(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_IS_GUEST, value).apply()
    }

    // ─── Уведомления ──────────────────────────────────

    fun getNotificationsEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_NOTIFICATIONS, true)

    fun setNotificationsEnabled(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_NOTIFICATIONS, value).apply()
    }

    // ─── Subscription cache ───────────────────────────
    // expiresAtMs — абсолютный timestamp окончания подписки (UTC ms).
    // Используется для: (1) живого таймера на главном экране,
    // (2) перепланирования алармов после перезагрузки устройства.

    fun getSubExpiresAt(ctx: Context): Long? {
        val v = prefs(ctx).getLong(KEY_SUB_EXPIRES_AT, 0L)
        return if (v <= 0L) null else v
    }

    fun setSubExpiresAt(ctx: Context, value: Long) {
        prefs(ctx).edit().putLong(KEY_SUB_EXPIRES_AT, value).apply()
    }

    fun clearSubExpiresAt(ctx: Context) {
        prefs(ctx).edit().remove(KEY_SUB_EXPIRES_AT).apply()
    }

    fun getSubType(ctx: Context): String? = prefs(ctx).getString(KEY_SUB_TYPE, null)

    fun setSubType(ctx: Context, value: String) {
        prefs(ctx).edit().putString(KEY_SUB_TYPE, value).apply()
    }
}
