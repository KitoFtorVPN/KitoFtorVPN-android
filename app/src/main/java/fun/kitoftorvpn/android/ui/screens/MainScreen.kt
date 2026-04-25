package `fun`.kitoftorvpn.android.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import `fun`.kitoftorvpn.android.ui.theme.Accent
import `fun`.kitoftorvpn.android.ui.theme.AccentBg08
import `fun`.kitoftorvpn.android.ui.theme.Bg
import `fun`.kitoftorvpn.android.ui.theme.BgConnecting
import `fun`.kitoftorvpn.android.ui.theme.BgOn
import `fun`.kitoftorvpn.android.ui.theme.Border
import `fun`.kitoftorvpn.android.ui.theme.Danger
import `fun`.kitoftorvpn.android.ui.theme.DangerBg06
import `fun`.kitoftorvpn.android.ui.theme.DangerBg08
import `fun`.kitoftorvpn.android.ui.theme.DangerBg12
import `fun`.kitoftorvpn.android.ui.theme.DangerBg15
import `fun`.kitoftorvpn.android.ui.theme.Surface
import `fun`.kitoftorvpn.android.ui.theme.TextDim
import `fun`.kitoftorvpn.android.ui.theme.TextMain
import `fun`.kitoftorvpn.android.ui.theme.TextMuted
import `fun`.kitoftorvpn.android.ui.theme.Warning
import `fun`.kitoftorvpn.android.ui.theme.WarningBg05
import `fun`.kitoftorvpn.android.vpn.MainViewModel
import `fun`.kitoftorvpn.android.vpn.VpnState as RepoVpnState
import kotlinx.coroutines.delay

enum class VpnUiState { OFF, CONNECTING, ON }
enum class SubStatus { ACTIVE, EXPIRED, TEST_ENDED, NONE }

