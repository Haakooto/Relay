package com.clockr.app.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.clockr.app.R
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

fun formatDays(days: Set<DayOfWeek>): String =
    days.sorted().joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }

fun formatTime(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

/** Always-padded HH:MM:SS, for a running stopwatch/timer display. */
fun formatElapsedClock(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

@Composable
fun formatDuration(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return when {
        h > 0 && m > 0 -> stringResource(R.string.duration_hours_minutes, h, m)
        h > 0 -> stringResource(R.string.duration_hours, h)
        m > 0 && s > 0 -> stringResource(R.string.duration_minutes_seconds, m, s)
        m > 0 -> stringResource(R.string.duration_minutes, m)
        else -> stringResource(R.string.duration_seconds, s)
    }
}
