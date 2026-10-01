package com.relay.app.util

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Time-of-day text that follows the app's 24-hour setting rather than the system one. */
object TimeText {
    fun of(time: LocalTime, is24h: Boolean): String =
        time.format(DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm a", AppLocale.formatting))

    fun of(instant: Instant, is24h: Boolean, zone: ZoneId = ZoneId.systemDefault()): String =
        of(instant.atZone(zone).toLocalTime(), is24h)

    fun ofMillis(millis: Long, is24h: Boolean): String = of(Instant.ofEpochMilli(millis), is24h)
}