@Composable
fun MainScreen(
    subStatus: SubStatus = SubStatus.ACTIVE,
    // Абсолютный timestamp окончания подписки (ms, UTC). Если null — показывается
    // заглушка. Внутри крутится живой таймер, пересчитывает время раз в секунду.
    expiresAtMs: Long? = System.currentTimeMillis() + 29L * 86400L * 1000L,
    isGuest: Boolean = false,
    appVersion: String = "1.0",
    onOpenSettings: () -> Unit = {},
    onLogout: () -> Unit = {},
    onExitGuest: () -> Unit = {},
    onBuySubscription: () -> Unit = {},
    onRenewSubscription: () -> Unit = {},
    onOpenFaq: () -> Unit = {},
) {
    val activity = LocalContext.current as androidx.activity.ComponentActivity
    val vm: MainViewModel = viewModel(viewModelStoreOwner = activity)
    val repoState by vm.vpnState.collectAsStateWithLifecycle()
    val timer by vm.timerSeconds.collectAsStateWithLifecycle()
    val error by vm.lastError.collectAsStateWithLifecycle()
    val hasConfig by vm.hasConfig.collectAsStateWithLifecycle()
    val importMessage by vm.importMessage.collectAsStateWithLifecycle()

    // Живой таймер подписки — ремейнинг время в секундах, обновляется раз в секунду.
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(expiresAtMs) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000L)
        }
    }
    val expiresInSeconds: Long? = expiresAtMs?.let {
        val diff = (it - nowMs) / 1000L
        if (diff < 0L) 0L else diff
    }
    val timeLeft: String? = expiresInSeconds?.let { formatRemaining(it) }

    val context = LocalContext.current

    // Launcher для запроса разрешения VpnService.
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            vm.connect()
        }
    }

    // Надёжный пикер .conf файлов для всех устройств включая проблемные Samsung.
    // Стратегия:
    //   1. Пробуем ACTION_OPEN_DOCUMENT (показывает только файлы, скрывает галерею/музыку).
    //   2. Если на устройстве нет DocumentsUI (часть Samsung/кастомных прошивок) —
    //      Intent кинет ActivityNotFoundException: ловим и запускаем fallback.
    //   3. Fallback: ACTION_GET_CONTENT с "*/*" + CATEGORY_OPENABLE через createChooser.
    //      "*/*" вместо "text/plain" — иначе .conf (octet-stream / unknown MIME) не видно.
    //   4. После выбора валидируем URI: файл должен иметь расширение .conf
    //      ИЛИ пройти содержательную проверку [Interface]/[Peer] в saveConfig.
    // На некоторых Samsung-сборках пикер возвращает RESULT_CANCELED даже после выбора файла,
    // но URI при этом присутствует. Правило: если URI есть — используем, resultCode игнорируем.
    val configPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val uri: Uri? = data?.data
            ?: data?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri

        android.util.Log.d(
            "ImportConfig",
            "picker resultCode=${result.resultCode}, uri=$uri"
        )

        if (uri != null) {
            vm.importConfig(uri)
        } else {
            android.widget.Toast.makeText(
                context,
                "Файл не выбран",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Альтернативный лаунчер через OpenDocument contract — на Samsung часто работает,
    // когда ручной Intent возвращает cancel.
    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        android.util.Log.d("ImportConfig", "openDoc returned uri=$uri")
        if (uri != null) vm.importConfig(uri)
    }

    fun launchConfigPicker() {
        val mimes = arrayOf(
            "text/plain",
            "application/octet-stream",
            "application/x-wireguard-config"
        )

        // На Samsung SAF DocumentsUI недоступен — работает только GET_CONTENT через
        // системный chooser (3 варианта: Галерея/Звук/Мои файлы). Выбор "Мои файлы"
        // позволяет найти .conf.
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, mimes)
        }
        try {
            configPickerLauncher.launch(Intent.createChooser(intent, "Выберите .conf файл"))
        } catch (e: Exception) {
            android.util.Log.e("ImportConfig", "No file picker available", e)
            android.widget.Toast.makeText(
                context,
                "На устройстве нет файлового менеджера",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    val uiState = repoState.toUi()

    // Локальная коррекция статуса: если таймер уже добежал до нуля,
    // показываем "Истекла" моментально, не дожидаясь ответа сервера.
    // Это убирает мерцание кнопки "Продлить" при открытии после истечения.
    val effectiveSubStatus = if (
        !isGuest &&
        subStatus == SubStatus.ACTIVE &&
        expiresInSeconds != null && expiresInSeconds <= 0L
    ) SubStatus.EXPIRED else subStatus

    MainScreenStateless(
        vpnState = uiState,
        timerSeconds = timer,
        errorText = error,
        importMessage = importMessage,
        hasConfig = hasConfig,
        subStatus = effectiveSubStatus,
        timeLeft = timeLeft,
        expiresInSeconds = expiresInSeconds,
        isGuest = isGuest,
        appVersion = appVersion,
        onToggleVpn = {
            if (uiState == VpnUiState.ON) {
                vm.disconnect()
            } else if (uiState == VpnUiState.OFF) {
                val prep: Intent? = VpnService.prepare(context)
                if (prep != null) vpnPermissionLauncher.launch(prep)
                else vm.connect()
            }
        },
        onDismissError = { vm.clearError() },
        onImportConfig = {
            launchConfigPicker()
        },
        onDeleteConfig = { vm.deleteConfig() },
        onDismissImportMessage = { vm.clearImportMessage() },
        onOpenSettings = onOpenSettings,
        onLogout = onLogout,
        onExitGuest = onExitGuest,
        onBuySubscription = onBuySubscription,
        onRenewSubscription = onRenewSubscription,
        onOpenFaq = onOpenFaq,
    )
}

private fun RepoVpnState.toUi(): VpnUiState = when (this) {
    RepoVpnState.OFF -> VpnUiState.OFF
    RepoVpnState.CONNECTING -> VpnUiState.CONNECTING
    RepoVpnState.ON -> VpnUiState.ON
}

@Composable
fun MainScreenStateless(
    vpnState: VpnUiState,
    timerSeconds: Long,
    errorText: String?,
    importMessage: String?,
    hasConfig: Boolean,
    subStatus: SubStatus,
    timeLeft: String?,
    expiresInSeconds: Long?,
    isGuest: Boolean,
    appVersion: String,
    onToggleVpn: () -> Unit,
    onDismissError: () -> Unit,
    onImportConfig: () -> Unit,
    onDeleteConfig: () -> Unit,
    onDismissImportMessage: () -> Unit,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit,
    onExitGuest: () -> Unit,
    onBuySubscription: () -> Unit,
    onRenewSubscription: () -> Unit,
    onOpenFaq: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    val bgColor by animateColorAsState(
        targetValue = when (vpnState) {
            VpnUiState.ON -> BgOn
            VpnUiState.CONNECTING -> BgConnecting
            VpnUiState.OFF -> Bg
        },
        animationSpec = tween(800),
        label = "bgColor"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ─── Top bar ─────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "KitoFtorVPN",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMain
                    )
                    Box {
                        MenuButton(onClick = { menuOpen = !menuOpen })
                        if (menuOpen) {
                            Popup(
                                alignment = Alignment.TopEnd,
                                offset = androidx.compose.ui.unit.IntOffset(
                                    x = 0,
                                    y = with(androidx.compose.ui.platform.LocalDensity.current) { 52.dp.roundToPx() }
                                ),
                                properties = PopupProperties(focusable = true),
                                onDismissRequest = { menuOpen = false }
                            ) {
                                DropdownContent(
                                    isGuest = isGuest,
                                    onOpenFaq = { menuOpen = false; onOpenFaq() },
                                    onOpenSettings = { menuOpen = false; onOpenSettings() },
                                    onLogout = { menuOpen = false; onLogout() },
                                    onExitGuest = { menuOpen = false; onExitGuest() }
                                )
                            }
                        }
                    }
                }
            }

            // ─── Status card ─────────────────────────
            if (isGuest) {
                GuestStatusCard()
            } else {
                SubscriptionCard(
                    status = subStatus,
                    timeLeft = timeLeft,
                    expiresInSeconds = expiresInSeconds,
                    onRenew = onRenewSubscription
                )
            }

            // ─── Центр ───────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val canConnect = subStatus == SubStatus.ACTIVE

                when {
                    !hasConfig -> ImportPrompt(onImport = onImportConfig)

                    !canConnect -> DisabledPowerWithRenew(
                        subStatus = subStatus,
                        onAction = if (subStatus == SubStatus.NONE) onBuySubscription else onRenewSubscription
                    )

                    else -> PowerSection(
                        state = vpnState,
                        timerSeconds = timerSeconds,
                        errorText = errorText,
                        onToggle = onToggleVpn,
                        onDismissError = onDismissError,
                    )
                }
            }
        }

        // ─── Toast импорта (оверлей снизу) ─────────
        if (importMessage != null) {
            ImportToast(
                message = importMessage,
                onDismiss = onDismissImportMessage,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
            )
        }
    }
}

