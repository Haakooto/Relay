package com.relay.app.data.run

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class RunKind { TIMER, RELAY }

enum class RunPhase {
    RUNNING,
    PAUSED,
    /** At an interaction-required boundary: rings until Continue / Stop relay. */
    AWAITING,
    /** The last step ended: rings until Stop / Restart / +1 min. */
    DONE,
}

/**
 * A live run (spec §14 Run), persisted so it survives a process kill and a reboot. The plan is a
 * frozen snapshot taken at start: editing the definition afterwards doesn't touch a running run.
 */
@Entity(tableName = "runs")
data class RunEntity(
    @PrimaryKey val id: String,
    val kind: RunKind,
    /** Null = unsaved (anonymous). */
    val sourceDefId: String?,
    /** Free text, shown on the ringing screen. "" = none. */
    val label: String,
    /** JSON `List<RunStep>`, see [com.relay.app.run.RunStep]. */
    val plan: String,
    val stepIndex: Int,
    val phase: RunPhase,
    /** RUNNING only: when the current step ends. */
    val stepEndsAtMillis: Long?,
    /** PAUSED only: time left in the current step. */
    val remainingMillis: Long?,
    /** AWAITING/DONE: when it started ringing. */
    val alertSinceMillis: Long?,
    val startedAtMillis: Long,
    /** "user", or a schedule id when an alarm started it (step 5). */
    val startedBy: String,
)
