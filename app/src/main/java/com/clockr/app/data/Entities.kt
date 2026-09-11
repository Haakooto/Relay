package com.clockr.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Spec §03 SoundProfile. */
@Entity(tableName = "sound_profiles")
data class SoundProfileEntity(
    @PrimaryKey val id: String,
    val ringtoneUri: String,
    val vibrationPattern: String,
    val volume: Float,
)

/**
 * Spec §03 TimerDef — a leaf block. [durationExpr] is a chainlang duration expression
 * (`"25m"`, `"2h30m10s"`, `"MINUTE(NOW)"`), evaluated on demand rather than stored resolved,
 * since it may depend on `NOW`.
 *
 * [headerId] and [sortOrder] are purely a library-list presentation concern (which
 * [LibraryHeaderEntity] it's grouped under, if any, and its position within that group or among
 * the ungrouped) — chainlang and the run engine never look at them. Defaulted rather than
 * required so every existing call site (save/edit) that doesn't care about placement keeps
 * whatever placement a block already had.
 */
@Entity(tableName = "timer_defs")
data class TimerDefEntity(
    @PrimaryKey val id: String,
    val label: String,
    val durationExpr: String,
    val soundProfileId: String,
    val persistent: Boolean,
    val headerId: String? = null,
    val sortOrder: Long = System.currentTimeMillis(),
)

/**
 * Spec §03 ChainDef — a composite block. [contentExpr] is the full chain-content DSL
 * (spec §6.4), e.g. `"(repeat(4, (Work, Break)))"`. [soundOverrides] is a small
 * `label=soundProfileId` list, semicolon-separated — a real key/value store would replace this,
 * kept simple here since the sound-override UI/flow itself is still open per spec §11.
 */
/** Synthesized via [android.media.ToneGenerator] — no audio asset to pick, so a short fixed
 *  set rather than the ringtone library (which is exactly the "long-winded" problem this exists
 *  to avoid). [SILENT] disables the seamless-transition cue entirely. Per-Chain, not global —
 *  different chains sound different at their own seamless boundaries. */
enum class TransitionBeepTone { BEEP, DOUBLE_BEEP, CHIME, PIP, SILENT }

@Entity(tableName = "chain_defs")
data class ChainDefEntity(
    @PrimaryKey val id: String,
    val label: String,
    val contentExpr: String,
    val defaultSoundProfileId: String,
    val soundOverrides: String = "",
    val transitionMode: String,
    val persistent: Boolean,
    /** See [TimerDefEntity.headerId]. */
    val headerId: String? = null,
    val sortOrder: Long = System.currentTimeMillis(),
    /** [TransitionBeepTone] as a string, same nullable-enum-as-string pattern as the rest of
     *  this entity's enum-backed fields. */
    val transitionBeepTone: String = TransitionBeepTone.BEEP.name,
) {
    fun soundOverrideMap(): Map<String, String> =
        soundOverrides.split(";").filter { it.isNotBlank() }
            .associate { it.substringBefore('=') to it.substringAfter('=') }

    companion object {
        fun encodeSoundOverrides(map: Map<String, String>): String =
            map.entries.joinToString(";") { "${it.key}=${it.value}" }
    }
}

/** A user-named, user-orderable section in the Timers library list — purely organizational,
 *  no runtime meaning (a Timer/Chain under a header runs exactly the same as one that isn't). */
@Entity(tableName = "library_headers")
data class LibraryHeaderEntity(
    @PrimaryKey val id: String,
    val title: String,
    val sortOrder: Long = System.currentTimeMillis(),
)
