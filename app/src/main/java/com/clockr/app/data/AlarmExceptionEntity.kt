package com.clockr.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A skip or time-override for one [AlarmEntity]'s normal firing over a date range (a single date
 * has startEpochDay == endEpochDay). [overrideHour]/[overrideMinute] follow the same
 * two-nullable-columns sum-type pattern as [AlarmEntity]'s "delete after" fields: both null means
 * SKIP, both non-null means OVERRIDE — never a separate boolean that could disagree with stale
 * hour/minute values. No FK to [AlarmEntity.id]; this codebase never uses Room FK constraints,
 * cleanup on alarm delete is explicit application code.
 */
@Entity(tableName = "alarm_exceptions", indices = [Index("alarmId")])
data class AlarmExceptionEntity(
    @PrimaryKey val id: String,
    val alarmId: String,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val overrideHour: Int?,
    val overrideMinute: Int?,
) {
    val isOverride: Boolean get() = overrideHour != null && overrideMinute != null
}
