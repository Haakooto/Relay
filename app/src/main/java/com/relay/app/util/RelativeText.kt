package com.relay.app.util

import android.content.Context
import com.relay.app.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

object RelativeText {
    /** "8 h 12 min" style, for "rings in ...". Rounds up so it never says 0 min too early. */
    fun until(context: Context, millis: Long): String {
        val totalMinutes = ((millis + 59_999) / 60_000).coerceAtLeast(1)
        val days = totalMinutes / (24 * 60)
        val hours = (totalMinutes / 60) % 24
        val minutes = totalMinutes % 60
        return when {
            days > 0 -> context.getString(R.string.duration_days_hours, days, hours)
            hours > 0 -> context.getString(R.string.duration_hours_minutes, hours, minutes)
            else -> context.getString(R.string.duration_minutes, minutes)
        }
    }

    /** Weekday set as text, in the user's week order: "Every day", "Weekdays", or "Mon, Wed". */
    fun days(context: Context, days: Set<DayOfWeek>, firstDay: DayOfWeek): String = when {
        days.size == 7 -> context.getString(R.string.repeat_every_day)
        days == setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY) ->
            context.getString(R.string.repeat_weekdays)
        days == setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> context.getString(R.string.repeat_weekends)
        else -> weekOrder(firstDay).filter { it in days }.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, AppLocale.formatting) }
    }

    fun weekOrder(firstDay: DayOfWeek): List<DayOfWeek> = (0L until 7L).map { firstDay.plus(it) }

    /** "Today", "Tomorrow", or a medium date. */
    fun date(context: Context, date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
        today -> context.getString(R.string.date_today)
        today.plusDays(1) -> context.getString(R.string.date_tomorrow)
        else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(AppLocale.formatting))
    }
}
