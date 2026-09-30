package com.relay.app.data.library

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** A sound choice: [uri] null = "the default" at that level (spec §14 SoundSpec). */
@Serializable
data class SoundSpec(val uri: String? = null, val vibrate: Boolean = true)

/** How a relay advances between its steps (spec §05). */
enum class TransitionMode { SEAMLESS, INTERACTION }

/** The short cue at a seamless boundary. Generated tones, no audio files. */
enum class TransitionTone { BEEP, DOUBLE_BEEP, CHIME, PIP, SILENT }

/**
 * Per-step override inside one relay (spec §05): a sound for the step itself, and/or the
 * transition for the boundary right after it. Keyed by the step's index in the relay's own
 * resolved steps (what the editor preview lists).
 */
@Serializable
data class StepOverride(val soundUri: String? = null, val transition: TransitionMode? = null)

/**
 * A saved Timer (spec §14 TimerDef). Only saved timers exist as rows; an unsaved one lives only
 * as its run. [label] is unique across timers and relays, and referenceable from scripts.
 */
@Entity(tableName = "timer_defs", indices = [Index("label")])
data class TimerDefEntity(
    @PrimaryKey val id: String,
    val label: String,
    /** A single duration in the relay language: `25m`, `1h30m`, `90`, `MINUTE(NOW)`. */
    val durationExpr: String,
    @Embedded(prefix = "sound_") val sound: SoundSpec,
    val headerId: String?,
    val sortOrder: Long,
)

/** A saved Relay (spec §14 RelayDef). */
@Entity(tableName = "relay_defs", indices = [Index("label")])
data class RelayDefEntity(
    @PrimaryKey val id: String,
    val label: String,
    val script: String,
    /** For anonymous steps. */
    @Embedded(prefix = "sound_") val defaultSound: SoundSpec,
    val transitionMode: TransitionMode,
    val transitionTone: TransitionTone,
    /** JSON of `Map<Int, StepOverride>`, see [com.relay.app.run.StepOverrides]. */
    val stepOverrides: String,
    val headerId: String?,
    val sortOrder: Long,
)