// ─── Status card ─────────────────────────────────────────

@Composable
private fun SubscriptionCard(
    status: SubStatus,
    timeLeft: String?,
    expiresInSeconds: Long?,
    onRenew: () -> Unit
) {
    val (statusText, statusColor) = when (status) {
        SubStatus.ACTIVE -> "Активна" to Accent
        SubStatus.EXPIRED -> "Истекла" to Danger
        SubStatus.TEST_ENDED -> "Тест завершён" to Danger
        SubStatus.NONE -> "Нет подписки" to TextMuted
    }

    val isExpiring = expiresInSeconds != null && expiresInSeconds in 1L..259_200L
    val timeColor = if (isExpiring) Danger else TextMain

    // Показываем строку "Осталось" только при активной подписке и положительном времени.
    val showTimeRow = status == SubStatus.ACTIVE &&
            timeLeft != null &&
            (expiresInSeconds == null || expiresInSeconds > 0L)

    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Surface)
                .border(1.dp, Border, RoundedCornerShape(14.dp))
                .padding(horizontal = 20.dp)
        ) {
            StatusRow("Подписка", statusText, statusColor, hasDivider = showTimeRow)
            if (showTimeRow) {
                StatusRow("Осталось", timeLeft!!, timeColor, hasDivider = false)
            }
        }

        if (isExpiring && status == SubStatus.ACTIVE) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DangerBg06)
                    .border(1.dp, DangerBg12, RoundedCornerShape(12.dp))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Подписка заканчивается", fontSize = 15.sp, color = Danger)
                Text(
                    "Продлить",
                    fontSize = 15.sp,
                    color = Danger,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable(onClick = onRenew)
                )
            }
        }
    }
}

