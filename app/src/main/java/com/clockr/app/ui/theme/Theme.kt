package com.clockr.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = ClockrAccent,
    secondary = ClockrTeal,
    tertiary = ClockrBrass,
    background = ClockrPaper,
    surface = ClockrPaper,
    onBackground = ClockrInk,
    onSurface = ClockrInk,
)

private val DarkColors = darkColorScheme(
    primary = ClockrAccent,
    secondary = ClockrTeal,
    tertiary = ClockrBrass,
    background = ClockrPaperDark,
    surface = ClockrPaperDark,
    onBackground = ClockrInkDark,
    onSurface = ClockrInkDark,
)

@Composable
fun ClockrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ClockrTypography,
        content = content,
    )
}
