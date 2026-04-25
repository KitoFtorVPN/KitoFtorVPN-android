package `fun`.kitoftorvpn.android.perapp

import android.content.Context

/**
 * Хранит множество package name приложений, ИСКЛЮЧЁННЫХ из VPN.
 * Трафик этих приложений пойдёт напрямую, минуя туннель
 * (VpnService.Builder.addDisallowedApplication).
 *
 * Примечание: сам VpnService поднимается из нативной либы amneziawg,
 * туда передать список невозможно. Поэтому исключения применяются при
 * поднятии туннеля через inline hook в VpnRepository (см. applyAppExclusions).
 */
object AppExclusionStore {
    private const val PREFS = "kitoftorvpn_perapp"
    private const val KEY_EXCLUDED = "excluded_packages"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Возвращает множество package name, исключённых из VPN. */
    fun load(ctx: Context): Set<String> =
        prefs(ctx).getStringSet(KEY_EXCLUDED, emptySet()) ?: emptySet()

    /** Сохраняет множество. */
    fun save(ctx: Context, packages: Set<String>) {
        prefs(ctx).edit()
            .putStringSet(KEY_EXCLUDED, packages.toSet())   // defensive copy
            .apply()
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().remove(KEY_EXCLUDED).apply()
    }
}
