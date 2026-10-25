package com.relay.app.event

import com.relay.app.data.event.EventEntity
import com.relay.app.data.event.RepeatKind
import com.relay.app.data.schedule.AfterFire
import com.relay.app.data.schedule.RingOptions
import com.relay.app.data.schedule.ScheduleEntity
import com.relay.app.data.schedule.TargetKind
import com.relay.app.schedule.Rrule
import com.relay.app.util.MinuteOfDay
import java.time.LocalDate

/** What an item's reminder does and how early, as its editor holds it (spec §10b). */
data class ReminderDraft(
    /** Before the item's start; for an all-day item, before [ALL_DAY_REMINDER_MINUTE] on its day. */
    val leadMinutes: Int,
    val target: TargetKind,
    val targetDefId: String?,
    val targetSpec: String,
    val ring: RingOptions,
)

/** An all-day item's reminder is relative to 09:00 on its day, in minutes after midnight. */
const val ALL_DAY_REMINDER_MINUTE = 9 * 60

/** Lead times offered for a timed event, in minutes. 0 = at its start. */
val TIMED_LEADS = listOf(0, 5, 15, 30, 60, 24 * 60)

/** Lead times offered for an all-day event: 09:00 on the day, or 09:00 the day before. */
val ALL_DAY_LEADS = listOf(0, 24 * 60)

/**
 * Pure mapping from a calendar item to the schedule that reminds of it. The schedule carries the
 * item's date, time and repeat itself, so firing never needs the events table.
 */
object EventReminders {
    fun scheduleFor(event: EventEntity, reminder: ReminderDraft, scheduleId: String, createdAtMillis: Long): ScheduleEntity {
        val start = LocalDate.ofEpochDay(event.startEpochDay)
        val time = MinuteOfDay.toTime(event.startMinute ?: ALL_DAY_REMINDER_MINUTE)
        val repeat = effectiveRepeat(event)
        return ScheduleEntity(
            id = scheduleId,
            label = event.title,
            hour = time.hour,
            minute = time.minute,
            oneOffEpochDay = if (repeat == RepeatKind.NONE) event.startEpochDay else null,
            repeatDays = if (repeat == RepeatKind.WEEKLY) event.repeatDays else 0,
            endEpochDay = if (repeat == RepeatKind.NONE) null else event.untilEpochDay,
            remainingCount = null,
            // Kept, not deleted, once it has fired: the item still shows its reminder.
            afterFire = AfterFire.DISABLE,
            enabled = true,
            target = reminder.target,
            ring = reminder.ring,
            createdAtMillis = createdAtMillis,
            createdBy = "user",
            snoozedUntilMillis = null,
            snoozesUsed = 0,
            nextFireAtMillis = null,
            targetDefId = reminder.targetDefId,
            targetSpec = reminder.targetSpec,
            eventId = event.id,
            startEpochDay = if (repeat == RepeatKind.NONE) null else event.startEpochDay,
            repeatMonthDay = if (repeat == RepeatKind.MONTHLY || repeat == RepeatKind.YEARLY) start.dayOfMonth else null,
            repeatMonth = if (repeat == RepeatKind.YEARLY) start.monthValue else null,
            leadMinutes = reminder.leadMinutes,
            repeatRule = event.repeatRule.takeIf { repeat == RepeatKind.CUSTOM },
        )
    }

    /** A weekly repeat with no weekday picked, or a rule that won't parse, has nothing to repeat on: a one-off. */
    private fun effectiveRepeat(event: EventEntity): RepeatKind = when {
        event.repeatKind == RepeatKind.WEEKLY && event.repeatDays == 0 -> RepeatKind.NONE
        event.repeatKind == RepeatKind.CUSTOM && event.repeatRule?.let { Rrule.parse(it) } == null -> RepeatKind.NONE
        else -> event.repeatKind
    }
}
