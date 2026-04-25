package `fun`.kitoftorvpn.android.update

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import `fun`.kitoftorvpn.android.ui.theme.Accent
import `fun`.kitoftorvpn.android.ui.theme.Bg
import `fun`.kitoftorvpn.android.ui.theme.Border
import `fun`.kitoftorvpn.android.ui.theme.Danger
import `fun`.kitoftorvpn.android.ui.theme.Surface
import `fun`.kitoftorvpn.android.ui.theme.TextDim
import `fun`.kitoftorvpn.android.ui.theme.TextMain
import `fun`.kitoftorvpn.android.ui.theme.TextMuted

@Composable
fun UpdateDialog(
    info: UpdateChecker.UpdateInfo,
    currentVersion: String,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val state by UpdateInstaller.state.collectAsState()

    Dialog(
        onDismissRequest = {
            // Forced релиз — закрыть нельзя, нажатие "вне диалога" игнорируется.
            if (!info.forced && state !is UpdateInstaller.State.Downloading) onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = !info.forced && state !is UpdateInstaller.State.Downloading,
            dismissOnClickOutside = !info.forced && state !is UpdateInstaller.State.Downloading,
            usePlatformDefaultWidth = false
        )
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Surface)
                .border(1.dp, Border, RoundedCornerShape(18.dp))
                .padding(22.dp)
        ) {
            Text(
                "Доступна новая версия ${info.versionName}",
                color = TextMain,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Текущая: $currentVersion",
                color = TextMuted,
                fontSize = 12.sp
            )

            if (info.releaseNotes.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Bg)
                        .border(1.dp, Border, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        info.releaseNotes,
                        color = TextDim,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }
            }

            // Прогресс/статус
            when (val s = state) {
                is UpdateInstaller.State.Downloading -> {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { s.percent / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = Accent,
                        trackColor = Border
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("Скачивание: ${s.percent}%", color = TextDim, fontSize = 12.sp)
                }
                is UpdateInstaller.State.Error -> {
                    Spacer(Modifier.height(12.dp))
                    Text(s.message, color = Danger, fontSize = 13.sp)
                }
                else -> { /* idle / ready — кнопки управляют действиями */ }
            }

            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!info.forced && state !is UpdateInstaller.State.Downloading) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Bg)
                            .border(1.dp, Border, RoundedCornerShape(12.dp))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Позже", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                }

                val downloading = state is UpdateInstaller.State.Downloading
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (downloading) TextMuted else Accent)
                        .clickable(enabled = !downloading, onClick = onUpdate),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (downloading) "Скачивание..." else "Обновить",
                        color = Bg,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
