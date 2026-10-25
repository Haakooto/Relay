package com.relay.app.data.event

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.relay.app.data.schedule.TargetKind

/**
 * A calendar (spec §10b, #34): a named, coloured group of items and alarms with defaults for new
 * items. Every event belongs to one; an alarm too (`ScheduleEntity.calendarId`). None is special:
 * the three made by the v9 update can be renamed, recoloured or deleted like any other.
 */
@Entity(tableName = "calendars")
data class CalendarEntity(
    @PrimaryKey val id: String,
    /** Blank = the starting calendar's name in the app's language (see [STARTING_CALENDARS]). */
    val name: String,
    /** ARGB. */
    val color: Long,
    val sortOrder: Int,
    /** Ticked in the Calendar pane's list. */
    val shown: Boolean,
    /** New alarms go here. Exactly one calendar has it. */
    val defaultForAlarms: Boolean,
    /** A new item's reminder: minutes before, or null for none. */
    val defaultLeadMinutes: Int?,
    /** What a new item's reminder does. */
    val defaultTarget: TargetKind,
    val defaultRepeat: RepeatKind,
    /** A phone calendar (CalendarContract id) its items are copied to (#16); null = none. */
    val exportCalendarId: Long? = null,
    /** An https .ics address it mirrors (#33): its items are read-only and replaced on each refresh. */
    val subscriptionUrl: String? = null,
    /** When the subscription last refreshed; null = never. */
    val refreshedAtMillis: Long? = null,
) {
    val isSubscribed: Boolean get() = subscriptionUrl != null
}

const val PERSONAL_CALENDAR = "personal"
const val BIRTHDAYS_CALENDAR = "birthdays"
const val ALARMS_CALENDAR = "alarms"

/** The ids the v9 update creates; their blank names show translated. */
val STARTING_CALENDARS = listOf(PERSONAL_CALENDAR, BIRTHDAYS_CALENDAR, ALARMS_CALENDAR)
