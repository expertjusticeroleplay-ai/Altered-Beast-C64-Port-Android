package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ArcadeColorScheme = darkColorScheme(
    primary = ArcadeGold,
    secondary = ArcadeCyan,
    tertiary = ArcadeCrimson,
    background = ArcadeObsidian,
    surface = ArcadeSurface,
    onPrimary = ArcadeObsidian,
    onSecondary = ArcadeObsidian,
    onTertiary = ArcadeTextPrimary,
    onBackground = ArcadeTextPrimary,
    onSurface = ArcadeTextPrimary
)

@Composable
fun AlteredBeastTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ArcadeColorScheme,
        typography = Typography,
        content = content
    )
}
