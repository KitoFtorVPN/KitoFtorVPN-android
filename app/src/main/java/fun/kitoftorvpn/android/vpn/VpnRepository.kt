package `fun`.kitoftorvpn.android.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.amnezia.awg.backend.GoBackend
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.backend.TunnelActionHandler
import org.amnezia.awg.config.Config
import java.io.ByteArrayInputStream

enum class VpnState { OFF, CONNECTING, ON }

/** Тип транспорта активной сети — нужен, чтобы отличать Wi-Fi от мобильной связи. */
private enum class TransportType { WIFI, CELLULAR, ETHERNET, OTHER, NONE }

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

    // Последний конфиг, с которым был успешный connect() — нужен, чтобы
    // переподключиться на новой сети с теми же параметрами (whitelist/exclusions
    // уже вшиты внутрь этой строки на момент вызова connect()).
    @Volatile private var lastConfigText: String? = null

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var lastTransport: TransportType = TransportType.NONE
    // Дебаунс: при переключении вышка/точка может пару раз мигнуть сетями подряд
    // в течение доли секунды — не хотим переподключаться на каждое промежуточное событие.
    private var switchJob: Job? = null
    private const val SWITCH_DEBOUNCE_MS = 800L
    private const val POLL_INTERVAL_MS = 2000L

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
        startNetworkMonitoring(context.applicationContext)
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

    /**
     * Подписываемся на смену сети-по-умолчанию системы (т.е. той сети, через
     * которую реально идёт трафик приложений). Когда телефон переключается
     * Wi-Fi <-> мобильная связь, GoBackend держит туннель привязанным к уже
     * умершему сетевому интерфейсу, и трафик перестаёт идти, хотя сам VpnService
     * формально жив. Чиним это явным reconnect'ом туннеля на новой сети.
     *
     * Важно: используем registerDefaultNetworkCallback (а не обычный
     * registerNetworkCallback + чтение cm.activeNetwork) и берём тип транспорта
     * из параметров самого колбэка (onCapabilitiesChanged), а не повторным
     * синхронным запросом к ConnectivityManager — так советует документация
     * Android, потому что синхронный запрос сразу после onAvailable может
     * вернуть устаревшие данные (race condition) и реконнект не сработает.
     */
    private fun startNetworkMonitoring(context: Context) {
        if (networkCallback != null) return
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        connectivityManager = cm
        lastTransport = currentTransport(cm)

        val callback = object : ConnectivityManager.NetworkCallback() {
            // onAvailable сообщает только что появилась сеть-по-умолчанию, но
            // её capabilities в этот момент могут быть ещё не финальными —
            // решение принимаем по onCapabilitiesChanged, который Android
            // гарантированно вызывает следом с актуальными данными.
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val transport = transportFromCapabilities(caps)
                handlePossibleSwitch(transport)
            }

            override fun onLost(network: Network) {
                // Сеть-по-умолчанию исчезла (например, Wi-Fi выключили и
                // мобильная связь ещё не подхватила). Запоминаем "нет сети" —
                // дождёмся следующего onCapabilitiesChanged с новой сетью.
                handlePossibleSwitch(TransportType.NONE)
            }
        }

        try {
            cm.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        } catch (e: Exception) {
            Log.w(TAG, "registerDefaultNetworkCallback failed", e)
        }

        // Подстраховка: некоторые прошивки (особенно от китайских производителей)
        // нестабильно доставляют NetworkCallback. Параллельно с колбэком держим
        // лёгкий опрос раз в 2 секунды — он точно поймает смену сети, даже если
        // колбэк почему-то не сработал. Цена — пара миллисекунд раз в 2 секунды.
        repoScope.launch {
            while (true) {
                delay(POLL_INTERVAL_MS)
                val polled = currentTransport(cm)
                handlePossibleSwitch(polled)
            }
        }
    }

    /** Синхронный снимок текущей сети — используется только для начального значения. */
    private fun currentTransport(cm: ConnectivityManager): TransportType {
        val active = cm.activeNetwork ?: return TransportType.NONE
        val caps = cm.getNetworkCapabilities(active) ?: return TransportType.NONE
        return transportFromCapabilities(caps)
    }

    private fun transportFromCapabilities(caps: NetworkCapabilities): TransportType = when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> TransportType.WIFI
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> TransportType.CELLULAR
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> TransportType.ETHERNET
        else -> TransportType.OTHER
    }

    /**
     * Реагируем только если реально сменился тип транспорта сети-по-умолчанию
     * (а не просто мигнули прочие capabilities той же сети), и только если
     * туннель сейчас поднят.
     */
    private fun handlePossibleSwitch(newTransport: TransportType) {
        if (newTransport == TransportType.NONE) {
            // Сеть пропала полностью — ничего не делаем, дождёмся следующей сети.
            lastTransport = TransportType.NONE
            return
        }
        if (newTransport == lastTransport) return
        val previous = lastTransport
        lastTransport = newTransport
        Log.d(TAG, "Network transport changed: $previous -> $newTransport")

        if (_state.value != VpnState.ON) return
        val configText = lastConfigText ?: return

        // Дебаунс: ждём, не сменится ли сеть ещё раз почти сразу, и только потом
        // переподключаем туннель на новой сети.
        switchJob?.cancel()
        switchJob = repoScope.launch {
            delay(SWITCH_DEBOUNCE_MS)
            if (lastTransport != newTransport) return@launch // сеть успела измениться снова
            if (_state.value != VpnState.ON) return@launch
            Log.d(TAG, "Reconnecting tunnel after network switch to $newTransport")
            reconnectInternal(configText)
        }
    }

    /** Полный цикл down/up туннеля на текущей активной сети, без участия UI. */
    private suspend fun reconnectInternal(configText: String) {
        withContext(Dispatchers.IO) {
            val ctx = appContext ?: return@withContext
            val b = backend ?: return@withContext
            try {
                userInitiatedDisconnect = true
                b.setState(tunnel, Tunnel.State.DOWN, null)
            } catch (e: Exception) {
                Log.w(TAG, "reconnect: down step failed", e)
            }
            try {
                val config = Config.parse(ByteArrayInputStream(configText.toByteArray()))
                b.setState(tunnel, Tunnel.State.UP, config)
                _connectedAt.value = System.currentTimeMillis()
                Log.d(TAG, "Reconnected after network switch")
            } catch (e: Exception) {
                Log.e(TAG, "reconnect: up step failed", e)
                _lastError.value = e.message ?: "Ошибка переподключения"
                _state.value = VpnState.OFF
                _connectedAt.value = null
                appContext?.let { VpnNotification.hide(it) }
            }
        }
    }

    suspend fun connect(context: Context, configText: String) {
        withContext(Dispatchers.IO) {
            _lastError.value = null
            _state.value = VpnState.CONNECTING
            try {
                val config = Config.parse(ByteArrayInputStream(configText.toByteArray()))
                val b = ensureBackend(context)
                b.setState(tunnel, Tunnel.State.UP, config)
                lastConfigText = configText
                connectivityManager?.let { lastTransport = currentTransport(it) }
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
            switchJob?.cancel()
            lastConfigText = null
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
