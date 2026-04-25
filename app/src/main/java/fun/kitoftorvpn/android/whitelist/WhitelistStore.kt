package `fun`.kitoftorvpn.android.whitelist

import android.content.Context

/**
 * Хранит список "сайтов в обход VPN" (whitelist). Используется для split-тунелинга:
 * при поднятии туннеля домены из списка резолвятся в IP, расширяются до /24 и
 * вычитаются из AllowedIPs = 0.0.0.0/0. См. WhitelistEngine.applyToConfig.
 */
object WhitelistStore {
    private const val PREFS = "kitoftorvpn_whitelist"
    private const val KEY_ENTRIES = "entries"   // одной строкой, разделитель \n

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(ctx: Context): List<String> {
        val raw = prefs(ctx).getString(KEY_ENTRIES, "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** Возвращает сохранённые записи как текст для textarea (по строке на запись). */
    fun loadAsText(ctx: Context): String = load(ctx).joinToString("\n")

    /** Сохраняет список. Пустые строки/дубликаты отфильтровываются. */
    fun save(ctx: Context, entries: List<String>) {
        val cleaned = entries.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        prefs(ctx).edit().putString(KEY_ENTRIES, cleaned.joinToString("\n")).apply()
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().remove(KEY_ENTRIES).apply()
    }
}
