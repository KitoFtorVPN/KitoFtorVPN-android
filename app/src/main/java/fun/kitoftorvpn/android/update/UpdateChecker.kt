package `fun`.kitoftorvpn.android.update

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Клиент GitHub Releases API.
 * Дёргает https://api.github.com/repos/KitoFtorVPN/KitoFtorVPN-android/releases/latest,
 * возвращает имя версии, ссылку на APK, описание релиза, флаг forced.
 *
 * Forced-релиз: в описании релиза на GitHub должна быть строка "[forced]" — тогда
 * пользователь не сможет отложить обновление.
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val RELEASES_URL =
        "https://api.github.com/repos/KitoFtorVPN/KitoFtorVPN-android/releases/latest"

    data class UpdateInfo(
        val versionName: String,   // "1.1.0"
        val apkUrl: String,        // ссылка на скачивание APK
        val releaseNotes: String,  // описание релиза
        val forced: Boolean,       // обязательное обновление
        val sizeBytes: Long,       // размер APK
    )

    suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL(RELEASES_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "KitoFtorVPN-Android")
                connectTimeout = 8000
                readTimeout = 8000
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                Log.w(TAG, "HTTP $code")
                conn.disconnect()
                return@withContext null
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(body)
            // tag_name обычно "v1.1.0" — убираем префикс v
            val tag = json.optString("tag_name", "").trimStart('v', 'V').trim()
            if (tag.isEmpty()) return@withContext null

            val notes = json.optString("body", "")
            val forced = notes.contains("[forced]", ignoreCase = true)

            // Ищем asset с .apk
            val assets = json.optJSONArray("assets") ?: return@withContext null
            var apkUrl = ""
            var apkSize = 0L
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url", "")
                    apkSize = asset.optLong("size", 0L)
                    break
                }
            }
            if (apkUrl.isEmpty()) {
                Log.w(TAG, "no .apk asset found in release")
                return@withContext null
            }

            UpdateInfo(
                versionName = tag,
                apkUrl = apkUrl,
                releaseNotes = notes.replace("[forced]", "", ignoreCase = true).trim(),
                forced = forced,
                sizeBytes = apkSize,
            )
        } catch (e: Exception) {
            Log.e(TAG, "fetchLatest failed", e)
            null
        }
    }

    /**
     * Возвращает true если remote-версия новее текущей.
     * Сравнение: семвер 1.2.3 (числовое сравнение по компонентам).
     * Если форматы не парсятся — fallback на строковое сравнение.
     */
    fun isNewer(current: String, remote: String): Boolean {
        return try {
            val cur = parseVersion(current)
            val rem = parseVersion(remote)
            for (i in 0 until maxOf(cur.size, rem.size)) {
                val a = cur.getOrElse(i) { 0 }
                val b = rem.getOrElse(i) { 0 }
                if (b > a) return true
                if (b < a) return false
            }
            false
        } catch (_: Exception) {
            current != remote
        }
    }

    private fun parseVersion(v: String): List<Int> =
        v.trim().split(".", "-").mapNotNull { it.takeWhile { c -> c.isDigit() }.toIntOrNull() }
}
