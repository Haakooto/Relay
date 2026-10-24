package com.relay.app.data.event

import androidx.room.Entity
import androidx.room.PrimaryKey

/** An event has a duration and a note; a reminder is a single moment (spec §10b). */
enum class EventKind { EVENT, REMINDER }

/** How a calendar item repeats. Monthly and yearly skip months that lack the start's date. */
enum class RepeatKind { NONE, WEEKLY, MONTHLY, YEARLY }

/**
 * A calendar item (spec §10b): a dated, titled entry made from the Calendar pane. Its reminder,
 * if any, is an ordinary schedule pointing back here through `ScheduleEntity.eventId`, so firing
 * stays in the one module. Times are local wall-clock times, like alarms.
 */
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val id: String,
    val kind: EventKind,
    val title: String,
    val note: String,
    val startEpochDay: Long,
    /** Minutes after midnight; null = all day. A reminder always has one. */
    val startMinute: Int?,
    /** Last day, inclusive; null = the start day. */
    val endEpochDay: Long?,
    /** Minutes after midnight on the end day; null = no end time. */
    val endMinute: Int?,
    val repeatKind: RepeatKind,
    /** Weekly only: a [com.relay.app.data.schedule.WeekdayMask]. */
    val repeatDays: Int,
    /** Repeating only: last day an occurrence may start, inclusive. */
    val untilEpochDay: Long?,
    val createdAtMillis: Long,
) {
    val isAllDay: Boolean get() = startMinute == null

    /** Whole days from an occurrence's start day to its last day. */
    val spanDays: Long get() = ((endEpochDay ?: startEpochDay) - startEpochDay).coerceAtLeast(0)
}
