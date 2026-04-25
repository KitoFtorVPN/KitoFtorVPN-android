package `fun`.kitoftorvpn.android.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import `fun`.kitoftorvpn.android.settings.AppPreferences
import `fun`.kitoftorvpn.android.api.ReferralApi
import `fun`.kitoftorvpn.android.ui.theme.Accent
import `fun`.kitoftorvpn.android.ui.theme.AccentBg08
import `fun`.kitoftorvpn.android.ui.theme.Bg
import `fun`.kitoftorvpn.android.ui.theme.Border
import `fun`.kitoftorvpn.android.ui.theme.Danger
import `fun`.kitoftorvpn.android.ui.theme.Surface
import `fun`.kitoftorvpn.android.ui.theme.TextDim
import `fun`.kitoftorvpn.android.ui.theme.TextMain
import `fun`.kitoftorvpn.android.ui.theme.TextMuted
import `fun`.kitoftorvpn.android.vpn.MainViewModel
import androidx.compose.runtime.LaunchedEffect

@Composable
fun SettingsScreen(
    appVersion: String = "1.0",
    isGuest: Boolean = false,
    sessionToken: String? = null,
    onBack: () -> Unit = {},
    onOpenWhitelist: () -> Unit = {},
    onOpenAppExclusions: () -> Unit = {},
) {
    val context = LocalContext.current
    val activity = context as androidx.activity.ComponentActivity
    val vm: MainViewModel = viewModel(viewModelStoreOwner = activity)
    val hasConfig by vm.hasConfig.collectAsStateWithLifecycle()
    val importMessage by vm.importMessage.collectAsStateWithLifecycle()

    // Локальный стейт префов; подгружается при первой композиции.
    var notificationsEnabled by remember { mutableStateOf(AppPreferences.getNotificationsEnabled(context)) }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Реферальная программа — грузится если не гость
    var refInfo by remember { mutableStateOf<ReferralApi.RefInfo?>(null) }
    var refLoading by remember { mutableStateOf(false) }
    LaunchedEffect(sessionToken, isGuest) {
        if (!isGuest && !sessionToken.isNullOrEmpty()) {
            refLoading = true
            refInfo = ReferralApi.fetchRefInfo(sessionToken)
            refLoading = false
        }
    }

    // File picker — тот же подход, что и на главном экране.
    val configPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri: Uri? = result.data?.data
            ?: result.data?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        if (uri != null) vm.importConfig(uri)
    }

    fun launchConfigPicker() {
        val mimes = arrayOf(
            "text/plain",
            "application/octet-stream",
            "application/x-wireguard-config"
        )
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, mimes)
        }
        try {
            configPickerLauncher.launch(Intent.createChooser(intent, "Выберите .conf файл"))
        } catch (e: Exception) {
            android.util.Log.e("ImportConfig", "No file picker available", e)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(20.dp))

            // ─── Header с кнопкой назад ─────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Surface)
                        .border(1.dp, Border, RoundedCornerShape(10.dp))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        imageVector = backVector(TextMain),
                        contentDescription = "Назад",
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.size(12.dp))
                Text(
                    text = "Настройки",
                    color = TextMain,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(24.dp))

            // ─── Раздел: Конфигурация ──────────────
            SectionTitle("Конфигурация")
            Card {
                ActionRow(
                    title = if (hasConfig) "Сменить конфигурацию" else "Загрузить конфигурацию",
                    subtitle = if (hasConfig) "Заменить текущий .conf" else "Импортировать .conf из файла",
                    onClick = { launchConfigPicker() },
                    hasDivider = hasConfig
                )
                if (hasConfig) {
                    ActionRow(
                        title = "Удалить конфигурацию",
                        subtitle = "Стереть сохранённый .conf с устройства",
                        danger = true,
                        onClick = { showDeleteConfirm = true },
                        hasDivider = false
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ─── Раздел: Уведомления ────────────────
            SectionTitle("Уведомления")
            Card {
                ToggleRow(
                    title = "Уведомления",
                    subtitle = "Напоминания о подписке и разрывах VPN",
                    checked = notificationsEnabled,
                    onChange = {
                        notificationsEnabled = it
                        AppPreferences.setNotificationsEnabled(context, it)
                        if (!it) {
                            `fun`.kitoftorvpn.android.notifications.SubNotificationScheduler.cancelAll(context)
                        } else {
                            // При включении — перепланировать из сохранённого expiresAt.
                            val expiresAt = AppPreferences.getSubExpiresAt(context)
                            if (expiresAt != null) {
                                val type = when (AppPreferences.getSubType(context)) {
                                    "test" -> `fun`.kitoftorvpn.android.api.SubscriptionApi.SubInfo.SubType.TEST
                                    "sub" -> `fun`.kitoftorvpn.android.api.SubscriptionApi.SubInfo.SubType.SUB
                                    else -> null
                                }
                                `fun`.kitoftorvpn.android.notifications.SubNotificationScheduler.reschedule(
                                    context, expiresAt, type
                                )
                            }
                        }
                    },
                    hasDivider = false
                )
            }

            Spacer(Modifier.height(20.dp))

            // ─── Раздел: Сплит-туннелинг ────────────
            SectionTitle("Сплит-туннелинг")
            Card {
                ActionRow(
                    title = "Белый список",
                    subtitle = "Сайты, трафик к которым пойдёт в обход VPN",
                    onClick = onOpenWhitelist,
                    hasDivider = true
                )
                ActionRow(
                    title = "Исключения приложений",
                    subtitle = "Приложения, которые работают без VPN",
                    onClick = onOpenAppExclusions,
                    hasDivider = false
                )
            }

            Spacer(Modifier.height(20.dp))

            // ─── Раздел: Реферальная программа (только для авторизованных) ──
            if (!isGuest) {
                SectionTitle("Реферальная программа")
                Card {
                    val info = refInfo
                    if (refLoading && info == null) {
                        Text(
                            "Загрузка...",
                            color = TextMuted,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp)
                        )
                    } else if (info == null) {
                        Text(
                            "Не удалось загрузить реф-ссылку",
                            color = TextMuted,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp)
                        )
                    } else {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                            Text(
                                "Ваша ссылка",
                                color = TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                info.refLink,
                                color = Accent,
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AccentBg08)
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Surface)
                                        .border(1.dp, Border, RoundedCornerShape(10.dp))
                                        .clickable {
                                            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            cm.setPrimaryClip(android.content.ClipData.newPlainText("ref", info.refLink))
                                            android.widget.Toast.makeText(context, "Ссылка скопирована", android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Копировать", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextMain)
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Accent)
                                        .clickable {
                                            val share = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, "Попробуй KitoFtor VPN: ${info.refLink}")
                                            }
                                            try {
                                                context.startActivity(Intent.createChooser(share, "Поделиться ссылкой"))
                                            } catch (_: Exception) {}
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Поделиться", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Bg)
                                }
                            }
                        }
                        Divider()
                        InfoRow("Приглашено", info.refCount.toString(), hasDivider = true)
                        InfoRow("Бонусных дней", info.refBonusDays.toString(), hasDivider = false)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "За каждого друга, купившего подписку, вы оба получите +7 дней бесплатно.",
                    color = TextDim,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(Modifier.height(20.dp))
            }

            // ─── Раздел: О приложении ──────────────
            SectionTitle("О приложении")
            Card {
                InfoRow("Версия", "v$appVersion", hasDivider = true)
                LinkRow(
                    title = "Сайт",
                    subtitle = "kitoftorvpn.fun",
                    onClick = { openUrl(context, "https://kitoftorvpn.fun") },
                    hasDivider = false
                )
            }

            Spacer(Modifier.height(40.dp))
        }

        // ─── Toast импорта ────────────────────────
        if (importMessage != null) {
            ImportToast(
                message = importMessage!!,
                onDismiss = { vm.clearImportMessage() },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
            )
        }

        // ─── Диалог подтверждения удаления ────────
        if (showDeleteConfirm) {
            ConfirmDialog(
                title = "Удалить конфигурацию?",
                message = "Сохранённый .conf будет удалён с устройства. Для подключения потребуется загрузить файл заново.",
                confirmText = "Удалить",
                cancelText = "Отмена",
                danger = true,
                onConfirm = {
                    vm.deleteConfig()
                    showDeleteConfirm = false
                },
                onDismiss = { showDeleteConfirm = false }
            )
        }
    }
}

