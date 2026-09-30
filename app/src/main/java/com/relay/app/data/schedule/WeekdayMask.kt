package com.relay.app.data.schedule

import java.time.DayOfWeek

/** [ScheduleEntity.repeatDays] encoding: bit 0 = Monday ... bit 6 = Sunday. */
object WeekdayMask {
    fun of(days: Set<DayOfWeek>): Int = days.fold(0) { mask, day -> mask or bit(day) }
    fun toSet(mask: Int): Set<DayOfWeek> = DayOfWeek.entries.filter { mask and bit(it) != 0 }.toSet()
    fun contains(mask: Int, day: DayOfWeek): Boolean = mask and bit(day) != 0
    private fun bit(day: DayOfWeek) = 1 shl (day.value - 1)
}
