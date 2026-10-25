package com.relay.app.ui.calendar

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.relay.app.R
import com.relay.app.event.ALL_DAY_REMINDER_MINUTE
import com.relay.app.event.CalendarEntry
import com.relay.app.util.AppLocale
import com.relay.app.util.MinuteOfDay
import com.relay.app.util.TimeText
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Text shared by the Calendar pane and the item editor. */
object CalendarText {
    /** "October 2026", in the standalone form some languages need for a month on its own. */
    fun month(m: YearMonth): String =
        m.format(DateTimeFormatter.ofPattern("LLLL yyyy", AppLocale.formatting)).replaceFirstChar { it.uppercase(AppLocale.formatting) }

    fun time(minute: Int, is24h: Boolean): String = TimeText.of(MinuteOfDay.toTime(minute), is24h)

    /**
     * An entry's when: "10:00", "10:00–11:00" or "All day"; a multi-day one (#35) as its whole
     * range, "Mon 10 – Wed 12" or "Mon 10 10:00 – Wed 12 12:00". Shared by the pane and widgets.
     */
    fun entryTime(context: Context, entry: CalendarEntry, is24h: Boolean): String {
        val start = entry.spanStart
        val end = entry.spanEnd
        if (start == null || end == null) {
            return entry.startMinute?.let { time(it, is24h) + (entry.endMinute?.let { e -> "–" + time(e, is24h) } ?: "") }
                ?: context.getString(R.string.calendar_all_day)
        }
        val day = DateTimeFormatter.ofPattern("EEE d", AppLocale.formatting)
        val from = start.format(day) + (entry.startMinute?.let { " " + time(it, is24h) } ?: "")
        val to = end.format(day) + (entry.endMinute?.let { " " + time(it, is24h) } ?: "")
        return "$from – $to"
    }

    /** "10:00", "10:00–11:00"; null start = all day. */
    @Composable
    fun span(startMinute: Int?, endMinute: Int?, is24h: Boolean): String = when (startMinute) {
        null -> stringResource(R.string.calendar_all_day)
        else -> time(startMinute, is24h) + (endMinute?.let { "–" + time(it, is24h) } ?: "")
    }

    /** A lead time as a choice: "15 min before", "09:00 the day before". */
    @Composable
    fun lead(leadMinutes: Int, allDay: Boolean, is24h: Boolean): String {
        if (allDay) {
            val at = time(ALL_DAY_REMINDER_MINUTE, is24h)
            return stringResource(if (leadMinutes >= DAY) R.string.lead_the_day_before else R.string.lead_on_the_day, at)
        }
        return when (leadMinutes) {
            0 -> stringResource(R.string.lead_at_start)
            HOUR -> stringResource(R.string.lead_hour_before)
            DAY -> stringResource(R.string.lead_day_before)
            else -> stringResource(R.string.lead_minutes_before, leadMinutes)
        }
    }

    private const val HOUR = 60
    private const val DAY = 24 * 60
}
