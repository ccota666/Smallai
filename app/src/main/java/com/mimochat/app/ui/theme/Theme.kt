package com.mimochat.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.mimochat.app.data.AppSettings

// 米白护眼 (cream-eye-care) 主题
private val LightColors = lightColorScheme(
    primary = Color(0xFF5F7D6B),           // accent 鼠尾草绿
    onPrimary = Color(0xFFFAF6EF),         // accent.on
    primaryContainer = Color(0xFFE4EEE6),  // accent.soft
    onPrimaryContainer = Color(0xFF455E50),// accent.active
    secondary = Color(0xFF5A7A68),         // link
    onSecondary = Color(0xFFFAF6EF),
    secondaryContainer = Color(0xFFE4EEE6),
    onSecondaryContainer = Color(0xFF455E50),
    tertiary = Color(0xFF6A8899),          // info
    onTertiary = Color(0xFFFAF6EF),
    background = Color.White,              // bg.canvas
    onBackground = Color(0xFF2F2B25),      // text.primary
    surface = Color.White,                 // bg.surface
    onSurface = Color(0xFF2F2B25),
    surfaceVariant = Color(0xFFEDE6D8),    // bg.muted
    onSurfaceVariant = Color(0xFF5A5348),  // text.secondary
    error = Color(0xFFB36B5D),             // danger
    onError = Color(0xFFFAF6EF),
    errorContainer = Color(0xFFF0DCD6),
    onErrorContainer = Color(0xFF5A3028),
    outline = Color(0xFFCFC5B2),           // border.strong
    outlineVariant = Color(0xFFE0D8C8)     // border
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FA08C),           // accent
    onPrimary = Color(0xFF1E1C19),         // accent.on
    primaryContainer = Color(0xFF2A3530),  // accent.soft
    onPrimaryContainer = Color(0xFFEDE6D8),
    secondary = Color(0xFF8FB09B),         // link
    onSecondary = Color(0xFF1E1C19),
    secondaryContainer = Color(0xFF2A3530),
    onSecondaryContainer = Color(0xFFEDE6D8),
    tertiary = Color(0xFF7A9AAB),          // info
    onTertiary = Color(0xFF1E1C19),
    background = Color.Black,              // bg.canvas
    onBackground = Color(0xFFEDE6D8),      // text.primary
    surface = Color.Black,                 // bg.surface
    onSurface = Color(0xFFEDE6D8),
    surfaceVariant = Color(0xFF322E29),    // bg.muted
    onSurfaceVariant = Color(0xFFB8AE9C),  // text.secondary
    error = Color(0xFFC47A6B),             // danger
    onError = Color(0xFF1E1C19),
    errorContainer = Color(0xFF3A2A26),
    onErrorContainer = Color(0xFFF0DCD6),
    outline = Color(0xFF4A443B),           // border.strong
    outlineVariant = Color(0xFF3A352E)     // border
)

@Composable
fun MiMoChatTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        0 -> false
        1 -> true
        else -> isSystemInDarkTheme()
    }

    // 让状态栏/导航栏图标颜色跟随应用主题
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
        }
    }

    val fs = settings.fontSize.coerceIn(12f, 26f)
    val typography = Typography(
        bodyLarge = TextStyle(fontSize = fs.sp, lineHeight = (fs * 1.5f).sp),
        bodyMedium = TextStyle(fontSize = fs.sp, lineHeight = (fs * 1.45f).sp),
        bodySmall = TextStyle(fontSize = (fs - 2f).sp, lineHeight = (fs * 1.4f).sp),
        titleLarge = TextStyle(fontSize = (fs + 6f).sp, lineHeight = (fs * 1.4f).sp),
        titleMedium = TextStyle(fontSize = (fs + 3f).sp, lineHeight = (fs * 1.4f).sp),
        labelMedium = TextStyle(fontSize = (fs - 2f).sp, lineHeight = (fs * 1.3f).sp),
        labelSmall = TextStyle(fontSize = (fs - 3f).sp, lineHeight = (fs * 1.2f).sp)
    )
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = typography,
        content = content
    )
}