// ─── Sections ──────────────────────────────────────────

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        color = TextMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface)
            .border(1.dp, Border, RoundedCornerShape(14.dp))
    ) { content() }
}

@Composable
private fun ActionRow(
    title: String,
    subtitle: String? = null,
    danger: Boolean = false,
    hasDivider: Boolean = true,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(
            text = title,
            color = if (danger) Danger else TextMain,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = TextMuted, fontSize = 12.sp)
        }
    }
    if (hasDivider) Divider()
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    hasDivider: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = TextMuted, fontSize = 12.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Accent,
                uncheckedThumbColor = TextDim,
                uncheckedTrackColor = Border
            )
        )
    }
    if (hasDivider) Divider()
}

@Composable
private fun InfoRow(title: String, value: String, hasDivider: Boolean = true) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = TextMain, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, color = TextMuted, fontSize = 14.sp)
    }
    if (hasDivider) Divider()
}

@Composable
private fun LinkRow(title: String, subtitle: String, onClick: () -> Unit, hasDivider: Boolean = true) {
    Column(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, color = Accent, fontSize = 12.sp)
    }
    if (hasDivider) Divider()
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Border)
    )
}

// ─── Utils ────────────────────────────────────────────

private fun openUrl(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (_: Exception) { /* ignore */ }
}

// ─── Icons ────────────────────────────────────────────

private fun backVector(color: Color): ImageVector =
    ImageVector.Builder(
        name = "Back", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).path(
        stroke = SolidColor(color),
        strokeLineWidth = 2.5f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(15f, 18f); lineTo(9f, 12f); lineTo(15f, 6f)
    }.build()
