package `fun`.kitoftorvpn.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import `fun`.kitoftorvpn.android.BuildConfig
import `fun`.kitoftorvpn.android.api.SubscriptionApi
import `fun`.kitoftorvpn.android.api.SubscriptionRepository
import `fun`.kitoftorvpn.android.auth.AuthManager
import `fun`.kitoftorvpn.android.notifications.SubNotificationScheduler
import `fun`.kitoftorvpn.android.settings.AppPreferences
import `fun`.kitoftorvpn.android.storage.ConfigStore
import `fun`.kitoftorvpn.android.ui.screens.AppExclusionScreen
import `fun`.kitoftorvpn.android.ui.screens.FaqScreen
import `fun`.kitoftorvpn.android.ui.screens.LoginScreen
import `fun`.kitoftorvpn.android.ui.screens.MainScreen
import `fun`.kitoftorvpn.android.ui.screens.SettingsScreen
import `fun`.kitoftorvpn.android.ui.screens.SubStatus
import `fun`.kitoftorvpn.android.ui.screens.WhitelistScreen
import `fun`.kitoftorvpn.android.update.UpdateChecker
import `fun`.kitoftorvpn.android.update.UpdateDialog
import `fun`.kitoftorvpn.android.update.UpdateInstaller
import `fun`.kitoftorvpn.android.ui.theme.Bg
import `fun`.kitoftorvpn.android.ui.theme.KitoFtorVPNTheme
import `fun`.kitoftorvpn.android.vpn.VpnRepository
import `fun`.kitoftorvpn.android.vpn.VpnState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private val APP_VERSION: String = BuildConfig.VERSION_NAME

private fun openBrowser(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (_: Exception) { /* ignore */ }
}

private object Routes {
    const val LOGIN = "login"
    const val MAIN = "main"
    const val SETTINGS = "settings"
    const val WHITELIST = "whitelist"
    const val APP_EXCLUSIONS = "app_exclusions"
    const val FAQ = "faq"
}

class MainActivity : ComponentActivity() {

