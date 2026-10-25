package com.relay.app.data.schedule

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** What a one-off schedule does once its ringing is finally over (spec §04). */
enum class AfterFire { DELETE, DISABLE }

/**
 * What a schedule does when it fires (spec §03/§04): ring, post a notification, or quietly start
 * a timer, a relay or a stopwatch. A timer or relay is either saved ([ScheduleEntity.targetDefId])
 * or inline ([ScheduleEntity.targetSpec]). Stored by name, so the order here is only the UI's.
 */
enum class TargetKind { RING, NOTIFY, TIMER, RELAY, STOPWATCH }

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
 * A repeating one has a weekday bitmask in [repeatDays] (see [WeekdayMask]), or repeats monthly
 * or yearly on [repeatMonthDay] (spec §10b, calendar reminders only for now).
 *
 * The last block of fields is runtime state owned by [com.relay.app.schedule.ScheduleRepository];
 * the editor never writes it.
 */
@Entity(tableName = "schedules", indices = [Index("eventId")])
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

    /** TIMER / RELAY: a saved definition's id. Null = the inline one in [targetSpec]. */
    val targetDefId: String? = null,
    /** [com.relay.app.schedule.TargetSpec] JSON: inline timer/relay, or the stopwatch to start. */
    @ColumnInfo(defaultValue = "{}") val targetSpec: String = "{}",

    // Calendar reminders (spec §10b). The schedule carries everything firing needs, so the
    // firing path never reads the events table.
    /** The calendar item this reminds of; null = an alarm. */
    val eventId: String? = null,
    /** Repeating only: no occurrence before this day. Null = no lower bound. */
    val startEpochDay: Long? = null,
    /** Repeats monthly on this day of the month (yearly with [repeatMonth]); months without it are skipped. */
    val repeatMonthDay: Int? = null,
    /** Yearly repeats: the month, 1 to 12. */
    val repeatMonth: Int? = null,
    /** Fires this long before each occurrence: a reminder's lead time. */
    @ColumnInfo(defaultValue = "0") val leadMinutes: Int = 0,
    /** An alarm's calendar (#34); null = the default for alarms. A reminder uses its item's. */
    val calendarId: String? = null,
    /** A reminder's repeat rule (#43), from [startEpochDay] on; wins over the other repeat fields. */
    val repeatRule: String? = null,
) {
    val isRepeating: Boolean get() = repeatDays != 0 || repeatMonthDay != null || repeatRule != null
}