@Composable
private fun GuestStatusCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface)
            .border(1.dp, Border, RoundedCornerShape(14.dp))
            .padding(horizontal = 20.dp)
    ) {
        StatusRow("Режим", "Гостевой", TextMain, hasDivider = false)
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    hasDivider: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp, color = TextDim)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
    if (hasDivider) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Border))
    }
}

// ─── Центр ────────────────────────────────────────────────

/**
 * Фиксированная секция с кнопкой и подписями.
 *
 * Чтобы кнопка не прыгала при смене состояния — все блоки (status/hint/timer)
 * имеют фиксированные высоты. При переходе OFF → CONNECTING → ON сумма высот
 * не меняется.
 *
 * Раскладка (сверху вниз):
 *   power-btn (190) | 26 | status (30) | 6 | hint (22) | 4 | timer-slot (24)
 */
@Composable
private fun PowerSection(
    state: VpnUiState,
    timerSeconds: Long,
    errorText: String?,
    onToggle: () -> Unit,
    onDismissError: () -> Unit,
) {
    val statusText = when (state) {
        VpnUiState.ON -> "Подключено"
        VpnUiState.CONNECTING -> "Подключение..."
        VpnUiState.OFF -> "Не подключено"
    }
    val hintText = when (state) {
        VpnUiState.CONNECTING -> "Устанавливаем соединение"
        VpnUiState.OFF -> "Нажмите для подключения"
        VpnUiState.ON -> ""  // слот всё равно зарезервирован
    }
    val statusColor by animateColorAsState(
        targetValue = when (state) {
            VpnUiState.ON -> Accent
            VpnUiState.CONNECTING -> Warning
            VpnUiState.OFF -> TextMuted
        },
        animationSpec = tween(400),
        label = "status-color"
    )

    PowerButton(state = state, onClick = onToggle)

    Spacer(Modifier.height(26.dp))
    // status — фиксированная высота
    Box(modifier = Modifier.height(32.dp), contentAlignment = Alignment.Center) {
        Text(statusText, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = statusColor)
    }
    Spacer(Modifier.height(6.dp))
    // hint — фиксированная высота, пустая строка тоже занимает место
    Box(modifier = Modifier.height(22.dp), contentAlignment = Alignment.Center) {
        if (hintText.isNotEmpty()) {
            Text(hintText, fontSize = 15.sp, color = TextMuted)
        }
    }
    Spacer(Modifier.height(4.dp))
    // timer — всегда зарезервировано место; когда VPN off, просто пусто
    Box(modifier = Modifier.height(24.dp), contentAlignment = Alignment.Center) {
        androidx.compose.animation.AnimatedVisibility(
            visible = state == VpnUiState.ON,
            enter = androidx.compose.animation.fadeIn(animationSpec = tween(500)) +
                    androidx.compose.animation.slideInVertically(
                        animationSpec = tween(500),
                        initialOffsetY = { it / 2 }
                    ),
            exit = androidx.compose.animation.fadeOut(animationSpec = tween(200))
        ) {
            Text(
                formatTimer(timerSeconds),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Accent
            )
        }
    }

    if (errorText != null) {
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(DangerBg06)
                .border(1.dp, DangerBg12, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .clickable(onClick = onDismissError)
        ) {
            Text(errorText, fontSize = 13.sp, color = Danger, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PowerButton(state: VpnUiState, onClick: () -> Unit) {
    val borderColor by animateColorAsState(
        targetValue = when (state) {
            VpnUiState.ON -> Accent
            VpnUiState.CONNECTING -> Warning
            VpnUiState.OFF -> Border
        },
        animationSpec = tween(400),
        label = "pb-border"
    )
    val fillColor by animateColorAsState(
        targetValue = when (state) {
            VpnUiState.ON -> AccentBg08
            VpnUiState.CONNECTING -> WarningBg05
            VpnUiState.OFF -> Surface
        },
        animationSpec = tween(400),
        label = "pb-fill"
    )
    val iconColor by animateColorAsState(
        targetValue = when (state) {
            VpnUiState.ON -> Accent
            VpnUiState.CONNECTING -> Warning
            VpnUiState.OFF -> TextMuted
        },
        animationSpec = tween(400),
        label = "pb-icon"
    )

    val infinite = rememberInfiniteTransition(label = "pb-infinite")

    // Pulse во время CONNECTING — лёгкое пульсирование всей кнопки
    val pulse by infinite.animateFloat(
        initialValue = 1f,
        targetValue = if (state == VpnUiState.CONNECTING) 0.93f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Вращение внешнего кольца во время CONNECTING
    val ringRotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = if (state == VpnUiState.CONNECTING) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring-rot"
    )

    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier.size(210.dp),
        contentAlignment = Alignment.Center
    ) {
        // Внешнее вращающееся кольцо (видимо только во время CONNECTING)
        if (state == VpnUiState.CONNECTING) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier.size(210.dp).graphicsRotate(ringRotation)
            ) {
                val strokeWidth = 4.dp.toPx()
                val diameter = size.minDimension - strokeWidth
                drawArc(
                    color = Warning,
                    startAngle = -90f,
                    sweepAngle = 80f,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2, strokeWidth / 2),
                    size = androidx.compose.ui.geometry.Size(diameter, diameter),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round
                    )
                )
            }
        }

        Box(
            modifier = Modifier
                .size(190.dp)
                .graphicsScale(pulse)
                .clip(CircleShape)
                .background(fillColor)
                .border(4.dp, borderColor, CircleShape)
                .clickable(
                    interactionSource = interaction,
                    indication = ripple(bounded = false, radius = 95.dp, color = iconColor),
                    enabled = state != VpnUiState.CONNECTING,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                imageVector = powerVector(iconColor),
                contentDescription = "Power",
                modifier = Modifier.size(60.dp)
            )
        }
    }
}

