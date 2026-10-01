package com.relay.app.data.stopwatch

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One stopwatch (spec §07), persisted so it survives a process kill and a reboot. Elapsed time
 * is computed, never ticked: [t0Millis] + [accumulatedMillis] + (now - [startedAtMillis]) while
 * running. Wall-clock based, so it also keeps counting across a reboot.
 */
@Entity(tableName = "stopwatches")
data class StopwatchEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** The starting offset; restart goes back here, not to zero. */
    val t0Millis: Long,
    val accumulatedMillis: Long,
    /** Null = paused. */
    val startedAtMillis: Long?,
    val sortOrder: Long,
)

/** A recorded lap. The time is read-only; only the note can change (spec §07). */
@Entity(
    tableName = "laps",
    foreignKeys = [ForeignKey(StopwatchEntity::class, ["id"], ["stopwatchId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("stopwatchId")],
)
data class LapEntity(
    @PrimaryKey val id: String,
    val stopwatchId: String,
    val number: Int,
    /** The stopwatch's total shown time at the lap, t0 included. */
    val elapsedMillis: Long,
    val note: String,
)
