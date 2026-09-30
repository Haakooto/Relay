package com.relay.app.firing

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Looping alarm audio and vibration (spec §13). Our own playback on the alarm stream, not a
 * notification-channel sound: v0.5 showed a channel sound is one-shot and any other notification
 * cuts it off. Every step has a fallback, ending in a generated tone: failing to make noise is
 * the one outcome this class must not produce.
 */
class RingPlayer(private val context: Context, private val scope: CoroutineScope) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attributes)
        .build()

    private var player: MediaPlayer? = null
    private var toneJob: Job? = null
    private var rampJob: Job? = null

    fun play(item: RingItem) {
        stop()
        audioManager.requestAudioFocus(focusRequest)
        if (!startMediaPlayer(item)) startToneFallback()
        if (item.vibrate) {
            runCatching {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 800, 800), 0),
                    VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
                )
            }.onFailure { Log.w(TAG, "vibration failed", it) }
        }
    }

    fun stop() {
        rampJob?.cancel()
        toneJob?.cancel()
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        runCatching { vibrator.cancel() }
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    private fun startMediaPlayer(item: RingItem): Boolean {
        for (uri in candidates(item.soundUri)) {
            val mp = MediaPlayer()
            try {
                mp.setAudioAttributes(attributes)
                mp.setDataSource(context, uri)
                mp.isLooping = true
                mp.prepare()
                val startVolume = if (item.rampSeconds > 0) RAMP_START else 1f
                mp.setVolume(startVolume, startVolume)
                mp.start()
                player = mp
                if (item.rampSeconds > 0) startRamp(mp, item.rampSeconds)
                return true
            } catch (e: Exception) {
                Log.w(TAG, "can't play $uri, trying the next sound", e)
                mp.release()
            }
        }
        return false
    }

    /** The picked sound, then the system alarm, notification and ringtone defaults. */
    private fun candidates(picked: String?): List<Uri> = listOfNotNull(
        picked?.let(Uri::parse),
        RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM),
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
    ).distinct()

    private fun startRamp(mp: MediaPlayer, seconds: Int) {
        rampJob = scope.launch {
            val steps = 50
            for (i in 1..steps) {
                delay(seconds * 1000L / steps)
                val v = RAMP_START + (1f - RAMP_START) * i / steps
                runCatching { mp.setVolume(v, v) }
            }
        }
    }

    private fun startToneFallback() {
        Log.e(TAG, "no playable sound, falling back to a generated tone")
        toneJob = scope.launch {
            val tone = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME) }.getOrNull() ?: return@launch
            try {
                while (isActive) {
                    tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1000)
                    delay(1500)
                }
            } finally {
                tone.release()
            }
        }
    }

    private companion object {
        const val TAG = "RingPlayer"
        const val RAMP_START = 0.05f
    }
}
