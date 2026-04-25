package `fun`.kitoftorvpn.android.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Клиент API реферальной программы my.kitoftorvpn.fun/api/referral.
 * Авторизация — cookie cabinet_session=<token>.
 */
object ReferralApi {

    private const val TAG = "ReferralApi"
    private const val BASE_URL = "https://my.kitoftorvpn.fun"

    data class RefInfo(
        val refLink: String,
        val refCount: Int,
        val refBonusDays: Int,
    )

    /**
     * Возвращает реф-ссылку и статистику. null — при ошибке.
     */
    suspend fun fetchRefInfo(token: String): RefInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/api/referral")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Cookie", "cabinet_session=$token")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 8000
                readTimeout = 8000
                instanceFollowRedirects = false
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                Log.w(TAG, "unexpected HTTP $code")
                conn.disconnect()
                return@withContext null
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val json = JSONObject(body)
            RefInfo(
                refLink = json.optString("ref_link", ""),
                refCount = json.optInt("ref_count", 0),
                refBonusDays = json.optInt("ref_bonus_days", 0),
            )
        } catch (e: Exception) {
            Log.e(TAG, "fetchRefInfo failed", e)
            null
        }
    }
}
