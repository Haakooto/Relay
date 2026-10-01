package com.relay.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.relay.app.data.settings.ThemeSetting

private val LightColors = with(LightTokens) {
    lightColorScheme(
        primary = primary, onPrimary = onPrimary, primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
        secondary = secondary, onSecondary = onSecondary, secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary, onTertiary = onTertiary, tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
        background = background, onBackground = onBackground, surface = surface, onSurface = onSurface,
        surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant, outline = outline, outlineVariant = outlineVariant,
        surfaceContainerLowest = surfaceContainerLowest, surfaceContainerLow = surfaceContainerLow, surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh, surfaceContainerHighest = surfaceContainerHighest,
        surfaceTint = primary, inverseSurface = inverseSurface, inverseOnSurface = inverseOnSurface, inversePrimary = inversePrimary,
    )
}

private val DarkColors = with(DarkTokens) {
    darkColorScheme(
        primary = primary, onPrimary = onPrimary, primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
        secondary = secondary, onSecondary = onSecondary, secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary, onTertiary = onTertiary, tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
        background = background, onBackground = onBackground, surface = surface, onSurface = onSurface,
        surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant, outline = outline, outlineVariant = outlineVariant,
        surfaceContainerLowest = surfaceContainerLowest, surfaceContainerLow = surfaceContainerLow, surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh, surfaceContainerHighest = surfaceContainerHighest,
        surfaceTint = primary, inverseSurface = inverseSurface, inverseOnSurface = inverseOnSurface, inversePrimary = inversePrimary,
    )
}

/** Pure black surfaces for OLED screens; containers stay just off black so cards still read. */
private val AmoledColors = DarkColors.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0A),
    surfaceContainer = Color(0xFF111110),
    surfaceContainerHigh = Color(0xFF1A1A18),
    surfaceContainerHighest = Color(0xFF242422),
)

@Composable
fun RelayTheme(theme: ThemeSetting, content: @Composable () -> Unit) {
    val colors = when (theme) {
        ThemeSetting.LIGHT -> LightColors
        ThemeSetting.DARK -> DarkColors
        ThemeSetting.AMOLED -> AmoledColors
        ThemeSetting.SYSTEM -> if (isSystemInDarkTheme()) DarkColors else LightColors
    }
    MaterialTheme(colorScheme = colors, typography = RelayTypography, content = content)
}
