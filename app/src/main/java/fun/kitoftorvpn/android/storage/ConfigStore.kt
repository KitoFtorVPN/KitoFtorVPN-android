package `fun`.kitoftorvpn.android.storage

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Шифрованное хранилище для VPN-конфига и токена сессии.
 *
 * Android-аналог Windows DPAPI: ключ генерится один раз и хранится в Android Keystore
 * (аппаратно, если устройство поддерживает TEE/StrongBox), сами данные — в
 * EncryptedSharedPreferences (AES-256-GCM).
 *
 * Ключи:
 *   - "config"  — текст .conf файла
 *   - "token"   — session_token для API кабинета (пока не используется, задел)
 */
object ConfigStore {

    private const val TAG = "ConfigStore"
    private const val PREFS_NAME = "kitoftorvpn_secure"
    private const val KEY_CONFIG = "config"
    private const val KEY_TOKEN = "token"

    @Volatile
    private var prefs: SharedPreferences? = null

    private fun getPrefs(context: Context): SharedPreferences {
        prefs?.let { return it }
        return synchronized(this) {
            prefs ?: run {
                val masterKey = MasterKey.Builder(context.applicationContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                val p = EncryptedSharedPreferences.create(
                    context.applicationContext,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
                prefs = p
                p
            }
        }
    }

    // ─── Config ────────────────────────────────────────

    /** Возвращает сохранённый .conf текст, либо null если не импортирован. */
    fun loadConfig(context: Context): String? = try {
        getPrefs(context).getString(KEY_CONFIG, null)?.ifBlank { null }
    } catch (e: Exception) {
        Log.e(TAG, "loadConfig failed", e)
        null
    }

    /** true если конфиг импортирован. */
    fun hasConfig(context: Context): Boolean = try {
        getPrefs(context).contains(KEY_CONFIG) &&
            !getPrefs(context).getString(KEY_CONFIG, null).isNullOrBlank()
    } catch (e: Exception) {
        Log.e(TAG, "hasConfig failed", e)
        false
    }

    /**
     * Проверяет формат и сохраняет. Возвращает null если всё ок, иначе — текст ошибки.
     */
    fun saveConfig(context: Context, confText: String): String? {
        // Убираем BOM (\uFEFF), NBSP, трим.
        val trimmed = confText.trimStart('\uFEFF').trim()
        // Ищем секции case-insensitive и с допуском на пробелы внутри скобок.
        val hasInterface = Regex("""(?im)^\s*\[\s*Interface\s*\]""").containsMatchIn(trimmed)
        val hasPeer = Regex("""(?im)^\s*\[\s*Peer\s*\]""").containsMatchIn(trimmed)
        if (!hasInterface || !hasPeer) {
            Log.w(TAG, "saveConfig: invalid format (hasInterface=$hasInterface, hasPeer=$hasPeer, len=${trimmed.length})")
            return "Неверный формат файла. Нужен .conf файл из личного кабинета."
        }
        return try {
            getPrefs(context).edit().putString(KEY_CONFIG, trimmed).apply()
            Log.d(TAG, "saveConfig: ok, ${trimmed.length} chars")
            null
        } catch (e: Exception) {
            Log.e(TAG, "saveConfig failed", e)
            "Не удалось сохранить конфигурацию."
        }
    }

    fun deleteConfig(context: Context) {
        try {
            getPrefs(context).edit().remove(KEY_CONFIG).apply()
        } catch (e: Exception) {
            Log.e(TAG, "deleteConfig failed", e)
        }
    }

    // ─── Token (задел на шаг "Авторизация") ───────────

    fun loadToken(context: Context): String? = try {
        getPrefs(context).getString(KEY_TOKEN, null)?.ifBlank { null }
    } catch (e: Exception) {
        Log.e(TAG, "loadToken failed", e)
        null
    }

    fun saveToken(context: Context, token: String) {
        try {
            getPrefs(context).edit().putString(KEY_TOKEN, token).apply()
        } catch (e: Exception) {
            Log.e(TAG, "saveToken failed", e)
        }
    }

    fun deleteToken(context: Context) {
        try {
            getPrefs(context).edit().remove(KEY_TOKEN).apply()
        } catch (e: Exception) {
            Log.e(TAG, "deleteToken failed", e)
        }
    }
}
