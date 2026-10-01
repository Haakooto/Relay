package com.relay.app.ui.theme

import androidx.compose.ui.graphics.Color

// Brand seeds, spec §15. Used as accents (clock faces, highlights), not as large surfaces.
val Ink = Color(0xFF151513)
val Paper = Color(0xFFF4F2EA)
val Accent = Color(0xFFC7501E)
val Teal = Color(0xFF2E6F66)
val Brass = Color(0xFF7A6A4F)

// Light scheme. A near-white, barely warm background instead of the cream "paper" (which read
// as sun-bleached across a whole screen), neutral greys for selection, and a deeper orange so
// orange text clears 5.8:1 contrast. Every role is set: anything left out falls back to
// Material's default purple, which is where the lavender chips came from.
object LightTokens {
    val primary = Color(0xFFA8401A)
    val onPrimary = Color(0xFFFFFFFF)
    val primaryContainer = Color(0xFFFFDBCE)
    val onPrimaryContainer = Color(0xFF3A0B00)
    val secondary = Color(0xFF5D5E58)
    val onSecondary = Color(0xFFFFFFFF)
    val secondaryContainer = Color(0xFFDEDED8)
    val onSecondaryContainer = Color(0xFF1B1C18)
    val tertiary = Teal
    val onTertiary = Color(0xFFFFFFFF)
    val tertiaryContainer = Color(0xFFB8ECE2)
    val onTertiaryContainer = Color(0xFF00201C)
    val background = Color(0xFFF8F8F6)
    val onBackground = Color(0xFF1B1C18)
    val surface = Color(0xFFF8F8F6)
    val onSurface = Color(0xFF1B1C18)
    val surfaceVariant = Color(0xFFE4E3DE)
    val onSurfaceVariant = Color(0xFF46463F)
    val outline = Color(0xFF77776F)
    val outlineVariant = Color(0xFFC8C7C0)
    val surfaceContainerLowest = Color(0xFFFFFFFF)
    val surfaceContainerLow = Color(0xFFF2F2EF)
    val surfaceContainer = Color(0xFFECECE8)
    val surfaceContainerHigh = Color(0xFFE6E6E2)
    val surfaceContainerHighest = Color(0xFFE0E0DB)
    val inverseSurface = Color(0xFF30312C)
    val inverseOnSurface = Color(0xFFF2F1EB)
    val inversePrimary = Color(0xFFFFB59A)
}

object DarkTokens {
    val primary = Color(0xFFFFB59A)
    val onPrimary = Color(0xFF5A1B00)
    val primaryContainer = Color(0xFF7E2C0A)
    val onPrimaryContainer = Color(0xFFFFDBCE)
    val secondary = Color(0xFFC6C7BF)
    val onSecondary = Color(0xFF2F312B)
    val secondaryContainer = Color(0xFF3F403B)
    val onSecondaryContainer = Color(0xFFE3E3DB)
    val tertiary = Color(0xFF8FD3C6)
    val onTertiary = Color(0xFF003731)
    val tertiaryContainer = Color(0xFF00504A)
    val onTertiaryContainer = Color(0xFFB8ECE2)
    val background = Color(0xFF131411)
    val onBackground = Color(0xFFE4E3DD)
    val surface = Color(0xFF131411)
    val onSurface = Color(0xFFE4E3DD)
    val surfaceVariant = Color(0xFF46463F)
    val onSurfaceVariant = Color(0xFFC8C7C0)
    val outline = Color(0xFF92918A)
    val outlineVariant = Color(0xFF46463F)
    val surfaceContainerLowest = Color(0xFF0E0F0C)
    val surfaceContainerLow = Color(0xFF1B1C19)
    val surfaceContainer = Color(0xFF1F201D)
    val surfaceContainerHigh = Color(0xFF2A2A27)
    val surfaceContainerHighest = Color(0xFF353531)
    val inverseSurface = Color(0xFFE4E3DD)
    val inverseOnSurface = Color(0xFF30312C)
    val inversePrimary = Color(0xFFA8401A)
}
