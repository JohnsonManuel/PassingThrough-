package com.passingthrough.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/*
 * Material 3 color schemes generated with material-color-utilities (TonalSpot) from the brand
 * colors: deep green #1F5C4A (primary), warm amber #C8862B (secondary), a sky blue harmonized to
 * the green (tertiary), and the cream #FAF6EE hue for the neutral surfaces.
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F5C4A), // Brand green (generated tone 40 was 0xFF2D6956).
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB2EFD7),
    onPrimaryContainer = Color(0xFF0F503F),
    inversePrimary = Color(0xFF96D3BC),
    secondary = Color(0xFF865300),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDDB8),
    onSecondaryContainer = Color(0xFF663E00),
    tertiary = Color(0xFF006780),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFB9EAFF),
    onTertiaryContainer = Color(0xFF004D61),
    background = Color(0xFFFDF9F0),
    onBackground = Color(0xFF1C1C17),
    surface = Color(0xFFFDF9F0),
    onSurface = Color(0xFF1C1C17),
    surfaceVariant = Color(0xFFE6E3D1),
    onSurfaceVariant = Color(0xFF48473A),
    surfaceTint = Color(0xFF2D6956),
    inverseSurface = Color(0xFF31312B),
    inverseOnSurface = Color(0xFFF4F0E7),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF797869),
    outlineVariant = Color(0xFFCAC7B6),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFDF9F0),
    surfaceContainer = Color(0xFFF1EEE5),
    surfaceContainerHigh = Color(0xFFECE8DF),
    surfaceContainerHighest = Color(0xFFE6E2D9),
    surfaceContainerLow = Color(0xFFF7F3EA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDDDAD1),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF96D3BC),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF0F503F),
    onPrimaryContainer = Color(0xFFB2EFD7),
    inversePrimary = Color(0xFF2D6956),
    secondary = Color(0xFFFFB961),
    onSecondary = Color(0xFF472A00),
    secondaryContainer = Color(0xFF663E00),
    onSecondaryContainer = Color(0xFFFFDDB8),
    tertiary = Color(0xFF85D1EE),
    onTertiary = Color(0xFF003544),
    tertiaryContainer = Color(0xFF004D61),
    onTertiaryContainer = Color(0xFFB9EAFF),
    background = Color(0xFF14140F),
    onBackground = Color(0xFFE6E2D9),
    surface = Color(0xFF14140F),
    onSurface = Color(0xFFE6E2D9),
    surfaceVariant = Color(0xFF48473A),
    onSurfaceVariant = Color(0xFFCAC7B6),
    surfaceTint = Color(0xFF96D3BC),
    inverseSurface = Color(0xFFE6E2D9),
    inverseOnSurface = Color(0xFF31312B),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF939181),
    outlineVariant = Color(0xFF48473A),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF3A3933),
    surfaceContainer = Color(0xFF20201A),
    surfaceContainerHigh = Color(0xFF2B2A24),
    surfaceContainerHighest = Color(0xFF36352F),
    surfaceContainerLow = Color(0xFF1C1C17),
    surfaceContainerLowest = Color(0xFF0F0E0A),
    surfaceDim = Color(0xFF14140F),
)

/** Material 3 corner tokens: cards medium, sheets and dialogs extra large, buttons and chips full. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** The Material 3 spacing scale (multiples of 4 dp), so screens don't scatter raw sizes. */
object Spacing {
    val xxs = 4.dp
    val xs = 8.dp
    val s = 12.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp
}

/** Readable width for text-heavy pages on tablets and foldables. */
val MaxContentWidth = 840.dp

/**
 * The app keeps its own calm travel palette rather than the wallpaper's dynamic colors, so the
 * green/amber identity (map pins, location dot, icon) stays consistent with the rest of the UI.
 */
@Composable
fun PassingThroughTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = Typography(),
        shapes = AppShapes,
        content = content,
    )
}
