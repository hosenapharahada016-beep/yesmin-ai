package com.yesmin.ai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val YPurple = Color(0xFF8B5CF6)
val YCyan = Color(0xFF22D3EE)
val YPink = Color(0xFFF472B6)
val YBg = Color(0xFF0B0714)

private val DarkScheme = darkColorScheme(
    primary = YPurple,
    onPrimary = Color.White,
    secondary = YCyan,
    onSecondary = Color(0xFF00252B),
    tertiary = YPink,
    background = YBg,
    onBackground = Color(0xFFEDE9FE),
    surface = Color(0xFF150F24),
    onSurface = Color(0xFFEDE9FE),
    surfaceVariant = Color(0xFF211838),
    onSurfaceVariant = Color(0xFFC4B5FD),
    error = Color(0xFFFF6B8A)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF6D3FD9),
    onPrimary = Color.White,
    secondary = Color(0xFF0891B2),
    tertiary = Color(0xFFDB2777),
    background = Color(0xFFFAF7FF),
    onBackground = Color(0xFF1B1030),
    surface = Color.White,
    onSurface = Color(0xFF1B1030),
    surfaceVariant = Color(0xFFEFE8FF),
    onSurfaceVariant = Color(0xFF4C3A7A)
)

/** mode: "dark", "light" or "system" */
@Composable
fun YesminTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) {
        "light" -> false
        "system" -> isSystemInDarkTheme()
        else -> true
    }
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
}
