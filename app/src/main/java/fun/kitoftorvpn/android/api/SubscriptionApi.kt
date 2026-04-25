package `fun`.kitoftorvpn.android.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Клиент API кабинета my.kitoftorvpn.fun.
 * Авторизация — cookie cabinet_session=<token>, см. app.py/get_current_user.
 */
object SubscriptionApi {

    private const val TAG = "SubscriptionApi"
    private const val BASE_URL = "https://my.kitoftorvpn.fun"

    /**
     * Результат запроса /api/sub.
     * Схема ответа (cabinet/app.py::_sub_info):
     *   active: {status, type, time_left, expires_in, devices, confs_count}
     *   expired: {status}
     *   test_ended: {status}
     *   none: {status}
     *   401: {error, redirect}
     */
    data class SubInfo(
        val status: Status,
        val type: SubType?,
        val expiresInSeconds: Long?,
        val timeLeft: String?,
        val devices: Int,
    ) {
        enum class Status { ACTIVE, EXPIRED, TEST_ENDED, NONE, UNAUTHORIZED, ERROR }
        enum class SubType { SUB, TEST }
    }

    /**
     * Запрашивает /api/sub с токеном сессии. Возвращает SubInfo.
     * При сетевой ошибке → SubInfo(status=ERROR).
     * При 401 → SubInfo(status=UNAUTHORIZED).
     */
    suspend fun fetchSubInfo(token: String): SubInfo = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/api/sub")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Cookie", "cabinet_session=$token")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 8000
                readTimeout = 8000
                instanceFollowRedirects = false
            }

            val code = conn.responseCode
            if (code == 401) {
                conn.disconnect()
                return@withContext SubInfo(
                    status = SubInfo.Status.UNAUTHORIZED,
                    type = null, expiresInSeconds = null, timeLeft = null, devices = 0
                )
            }
            if (code !in 200..299) {
                Log.w(TAG, "unexpected HTTP $code")
                conn.disconnect()
                return@withContext SubInfo(
                    status = SubInfo.Status.ERROR,
                    type = null, expiresInSeconds = null, timeLeft = null, devices = 0
                )
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            Log.d(TAG, "body=$body")

            val json = JSONObject(body)
            val status = when (json.optString("status", "")) {
                "active" -> SubInfo.Status.ACTIVE
                "expired" -> SubInfo.Status.EXPIRED
                "test_ended" -> SubInfo.Status.TEST_ENDED
                "none" -> SubInfo.Status.NONE
                else -> SubInfo.Status.ERROR
            }
            val type = when (json.optString("type", "")) {
                "sub" -> SubInfo.SubType.SUB
                "test" -> SubInfo.SubType.TEST
                else -> null
            }
            val expiresIn = if (json.has("expires_in")) json.optLong("expires_in") else null
            val timeLeft = if (json.has("time_left")) json.optString("time_left") else null
            val devices = json.optInt("devices", 0)

            SubInfo(status, type, expiresIn, timeLeft, devices)
        } catch (e: Exception) {
            Log.e(TAG, "fetchSubInfo failed", e)
            SubInfo(
                status = SubInfo.Status.ERROR,
                type = null, expiresInSeconds = null, timeLeft = null, devices = 0
            )
        }
    }
}
