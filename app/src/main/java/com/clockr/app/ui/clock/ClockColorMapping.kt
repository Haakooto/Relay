package com.clockr.app.ui.clock

import androidx.compose.ui.graphics.Color
import com.clockr.app.data.ClockColor

/** [ClockColor] -> the actual brand palette Color, matching ui/theme/Color.kt's tokens exactly.
 *  One place for this so the in-app config screen, ClockScreen, and the widget's digital face
 *  and box styling can't quietly drift out of sync with each other. */
fun ClockColor.toComposeColor(): Color = when (this) {
    ClockColor.INK -> Color(0xFF151513)
    ClockColor.ACCENT -> Color(0xFFC7501E)
    ClockColor.TEAL -> Color(0xFF2E6F66)
    ClockColor.BRASS -> Color(0xFF7A6A4F)
}
