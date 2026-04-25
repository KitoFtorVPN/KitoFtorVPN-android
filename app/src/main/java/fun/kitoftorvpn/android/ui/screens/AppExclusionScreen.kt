package `fun`.kitoftorvpn.android.ui.screens

import android.graphics.drawable.Drawable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `fun`.kitoftorvpn.android.perapp.AppExclusionStore
import `fun`.kitoftorvpn.android.perapp.AppListRepository
import `fun`.kitoftorvpn.android.perapp.InstalledApp
import `fun`.kitoftorvpn.android.ui.theme.Accent
import `fun`.kitoftorvpn.android.ui.theme.Bg
import `fun`.kitoftorvpn.android.ui.theme.Border
import `fun`.kitoftorvpn.android.ui.theme.Surface
import `fun`.kitoftorvpn.android.ui.theme.TextDim
import `fun`.kitoftorvpn.android.ui.theme.TextMain
import `fun`.kitoftorvpn.android.ui.theme.TextMuted

/**
 * Экран "Исключённые приложения" — per-app split tunneling.
 *
 * Вкладки: "Выбранные" (только с галочкой), "Все".
 * Поиск по названию. Чекбокс против каждого приложения = исключить из VPN.
 * Изменения сохраняются моментально (без кнопки Сохранить) —
 * при следующем connect применятся.
 *
 * @param onChanged сигнал "список изменился, переподключить если VPN активен".
 */
@Composable
fun AppExclusionScreen(
    onBack: () -> Unit = {},
    onChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var excluded by remember { mutableStateOf(AppExclusionStore.load(context)) }
    var query by remember { mutableStateOf("") }
    var tabIndex by remember { mutableStateOf(0) }  // 0 = Выбранные, 1 = Все

    LaunchedEffect(Unit) {
        apps = AppListRepository.loadUserApps(context)
        loading = false
    }

    // Фильтр по вкладке и поиску.
    val filtered = remember(apps, excluded, query, tabIndex) {
        val byTab = if (tabIndex == 0) apps.filter { it.packageName in excluded } else apps
        if (query.isBlank()) byTab
        else byTab.filter {
            it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Bg)) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(20.dp))

            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                        imageVector = backArrow(TextMain),
                        contentDescription = "Назад",
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.size(12.dp))
                Text(
                    "Исключения",
                    color = TextMain,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Приложения с галочкой идут напрямую, минуя VPN. Остальные — через VPN.",
                color = TextDim,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(Modifier.height(14.dp))

            // Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Surface)
                    .border(1.dp, Border, RoundedCornerShape(10.dp))
                    .padding(4.dp)
            ) {
                TabChip(
                    text = "Выбранные (${excluded.size})",
                    selected = tabIndex == 0,
                    modifier = Modifier.weight(1f),
                    onClick = { tabIndex = 0 }
                )
                TabChip(
                    text = "Все (${apps.size})",
                    selected = tabIndex == 1,
                    modifier = Modifier.weight(1f),
                    onClick = { tabIndex = 1 }
                )
            }

            Spacer(Modifier.height(10.dp))

            // Search
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Surface)
                    .border(1.dp, Border, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text("Поиск по названию", color = TextMuted, fontSize = 14.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = TextMain, fontSize = 14.sp),
                    cursorBrush = SolidColor(Accent),
                    singleLine = true
                )
            }

            Spacer(Modifier.height(10.dp))

            // List
            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Загрузка списка приложений...", color = TextMuted, fontSize = 14.sp)
                }
            } else if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (tabIndex == 0) "Ни одно приложение не выбрано" else "Ничего не найдено",
                        color = TextMuted, fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Surface)
                        .border(1.dp, Border, RoundedCornerShape(14.dp))
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        AppRow(
                            app = app,
                            checked = app.packageName in excluded,
                            onToggle = { checked ->
                                excluded = if (checked) excluded + app.packageName
                                           else excluded - app.packageName
                                AppExclusionStore.save(context, excluded)
                                onChanged()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Accent else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) Color.White else TextMain,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
private fun AppRow(
    app: InstalledApp,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppIcon(app.icon, size = 36.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                app.label,
                color = TextMain,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(app.packageName, color = TextMuted, fontSize = 11.sp, maxLines = 1)
        }
        Checkbox(checked = checked, onChange = onToggle)
    }
    Divider()
}

@Composable
private fun AppIcon(drawable: Drawable?, size: androidx.compose.ui.unit.Dp) {
    val density = LocalDensity.current
    val px = with(density) { size.toPx() }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
    ) {
        if (drawable == null) {
            Box(modifier = Modifier.fillMaxSize().background(Border))
        } else {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                drawAppIcon(drawable, px)
            }
        }
    }
}

private fun DrawScope.drawAppIcon(d: Drawable, px: Float) {
    drawIntoCanvas { canvas ->
        d.setBounds(0, 0, px.toInt(), px.toInt())
        d.draw(canvas.nativeCanvas)
    }
    @Suppress("UNUSED_EXPRESSION")
    Size.Unspecified // keep import
}

@Composable
private fun Checkbox(checked: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (checked) Accent else Color.Transparent)
            .border(1.5.dp, if (checked) Accent else Border, RoundedCornerShape(6.dp))
            .clickable { onChange(!checked) },
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            androidx.compose.foundation.Image(
                imageVector = checkSmall(Color.White),
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun Divider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Border))
}

private fun backArrow(color: Color): ImageVector =
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

private fun checkSmall(color: Color): ImageVector =
    ImageVector.Builder(
        name = "Check", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).path(
        stroke = SolidColor(color),
        strokeLineWidth = 3f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(20f, 6f); lineTo(9f, 17f); lineTo(4f, 12f)
    }.build()