    // Launcher для запроса POST_NOTIFICATIONS (Android 13+). Результат нам
    // не важен — либо юзер разрешил (уведомление покажется), либо нет
    // (приложение всё равно работает, просто без notification).
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* ignore */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Если приложение открылось по deep link (kitoftorvpn://auth?token=...),
        // сразу отдадим токен в AuthManager — поток подберёт подписчик в LoginScreen.
        AuthManager.handleIncomingUri(intent?.data)
        // На Android 13+ без явного разрешения уведомления не показываются.
        // Спросим при первом запуске (система сама покажет диалог один раз;
        // если юзер откажется — второй раз не покажет, это нормально).
        requestNotificationPermissionIfNeeded()

        setContent {
            KitoFtorVPNTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Bg
                ) { innerPadding ->
                    val navController = rememberNavController()
                    AppNavHost(
                        navController = navController,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(Bg)
                    )
                }
            }
        }
    }

    /** Если приложение уже живо и пришёл новый intent (юзер вернулся из браузера). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        AuthManager.handleIncomingUri(intent.data)
    }

    private fun requestNotificationPermissionIfNeeded() {
        // До Android 13 разрешение не требуется — уведомления работают из коробки.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val already = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (already) return
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Проверка обновления через GitHub Releases API. Запускается при первом запуске
    // приложения (один раз). Если найдена более новая версия — показывается диалог.
    var updateInfo by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<UpdateChecker.UpdateInfo?>(null)
    }
    var updateDismissed by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }
    LaunchedEffect(Unit) {
        val latest = UpdateChecker.fetchLatest() ?: return@LaunchedEffect
        if (UpdateChecker.isNewer(BuildConfig.VERSION_NAME, latest.versionName)) {
            updateInfo = latest
        }
    }

    val info = updateInfo
    if (info != null && !updateDismissed) {
        val scope = rememberCoroutineScope()
        UpdateDialog(
            info = info,
            currentVersion = BuildConfig.VERSION_NAME,
            onUpdate = {
                scope.launch {
                    val file = UpdateInstaller.download(context, info.apkUrl)
                    if (file != null) {
                        UpdateInstaller.install(context, file)
                    }
                }
            },
            onDismiss = {
                updateDismissed = true
                UpdateInstaller.reset()
            }
        )
    }
    // Стартовый экран выбирается по сохранённой сессии:
    //   - есть cabinet_session токен → MAIN (авторизованный)
    //   - был установлен guest_mode флаг → MAIN (гость)
    //   - иначе → LOGIN
    val start = remember {
        when {
            !ConfigStore.loadToken(context).isNullOrBlank() -> Routes.MAIN + "?guest=0"
            AppPreferences.getGuestMode(context) -> Routes.MAIN + "?guest=1"
            else -> Routes.LOGIN
        }
    }
    NavHost(
        navController = navController,
        startDestination = start,
        modifier = modifier
    ) {
        composable(Routes.LOGIN) {
            val context = LocalContext.current

            // Слушаем токены из AuthManager. LaunchedEffect привязан к ключу
            // "login" — пока мы на этом экране, корутина активна. Как только
            // токен приходит — сохраняем в шифрованном хранилище и переходим
            // на Main.
            LaunchedEffect(Unit) {
                AuthManager.tokenFlow.collectLatest { token ->
                    ConfigStore.saveToken(context, token)
                    AppPreferences.setGuestMode(context, false)
                    navController.navigateToMain()
                }
            }

            LoginScreen(
                onLogin = { AuthManager.openLogin(context) },
                onRegister = { AuthManager.openRegister(context) },
                onGoogle = { AuthManager.openGoogle(context) },
                onGuest = {
                    AppPreferences.setGuestMode(context, true)
                    navController.navigateToMain(isGuest = true)
                },
            )
        }

        composable(Routes.MAIN + "?guest={guest}") { backStack ->
            val isGuest = backStack.arguments?.getString("guest") == "1"
            val context = LocalContext.current
            val scope = rememberCoroutineScope()

            // Запускаем поллинг подписки для авторизованных.
            LaunchedEffect(isGuest) {
                if (!isGuest) {
                    SubscriptionRepository.startPolling(context)
                } else {
                    SubscriptionRepository.stopPolling()
                }
            }

            // При возврате приложения из фона — мгновенно проверяем подписку,
            // чтобы видеть свежий статус сразу (например, после ручной выдачи подписки админом).
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            androidx.compose.runtime.DisposableEffect(lifecycleOwner, isGuest) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && !isGuest) {
                        SubscriptionRepository.refreshNow(context)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            val subInfo by SubscriptionRepository.subInfo.collectAsStateWithLifecycle()

            // В гостевом режиме подписка не проверяется — гость подключается по своему конфигу.
            // subInfo может содержать закэшированные данные прошлого аккаунта — игнорируем их.
            val subStatusMapped = if (isGuest) {
                SubStatus.ACTIVE
            } else when (subInfo?.status) {
                SubscriptionApi.SubInfo.Status.ACTIVE -> SubStatus.ACTIVE
                SubscriptionApi.SubInfo.Status.EXPIRED -> SubStatus.EXPIRED
                SubscriptionApi.SubInfo.Status.TEST_ENDED -> SubStatus.TEST_ENDED
                SubscriptionApi.SubInfo.Status.NONE -> SubStatus.NONE
                else -> SubStatus.ACTIVE   // до первого ответа показываем optimistic
            }

            val expiresAtMs: Long? = if (isGuest) null else {
                subInfo?.expiresInSeconds
                    ?.takeIf { it > 0L }
                    ?.let { System.currentTimeMillis() + it * 1000L }
                    ?: AppPreferences.getSubExpiresAt(context)
            }

            MainScreen(
                isGuest = isGuest,
                subStatus = subStatusMapped,
                expiresAtMs = expiresAtMs,
                appVersion = APP_VERSION,
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onBuySubscription = {
                    openBrowser(context, "https://my.kitoftorvpn.fun")
                },
                onRenewSubscription = {
                    openBrowser(context, "https://my.kitoftorvpn.fun")
                },
                onLogout = {
                    val token = ConfigStore.loadToken(context)
                    scope.launch {
                        VpnRepository.disconnect()
                        if (!token.isNullOrBlank()) {
                            backendLogout(token)
                        }
                    }
                    ConfigStore.deleteToken(context)
                    SubscriptionRepository.stopPolling()
                    SubNotificationScheduler.cancelAll(context)
                    AppPreferences.clearSubExpiresAt(context)
                    AppPreferences.setGuestMode(context, false)
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onExitGuest = {
                    scope.launch { VpnRepository.disconnect() }
                    AppPreferences.setGuestMode(context, false)
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onOpenFaq = { navController.navigate(Routes.FAQ) },
            )
        }

        composable(Routes.SETTINGS) {
            val context = LocalContext.current
            val isGuest = remember { AppPreferences.getGuestMode(context) }
            val token = remember { ConfigStore.loadToken(context) }
            SettingsScreen(
                appVersion = APP_VERSION,
                isGuest = isGuest,
                sessionToken = token,
                onBack = { navController.popBackStack() },
                onOpenWhitelist = { navController.navigate(Routes.WHITELIST) },
                onOpenAppExclusions = { navController.navigate(Routes.APP_EXCLUSIONS) }
            )
        }

        composable(Routes.FAQ) {
            FaqScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.WHITELIST) {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            WhitelistScreen(
                onBack = { navController.popBackStack() },
                onSaved = { entries ->
                    // После сохранения — если VPN активен, переподключаем с новым whitelist.
                    scope.launch {
                        if (VpnRepository.state.value == VpnState.ON) {
                            VpnRepository.disconnect()
                            val raw = ConfigStore.loadConfig(context)
                            if (!raw.isNullOrBlank()) {
                                val withWl = if (entries.isEmpty()) raw else {
                                    try {
                                        `fun`.kitoftorvpn.android.whitelist.WhitelistEngine
                                            .applyToConfig(raw, entries)
                                    } catch (_: Exception) { raw }
                                }
                                val exclusions = `fun`.kitoftorvpn.android.perapp.AppExclusionStore.load(context)
                                val finalConf = applyExclusions(withWl, exclusions)
                                VpnRepository.connect(context, finalConf)
                            }
                        }
                    }
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.APP_EXCLUSIONS) {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            AppExclusionScreen(
                onBack = { navController.popBackStack() },
                onChanged = {
                    // Если VPN активен — переподключаем с новыми исключениями.
                    scope.launch {
                        if (VpnRepository.state.value == VpnState.ON) {
                            VpnRepository.disconnect()
                            val raw = ConfigStore.loadConfig(context)
                            if (!raw.isNullOrBlank()) {
                                val entries = `fun`.kitoftorvpn.android.whitelist.WhitelistStore.load(context)
                                val withWl = if (entries.isEmpty()) raw else {
                                    try {
                                        `fun`.kitoftorvpn.android.whitelist.WhitelistEngine
                                            .applyToConfig(raw, entries)
                                    } catch (_: Exception) { raw }
                                }
                                val exclusions = `fun`.kitoftorvpn.android.perapp.AppExclusionStore.load(context)
                                val finalConf = applyExclusions(withWl, exclusions)
                                VpnRepository.connect(context, finalConf)
                            }
                        }
                    }
                }
            )
        }
    }
}

/**
 * Вставляет ExcludedApplications = pkg1, pkg2 в секцию [Interface] конфига.
 * Библиотека amneziawg-android (форк wireguard-android) парсит это поле
 * и применяет через VpnService.Builder.addDisallowedApplication.
 */
