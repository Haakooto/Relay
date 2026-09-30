package com.relay.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.relay.app.data.settings.ThemeSetting

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Paper,
    secondary = Teal,
    onSecondary = Paper,
    tertiary = Brass,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceContainer = SurfaceLight,
)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Paper,
    secondary = Teal,
    onSecondary = Paper,
    tertiary = Brass,
    background = PaperDark,
    onBackground = InkDark,
    surface = PaperDark,
    onSurface = InkDark,
    surfaceContainer = SurfaceDark,
)

private val AmoledColors = DarkColors.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainer = Color(0xFF111111),
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
