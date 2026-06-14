package com.smallworld.drawer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ColorScheme = darkColorScheme(
    primary = Gold,
    secondary = AccentBlue,
    tertiary = AccentGreen,
    background = BgDark,
    surface = BgCard,
    surfaceVariant = BgCardHover,
    onPrimary = BgDark,
    onSecondary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    outline = Border,
    error = AccentRed,
)

@Composable
fun SmallWorldDrawerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        typography = Typography,
        content = content
    )
}
