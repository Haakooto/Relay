package com.relay.app.data.schedule

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ExceptionKind { SKIP, RETIME }

/** Skip or retime a schedule on a date range, inclusive (spec §04). */
@Entity(
    tableName = "schedule_exceptions",
    foreignKeys = [ForeignKey(
        entity = ScheduleEntity::class,
        parentColumns = ["id"],
        childColumns = ["scheduleId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("scheduleId")],
)
data class ScheduleExceptionEntity(
    @PrimaryKey val id: String,
    val scheduleId: String,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val kind: ExceptionKind,
    /** RETIME only. */
    val retimeHour: Int?,
    val retimeMinute: Int?,
)
