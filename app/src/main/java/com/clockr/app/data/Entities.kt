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
 */
@Entity(tableName = "timer_defs")
data class TimerDefEntity(
    @PrimaryKey val id: String,
    val label: String,
    val durationExpr: String,
    val soundProfileId: String,
    val persistent: Boolean,
)

/**
 * Spec §03 ChainDef — a composite block. [contentExpr] is the full chain-content DSL
 * (spec §6.4), e.g. `"(repeat(4, (Work, Break)))"`. [soundOverrides] is a small
 * `label=soundProfileId` list, semicolon-separated — a real key/value store would replace this,
 * kept simple here since the sound-override UI/flow itself is still open per spec §11.
 */
@Entity(tableName = "chain_defs")
data class ChainDefEntity(
    @PrimaryKey val id: String,
    val label: String,
    val contentExpr: String,
    val defaultSoundProfileId: String,
    val soundOverrides: String = "",
    val transitionMode: String,
    val persistent: Boolean,
) {
    fun soundOverrideMap(): Map<String, String> =
        soundOverrides.split(";").filter { it.isNotBlank() }
            .associate { it.substringBefore('=') to it.substringAfter('=') }

    companion object {
        fun encodeSoundOverrides(map: Map<String, String>): String =
            map.entries.joinToString(";") { "${it.key}=${it.value}" }
    }
}
