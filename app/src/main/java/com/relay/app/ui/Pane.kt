package com.relay.app.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.ui.graphics.vector.ImageVector
import com.relay.app.R

/** The five top-level panes (spec §01). [route] is a fixed nav key, never shown to the user. */
enum class Pane(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    ALARMS("alarms", R.string.pane_alarms, Icons.Outlined.Alarm),
    TIMERS("timers", R.string.pane_timers, Icons.Outlined.HourglassBottom),
    STOPWATCH("stopwatch", R.string.pane_stopwatch, Icons.Outlined.Timer),
    CLOCK("clock", R.string.pane_clock, Icons.Outlined.Schedule),
    SETTINGS("settings", R.string.pane_settings, Icons.Outlined.Settings),
}
