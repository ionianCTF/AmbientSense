package com.ambientsense.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = AmbientPalette.BookCloth,
    onPrimary = Color.White,
    primaryContainer = AmbientPalette.Manilla,
    onPrimaryContainer = AmbientPalette.SlateDark,
    secondary = AmbientPalette.Olive,
    onSecondary = Color.White,
    secondaryContainer = AmbientPalette.Cloud,
    onSecondaryContainer = AmbientPalette.SlateDark,
    tertiary = AmbientPalette.Stone,
    onTertiary = Color.White,
    background = AmbientPalette.Ivory,
    onBackground = AmbientPalette.SlateDark,
    surface = AmbientPalette.Paper,
    onSurface = AmbientPalette.SlateDark,
    surfaceVariant = AmbientPalette.IvoryDeep,
    onSurfaceVariant = AmbientPalette.SlateMid,
    outline = Color(0xFFDCD6C8),
    outlineVariant = Color(0xFFEAE5D9),
    error = AmbientPalette.DangerColor
)

private val DarkColors = darkColorScheme(
    primary = AmbientPalette.Clay,
    onPrimary = Color(0xFF2A1206),
    primaryContainer = Color(0xFF3A241B),
    onPrimaryContainer = AmbientPalette.Manilla,
    secondary = AmbientPalette.OliveBright,
    onSecondary = Color(0xFF14200B),
    secondaryContainer = Color(0xFF232B1C),
    onSecondaryContainer = Color(0xFFD9E2C8),
    tertiary = Color(0xFF8FB3D6),
    onTertiary = Color(0xFF0C1B28),
    background = AmbientPalette.Ink,
    onBackground = AmbientPalette.InkText,
    surface = AmbientPalette.InkSurface,
    onSurface = AmbientPalette.InkText,
    surfaceVariant = AmbientPalette.InkRaised,
    onSurfaceVariant = AmbientPalette.InkTextDim,
    outline = AmbientPalette.InkBorder,
    outlineVariant = Color(0xFF2A2825),
    error = Color(0xFFE08A80)
)

@Composable
fun AmbientTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AmbientTypography,
        content = content
    )
}
