package com.clockr.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.clockr.app.R
import com.clockr.app.data.TransitionBeepTone

@Composable
fun TransitionBeepTone.displayName(): String = stringResource(
    when (this) {
        TransitionBeepTone.BEEP -> R.string.beep_tone_beep
        TransitionBeepTone.DOUBLE_BEEP -> R.string.beep_tone_double_beep
        TransitionBeepTone.CHIME -> R.string.beep_tone_chime
        TransitionBeepTone.PIP -> R.string.beep_tone_pip
        TransitionBeepTone.SILENT -> R.string.beep_tone_silent
    },
)
