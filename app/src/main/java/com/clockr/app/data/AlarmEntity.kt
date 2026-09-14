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
    /**
     * Spec §4.2's "generalizing: scheduled triggers" — null (both) means a plain alarm, exactly
     * today's behavior. Non-null means firing starts this Timer/Chain via [com.clockr.app.run.TimerRunService]
     * instead of showing the alarm dismiss/snooze notification; [soundProfileId]/[snoozeMinutes]/
     * [maxSnoozes] are then unused (the run engine has its own sound and its own Stop/Restart/+1min
     * prompt), and the config screen hides them rather than leave dead controls.
     */
    val targetBlockId: String? = null,
    val targetBlockKind: String? = null,
) {
    val isRepeating: Boolean get() = repeatDays.isNotBlank()
    val hasBlockTarget: Boolean get() = targetBlockId != null && targetBlockKind != null

    fun repeatDaySet(): Set<DayOfWeek> =
        repeatDays.split(",").filter { it.isNotBlank() }.map { DayOfWeek.valueOf(it) }.toSet()

    companion object {
        fun encodeDays(days: Set<DayOfWeek>): String = days.joinToString(",") { it.name }
    }
}
