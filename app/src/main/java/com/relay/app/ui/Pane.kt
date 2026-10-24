package com.relay.app.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.ui.graphics.vector.ImageVector
import com.relay.app.R

/**
 * The five panes in the bottom bar (spec §01, §10b). [route] is a fixed nav key, never shown to
 * the user. Settings isn't one: it opens from the gear in each pane's header.
 */
enum class Pane(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    ALARMS("alarms", R.string.pane_alarms, Icons.Outlined.Alarm),
    CALENDAR("calendar", R.string.pane_calendar, Icons.Outlined.CalendarMonth),
    TIMERS("timers", R.string.pane_timers, Icons.Outlined.HourglassBottom),
    STOPWATCH("stopwatch", R.string.pane_stopwatch, Icons.Outlined.Timer),
    CLOCK("clock", R.string.pane_clock, Icons.Outlined.Schedule),
}
