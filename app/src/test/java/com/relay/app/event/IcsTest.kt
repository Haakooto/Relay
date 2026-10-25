package com.relay.app.event

import com.relay.app.data.event.EventEntity
import com.relay.app.data.event.EventKind
import com.relay.app.data.event.RepeatKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class IcsTest {
    private val zone = ZoneId.of("Europe/Oslo")
    private fun d(text: String) = LocalDate.parse(text)

    private fun file(vararg events: String) =
        (listOf("BEGIN:VCALENDAR", "VERSION:2.0") + events.flatMap { listOf("BEGIN:VEVENT") + it.trimIndent().lines() + "END:VEVENT" } +
            "END:VCALENDAR").joinToString("\r\n")

    private fun one(event: String) = Ics.read(file(event), zone).events.single()

    @Test fun timedEventInUtcBecomesLocal() {
        val e = one(
            """
            UID:abc
            SUMMARY:Dentist
            DTSTART:20261013T080000Z
            DTEND:20261013T090000Z
            """,
        )
        assertEquals("abc", e.uid)
        assertEquals(d("2026-10-13"), e.startDay)
        assertEquals(10 * 60, e.startMinute) // CEST is UTC+2
        assertEquals(11 * 60, e.endMinute)
        assertNull(e.endDay)
    }

    @Test fun tzidIsHonoured() =
        assertEquals(15 * 60, one("DTSTART;TZID=America/New_York:20261013T090000\nSUMMARY:Call").startMinute)

    @Test fun allDayEndIsExclusive() {
        val e = one("DTSTART;VALUE=DATE:20261031\nDTEND;VALUE=DATE:20261102\nSUMMARY:Trip")
        assertNull(e.startMinute)
        assertEquals(d("2026-11-01"), e.endDay)
    }

    @Test fun aOneDayAllDayEventHasNoEndDay() =
        assertNull(one("DTSTART;VALUE=DATE:20261031\nDTEND;VALUE=DATE:20261101\nSUMMARY:Holiday").endDay)

    @Test fun durationGivesTheEnd() =
        assertEquals(10 * 60 + 30, one("DTSTART;TZID=Europe/Oslo:20261013T100000\nDURATION:PT30M\nSUMMARY:x").endMinute)

    @Test fun foldedLinesAndEscapesAreUndone() {
        val e =
            one("SUMMARY:Lunch\\, then a walk\r\nDESCRIPTION:Line one\\nline\r\n  two\r\nDTSTART;VALUE=DATE:20261013")
        assertEquals("Lunch, then a walk", e.title)
        assertEquals("Line one\nline two", e.note)
    }

    @Test fun firstAlarmIsTheLead() =
        assertEquals(15, one("DTSTART:20261013T080000Z\nSUMMARY:x\nBEGIN:VALARM\nTRIGGER:-PT15M\nACTION:DISPLAY\nEND:VALARM").alarmLead)

    @Test fun cancelledAndBrokenEventsAreSkipped() {
        val text =
            file("STATUS:CANCELLED\nDTSTART:20261013T080000Z", "SUMMARY:no start", "DTSTART;VALUE=DATE:20261013\nSUMMARY:ok")
        val result = Ics.read(text, zone)
        assertEquals(listOf("ok"), result.events.map { it.title })
        assertEquals(2, result.skipped)
    }

    @Test fun repeatsRelayCanShowStaySimpleOthersBecomeRules() {
        fun entity(rule: String) = Ics.toEntity(
            one("DTSTART;VALUE=DATE:20261013\nSUMMARY:x\nRRULE:$rule"), "personal", "id", 0, zone,
        ) { "not kept: $it" }
        assertEquals(RepeatKind.YEARLY, entity("FREQ=YEARLY").repeatKind)
        val secondTuesday = entity("FREQ=MONTHLY;BYDAY=2TU;UNTIL=20261231")
        assertEquals(RepeatKind.CUSTOM, secondTuesday.repeatKind)
        assertEquals("FREQ=MONTHLY;BYDAY=2TU", secondTuesday.repeatRule)
        assertEquals(d("2026-12-31").toEpochDay(), secondTuesday.untilEpochDay)
    }

    @Test fun aRepeatOutsideTheSetIsTheFirstDateWithANote() {
        val hourly = one("DTSTART;VALUE=DATE:20261013\nSUMMARY:x\nRRULE:FREQ=HOURLY")
        val e = Ics.toEntity(hourly, "personal", "id", 0, zone) { "not kept: $it" }
        assertEquals(RepeatKind.NONE, e.repeatKind)
        assertTrue(e.note.contains("not kept: FREQ=HOURLY"))
    }

    @Test fun writtenFilesReadBack() {
        val item = EventEntity(
            "id1", EventKind.EVENT, "Lunch, then a walk", "Bring\nshoes", d("2026-10-13").toEpochDay(), 12 * 60, null, 13 * 60,
            RepeatKind.CUSTOM, 0, null, 0, repeatRule = "FREQ=MONTHLY;BYDAY=2TU", icsUid = "uid-1",
        )
        val text = IcsWriter.write(listOf(item), mapOf("id1" to 15), zone, Instant.EPOCH)
        val back = Ics.read(text, zone).events.single()
        assertEquals("uid-1", back.uid)
        assertEquals("Lunch, then a walk", back.title)
        assertEquals("Bring\nshoes", back.note)
        assertEquals(12 * 60, back.startMinute)
        assertEquals(13 * 60, back.endMinute)
        assertEquals("FREQ=MONTHLY;BYDAY=2TU", back.rule)
        assertEquals(15, back.alarmLead)
        assertTrue(text.lines().all { it.length <= 75 })
    }

    @Test fun tooManyEventsAreCapped() {
        val many = (1..Ics.MAX_EVENTS + 3).map { "DTSTART;VALUE=DATE:20261013\nSUMMARY:e$it" }.toTypedArray()
        val result = Ics.read(file(*many), zone)
        assertEquals(Ics.MAX_EVENTS, result.events.size)
        assertEquals(3, result.skipped)
    }
}
