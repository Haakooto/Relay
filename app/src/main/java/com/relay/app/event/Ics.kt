package com.relay.app.event

import com.relay.app.data.event.EventEntity
import com.relay.app.data.event.EventKind
import com.relay.app.data.event.RepeatKind
import com.relay.app.schedule.Rrule
import com.relay.app.util.MinuteOfDay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** One event read from a calendar file (#33), already in the phone's own time zone. */
data class IcsEvent(
    val uid: String?,
    val title: String,
    val note: String,
    val startDay: LocalDate,
    /** Minutes after midnight; null = all day. */
    val startMinute: Int?,
    /** Last day, inclusive; null = the start day. */
    val endDay: LocalDate?,
    val endMinute: Int?,
    /** The RRULE as it came, or null. */
    val rule: String?,
    /** Minutes before the start of its first reminder (VALARM), if it has one. */
    val alarmLead: Int?,
)

/** What a calendar file held: its events, and how many were left out (cancelled, unreadable, over the limit). */
data class IcsFile(val events: List<IcsEvent>, val skipped: Int)

/**
 * Calendar files (RFC 5545, #33), read and written. Our own small reader rather than a library
 * (principles §4.7). A file from outside is untrusted (§6.6): it's capped in size, lines and
 * events, and anything malformed is skipped, never thrown.
 */
object Ics {
    const val MAX_BYTES = 2 * 1024 * 1024
    const val MAX_EVENTS = 5000
    private const val MAX_LINE = 10_000

    /** A content line: name, parameters and value, unfolded. */
    private data class Line(val name: String, val params: Map<String, String>, val value: String)

    fun read(text: String, zone: ZoneId): IcsFile {
        val blocks = blocks(text)
        val events = blocks.take(MAX_EVENTS * 2).mapNotNull { runCatching { event(it, zone) }.getOrNull() }.take(MAX_EVENTS).toList()
        return IcsFile(events, skipped = blocks.count() - events.size)
    }

    /** Each VEVENT's lines; a nested block's (VALARM) are tagged "ALARM." so the event can find its first reminder. */
    private fun blocks(text: String): Sequence<List<Line>> = sequence {
        var block: MutableList<Line>? = null
        var depth = 0
        for (line in lines(text)) {
            val isEvent = line.value.equals("VEVENT", ignoreCase = true)
            when {
                line.name == "BEGIN" && isEvent && block == null -> { block = mutableListOf(); depth = 0 }
                line.name == "END" && isEvent && block != null -> { yield(block); block = null }
                block != null -> {
                    if (line.name == "BEGIN") depth++
                    if (line.name == "END") depth--
                    block += if (depth > 0 && line.name != "BEGIN") line.copy(name = "ALARM." + line.name) else line
                }
            }
        }
    }

    /** Unfolds continuation lines and splits each into name, parameters and value. */
    private fun lines(text: String): Sequence<Line> = sequence {
        val unfolded = StringBuilder()
        fun flush(): Line? {
            if (unfolded.isEmpty()) return null
            val raw = unfolded.toString()
            unfolded.clear()
            if (raw.length > MAX_LINE) return null
            return parse(raw)
        }
        for (physical in text.lineSequence()) {
            if (physical.startsWith(" ") || physical.startsWith("\t")) {
                if (unfolded.length < MAX_LINE) unfolded.append(physical, 1, physical.length)
            } else {
                flush()?.let { yield(it) }
                unfolded.append(physical.trimEnd('\r'))
            }
        }
        flush()?.let { yield(it) }
    }

    private fun parse(raw: String): Line? {
        val colon = valueStart(raw) ?: return null
        val head = raw.substring(0, colon).split(';')
        val params = head.drop(1).mapNotNull { p ->
            p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].uppercase() to it[1].trim('"') }
        }
        return Line(head[0].uppercase(), params.toMap(), raw.substring(colon + 1))
    }

    /** The colon that starts the value: the first one outside a quoted parameter. */
    private fun valueStart(raw: String): Int? {
        var quoted = false
        raw.forEachIndexed { i, c ->
            if (c == '"') quoted = !quoted
            if (c == ':' && !quoted) return i
        }
        return null
    }

    private fun event(lines: List<Line>, zone: ZoneId): IcsEvent? {
        fun first(name: String) = lines.firstOrNull { it.name == name }
        if (first("STATUS")?.value?.uppercase() == "CANCELLED") return null
        val start = first("DTSTART")?.let { time(it, zone) } ?: return null
        val end = first("DTEND")?.let { time(it, zone) }
            ?: first("DURATION")?.let { d -> duration(d.value)?.let { start.plus(it, zone) } }
        val last = lastOf(start, end)
        return IcsEvent(
            uid = first("UID")?.value?.take(MAX_UID),
            title = unescape(first("SUMMARY")?.value.orEmpty()).take(MAX_TEXT),
            note = unescape(first("DESCRIPTION")?.value.orEmpty()).take(MAX_TEXT),
            startDay = start.day,
            startMinute = start.minute,
            endDay = last.day.takeIf { it.isAfter(start.day) },
            endMinute = last.minute,
            rule = first("RRULE")?.value,
            alarmLead = first("ALARM.TRIGGER")?.let(::lead),
        )
    }

    /**
     * Where an event ends, as a last day and its end minute (null = no end time). An all-day end
     * is exclusive, the day after the last; so is a timed end at midnight.
     */
    private fun lastOf(start: When, end: When?): When {
        if (end == null || start.allDay) return When(end?.day?.minusDays(1) ?: start.day, null, null)
        if (end.allDay) return When(end.day.minusDays(1), null, null)
        val atMidnight = end.minute == 0 && end.day.isAfter(start.day)
        return if (atMidnight) When(end.day.minusDays(1), null, null) else end
    }

    /** A reminder trigger relative to the start ("-PT15M") as minutes before it; an absolute one is ignored. */
    private fun lead(trigger: Line): Int? = trigger.takeIf { it.params["VALUE"] != "DATE-TIME" }
        ?.let { duration(it.value) }?.let { -it.toMinutes().toInt() }?.takeIf { it >= 0 }

    /** A date or time as a local day and minute; all-day for a plain date. */
    private data class When(val day: LocalDate, val minute: Int?, val instant: Instant?) {
        val allDay get() = minute == null

        /** [d] later: whole days for an all-day date, else the instant moved and seen in [zone]. */
        fun plus(d: Duration, zone: ZoneId): When {
            val at = instant ?: return When(day.plusDays(d.toDays()), null, null)
            val local = at.plus(d).atZone(zone)
            return When(local.toLocalDate(), MinuteOfDay.of(local.toLocalTime()), at.plus(d))
        }
    }

    private fun time(line: Line, zone: ZoneId): When? {
        val v = line.value.trim()
        if (line.params["VALUE"] == "DATE" || v.length == DATE_LENGTH) return When(LocalDate.parse(v, BASIC_DATE), null, null)
        val local = LocalDateTime.parse(v.removeSuffix("Z"), BASIC_TIME)
        val source = when {
            v.endsWith("Z") -> ZoneOffset.UTC
            else -> line.params["TZID"]?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: zone
        }
        val here = local.atZone(source).withZoneSameInstant(zone)
        return When(here.toLocalDate(), MinuteOfDay.of(here.toLocalTime()), here.toInstant())
    }

    /** "PT1H30M", "-P1D", "P2W"; null if it isn't one. */
    private fun duration(text: String): Duration? = runCatching {
        val t = text.trim()
        val negative = t.startsWith("-")
        val body = t.trimStart('-', '+')
        val weeks = Regex("^P(\\d+)W$").find(body)?.groupValues?.get(1)?.toLong()
        val d = if (weeks != null) Duration.ofDays(weeks * DAYS_PER_WEEK) else Duration.parse(body)
        if (negative) d.negated() else d
    }.getOrNull()

    private fun unescape(text: String): String = text.replace("\\n", "\n").replace("\\N", "\n")
        .replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

    /**
     * [e] as an item in [calendarId] with [id]. A repeat Relay can express is kept (simple ones as
     * the old fields); one it can't becomes the first date only, with [unsupported] saying what the
     * file asked for added to the note, so nothing is silently lost.
     */
    fun toEntity(
        e: IcsEvent,
        calendarId: String,
        id: String,
        createdAtMillis: Long,
        zone: ZoneId,
        unsupported: (String) -> String,
    ): EventEntity {
        val parsed = e.rule?.let { Rrule.parse(it, zone) }
        val repeat = parsed?.let { (rule, until) ->
            Repeats.store(Repeats.load(RepeatKind.CUSTOM, 0, rule.format(), until, e.startDay), e.startDay)
        }
        val note = if (e.rule != null && parsed == null) {
            listOf(e.note, unsupported(e.rule)).filter { it.isNotBlank() }.joinToString("\n\n")
        } else {
            e.note
        }
        return EventEntity(
            id = id,
            kind = EventKind.EVENT,
            title = e.title,
            note = note,
            startEpochDay = e.startDay.toEpochDay(),
            startMinute = e.startMinute,
            endEpochDay = e.endDay?.toEpochDay(),
            endMinute = e.endMinute,
            repeatKind = repeat?.kind ?: RepeatKind.NONE,
            repeatDays = repeat?.days ?: 0,
            untilEpochDay = repeat?.until?.toEpochDay(),
            createdAtMillis = createdAtMillis,
            calendarId = calendarId,
            repeatRule = repeat?.rule,
            icsUid = e.uid,
        )
    }

    private const val DATE_LENGTH = 8
    private const val DAYS_PER_WEEK = 7L
    private const val MAX_UID = 255
    private const val MAX_TEXT = 4000
    internal val BASIC_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    internal val BASIC_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
}
