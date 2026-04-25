package `fun`.kitoftorvpn.android.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Скачивает APK обновления и устанавливает через системный установщик.
 *
 * Поток:
 *   1. download(url) → APK сохраняется в getExternalFilesDir(null)/update.apk
 *   2. install(file) → если нет разрешения REQUEST_INSTALL_PACKAGES, открываем настройки;
 *      иначе запускаем системный VIEW intent с FileProvider URI.
 *   3. Android показывает стандартный диалог "Установить обновление? [Установить]".
 */
object UpdateInstaller {

    private const val TAG = "UpdateInstaller"

    sealed class State {
        object Idle : State()
        data class Downloading(val percent: Int) : State()
        data class Ready(val file: File) : State()
        data class Error(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    fun reset() { _state.value = State.Idle }

    /** Скачивает APK, обновляя прогресс. Возвращает файл при успехе. */
    suspend fun download(context: Context, url: String): File? = withContext(Dispatchers.IO) {
        try {
            _state.value = State.Downloading(0)
            val outFile = File(context.getExternalFilesDir(null), "update.apk")
            if (outFile.exists()) outFile.delete()

            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "KitoFtorVPN-Android")
            }
            conn.connect()

            if (conn.responseCode !in 200..299) {
                _state.value = State.Error("Ошибка загрузки: HTTP ${conn.responseCode}")
                conn.disconnect()
                return@withContext null
            }

            val total = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
            var downloaded = 0L
            var lastPercent = -1

            conn.inputStream.use { input ->
                FileOutputStream(outFile).use { output ->
                    val buf = ByteArray(8 * 1024)
                    while (true) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        output.write(buf, 0, read)
                        downloaded += read
                        if (total > 0) {
                            val p = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                            if (p != lastPercent) {
                                _state.value = State.Downloading(p)
                                lastPercent = p
                            }
                        }
                    }
                    output.flush()
                }
            }
            conn.disconnect()
            _state.value = State.Ready(outFile)
            outFile
        } catch (e: Exception) {
            Log.e(TAG, "download failed", e)
            _state.value = State.Error("Не удалось скачать: ${e.message ?: "ошибка сети"}")
            null
        }
    }

    /**
     * Запускает установку APK. Перед этим проверяет разрешение
     * REQUEST_INSTALL_PACKAGES (Android 8+).
     *
     * Если разрешения нет — открывает системный экран "Установка из этого источника",
     * после возврата пользователю нужно повторить тап "Установить".
     *
     * Возвращает true если установка запущена, false если открыли экран разрешения.
     */
    fun install(context: Context, apk: File): Boolean {
        // Android 8+: нужно разрешение на установку из неизвестных источников.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(settingsIntent)
            } catch (e: Exception) {
                Log.e(TAG, "settings intent failed", e)
            }
            return false
        }

        // Разрешение есть → запускаем системный установщик через FileProvider.
        return try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, apk)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "install intent failed", e)
            _state.value = State.Error("Не удалось открыть установщик")
            false
        }
    }
}