private fun Modifier.graphicsRotate(degrees: Float): Modifier =
    this.then(Modifier.graphicsLayer(rotationZ = degrees))

private fun Modifier.graphicsScale(scale: Float): Modifier =
    this.then(Modifier.graphicsLayer(scaleX = scale, scaleY = scale))

@Composable
private fun ImportPrompt(onImport: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Для подключения загрузите\nфайл конфигурации (.conf)\nиз личного кабинета или бота",
            fontSize = 17.sp,
            color = TextDim,
            textAlign = TextAlign.Center,
            lineHeight = 28.sp
        )
        Spacer(Modifier.height(22.dp))
        // Полноценная кнопка вместо маленькой ссылки — удобнее на телефоне
        Box(
            modifier = Modifier
                .height(54.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Accent)
                .clickable(onClick = onImport)
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    imageVector = uploadVector(Bg),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Загрузить конфигурацию",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Bg
                )
            }
        }
    }
}

@Composable
private fun DisabledPowerWithRenew(subStatus: SubStatus, onAction: () -> Unit) {
    val label = if (subStatus == SubStatus.NONE) "Оформить подписку" else "Продлить подписку"
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(190.dp)
                .clip(CircleShape)
                .background(Surface)
                .border(4.dp, Border, CircleShape)
                .alpha(0.5f),
            contentAlignment = Alignment.Center
        ) {
            Image(
                imageVector = powerVector(TextMuted),
                contentDescription = null,
                modifier = Modifier.size(60.dp)
            )
        }
        Spacer(Modifier.height(26.dp))
        Text("Не подключено", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextMuted)
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .height(54.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(DangerBg08)
                .border(1.dp, DangerBg15, RoundedCornerShape(12.dp))
                .clickable(onClick = onAction)
                .padding(horizontal = 30.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Danger)
        }
    }
}

