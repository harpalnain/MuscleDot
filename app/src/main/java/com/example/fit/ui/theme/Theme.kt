package com.example.fit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class OverloadColors(
    val done: Color,
    val recordContainer: Color,
    val onRecordContainer: Color,
    val chartSurface: Color,
    val chartSession: Color,
    val chartLoad: Color,
    val chartCardio: Color,
    val chartGrid: Color,
    val chartBaseline: Color,
    val chartLabel: Color,
)

val LocalOverloadColors = staticCompositionLocalOf { LightOverloadColors }

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = PrimaryLight,
    onSecondary = OnPrimaryLight,
    secondaryContainer = PrimaryContainerLight,
    onSecondaryContainer = OnPrimaryContainerLight,
    background = BackgroundLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceContainer = SurfaceVariantLight,
    surfaceContainerHigh = SurfaceLight,
    surfaceContainerHighest = SurfaceVariantLight,
    surfaceContainerLow = SurfaceLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    error = ErrorLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = PrimaryDark,
    onSecondary = OnPrimaryDark,
    secondaryContainer = PrimaryContainerDark,
    onSecondaryContainer = OnPrimaryContainerDark,
    background = BackgroundDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainer = SurfaceVariantDark,
    surfaceContainerHigh = SurfaceVariantDark,
    surfaceContainerHighest = SurfaceVariantDark,
    surfaceContainerLow = SurfaceDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    error = ErrorDark,
)

/** Follows the phone's light or dark setting, with the app's own fixed colours (no wallpaper colours). */
@Composable
fun OverloadTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalOverloadColors provides if (darkTheme) DarkOverloadColors else LightOverloadColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = Typography,
            content = content,
        )
    }
}
