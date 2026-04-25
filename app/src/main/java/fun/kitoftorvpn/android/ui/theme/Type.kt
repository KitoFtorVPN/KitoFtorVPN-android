package `fun`.kitoftorvpn.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// В Windows-клиенте шрифт: -apple-system, 'Segoe UI', system-ui, sans-serif.
// На Android используем системный шрифт по умолчанию (FontFamily.Default = Roboto на большинстве устройств).
private val Default = FontFamily.Default

val AppTypography = Typography(
    // headlineMedium — большие заголовки (статус "Подключено" и т.д.) — 20sp, 700
    headlineMedium = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = (-0.3).sp
    ),
    // titleLarge — логотип "KitoFtorVPN" на экране логина — 19sp, 700
    titleLarge = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        letterSpacing = (-0.3).sp
    ),
    // titleMedium — название приложения в top-bar главного — 14sp, 700
    titleMedium = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp
    ),
    // bodyLarge — основной текст (подписка, drop-title) — 15sp, 600
    bodyLarge = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp
    ),
    // bodyMedium — обычный текст — 14sp, 400
    bodyMedium = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    // labelLarge — текст на кнопках — 14sp, 600
    labelLarge = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp
    ),
    // labelMedium — status-value — 14sp, 600
    labelMedium = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp
    ),
    // labelSmall — мелкие подписи, хинты, логотип-саб — 12sp, 400
    labelSmall = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp
    ),
    // bodySmall — 13sp обычного текста (desc, хинты с подпиской)
    bodySmall = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
)
