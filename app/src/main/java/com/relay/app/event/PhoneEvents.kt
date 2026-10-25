package com.relay.app.event

import com.relay.app.data.event.EventEntity
import com.relay.app.data.event.RepeatKind
import com.relay.app.data.schedule.WeekdayMask
import com.relay.app.util.MinuteOfDay
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** A calendar on the phone (CalendarContract, #16): Google and others, synced by their own apps. */
data class PhoneCalendar(val id: Long, val name: String, val account: String, val color: Long, val writable: Boolean)

/** One occurrence of a phone-calendar event, as CalendarContract.Instances gives it. */
data class PhoneInstance(
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    val beginMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    /** ARGB: the event's own colour, else its calendar's. */
    val color: Long,
)

/** The columns an item's copy in a phone calendar gets. Pure, so the mapping is tested on the JVM. */
data class PhoneEventFields(
    val title: String,
    val description: String,
    val dtStart: Long,
    /** One-off only; a repeating event has [duration] instead, as CalendarContract requires. */
    val dtEnd: Long?,
    val duration: String?,
    val allDay: Boolean,
    val timeZone: String,
    val rrule: String?,
)

/** A phone calendar's key among the shown calendars, so its ticks sit with Relay's own. */
const val PHONE_CALENDAR_PREFIX = "phone:"

fun phoneCalendarKey(id: Long) = "$PHONE_CALENDAR_PREFIX$id"

/** The phone calendar ids among [keys] (Relay's own calendar ids are left out). */
fun phoneCalendarIds(keys: Collection<String>): Set<Long> =
    keys.filter { it.startsWith(PHONE_CALENDAR_PREFIX) }.mapNotNull { it.removePrefix(PHONE_CALENDAR_PREFIX).toLongOrNull() }.toSet()

/** Between Relay items and phone-calendar events (#16), both ways. */
object PhoneEvents {
    private const val DEFAULT_LENGTH_MINUTES = 60L

    /**
     * [event] as a phone event. All-day items are UTC midnight to midnight, as the provider wants
     * them; timed ones are in [zone]. An item with no end lasts an hour. Repeats become RRULEs with
     * the same rule Relay uses: a month without the date is skipped (BYMONTHDAY does the same).
     */
    fun fields(event: EventEntity, zone: ZoneId): PhoneEventFields {
        val firstDay = LocalDate.ofEpochDay(event.startEpochDay)
        val lastDay = LocalDate.ofEpochDay(event.endEpochDay ?: event.startEpochDay)
        val allDay = event.startMinute == null
        val start: Long
        val end: Long
        if (allDay) {
            start = firstDay.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            end = lastDay.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        } else {
            start = firstDay.atTime(MinuteOfDay.toTime(event.startMinute!!)).atZone(zone).toInstant().toEpochMilli()
            end = event.endMinute?.let { lastDay.atTime(MinuteOfDay.toTime(it)).atZone(zone).toInstant().toEpochMilli() }
                ?.takeIf { it > start } ?: (start + Duration.ofMinutes(DEFAULT_LENGTH_MINUTES).toMillis())
        }
        val rrule = rrule(event, zone)
        val length = Duration.ofMillis(end - start)
        return PhoneEventFields(
            title = event.title,
            description = event.note,
            dtStart = start,
            dtEnd = if (rrule == null) end else null,
            duration = rrule?.let { if (allDay) "P${length.toDays()}D" else "PT${length.toMinutes()}M" },
            allDay = allDay,
            timeZone = if (allDay) "UTC" else zone.id,
            rrule = rrule,
        )
    }

    /** "FREQ=WEEKLY;BYDAY=MO,WE;UNTIL=…", or null for a one-off. */
    fun rrule(event: EventEntity, zone: ZoneId): String? {
        val start = LocalDate.ofEpochDay(event.startEpochDay)
        val rule = when (event.repeatKind) {
            RepeatKind.NONE -> return null
            RepeatKind.WEEKLY -> {
                val days = WeekdayMask.toSet(event.repeatDays).ifEmpty { setOf(start.dayOfWeek) }
                "FREQ=WEEKLY;BYDAY=" + DayOfWeek.entries.filter { it in days }.joinToString(",") { it.name.take(2) }
            }
            RepeatKind.MONTHLY -> "FREQ=MONTHLY;BYMONTHDAY=${start.dayOfMonth}"
            RepeatKind.YEARLY -> "FREQ=YEARLY;BYMONTH=${start.monthValue};BYMONTHDAY=${start.dayOfMonth}"
        }
        val until = event.untilEpochDay?.let { day ->
            // The last moment of the last day, in UTC basic form, as RFC 5545 wants.
            val last = LocalDate.ofEpochDay(day).plusDays(1).atStartOfDay(zone).toInstant().minusSeconds(1)
            ";UNTIL=" + UNTIL_FORMAT.format(last)
        }.orEmpty()
        return rule + until
    }

    /**
     * Phone [instances] as calendar entries, by day, from [from] to [to]. Leaves out Relay's own
     * copies ([hidden] event ids), so an item doesn't show twice. Multi-day ones span, like Relay's.
     */
    fun entries(
        instances: List<PhoneInstance>,
        hidden: Set<Long>,
        from: LocalDate,
        to: LocalDate,
        zone: ZoneId,
    ): Map<LocalDate, List<CalendarEntry>> = instances.filter { it.eventId !in hidden }.flatMap { i ->
        // All-day instances run UTC midnight to midnight; the end is exclusive.
        val startZone = if (i.allDay) ZoneOffset.UTC else zone
        val begin = Instant.ofEpochMilli(i.beginMillis).atZone(startZone)
        val last = Instant.ofEpochMilli((i.endMillis - 1).coerceAtLeast(i.beginMillis)).atZone(startZone)
        val firstDay = begin.toLocalDate()
        val lastDay = last.toLocalDate()
        val multi = lastDay.isAfter(firstDay)
        generateSequence(maxOf(firstDay, from)) { it.plusDays(1) }.takeWhile { !it.isAfter(minOf(lastDay, to)) }.map { day ->
            day to CalendarEntry(
                kind = EntryKind.PHONE,
                id = i.eventId.toString(),
                title = i.title,
                startMinute = if (i.allDay) null else MinuteOfDay.of(begin.toLocalTime()),
                endMinute = if (i.allDay) null else MinuteOfDay.of(Instant.ofEpochMilli(i.endMillis).atZone(zone).toLocalTime()),
                continued = day != firstDay,
                calendarId = phoneCalendarKey(i.calendarId),
                color = i.color,
                spanStart = if (multi) firstDay else null,
                spanEnd = if (multi) lastDay else null,
            )
        }.toList()
    }.groupBy({ it.first }, { it.second })

    private val UNTIL_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
}
