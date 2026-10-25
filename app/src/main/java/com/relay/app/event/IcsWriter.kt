package com.relay.app.event

import com.relay.app.data.event.EventEntity
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Writes calendar files (RFC 5545, #33), for export through the share sheet. [Ics] reads them. */
object IcsWriter {
    private const val FOLD_AT = 74
    private val UTC_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    private fun escape(text: String): String = text.replace("\\", "\\\\").replace(";", "\\;")
        .replace(",", "\\,").replace("\n", "\\n")

    /**
     * [events] as a calendar file. Each keeps its UID (or gets one from its id), so importing the
     * file again updates rather than duplicates; [leads] gives each item's reminder, if any.
     */
    fun write(events: List<EventEntity>, leads: Map<String, Int>, zone: ZoneId, now: Instant): String {
        val out = mutableListOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Relay//Calendar//EN", "CALSCALE:GREGORIAN")
        for (e in events) {
            val f = PhoneEvents.fields(e, zone)
            out += "BEGIN:VEVENT"
            out += "UID:" + (e.icsUid ?: "${e.id}@relay")
            out += "DTSTAMP:" + UTC_TIME.format(now)
            out += "SUMMARY:" + escape(e.title)
            if (e.note.isNotBlank()) out += "DESCRIPTION:" + escape(e.note)
            if (f.allDay) {
                out += "DTSTART;VALUE=DATE:" + Ics.BASIC_DATE.format(LocalDate.ofEpochDay(e.startEpochDay))
                out += "DTEND;VALUE=DATE:" + Ics.BASIC_DATE.format(LocalDate.ofEpochDay(e.endEpochDay ?: e.startEpochDay).plusDays(1))
            } else {
                out += "DTSTART;TZID=${zone.id}:" + Ics.BASIC_TIME.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(f.dtStart), zone))
                val endMillis = f.dtEnd ?: (f.dtStart + Duration.parse(f.duration ?: "PT1H").toMillis())
                out += "DTEND;TZID=${zone.id}:" + Ics.BASIC_TIME.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(endMillis), zone))
            }
            f.rrule?.let { out += "RRULE:$it" }
            leads[e.id]?.let { lead ->
                out += listOf("BEGIN:VALARM", "ACTION:DISPLAY", "DESCRIPTION:" + escape(e.title), "TRIGGER:-PT${lead}M", "END:VALARM")
            }
            out += "END:VEVENT"
        }
        out += "END:VCALENDAR"
        return out.joinToString("\r\n", postfix = "\r\n") { fold(it) }
    }

    /** RFC 5545 lines are folded at 75 octets; characters are close enough for these. */
    private fun fold(line: String): String = if (line.length <= FOLD_AT) line else
        line.chunked(FOLD_AT).joinToString("\r\n ")
}