/**
 * Toast импорта — показывается внизу экрана, автозакрывается через 4 секунды.
 * Цвет зависит от содержимого (успех/ошибка).
 */
@Composable
internal fun ImportToast(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSuccess = message.contains("сохранена", ignoreCase = true)
    val bgC = if (isSuccess) Surface else DangerBg06
    val borderC = if (isSuccess) Border else DangerBg12
    val textC = if (isSuccess) Accent else Danger

    LaunchedEffect(message) {
        delay(4000)
        onDismiss()
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgC)
            .border(1.dp, borderC, RoundedCornerShape(12.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .clickable(onClick = onDismiss),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSuccess) {
            Image(
                imageVector = checkVector(Accent),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(message, fontSize = 14.sp, color = textC)
    }
}

// ─── Menu ────────────────────────────────────────────────

@Composable
private fun MenuButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = TextDim, bounded = true),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(TextDim)
                )
            }
        }
    }
}

@Composable
private fun DropdownContent(
    isGuest: Boolean,
    onOpenFaq: () -> Unit,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit,
    onExitGuest: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(260.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .padding(6.dp)
    ) {
        MenuItem(text = "Помощь", icon = helpVector(TextDim), onClick = onOpenFaq)
        MenuItem(text = "Настройки", icon = settingsVector(TextDim), onClick = onOpenSettings)
        if (isGuest) {
            MenuItem(text = "Войти в аккаунт", icon = loginVector(TextDim), onClick = onExitGuest)
        } else {
            MenuItem(text = "Выйти из аккаунта", icon = logoutVector(Danger), onClick = onLogout, danger = true)
        }
    }
}

@Composable
private fun MenuItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = if (danger) Danger else TextMain),
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            fontSize = 16.sp,
            color = if (danger) Danger else TextDim
        )
    }
}

// ─── Утилиты ──────────────────────────────────────────────

