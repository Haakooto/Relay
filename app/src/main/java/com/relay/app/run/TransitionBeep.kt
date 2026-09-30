package com.relay.app.run

import android.media.AudioManager
import android.media.ToneGenerator
import com.relay.app.data.library.TransitionTone
import kotlinx.coroutines.delay

/**
 * The seamless-boundary cue (salvaged from v0.5). One place turns a [TransitionTone] into sound,
 * so the run engine and the editor's preview can't drift apart.
 */
object TransitionBeep {
    suspend fun play(tone: TransitionTone) {
        val toneType = when (tone) {
            TransitionTone.BEEP -> ToneGenerator.TONE_PROP_BEEP
            TransitionTone.DOUBLE_BEEP -> ToneGenerator.TONE_PROP_ACK
            TransitionTone.CHIME -> ToneGenerator.TONE_PROP_PROMPT
            TransitionTone.PIP -> ToneGenerator.TONE_CDMA_PIP
            TransitionTone.SILENT -> return
        }
        val generator = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME) }.getOrNull() ?: return
        try {
            generator.startTone(toneType, TONE_MILLIS)
            delay(TONE_MILLIS + 150L)
        } finally {
            generator.release()
        }
    }

    private const val TONE_MILLIS = 200
}
