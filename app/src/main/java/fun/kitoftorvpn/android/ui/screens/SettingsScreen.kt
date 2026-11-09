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

    // File picker — тот же подход, что и на главном экране.
    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> if (uri != null) vm.importConfig(uri) }

    val configPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri: Uri? = result.data?.data
            ?: result.data?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        if (uri != null) {
            vm.importConfig(uri)
        } else {
            android.widget.Toast.makeText(context, "Файл не выбран", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun launchConfigPicker() {
        // Без EXTRA_MIME_TYPES — иначе на Tecno/Infinix/Xiaomi .conf серый и не выбирается.
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        try {
            configPickerLauncher.launch(Intent.createChooser(intent, "Выберите .conf файл"))
        } catch (e: Exception) {
            android.util.Log.e("ImportConfig", "GET_CONTENT failed, trying OPEN_DOCUMENT", e)
            try {
                openDocLauncher.launch(arrayOf("*/*"))
            } catch (e2: Exception) {
                android.util.Log.e("ImportConfig", "No file picker available", e2)
                android.widget.Toast.makeText(context, "На устройстве нет файлового менеджера", android.widget.Toast.LENGTH_LONG).show()
            }
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
                    subtitle = if (hasConfig) "Заменить текущий файл .conf на новый" else "Выберите файл .conf, который вам выдал администратор",
                    onClick = { launchConfigPicker() },
                    hasDivider = hasConfig
                )
                if (hasConfig) {
                    ActionRow(
                        title = "Удалить конфигурацию",
                        subtitle = "Удалить сохранённый .conf с этого устройства",
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
                    title = "Уведомление о разрыве",
                    subtitle = "Сообщать, если VPN неожиданно отключился",
                    checked = notificationsEnabled,
                    onChange = {
                        notificationsEnabled = it
                        AppPreferences.setNotificationsEnabled(context, it)
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
                    subtitle = "Сайты, которые будут открываться напрямую, без VPN",
                    onClick = onOpenWhitelist,
                    hasDivider = true
                )
                ActionRow(
                    title = "Исключения приложений",
                    subtitle = "Приложения, которые будут работать напрямую, без VPN",
                    onClick = onOpenAppExclusions,
                    hasDivider = false
                )
            }

            Spacer(Modifier.height(20.dp))

            // ─── Раздел: О приложении ──────────────
            SectionTitle("О приложении")
            Card {
                InfoRow("Версия", "v$appVersion", hasDivider = false)
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
                message = "Сохранённый .conf будет удалён с устройства. Чтобы подключиться снова, нужно будет загрузить файл заново.",
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
