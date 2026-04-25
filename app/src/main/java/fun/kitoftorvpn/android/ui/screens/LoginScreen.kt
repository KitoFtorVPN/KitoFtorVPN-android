package `fun`.kitoftorvpn.android.ui.screens

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `fun`.kitoftorvpn.android.ui.theme.Accent
import `fun`.kitoftorvpn.android.ui.theme.AccentBg10
import `fun`.kitoftorvpn.android.ui.theme.AccentHover
import `fun`.kitoftorvpn.android.ui.theme.Bg
import `fun`.kitoftorvpn.android.ui.theme.Border
import `fun`.kitoftorvpn.android.ui.theme.TextDim
import `fun`.kitoftorvpn.android.ui.theme.TextMain
import `fun`.kitoftorvpn.android.ui.theme.TextMuted
import `fun`.kitoftorvpn.android.ui.theme.WhiteBg04

// Экран авторизации — порт ui/login.html из Windows, размеры увеличены ×1.4.
// Логика (браузер/OAuth/deep link) — на следующем шаге.
@Composable
fun LoginScreen(
    onLogin: () -> Unit = {},
    onRegister: () -> Unit = {},
    onGuest: () -> Unit = {},
    onGoogle: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(horizontal = 32.dp, vertical = 28.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ShieldLogo()
            Spacer(Modifier.height(14.dp))
            Text(
                "KitoFtorVPN",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
                color = TextMain
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Безопасный интернет",
                fontSize = 15.sp,
                color = TextDim
            )

            Spacer(Modifier.height(34.dp))

            Column(
                modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PrimaryButton(text = "Войти", onClick = onLogin)
                SecondaryButton(text = "Создать аккаунт", onClick = onRegister)
                SecondaryButton(text = "Войти как гость", onClick = onGuest)
            }

            Spacer(Modifier.height(18.dp))
            Divider(text = "или")
            Spacer(Modifier.height(18.dp))

            OAuthButton(
                text = "Войти через Google",
                icon = { GoogleIcon() },
                modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
                onClick = onGoogle
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = "Откроется браузер для входа.\nПосле авторизации вернётесь сюда.",
                fontSize = 13.sp,
                color = TextMuted,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun ShieldLogo() {
    Box(
        modifier = Modifier
            .size(78.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(AccentBg10),
        contentAlignment = Alignment.Center
    ) {
        Image(
            imageVector = shieldVector(),
            contentDescription = null,
            modifier = Modifier.size(40.dp)
        )
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Accent)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = AccentHover),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Bg,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(WhiteBg04)
            .border(1.dp, Border, RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = TextMain),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = TextMain,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun OAuthButton(
    text: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(WhiteBg04)
            .border(1.dp, Border, RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = TextMain),
                onClick = onClick
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(modifier = Modifier.size(22.dp)) { icon() }
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            color = TextMain,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun Divider(text: String) {
    Row(
        modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f).height(1.dp).background(Border))
        Text(
            text = text.uppercase(),
            color = TextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Box(modifier = Modifier.weight(1f).height(1.dp).background(Border))
    }
}

// ─── Icons ─────────────────────────────

private fun shieldVector(): ImageVector = ImageVector.Builder(
    name = "Shield",
    defaultWidth = 24.dp, defaultHeight = 24.dp,
    viewportWidth = 24f, viewportHeight = 24f
).path(
    stroke = SolidColor(Accent),
    strokeLineWidth = 2f,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
    pathFillType = PathFillType.NonZero,
) {
    moveTo(12f, 22f)
    reflectiveCurveToRelative(8f, -4f, 8f, -10f)
    verticalLineTo(5f)
    lineToRelative(-8f, -3f)
    lineToRelative(-8f, 3f)
    verticalLineToRelative(7f)
    curveToRelative(0f, 6f, 8f, 10f, 8f, 10f)
    close()
}.build()

@Composable
private fun GoogleIcon() {
    Image(
        imageVector = googleVector(),
        contentDescription = null,
        modifier = Modifier.size(22.dp)
    )
}

private fun googleVector(): ImageVector {
    val builder = ImageVector.Builder(
        name = "Google",
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    )
    builder.path(fill = SolidColor(Color(0xFF4285F4))) {
        moveTo(22.56f, 12.25f)
        curveToRelative(0f, -0.78f, -0.07f, -1.53f, -0.2f, -2.25f)
        horizontalLineTo(12f)
        verticalLineToRelative(4.26f)
        horizontalLineToRelative(5.92f)
        arcToRelative(5.06f, 5.06f, 0f, false, true, -2.2f, 3.32f)
        verticalLineToRelative(2.77f)
        horizontalLineToRelative(3.57f)
        curveToRelative(2.08f, -1.92f, 3.28f, -4.74f, 3.28f, -8.1f)
        close()
    }
    builder.path(fill = SolidColor(Color(0xFF34A853))) {
        moveTo(12f, 23f)
        curveToRelative(2.97f, 0f, 5.46f, -0.98f, 7.28f, -2.66f)
        lineToRelative(-3.57f, -2.77f)
        curveToRelative(-0.98f, 0.66f, -2.23f, 1.06f, -3.71f, 1.06f)
        curveToRelative(-2.86f, 0f, -5.29f, -1.93f, -6.16f, -4.53f)
        horizontalLineTo(2.18f)
        verticalLineToRelative(2.84f)
        curveTo(3.99f, 20.53f, 7.7f, 23f, 12f, 23f)
        close()
    }
    builder.path(fill = SolidColor(Color(0xFFFBBC05))) {
        moveTo(5.84f, 14.09f)
        curveToRelative(-0.22f, -0.66f, -0.35f, -1.36f, -0.35f, -2.09f)
        reflectiveCurveToRelative(0.13f, -1.43f, 0.35f, -2.09f)
        verticalLineTo(7.07f)
        horizontalLineTo(2.18f)
        curveTo(1.43f, 8.55f, 1f, 10.22f, 1f, 12f)
        reflectiveCurveToRelative(0.43f, 3.45f, 1.18f, 4.93f)
        lineToRelative(2.85f, -2.22f)
        lineToRelative(0.81f, -0.62f)
        close()
    }
    builder.path(fill = SolidColor(Color(0xFFEA4335))) {
        moveTo(12f, 5.38f)
        curveToRelative(1.62f, 0f, 3.06f, 0.56f, 4.21f, 1.64f)
        lineToRelative(3.15f, -3.15f)
        curveTo(17.45f, 2.09f, 14.97f, 1f, 12f, 1f)
        curveTo(7.7f, 1f, 3.99f, 3.47f, 2.18f, 7.07f)
        lineToRelative(3.66f, 2.84f)
        curveToRelative(0.87f, -2.6f, 3.3f, -4.53f, 6.16f, -4.53f)
        close()
    }
    return builder.build()
}
