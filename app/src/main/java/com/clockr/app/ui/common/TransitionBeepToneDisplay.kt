package com.clockr.app.ui.common

import com.clockr.app.data.TransitionBeepTone

fun TransitionBeepTone.displayName(): String = when (this) {
    TransitionBeepTone.BEEP -> "Beep"
    TransitionBeepTone.DOUBLE_BEEP -> "Double beep"
    TransitionBeepTone.CHIME -> "Chime"
    TransitionBeepTone.PIP -> "Pip"
    TransitionBeepTone.SILENT -> "Silent"
}
