package com.clockr.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.DayOfWeek

enum class OneOffAfterFire { DELETE, MARK_DONE }

/**
 * Spec §03/§04 Alarm. [repeatDays] empty means one-off. "Delete after" for a repeating alarm is
 * *either* an end date or an occurrence count (spec §04) — modeled as two nullable columns the
 * UI only ever sets one of, rather than a stricter sum type Room can't express directly.
 */
@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey val id: String,
    val hour: Int,
    val minute: Int,
    val label: String,
    val repeatDays: String, // comma-separated DayOfWeek names, e.g. "MONDAY,WEDNESDAY"; empty = one-off
    val oneOffAfterFire: OneOffAfterFire,
    val done: Boolean,
    val repeatUntilEpochDay: Long?,
    val repeatOccurrencesRemaining: Int?,
    val soundProfileId: String,
    val snoozeMinutes: Int,
    val maxSnoozes: Int?,
    val snoozesUsed: Int,
    val enabled: Boolean,
) {
    val isRepeating: Boolean get() = repeatDays.isNotBlank()

    fun repeatDaySet(): Set<DayOfWeek> =
        repeatDays.split(",").filter { it.isNotBlank() }.map { DayOfWeek.valueOf(it) }.toSet()

    companion object {
        fun encodeDays(days: Set<DayOfWeek>): String = days.joinToString(",") { it.name }
    }
}
