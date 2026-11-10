package com.drumsheesh.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkStudioColorScheme = darkColorScheme(
    primary = StudioColors.ElectricAmber,
    onPrimary = StudioColors.StageBackgroundDark,
    primaryContainer = StudioColors.ElectricAmberSubtle,
    onPrimaryContainer = StudioColors.ElectricAmber,
    secondary = StudioColors.AiCoachIndigo,
    onSecondary = StudioColors.StageBackgroundDark,
    tertiary = StudioColors.TomRingCyan,
    background = StudioColors.StageBackgroundDark,
    onBackground = StudioColors.TextPrimaryDark,
    surface = StudioColors.SurfaceDark,
    onSurface = StudioColors.TextPrimaryDark,
    surfaceVariant = StudioColors.SurfaceElevatedDark,
    onSurfaceVariant = StudioColors.TextSecondaryDark,
    outline = StudioColors.BorderDark,
    error = StudioColors.MissOrRecRed
)

private val LightStudioColorScheme = lightColorScheme(
    primary = StudioColors.ElectricAmberHover,
    onPrimary = StudioColors.SurfaceLight,
    secondary = StudioColors.AiCoachIndigoLight,
    onSecondary = StudioColors.SurfaceLight,
    background = StudioColors.StageBackgroundLight,
    onBackground = StudioColors.TextPrimaryLight,
    surface = StudioColors.SurfaceLight,
    onSurface = StudioColors.TextPrimaryLight,
    surfaceVariant = StudioColors.SurfaceElevatedLight,
    onSurfaceVariant = StudioColors.TextSecondaryLight,
    outline = StudioColors.BorderLight,
    error = StudioColors.MissOrRecRed
)

@Composable
fun DrumSheeshTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkStudioColorScheme else LightStudioColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DrumSheeshTypography,
        content = content
    )
}
