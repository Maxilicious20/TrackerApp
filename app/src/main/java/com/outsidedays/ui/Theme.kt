package com.outsidedays.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.outsidedays.core.Preferences

val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable fun OutsideTheme(p: Preferences, content: @Composable () -> Unit) {
    val dark = p.theme == "Dark" || (p.theme == "System" && isSystemInDarkTheme())
    val primary = when(p.accent) { "Sky" -> Color(0xFF365F78); "Lavender" -> Color(0xFF695381); "Peach" -> Color(0xFF875137); else -> Color(0xFF47634A) }
    val darkPrimary = when(p.accent) { "Sky" -> Color(0xFFB9DDF1); "Lavender" -> Color(0xFFD8C5EB); "Peach" -> Color(0xFFF1C8AE); else -> Color(0xFFC4DDB5) }
    val palette = if(dark) darkColorScheme(primary = darkPrimary, onPrimary = Color(0xFF183620), primaryContainer = primary, onPrimaryContainer = Color.White,
        background = Color(0xFF181D1A), onBackground = Color(0xFFF1EDE1), surface = Color(0xFF202823), onSurface = Color(0xFFF1EDE1), surfaceVariant = Color(0xFF354039), onSurfaceVariant = Color(0xFFD0D9CA), secondaryContainer = Color(0xFF504550), onSecondaryContainer = Color(0xFFF2DBEF))
    else lightColorScheme(primary = primary, onPrimary = Color.White, primaryContainer = Color(0xFFDCE8D1), onPrimaryContainer = Color(0xFF253C2B),
        background = Color(0xFFFAF7EE), onBackground = Color(0xFF28372E), surface = Color(0xFFFFFCF5), onSurface = Color(0xFF28372E), surfaceVariant = Color(0xFFEDEEE3), onSurfaceVariant = Color(0xFF465347), secondaryContainer = Color(0xFFE9E0EF), onSecondaryContainer = Color(0xFF51405B))
    val view = LocalView.current
    SideEffect {
        (view.context as? android.app.Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
        }
    }
    CompositionLocalProvider(LocalReducedMotion provides p.reducedMotion) {
    MaterialTheme(colorScheme = palette, typography = Typography(
        headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 36.sp, fontWeight = FontWeight.Medium),
        headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontSize = 28.sp, fontWeight = FontWeight.Medium),
        titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 23.sp),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
        labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    ), content = content)
    }
}
