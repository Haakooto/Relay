package com.clockr.app.util

import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

fun formatDays(days: Set<DayOfWeek>): String =
    days.sorted().joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }

fun formatTime(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

fun formatDuration(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        m > 0 && s > 0 -> "${m}m ${s}s"
        m > 0 -> "${m}m"
        else -> "${s}s"
    }
}
