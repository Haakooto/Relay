package com.relay.app.data.schedule

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

/** What a one-off schedule does once its ringing is finally over (spec §04). */
enum class AfterFire { DELETE, DISABLE }

/** Spec §03 target kinds. Only [RING] exists until build step 5 adds the others. */
enum class TargetKind { RING }

/** Config for the Ring target (spec §14 `Ring(sound, ramp, snooze, maxSnoozes)`). */
data class RingOptions(
    /** Null means the default alarm sound from Settings, or the system one. */
    val soundUri: String?,
    val vibrate: Boolean,
    /** 0 = full volume at once. */
    val rampSeconds: Int,
    val snoozeMinutes: Int,
    /** Null = unlimited. */
    val maxSnoozes: Int?,
)

/**
 * A schedule (UI: "Alarm"), spec §04/§14. A one-off has [oneOffEpochDay] set and [repeatDays] 0.
 * A repeating one has a weekday bitmask in [repeatDays] (see [WeekdayMask]).
 *
 * The last block of fields is runtime state owned by [com.relay.app.schedule.ScheduleRepository];
 * the editor never writes it.
 */
@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey val id: String,
    val label: String,
    val hour: Int,
    val minute: Int,
    val oneOffEpochDay: Long?,
    val repeatDays: Int,
    /** Repeating only: last day it may fire, inclusive. */
    val endEpochDay: Long?,
    /** Repeating only: occurrences left before it disables itself. Null = no count limit. */
    val remainingCount: Int?,
    val afterFire: AfterFire,
    val enabled: Boolean,
    val target: TargetKind,
    @Embedded(prefix = "ring_") val ring: RingOptions,
    val createdAtMillis: Long,
    /** "user" for now. Future external callers (spec §12) record their package here. */
    val createdBy: String,

    // Runtime state.
    val snoozedUntilMillis: Long?,
    val snoozesUsed: Int,
    /** The trigger time currently handed to AlarmManager. Used to spot missed and duplicate fires. */
    val nextFireAtMillis: Long?,
) {
    val isRepeating: Boolean get() = repeatDays != 0
}
