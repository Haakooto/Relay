package com.relay.app.schedule

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/** How often a rule's periods come round. */
enum class Freq { DAILY, WEEKLY, MONTHLY, YEARLY }

/** A weekday, or the nth (1 to 5) or nth from the end (-1 to -5) of it in its month or year. */
data class WeekdayNum(val day: DayOfWeek, val ordinal: Int? = null)

/**
 * A repeat rule (#43): the part of RFC 5545's RRULE that calendars actually use. Anything outside
 * it (hourly, by week number, by day of year, BYSETPOS) doesn't parse, so the caller can fall back.
 * UNTIL lives beside the rule (an item's last day), not in it, so [format] leaves it out.
 */
data class Rrule(
    val freq: Freq,
    val interval: Int = 1,
    val count: Int? = null,
    val byDay: List<WeekdayNum> = emptyList(),
    val byMonthDay: List<Int> = emptyList(),
    val byMonth: List<Int> = emptyList(),
) {
    /** "FREQ=MONTHLY;BYDAY=2TU", without UNTIL. */
    fun format(): String = buildList {
        add("FREQ=${freq.name}")
        if (interval != 1) add("INTERVAL=$interval")
        count?.let { add("COUNT=$it") }
        if (byMonth.isNotEmpty()) add("BYMONTH=" + byMonth.joinToString(","))
        if (byMonthDay.isNotEmpty()) add("BYMONTHDAY=" + byMonthDay.joinToString(","))
        if (byDay.isNotEmpty()) add("BYDAY=" + byDay.joinToString(",") { (it.ordinal?.toString() ?: "") + DAY_CODES.getValue(it.day) })
    }.joinToString(";")

    companion object {
        private val DAY_CODES = mapOf(
            DayOfWeek.MONDAY to "MO", DayOfWeek.TUESDAY to "TU", DayOfWeek.WEDNESDAY to "WE", DayOfWeek.THURSDAY to "TH",
            DayOfWeek.FRIDAY to "FR", DayOfWeek.SATURDAY to "SA", DayOfWeek.SUNDAY to "SU",
        )
        private val BY_CODE = DAY_CODES.entries.associate { (day, code) -> code to day }
        private val WEEKDAY_NUM = Regex("^([+-]?[1-5])?(MO|TU|WE|TH|FR|SA|SU)$")
        private const val MAX_INTERVAL = 1000
        private const val MAX_COUNT = 10_000
        private const val MONTHS = 12
        private const val MAX_MONTH_DAY = 31

        /**
         * The rule in [text] ("RRULE:" prefix optional), and its UNTIL as a local last day in
         * [zone]; null when it uses anything outside the supported set or is malformed.
         */
        fun parse(text: String, zone: ZoneId = ZoneOffset.UTC): Pair<Rrule, LocalDate?>? = runCatching {
            val parts = text.trim().removePrefix("RRULE:").split(';').filter { it.isNotBlank() }.associate { part ->
                val (key, value) = part.split('=', limit = 2).let { it[0].uppercase() to it.getOrElse(1) { "" }.uppercase() }
                key to value
            }
            val freq = Freq.valueOf(parts["FREQ"] ?: return null)
            // WKST only moves where weeks start, which matters for weekly rules every 2+ weeks; Monday is assumed.
            if (parts.keys.any { it !in KNOWN }) return null
            val interval = parts["INTERVAL"]?.toInt() ?: 1
            val count = parts["COUNT"]?.toInt()
            if (interval !in 1..MAX_INTERVAL || (count != null && count !in 1..MAX_COUNT)) return null
            val byDay = parts["BYDAY"]?.split(',')?.map { code ->
                val m = WEEKDAY_NUM.matchEntire(code) ?: return null
                WeekdayNum(BY_CODE.getValue(m.groupValues[2]), m.groupValues[1].takeIf { it.isNotEmpty() }?.toInt())
            }.orEmpty()
            val byMonthDay = parts["BYMONTHDAY"]?.split(',')?.map { it.toInt() }.orEmpty()
            val byMonth = parts["BYMONTH"]?.split(',')?.map { it.toInt() }.orEmpty()
            if (byMonthDay.any { it == 0 || it !in -MAX_MONTH_DAY..MAX_MONTH_DAY } || byMonth.any { it !in 1..MONTHS }) return null
            Rrule(freq, interval, count, byDay, byMonthDay, byMonth) to parts["UNTIL"]?.let { untilDay(it, zone) }
        }.getOrNull()

        private val KNOWN = setOf("FREQ", "INTERVAL", "COUNT", "UNTIL", "BYDAY", "BYMONTHDAY", "BYMONTH", "WKST")

        /** UNTIL as a date ("20261231") or a time, UTC with Z or floating ("20261231T225959Z"). */
        private fun untilDay(text: String, zone: ZoneId): LocalDate {
            if (text.length == DATE_LENGTH) return LocalDate.parse(text, BASIC_DATE)
            val time = LocalDateTime.parse(text.removeSuffix("Z"), BASIC_TIME)
            return if (text.endsWith("Z")) time.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDate() else time.toLocalDate()
        }

        private const val DATE_LENGTH = 8
        private val BASIC_DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val BASIC_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    }
}

/**
 * The dates a rule gives (#43), as RFC 5545 expands them. One evaluator for the calendar grid,
 * the widgets and reminder schedules, so they always agree.
 */
