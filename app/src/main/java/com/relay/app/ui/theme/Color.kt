package com.relay.app.ui.theme

import androidx.compose.ui.graphics.Color

// Brand seeds, spec §15. Used as accents (clock faces, highlights), not as large surfaces.
val Ink = Color(0xFF151513)
val Paper = Color(0xFFF4F2EA)
val Accent = Color(0xFFC7501E)
val Teal = Color(0xFF2E6F66)
val Brass = Color(0xFF7A6A4F)

// Light scheme: the cream paper look (preferred over a cooler near-white), with two fixes from
// the first device test: selection is a warm grey with ink text instead of Material's default
// lavender, and the orange is a shade deeper so text on paper reads at 4.9:1 (was 4.1:1).
// Every role is set: anything left out falls back to Material's default purple.
object LightTokens {
    val primary = Color(0xFFB4461A)
    val onPrimary = Color(0xFFFFFFFF)
    val primaryContainer = Accent
    val onPrimaryContainer = Color(0xFFFFFFFF)
    val secondary = Color(0xFF5E5A4E)
    val onSecondary = Color(0xFFFFFFFF)
    val secondaryContainer = Color(0xFFD6D2C4)
    val onSecondaryContainer = Ink
    val tertiary = Teal
    val onTertiary = Color(0xFFFFFFFF)
    val tertiaryContainer = Color(0xFFB8E3DA)
    val onTertiaryContainer = Color(0xFF00201C)
    val background = Paper
    val onBackground = Ink
    val surface = Paper
    val onSurface = Ink
    val surfaceVariant = Color(0xFFE4E0D2)
    val onSurfaceVariant = Color(0xFF4A473D)
    val outline = Color(0xFF7D7869)
    val outlineVariant = Color(0xFFCCC7B8)
    val surfaceContainerLowest = Color(0xFFFBFAF5)
    val surfaceContainerLow = Color(0xFFEFECE2)
    val surfaceContainer = Color(0xFFE9E6DA)
    val surfaceContainerHigh = Color(0xFFE3DFD2)
    val surfaceContainerHighest = Color(0xFFDDD9CB)
    val inverseSurface = Color(0xFF31302A)
    val inverseOnSurface = Paper
    val inversePrimary = Color(0xFFFF8A57)
}

// Dark scheme: the warm olive-black of v0.5 with a vivid orange (5.2:1 on the background), and
// an orange-tinted selection so chosen options pop instead of fading into grey.
object DarkTokens {
    val primary = Color(0xFFE8682F)
    val onPrimary = Ink
    val primaryContainer = Accent
    val onPrimaryContainer = Color(0xFFFFFFFF)
    val secondary = Color(0xFFFFB08C)
    val onSecondary = Color(0xFF4A1A05)
    val secondaryContainer = Color(0xFF5A2A12)
    val onSecondaryContainer = Color(0xFFFFD9C7)
    val tertiary = Color(0xFF6CC3B5)
    val onTertiary = Color(0xFF00332D)
    val tertiaryContainer = Teal
    val onTertiaryContainer = Color(0xFFE0FFF8)
    val background = Color(0xFF1C1E17)
    val onBackground = Color(0xFFEDEAE0)
    val surface = Color(0xFF1C1E17)
    val onSurface = Color(0xFFEDEAE0)
    val surfaceVariant = Color(0xFF3A3C32)
    val onSurfaceVariant = Color(0xFFCBC7B8)
    val outline = Color(0xFF959181)
    val outlineVariant = Color(0xFF45473C)
    val surfaceContainerLowest = Color(0xFF15170F)
    val surfaceContainerLow = Color(0xFF21231B)
    val surfaceContainer = Color(0xFF262820)
    val surfaceContainerHigh = Color(0xFF30322A)
    val surfaceContainerHighest = Color(0xFF3B3D34)
    val inverseSurface = Color(0xFFEDEAE0)
    val inverseOnSurface = Color(0xFF31302A)
    val inversePrimary = Color(0xFFB4461A)
}
