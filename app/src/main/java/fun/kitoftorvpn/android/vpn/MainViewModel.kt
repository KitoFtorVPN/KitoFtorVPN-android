package `fun`.kitoftorvpn.android.vpn

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `fun`.kitoftorvpn.android.storage.ConfigStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val vpnState: StateFlow<VpnState> = VpnRepository.state
    val lastError: StateFlow<String?> = VpnRepository.lastError

    private val _timerSeconds = MutableStateFlow(0L)
    val timerSeconds: StateFlow<Long> = _timerSeconds.asStateFlow()

    private val _hasConfig = MutableStateFlow(false)
    val hasConfig: StateFlow<Boolean> = _hasConfig.asStateFlow()

    /** Одноразовое сообщение: импорт успешен / ошибка импорта. null = нет сообщения. */
    private val _importMessage = MutableStateFlow<String?>(null)
    val importMessage: StateFlow<String?> = _importMessage.asStateFlow()

    init {
        // Проверяем наличие конфига при старте.
        _hasConfig.value = ConfigStore.hasConfig(getApplication())

        // Таймер.
        viewModelScope.launch {
            VpnRepository.connectedAt.collectLatest { start ->
                if (start == null) {
                    _timerSeconds.value = 0L
                    return@collectLatest
                }
                while (true) {
                    val diff = (System.currentTimeMillis() - start) / 1000L
                    _timerSeconds.value = if (diff < 0) 0L else diff
                    delay(1000L)
                }
            }
        }
    }

    /** Поднимает туннель, используя конфиг из ConfigStore. */
    fun connect() {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val confText = ConfigStore.loadConfig(ctx)
            if (confText == null) {
                _hasConfig.value = false
                return@launch
            }
            // ВСЕГДА очищаем старые ExcludedApplications/IncludedApplications —
            // они могут просачиваться из кэша конфига / прошлых сессий.
            var finalConf = stripAppFilters(confText)

            // Применяем whitelist (split-туннелинг по сайтам).
            val entries = `fun`.kitoftorvpn.android.whitelist.WhitelistStore.load(ctx)
            if (entries.isNotEmpty()) {
                finalConf = try {
                    `fun`.kitoftorvpn.android.whitelist.WhitelistEngine.applyToConfig(finalConf, entries)
                } catch (e: Exception) {
                    Log.e("MainViewModel", "whitelist apply failed", e)
                    finalConf
                }
            }

            // Применяем per-app exclusions только если список не пустой.
            val exclusions = `fun`.kitoftorvpn.android.perapp.AppExclusionStore.load(ctx)
            if (exclusions.isNotEmpty()) {
                finalConf = addExcludedApplications(finalConf, exclusions)
            }

            Log.d("MainViewModel", "final config:\n$finalConf")
            VpnRepository.connect(ctx, finalConf)
        }
    }

    /** Убирает строки ExcludedApplications и IncludedApplications из конфига. */
    private fun stripAppFilters(confText: String): String {
        return confText.split(Regex("""\r?\n"""))
            .filterNot {
                val t = it.trim()
                t.startsWith("ExcludedApplications", ignoreCase = true) ||
                        t.startsWith("IncludedApplications", ignoreCase = true)
            }
            .joinToString("\n")
    }

    private fun addExcludedApplications(confText: String, excluded: Set<String>): String {
        val lines = confText.split(Regex("""\r?\n""")).toMutableList()
        var interfaceStart = -1
        var insertAt = lines.size
        for (i in lines.indices) {
            val t = lines[i].trim()
            if (t.equals("[Interface]", ignoreCase = true)) interfaceStart = i
            else if (interfaceStart >= 0 && t.startsWith("[")) { insertAt = i; break }
        }
        if (interfaceStart < 0) return confText
        lines.add(insertAt, "ExcludedApplications = " + excluded.joinToString(", "))
        return lines.joinToString("\n")
    }

    fun disconnect() {
        viewModelScope.launch { VpnRepository.disconnect() }
    }

    fun clearError() = VpnRepository.clearError()

    /**
     * Импорт конфига из URI, который вернул SAF (file picker). Читает содержимое,
     * валидирует, сохраняет в ConfigStore.
     */
    fun importConfig(uri: Uri) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                // Persist permission — best-effort, не должен влиять на импорт.
                try {
                    ctx.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Throwable) { /* GET_CONTENT / некорректный URI — игнор */ }

                try {
                    val text = ctx.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader().readText()
                    }
                    Log.d("ImportConfig", "read ${text?.length ?: 0} chars from $uri")
                    if (text.isNullOrBlank()) {
                        "Файл пустой или не удалось прочитать."
                    } else {
                        ConfigStore.saveConfig(ctx, text)
                    }
                } catch (e: SecurityException) {
                    Log.e("ImportConfig", "permission denied", e)
                    "Нет доступа к файлу. Попробуйте снова."
                } catch (e: Exception) {
                    Log.e("ImportConfig", "read failed", e)
                    "Не удалось прочитать файл: ${e.message}"
                }
            }

            if (result == null) {
                _hasConfig.value = true
                _importMessage.value = "Конфигурация сохранена и зашифрована, оригинальный файл можно удалить для безопасности"
            } else {
                _importMessage.value = result
            }
        }
    }

    fun deleteConfig() {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            // Если VPN включён — опускаем перед удалением.
            if (VpnRepository.state.value == VpnState.ON) {
                VpnRepository.disconnect()
            }
            ConfigStore.deleteConfig(ctx)
            _hasConfig.value = false
        }
    }

    fun clearImportMessage() {
        _importMessage.value = null
    }
}
