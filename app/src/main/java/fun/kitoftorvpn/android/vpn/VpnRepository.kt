package `fun`.kitoftorvpn.android.vpn

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.amnezia.awg.backend.GoBackend
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.backend.TunnelActionHandler
import org.amnezia.awg.config.Config
import java.io.ByteArrayInputStream

enum class VpnState { OFF, CONNECTING, ON }

/**
 * Singleton-репозиторий VPN. Почему singleton, а не поле ViewModel:
 *   - ViewModel пересоздаётся при смене конфигурации, туннель должен жить дольше.
 *   - GoBackend регистрирует broadcast-receiver'ы — повторно создавать нельзя.
 */
object VpnRepository {

    private const val TAG = "VpnRepository"

    private val _state = MutableStateFlow(VpnState.OFF)
    val state: StateFlow<VpnState> = _state.asStateFlow()

    private val _connectedAt = MutableStateFlow<Long?>(null)
    val connectedAt: StateFlow<Long?> = _connectedAt.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var backend: GoBackend? = null
    private var appContext: Context? = null

    // Помечаем текущую операцию как инициированную пользователем (disconnect()
    // или ошибка connect()). Если переход ON -> OFF случится без флага —
    // это неожиданный разрыв, показываем уведомление.
    @Volatile private var userInitiatedDisconnect = false

    private val tunnel = object : Tunnel {
        override fun getName() = "KitoFtorVPN"
        override fun onStateChange(newState: Tunnel.State) {
            val mapped = when (newState) {
                Tunnel.State.UP -> VpnState.ON
                Tunnel.State.DOWN -> VpnState.OFF
            }
            Log.d(TAG, "Tunnel state change: $newState -> $mapped")
            if (mapped == VpnState.OFF) {
                val wasOn = _state.value == VpnState.ON
                _connectedAt.value = null
                appContext?.let { VpnNotification.hide(it) }
                // Неожиданный разрыв: был ON, сейчас OFF, и это не пользователь.
                if (wasOn && !userInitiatedDisconnect) {
                    appContext?.let {
                        `fun`.kitoftorvpn.android.notifications.VpnDisconnectNotifier.notifyDropped(it)
                    }
                }
                userInitiatedDisconnect = false
            } else if (mapped == VpnState.ON) {
                appContext?.let { VpnNotification.show(it) }
            }
            _state.value = mapped
            // Просим систему обновить Quick Settings Tile (плитку) даже если шторка закрыта.
            appContext?.let { requestTileRefresh(it) }
        }
        override fun isIpv4ResolutionPreferred() = true
        override fun isMetered() = false
    }

    private fun ensureBackend(context: Context): GoBackend {
        appContext = context.applicationContext
        backend?.let { return it }
        val b = GoBackend(context.applicationContext, object : TunnelActionHandler {
            override fun runPreUp(scripts: MutableCollection<String>) {}
            override fun runPostUp(scripts: MutableCollection<String>) {}
            override fun runPreDown(scripts: MutableCollection<String>) {}
            override fun runPostDown(scripts: MutableCollection<String>) {}
        })
        backend = b
        return b
    }

    suspend fun connect(context: Context, configText: String) {
        withContext(Dispatchers.IO) {
            _lastError.value = null
            _state.value = VpnState.CONNECTING
            try {
                val config = Config.parse(ByteArrayInputStream(configText.toByteArray()))
                val b = ensureBackend(context)
                b.setState(tunnel, Tunnel.State.UP, config)
                _connectedAt.value = System.currentTimeMillis()
                _state.value = VpnState.ON
                VpnNotification.show(context.applicationContext)
                Log.d(TAG, "Connected")
            } catch (e: Exception) {
                Log.e(TAG, "connect failed", e)
                _lastError.value = e.message ?: "Ошибка подключения"
                userInitiatedDisconnect = true
                _state.value = VpnState.OFF
                _connectedAt.value = null
            }
        }
    }

    suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            userInitiatedDisconnect = true
            try {
                backend?.setState(tunnel, Tunnel.State.DOWN, null)
            } catch (e: Exception) {
                Log.e(TAG, "disconnect failed", e)
            }
            _connectedAt.value = null
            _state.value = VpnState.OFF
            appContext?.let { VpnNotification.hide(it) }
        }
    }

    fun clearError() {
        _lastError.value = null
    }

    /**
     * Пинг системы, чтобы Quick Settings Tile запросила обновление даже при
     * закрытой шторке. Без этого плитка обновляется только когда юзер её видит.
     */
    private fun requestTileRefresh(ctx: Context) {
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
