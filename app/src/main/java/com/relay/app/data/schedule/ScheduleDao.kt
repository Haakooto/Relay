package com.relay.app.data.schedule

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules")
    fun observeAll(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules")
    suspend fun getAll(): List<ScheduleEntity>

    @Query("SELECT * FROM schedules WHERE id = :id")
    suspend fun get(id: String): ScheduleEntity?

    @Query("SELECT * FROM schedules WHERE eventId = :eventId")
    suspend fun forEvent(eventId: String): List<ScheduleEntity>

    @Upsert
    suspend fun upsert(schedule: ScheduleEntity)

    /** A deleted calendar's alarms move to another (#34). Doesn't touch arming. */
    @Query("UPDATE schedules SET calendarId = :to WHERE calendarId = :from")
    suspend fun moveCalendar(from: String, to: String)

    @Query("DELETE FROM schedules WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM schedule_exceptions")
    fun observeAllExceptions(): Flow<List<ScheduleExceptionEntity>>

    @Query("SELECT * FROM schedule_exceptions WHERE scheduleId = :scheduleId ORDER BY startEpochDay")
    suspend fun exceptionsFor(scheduleId: String): List<ScheduleExceptionEntity>

    @Upsert
    suspend fun upsertException(exception: ScheduleExceptionEntity)

    @Query("DELETE FROM schedule_exceptions WHERE id = :id")
    suspend fun deleteException(id: String)

    /** Housekeeping: exceptions entirely in the past can never matter again. */
    @Query("DELETE FROM schedule_exceptions WHERE endEpochDay < :beforeEpochDay")
    suspend fun deleteExceptionsEndingBefore(beforeEpochDay: Long)
}
