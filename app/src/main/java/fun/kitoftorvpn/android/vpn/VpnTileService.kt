package `fun`.kitoftorvpn.android.vpn

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import `fun`.kitoftorvpn.android.storage.ConfigStore
import `fun`.kitoftorvpn.android.whitelist.WhitelistEngine
import `fun`.kitoftorvpn.android.whitelist.WhitelistStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Плитка в шторке для включения/выключения VPN.
 *
 * Single-scope design: один application-level scope (AppScope) живёт всё время
 * работы процесса — выдерживает teardown самого сервиса. Отдельного scope'а
 * привязанного к service lifecycle нет — это было источником багов.
 */
@RequiresApi(Build.VERSION_CODES.N)
class VpnTileService : TileService() {

    private var listenJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        updateTile(VpnRepository.state.value)
        listenJob?.cancel()
        listenJob = AppScope.launch {
            VpnRepository.state.collect { updateTile(it) }
        }
    }

    override fun onStopListening() {
        listenJob?.cancel()
        listenJob = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val ctx = applicationContext
        val current = VpnRepository.state.value

        if (current == VpnState.ON || current == VpnState.CONNECTING) {
            AppScope.launch { VpnRepository.disconnect() }
            return
        }

        // current == OFF → подключаемся.
        val conf = ConfigStore.loadConfig(ctx)
        if (conf.isNullOrBlank()) { openMainActivity(); return }
        if (VpnService.prepare(ctx) != null) { openMainActivity(); return }

        AppScope.launch {
            val finalConf = buildConfig(ctx, conf)
            VpnRepository.connect(ctx, finalConf)
        }
    }

    /** Читает whitelist/exclusions и применяет к базовому конфигу. */
    private fun buildConfig(ctx: android.content.Context, rawConf: String): String {
        // Убираем старые фильтры (могут закэшироваться между запусками).
        var c = rawConf.split(Regex("""\r?\n""")).filterNot {
            val t = it.trim()
            t.startsWith("ExcludedApplications", ignoreCase = true) ||
                    t.startsWith("IncludedApplications", ignoreCase = true)
        }.joinToString("\n")

        val wl = WhitelistStore.load(ctx)
        if (wl.isNotEmpty()) {
            c = try {
                kotlinx.coroutines.runBlocking { WhitelistEngine.applyToConfig(c, wl) }
            } catch (_: Exception) { c }
        }
        val exc = `fun`.kitoftorvpn.android.perapp.AppExclusionStore.load(ctx)
        if (exc.isNotEmpty()) {
            c = insertExcluded(c, exc)
        }
        return c
    }

    private fun insertExcluded(confText: String, excluded: Set<String>): String {
        val lines = confText.split(Regex("""\r?\n""")).toMutableList()
        var iStart = -1
        var insertAt = lines.size
        for (i in lines.indices) {
            val t = lines[i].trim()
            if (t.equals("[Interface]", ignoreCase = true)) iStart = i
            else if (iStart >= 0 && t.startsWith("[")) { insertAt = i; break }
        }
        if (iStart < 0) return confText
        lines.add(insertAt, "ExcludedApplications = " + excluded.joinToString(", "))
        return lines.joinToString("\n")
    }

    private fun updateTile(state: VpnState) {
        val tile = qsTile ?: return
        tile.state = when (state) {
            VpnState.ON, VpnState.CONNECTING -> Tile.STATE_ACTIVE
            VpnState.OFF -> Tile.STATE_INACTIVE
        }
        tile.label = "KitoFtorVPN"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when (state) {
                VpnState.ON -> "Подключён"
                VpnState.CONNECTING -> "Подключение..."
                VpnState.OFF -> "Отключён"
            }
        }
        tile.updateTile()
    }

    private fun openMainActivity() {
        val intent = Intent(this, `fun`.kitoftorvpn.android.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = android.app.PendingIntent.getActivity(
                this, 0, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}

/** Singleton application-level scope. Живёт всё время процесса. */
private object AppScope : CoroutineScope {
    override val coroutineContext = Dispatchers.IO + SupervisorJob()
}
