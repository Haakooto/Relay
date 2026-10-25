package com.relay.app.util

import java.time.LocalTime

/** A time of day stored as minutes after midnight, as calendar items keep it (spec §14). */
object MinuteOfDay {
    private const val PER_HOUR = 60

    fun of(time: LocalTime): Int = time.hour * PER_HOUR + time.minute

    fun toTime(minute: Int): LocalTime = LocalTime.of(minute / PER_HOUR, minute % PER_HOUR)
}
