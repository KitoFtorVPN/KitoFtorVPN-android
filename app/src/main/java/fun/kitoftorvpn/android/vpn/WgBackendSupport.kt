package `fun`.kitoftorvpn.android.vpn

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import com.wgtunnel.backend.AndroidApplicationProvider
import com.wgtunnel.backend.Tunnel
import com.wgtunnel.backend.state.BackendStatus
import com.wgtunnel.backend.system.NetworkMonitor
import com.wgtunnel.backend.system.NetworkSnapshot
import `fun`.kitoftorvpn.android.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet6Address

/** Описание нашего единственного туннеля для движка. */
internal class KitoTunnel : Tunnel {
    override val id: Int = VpnRepository.TUNNEL_ID
    override val name: String = "KitoFtorVPN"
    override val isMetered: Boolean = false
    override val scriptsEnabled: Boolean = false
    override val ipStrategy: Tunnel.IpStrategy = Tunnel.IpStrategy.Ipv4Only
    override val features: Set<Tunnel.Feature> = setOf(
        Tunnel.Feature.Recovery(
            seamlessRecovery = true,
            dynamicDnsRecovery = true,
        )
    )

    override fun updateState(state: Tunnel.State) {}
}

/** Мост между движком и приложением: уведомления, плитка, открытие приложения. */
internal class KitoAppProvider(private val app: Context) : AndroidApplicationProvider {

    override val context: Context = app
    override val vpnNotificationId: Int = VpnNotification.NOTIFICATION_ID
    override val proxyNotificationId: Int = VpnNotification.NOTIFICATION_ID + 1

    override val vpnInitNotification: Notification
        get() = VpnNotification.build(app, "Подключение…")
    override val proxyInitNotification: Notification
        get() = VpnNotification.build(app, "Подключение…")

    override fun createVpnConfigurePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override suspend fun buildVpnPersistentNotification(status: BackendStatus): Notification =
        VpnNotification.build(app, textFor(status))

    override suspend fun buildProxyPersistentNotification(status: BackendStatus): Notification =
        VpnNotification.build(app, textFor(status))

    private fun textFor(status: BackendStatus): String {
        val state = status.activeTunnels[VpnRepository.TUNNEL_ID]?.transportState
        return when (state) {
            is Tunnel.State.Up -> "Подключён"
            Tunnel.State.Starting -> "Подключение…"
            Tunnel.State.Stopping -> "Отключение…"
            else -> "Подключён"
        }
    }

    override fun refreshStatusUi() {
        VpnRepository.requestTileRefresh(app)
    }
}

/**
 * Следит за «настоящей» сетью телефона (Wi-Fi / мобильная) — той, через которую
 * уходит сам VPN-трафик. Движок использует это, чтобы переживать смену сети.
 */
internal class KitoNetworkMonitor(context: Context) : NetworkMonitor {

    private val cm: ConnectivityManager? =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    private val _state = MutableStateFlow<NetworkSnapshot?>(null)
    override val networkState: StateFlow<NetworkSnapshot?> = _state.asStateFlow()

    private val known = LinkedHashMap<Network, NetworkCapabilities>()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            publish(network)
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            synchronized(known) { known[network] = caps }
            publish(network)
        }

        override fun onLinkPropertiesChanged(
            network: Network,
            linkProperties: android.net.LinkProperties
        ) {
            publish(network)
        }

        override fun onLost(network: Network) {
            synchronized(known) { known.remove(network) }
            publish(null)
        }
    }

    init {
        register()
    }

    private fun register() {
        val manager = cm ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                manager.registerBestMatchingNetworkCallback(
                    request, callback, android.os.Handler(android.os.Looper.getMainLooper())
                )
            } else {
                manager.registerNetworkCallback(request, callback)
            }
        } catch (_: Exception) {
        }
    }

    private fun publish(changed: Network?) {
        val manager = cm ?: return
        // Лучшая сеть: сначала проверенная системой, иначе любая известная.
        val best: Network? = synchronized(known) {
            val validated = known.entries.lastOrNull {
                it.value.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }?.key
            validated ?: known.keys.lastOrNull() ?: changed
        }
        if (best == null) {
            _state.value = null
            return
        }
        val caps = manager.getNetworkCapabilities(best)
        val props = manager.getLinkProperties(best)
        val hasIpv6 = props?.linkAddresses?.any {
            val a = it.address
            a is Inet6Address && !a.isLinkLocalAddress && !a.isLoopbackAddress
        }
        _state.value = NetworkSnapshot(
            key = "net-${best.networkHandle}",
            hasIpv6 = hasIpv6,
            isUsable = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
            network = best,
        )
    }
}
