package dev.notiq.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Dark = darkColorScheme(
    primary = Color(0xFFB9AEFF), onPrimary = Color(0xFF251D50), primaryContainer = Color(0xFF353047), onPrimaryContainer = Color(0xFFE5DFFF),
    background = Color(0xFF141416), onBackground = Color(0xFFF1F0F4), surface = Color(0xFF141416), onSurface = Color(0xFFF1F0F4),
    surfaceVariant = Color(0xFF232326), onSurfaceVariant = Color(0xFFA5A4AD), surfaceContainer = Color(0xFF1D1D20),
    surfaceContainerHigh = Color(0xFF27272B), outline = Color(0xFF75747E), outlineVariant = Color(0xFF323237),
)
private val Light = lightColorScheme(
    primary = Color(0xFF6554AC), onPrimary = Color.White, primaryContainer = Color(0xFFECE7FA), onPrimaryContainer = Color(0xFF30244C),
    background = Color(0xFFFAFAFB), onBackground = Color(0xFF202024), surface = Color(0xFFFAFAFB), onSurface = Color(0xFF202024),
    surfaceVariant = Color(0xFFF0EFF3), onSurfaceVariant = Color(0xFF66646F), surfaceContainer = Color(0xFFF2F1F5),
    surfaceContainerHigh = Color(0xFFEAE9EF), outline = Color(0xFF797782), outlineVariant = Color(0xFFDDDBE3),
)
@Composable
fun NotiqTheme(theme: String, content: @Composable () -> Unit) {
    val dark = when(theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light,
        typography = Typography(
            headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 38.sp, letterSpacing = (-.5).sp),
            titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 29.sp),
            titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
            bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
            labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
        ),
        shapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp)),
        content = content)
}
