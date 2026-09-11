package com.clockr.app.run

import android.media.AudioManager
import android.media.ToneGenerator
import com.clockr.app.data.TransitionBeepTone
import kotlinx.coroutines.delay

/**
 * The one place a [TransitionBeepTone] is turned into an actual sound — used by both the run
 * engine (on a seamless step boundary) and the Settings screen (to preview a choice before
 * committing to it), so the two can't drift into playing different things for the same setting.
 */
object TransitionBeep {
    suspend fun play(tone: TransitionBeepTone) {
        val toneType = when (tone) {
            TransitionBeepTone.BEEP -> ToneGenerator.TONE_PROP_BEEP
            TransitionBeepTone.DOUBLE_BEEP -> ToneGenerator.TONE_PROP_ACK
            TransitionBeepTone.CHIME -> ToneGenerator.TONE_PROP_PROMPT
            TransitionBeepTone.PIP -> ToneGenerator.TONE_CDMA_PIP
            TransitionBeepTone.SILENT -> return
        }
        val durationMs = 150
        val toneGenerator = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME) }.getOrNull() ?: return
        try {
            toneGenerator.startTone(toneType, durationMs)
            delay(durationMs + 100L)
        } finally {
            toneGenerator.release()
        }
    }
}
