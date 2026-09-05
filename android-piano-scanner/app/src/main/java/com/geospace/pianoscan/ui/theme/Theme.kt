package com.geospace.pianoscan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Ink = Color(0xFF15161A)
private val Paper = Color(0xFFFBF9F4)
private val Amber = Color(0xFFE0A340)
private val Teal = Color(0xFF2E8B8B)

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Ink,
    secondary = Teal,
    background = Ink,
    onBackground = Color(0xFFEDEDF0),
    surface = Color(0xFF1E2026),
    onSurface = Color(0xFFEDEDF0),
    surfaceVariant = Color(0xFF2A2D35),
    onSurfaceVariant = Color(0xFFC7C7CE)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF8A5A12),
    onPrimary = Color.White,
    secondary = Teal,
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink
)

@Composable
fun PianoScanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