internal fun applyExclusions(confText: String, excluded: Set<String>): String {
    if (excluded.isEmpty()) return confText
    val lines = confText.split(Regex("""\r?\n""")).toMutableList()
    // Убираем существующую строку, если была.
    lines.removeAll { it.trim().startsWith("ExcludedApplications", ignoreCase = true) }
    // Находим конец секции [Interface] — это первая строка [Peer] или конец файла.
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

private fun NavHostController.navigateToMain(isGuest: Boolean = false) {
    navigate(Routes.MAIN + "?guest=" + (if (isGuest) "1" else "0")) {
        popUpTo(Routes.LOGIN) { inclusive = true }
    }
}

/**
 * Вызывает /logout на бэкенде, чтобы cabinet_session с этим токеном перестала
 * быть валидной. Это важно чтобы при следующем "Войти" в приложении юзер не
 * попадал на страницу подтверждения с предыдущим аккаунтом.
 *
 * Не разлогинивает Google/Telegram — эти куки принадлежат oauth-провайдерам и
 * живут в браузере юзера. Юзер может выйти там самостоятельно при желании.
 */
private suspend fun backendLogout(token: String) {
    withContext(Dispatchers.IO) {
        try {
            val url = URL("https://my.kitoftorvpn.fun/logout")
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("Cookie", "cabinet_session=$token")
            conn.responseCode   // триггерит запрос
            conn.disconnect()
        } catch (_: Exception) {
            // Молча игнорим — логаут локальный всё равно пройдёт.
        }
    }
}
