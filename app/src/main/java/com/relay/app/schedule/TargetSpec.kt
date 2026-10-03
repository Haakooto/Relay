package com.relay.app.schedule

import com.relay.app.data.library.TransitionMode
import com.relay.app.data.library.TransitionTone
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The inline part of a schedule's target (spec §04): an unsaved timer or relay, or the
 * stopwatch to start. Only the fields for the schedule's [com.relay.app.data.schedule.TargetKind]
 * matter; the rest keep their defaults so switching kinds in the editor loses nothing.
 */
@Serializable
data class TargetSpec(
    /** Run label for a timer or relay; the name for a stopwatch. */
    val label: String = "",
    val durationExpr: String = "5m",
    val script: String = "(\n  5m,\n  5m\n)",
    val mode: TransitionMode = TransitionMode.SEAMLESS,
    val tone: TransitionTone = TransitionTone.BEEP,
    /** Stopwatch start offset; may be negative. */
    val t0Millis: Long = 0,
    val tags: List<String> = emptyList(),
    /** Played when the alarm starts it, so a quiet start is still noticed. */
    val startTone: TransitionTone = TransitionTone.BEEP,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
        fun decode(text: String?): TargetSpec = text?.let { runCatching { json.decodeFromString<TargetSpec>(it) }.getOrNull() } ?: TargetSpec()
        fun encode(spec: TargetSpec): String = json.encodeToString(spec)
    }
}