private fun formatTimer(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

// ─── Icons ────────────────────────────────────────────────

private fun powerVector(color: androidx.compose.ui.graphics.Color): ImageVector =
    ImageVector.Builder(
        name = "Power", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).path(
        stroke = SolidColor(color),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathFillType = PathFillType.NonZero,
    ) {
        moveTo(18.36f, 6.64f)
        arcToRelative(9f, 9f, 0f, true, true, -12.73f, 0f)
        moveTo(12f, 2f)
        verticalLineTo(12f)
    }.build()

private fun uploadVector(color: androidx.compose.ui.graphics.Color): ImageVector =
    ImageVector.Builder(
        name = "Upload", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).path(
        stroke = SolidColor(color),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(21f, 15f); verticalLineTo(19f)
        arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
        horizontalLineTo(5f)
        arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
        verticalLineTo(15f)
        moveTo(17f, 8f); lineTo(12f, 3f); lineTo(7f, 8f)
        moveTo(12f, 3f); verticalLineTo(15f)
    }.build()

private fun trashVector(color: androidx.compose.ui.graphics.Color): ImageVector =
    ImageVector.Builder(
        name = "Trash", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).path(
        stroke = SolidColor(color),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(3f, 6f); lineTo(5f, 6f); lineTo(21f, 6f)
        moveTo(19f, 6f)
        lineToRelative(-1f, 14f)
        arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
        horizontalLineTo(8f)
        arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
        lineTo(5f, 6f)
    }.build()

private fun settingsVector(color: androidx.compose.ui.graphics.Color): ImageVector =
    ImageVector.Builder(
        name = "Gear", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).path(
        stroke = SolidColor(color),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(15f, 12f)
        arcToRelative(3f, 3f, 0f, true, true, -6f, 0f)
        arcToRelative(3f, 3f, 0f, true, true, 6f, 0f)
        close()
        moveTo(12f, 2f); verticalLineTo(5f)
        moveTo(12f, 19f); verticalLineTo(22f)
        moveTo(2f, 12f); horizontalLineTo(5f)
        moveTo(19f, 12f); horizontalLineTo(22f)
        moveTo(4.93f, 4.93f); lineTo(7.05f, 7.05f)
        moveTo(16.95f, 16.95f); lineTo(19.07f, 19.07f)
        moveTo(4.93f, 19.07f); lineTo(7.05f, 16.95f)
        moveTo(16.95f, 7.05f); lineTo(19.07f, 4.93f)
    }.build()

private fun loginVector(color: androidx.compose.ui.graphics.Color): ImageVector =
    ImageVector.Builder(
        name = "Login", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).path(
        stroke = SolidColor(color),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(15f, 3f)
        horizontalLineTo(19f)
        arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
        verticalLineTo(19f)
        arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
        horizontalLineTo(15f)
        moveTo(10f, 17f); lineTo(15f, 12f); lineTo(10f, 7f)
        moveTo(15f, 12f); lineTo(3f, 12f)
    }.build()

private fun logoutVector(color: androidx.compose.ui.graphics.Color): ImageVector =
    ImageVector.Builder(
        name = "Logout", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).path(
        stroke = SolidColor(color),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(9f, 21f)
        horizontalLineTo(5f)
        arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
        verticalLineTo(5f)
        arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
        horizontalLineTo(9f)
        moveTo(16f, 17f); lineTo(21f, 12f); lineTo(16f, 7f)
        moveTo(21f, 12f); lineTo(9f, 12f)
    }.build()

private fun checkVector(color: androidx.compose.ui.graphics.Color): ImageVector =
    ImageVector.Builder(
        name = "Check", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).path(
        stroke = SolidColor(color),
        strokeLineWidth = 2.5f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(20f, 6f); lineTo(9f, 17f); lineTo(4f, 12f)
    }.build()

private fun helpVector(color: androidx.compose.ui.graphics.Color): ImageVector =
    ImageVector.Builder(
        name = "Help", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).path(
        stroke = SolidColor(color),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        // Круг
        moveTo(12f, 2f)
        arcToRelative(10f, 10f, 0f, true, true, 0.001f, 0f)
        close()
        // Знак вопроса (упрощённо)
        moveTo(9.5f, 9f)
        arcToRelative(2.5f, 2.5f, 0f, true, true, 4.5f, 1.5f)
        lineTo(12f, 13f)
        moveTo(12f, 17.5f)
        lineTo(12.01f, 17.5f)
    }.build()

// ─── Helpers ────────────────────────────────────────────

/**
 * Форматирует оставшееся время подписки в живой счётчик:
 *   > 1 дня:    "29 дней 21:34:52"
 *   < 1 дня:    "21:34:52"
 *   < 1 часа:   "34:52"
 *   < 1 минуты: "52 сек"
 *   истекла:    "0"
 */
internal fun formatRemaining(totalSeconds: Long): String {
    if (totalSeconds <= 0L) return "0"
    val days = totalSeconds / 86400L
    val hours = (totalSeconds % 86400L) / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    val hms = "%02d:%02d:%02d".format(hours, minutes, seconds)
    return when {
        days > 0L -> "${days} ${pluralDays(days)} $hms"
        hours > 0L -> hms
        minutes > 0L -> "%02d:%02d".format(minutes, seconds)
        else -> "$seconds сек"
    }
}

private fun pluralDays(n: Long): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> "дней"
        mod10 == 1L -> "день"
        mod10 in 2L..4L -> "дня"
        else -> "дней"
    }
}

