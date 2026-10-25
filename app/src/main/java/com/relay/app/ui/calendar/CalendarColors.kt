package com.relay.app.ui.calendar

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.relay.app.R
import com.relay.app.data.event.ALARMS_CALENDAR
import com.relay.app.data.event.BIRTHDAYS_CALENDAR
import com.relay.app.data.event.CalendarEntity
import com.relay.app.data.event.PERSONAL_CALENDAR

/**
 * Calendar colours (#34): twelve that all carry white text at 4.5:1 or better, so labels in
 * the grid and the widgets stay readable. A custom colour is allowed too.
 */
val CALENDAR_PALETTE = listOf(
    Color(0xFF1F6FB0), Color(0xFF4A4FB0), Color(0xFF7B3FA0), Color(0xFFA0306B),
    Color(0xFFB3261E), Color(0xFFC7501E), Color(0xFF8A5A00), Color(0xFF6B6E1F),
    Color(0xFF2F7A3A), Color(0xFF2E6F66), Color(0xFF7A6A4F), Color(0xFF5C5C5C),
)

/** A calendar's name: its own, or for the three made by the update, the translated one. */
fun calendarName(context: Context, calendar: CalendarEntity): String = calendar.name.ifBlank {
    when (calendar.id) {
        PERSONAL_CALENDAR -> context.getString(R.string.calendar_personal)
        BIRTHDAYS_CALENDAR -> context.getString(R.string.calendar_birthdays)
        ALARMS_CALENDAR -> context.getString(R.string.calendar_alarms)
        else -> context.getString(R.string.calendar_untitled)
    }
}

@Composable
fun calendarName(calendar: CalendarEntity): String = calendarName(LocalContext.current, calendar)