object RruleDates {
    /** A guard against a rule that never lands (BYMONTHDAY=31 in February only). */
    private const val MAX_PERIODS = 20_000

    /**
     * Every date of [rule] from [start] on, in order, up to [until] and to [last], and no more than
     * its COUNT (counted from [start], so iterate from there even when only later dates are wanted).
     */
    fun dates(rule: Rrule, start: LocalDate, until: LocalDate?, last: LocalDate): Sequence<LocalDate> = sequence {
        var produced = 0
        for (period in 0 until MAX_PERIODS) {
            val step = period.toLong() * rule.interval
            if (periodStart(rule.freq, start, step).isAfter(last)) return@sequence
            for (day in candidates(rule, start, step)) {
                if (day.isBefore(start)) continue
                if ((until != null && day.isAfter(until)) || day.isAfter(last)) return@sequence
                yield(day)
                produced++
                if (rule.count != null && produced >= rule.count) return@sequence
            }
        }
    }

    private fun periodStart(freq: Freq, start: LocalDate, step: Long): LocalDate = when (freq) {
        Freq.DAILY -> start.plusDays(step)
        Freq.WEEKLY -> start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(step)
        Freq.MONTHLY -> start.withDayOfMonth(1).plusMonths(step)
        Freq.YEARLY -> start.withDayOfYear(1).plusYears(step)
    }

    /** The period's dates, sorted: its expansions, then its limits (BYMONTH, BYDAY and BYMONTHDAY as filters). */
    private fun candidates(rule: Rrule, start: LocalDate, step: Long): List<LocalDate> = when (rule.freq) {
        Freq.DAILY -> listOf(start.plusDays(step)).filter { matchesLimits(rule, it) }
        Freq.WEEKLY -> {
            val monday = periodStart(Freq.WEEKLY, start, step)
            val days = rule.byDay.map { it.day }.ifEmpty { listOf(start.dayOfWeek) }
            days.map { monday.plusDays((it.value - 1).toLong()) }.filter { rule.byMonth.isEmpty() || it.monthValue in rule.byMonth }
        }
        Freq.MONTHLY -> {
            val month = YearMonth.from(start).plusMonths(step)
            if (rule.byMonth.isNotEmpty() && month.monthValue !in rule.byMonth) emptyList() else inMonth(rule, start, month)
        }
        Freq.YEARLY -> inYear(rule, start, start.year + step.toInt())
    }.distinct().sorted()

    private fun matchesLimits(rule: Rrule, day: LocalDate): Boolean =
        (rule.byMonth.isEmpty() || day.monthValue in rule.byMonth) &&
            (rule.byMonthDay.isEmpty() || rule.byMonthDay.any { resolve(YearMonth.from(day), it) == day.dayOfMonth }) &&
            (rule.byDay.isEmpty() || rule.byDay.any { it.day == day.dayOfWeek })

    /** A month's dates: by month day, or by weekday (nth, last, or every), or the start's own day. */
    private fun inMonth(rule: Rrule, start: LocalDate, month: YearMonth): List<LocalDate> = when {
        rule.byMonthDay.isNotEmpty() -> rule.byMonthDay.mapNotNull { resolve(month, it) }.map { month.atDay(it) }
            .filter { day -> rule.byDay.isEmpty() || rule.byDay.any { it.day == day.dayOfWeek } }
        rule.byDay.isNotEmpty() -> rule.byDay.flatMap { weekdays(month.atDay(1), month.atEndOfMonth(), it) }
        else -> listOfNotNull(start.dayOfMonth.takeIf { it <= month.lengthOfMonth() }?.let { month.atDay(it) })
    }

    private fun inYear(rule: Rrule, start: LocalDate, year: Int): List<LocalDate> {
        val months = rule.byMonth.map { YearMonth.of(year, it) }
        return when {
            months.isNotEmpty() -> months.flatMap { inMonth(rule, start, it) }
            // RFC 5545: BYMONTHDAY in a yearly rule without BYMONTH means every month.
            rule.byMonthDay.isNotEmpty() -> Month.entries.flatMap { inMonth(rule, start, YearMonth.of(year, it)) }
            // Weekdays counted through the whole year ("20MO", or every Monday).
            rule.byDay.isNotEmpty() -> {
                val first = LocalDate.of(year, Month.JANUARY, 1)
                rule.byDay.flatMap { weekdays(first, first.with(TemporalAdjusters.lastDayOfYear()), it) }
            }
            else -> listOfNotNull(runCatching { LocalDate.of(year, start.monthValue, start.dayOfMonth) }.getOrNull())
        }
    }

    /** A month day, counting from the end when negative; null when the month hasn't got it. */
    private fun resolve(month: YearMonth, day: Int): Int? {
        val length = month.lengthOfMonth()
        val resolved = if (day > 0) day else length + 1 + day
        return resolved.takeIf { it in 1..length }
    }

    /** Every [w] weekday from [first] to [last], or just its nth (from the end when negative). */
    private fun weekdays(first: LocalDate, last: LocalDate, w: WeekdayNum): List<LocalDate> {
        val all = generateSequence(first.with(TemporalAdjusters.nextOrSame(w.day))) { it.plusWeeks(1) }
            .takeWhile { !it.isAfter(last) }.toList()
        val n = w.ordinal ?: return all
        return listOfNotNull(if (n > 0) all.getOrNull(n - 1) else all.getOrNull(all.size + n))
    }
}
