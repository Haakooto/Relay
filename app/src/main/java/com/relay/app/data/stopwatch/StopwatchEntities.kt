package com.relay.app.data.stopwatch

import androidx.room.ColumnInfo
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
    /** The starting offset, possibly negative (counts up through zero); restart goes back here. */
    val t0Millis: Long,
    val accumulatedMillis: Long,
    /** Null = paused. */
    val startedAtMillis: Long?,
    val sortOrder: Long,
    /** JSON list of this stopwatch's own tags (e.g. "run", "break"); each stopwatch has its own. */
    @ColumnInfo(defaultValue = "[]") val tags: String = "[]",
)

/**
 * A recorded lap, identified by its end boundary. Times can be corrected (remove, insert, adjust a
 * boundary) without changing the total; every correction is logged in [LapCorrectionEntity].
 */
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
    /** JSON list, a subset of the stopwatch's tags. */
    @ColumnInfo(defaultValue = "[]") val tags: String = "[]",
)

/**
 * One lap correction, per stopwatch (spec §07). [ops] is a JSON list of reversible boundary
 * steps; undo runs their inverses backwards, redo runs them again. [undone] corrections stay listed
 * (greyed) until a new correction discards them.
 */
@Entity(
    tableName = "lap_corrections",
    foreignKeys = [ForeignKey(StopwatchEntity::class, ["id"], ["stopwatchId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("stopwatchId")],
)
data class LapCorrectionEntity(
    @PrimaryKey val id: String,
    val stopwatchId: String,
    val seq: Long,
    /** REMOVE / INSERT / ADJUST. */
    val kind: String,
    /** The lap number it was made on, at the time. */
    val lapNumber: Int,
    /** The amount: "into" time for INSERT, signed delta for ADJUST, 0 for REMOVE. */
    val amountMillis: Long,
    val label: String,
    val ops: String,
    /** Lap ids whose time this correction changed: they get the star while it's active. */
    val affected: String,
    val undone: Boolean,
    val createdAtMillis: Long,
)
