package `fun`.kitoftorvpn.android.vpn

import android.content.Context
import android.util.Log
import com.wgtunnel.backend.Tunnel
import com.wgtunnel.backend.TunnelBackend
import com.wgtunnel.backend.model.BackendMode
import com.wgtunnel.backend.state.BackendStatus
import com.wgtunnel.parser.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class VpnState { OFF, CONNECTING, ON }

/**
 * Singleton-репозиторий VPN. Почему singleton, а не поле ViewModel:
 *   - ViewModel пересоздаётся при смене конфигурации, туннель должен жить дольше.
 *   - Движок (WG Tunnel Core) нельзя создавать повторно.
 *
 * Движок сам следит за сменой сети (Wi-Fi <-> мобильная связь) и сам
 * переподключает туннель, поэтому ручного мониторинга сети здесь больше нет.
 */
object VpnRepository {

    private const val TAG = "VpnRepository"
    internal const val TUNNEL_ID = 1

    private val _state = MutableStateFlow(VpnState.OFF)
    val state: StateFlow<VpnState> = _state.asStateFlow()

    private val _connectedAt = MutableStateFlow<Long?>(null)
    val connectedAt: StateFlow<Long?> = _connectedAt.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var backend: TunnelBackend? = null
    private var appContext: Context? = null

    // Помечаем отключение как инициированное пользователем. Если переход
    // ON -> OFF случится без флага — это неожиданный разрыв, показываем уведомление.
    @Volatile private var userInitiatedDisconnect = false

    // true, пока идёт connect(): в это время пустой статус движка не считаем «выключено».
    @Volatile private var connecting = false

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tunnel = KitoTunnel()

    @Synchronized
    private fun ensureBackend(context: Context): TunnelBackend {
        val app = context.applicationContext
        appContext = app
        backend?.let { return it }
        val provider = KitoAppProvider(app)
        val monitor = KitoNetworkMonitor(app)
        val b = TunnelBackend(repoScope, provider, monitor)
        backend = b
        repoScope.launch {
            b.status.collect { applyStatus(it) }
        }
        return b
    }

    /** Переводит состояние движка в понятные UI состояния OFF / CONNECTING / ON. */
    private fun applyStatus(status: BackendStatus) {
        val active = status.activeTunnels[TUNNEL_ID]
        if (active == null && connecting) return

        val newState = when (active?.transportState) {
            null -> VpnState.OFF
            Tunnel.State.Starting -> VpnState.CONNECTING
            is Tunnel.State.Up -> VpnState.ON
            Tunnel.State.Stopping, Tunnel.State.Down -> VpnState.OFF
        }

        val old = _state.value
        if (newState == old) return
        Log.d(TAG, "State: $old -> $newState")

        when (newState) {
            VpnState.ON -> {
                if (_connectedAt.value == null) {
                    _connectedAt.value = active?.uptime ?: System.currentTimeMillis()
                }
            }
            VpnState.OFF -> {
                _connectedAt.value = null
                // Неожиданный разрыв: был ON, сейчас OFF, и это не пользователь.
                if (old == VpnState.ON && !userInitiatedDisconnect) {
                    appContext?.let {
                        `fun`.kitoftorvpn.android.notifications.VpnDisconnectNotifier.notifyDropped(it)
                    }
                }
                userInitiatedDisconnect = false
            }
            VpnState.CONNECTING -> {}
        }
        _state.value = newState
        appContext?.let { requestTileRefresh(it) }
    }

    suspend fun connect(context: Context, configText: String) {
        withContext(Dispatchers.IO) {
            _lastError.value = null
            userInitiatedDisconnect = false
            connecting = true
            _state.value = VpnState.CONNECTING
            try {
                val config = Config.parseQuickString(configText)
                config.validate()
                val b = ensureBackend(context)
                b.start(tunnel, BackendMode.Vpn(config)).getOrThrow()
                connecting = false
                applyStatus(b.status.value)
                Log.d(TAG, "Connect requested")
            } catch (e: Exception) {
                Log.e(TAG, "connect failed", e)
                connecting = false
                _lastError.value = e.message ?: "Ошибка подключения"
                userInitiatedDisconnect = true
                _state.value = VpnState.OFF
                _connectedAt.value = null
            } finally {
                connecting = false
            }
        }
    }

    suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            userInitiatedDisconnect = true
            try {
                backend?.stop(TUNNEL_ID)
            } catch (e: Exception) {
                Log.e(TAG, "disconnect failed", e)
            }
            _connectedAt.value = null
            _state.value = VpnState.OFF
            appContext?.let { requestTileRefresh(it) }
        }
    }

    fun clearError() {
        _lastError.value = null
    }

    /**
     * Пинг системы, чтобы Quick Settings Tile запросила обновление даже при
     * закрытой шторке.
     */
    internal fun requestTileRefresh(ctx: Context) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                android.service.quicksettings.TileService.requestListeningState(
                    ctx,
                    android.content.ComponentName(ctx, VpnTileService::class.java)
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "requestListeningState failed", e)
        }
    }
}
