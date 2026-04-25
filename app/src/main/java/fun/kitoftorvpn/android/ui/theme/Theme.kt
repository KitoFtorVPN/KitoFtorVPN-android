package `fun`.kitoftorvpn.android.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Всегда тёмная (как в Windows-клиенте). Светлую не делаем.
private val DarkColors = darkColorScheme(
    primary        = Accent,
    onPrimary      = Bg,
    secondary      = Accent,
    onSecondary    = Bg,
    background     = Bg,
    onBackground   = TextMain,
    surface        = Surface,
    onSurface      = TextMain,
    surfaceVariant = Surface,
    onSurfaceVariant = TextDim,
    error          = Danger,
    onError        = TextMain,
    outline        = Border,
)

@Composable
fun KitoFtorVPNTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Управляем иконками системных баров (светлые/тёмные). Сами фоны system bars
    // делает прозрачными enableEdgeToEdge() в Activity — нам остаётся только
    // сказать системе, что наш фон тёмный → иконки должны быть светлыми.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insets = WindowCompat.getInsetsController(window, view)
            insets.isAppearanceLightStatusBars = false
            insets.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = DarkColors,
        typography = AppTypography,
        content = content
    )
}
