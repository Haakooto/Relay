package com.relay.app.data.event

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarDao {
    @Query("SELECT * FROM calendars ORDER BY sortOrder")
    fun observeAll(): Flow<List<CalendarEntity>>

    @Query("SELECT * FROM calendars ORDER BY sortOrder")
    suspend fun getAll(): List<CalendarEntity>

    @Query("SELECT * FROM calendars WHERE id = :id")
    suspend fun get(id: String): CalendarEntity?

    @Upsert
    suspend fun upsert(calendar: CalendarEntity)

    @Query("DELETE FROM calendars WHERE id = :id")
    suspend fun delete(id: String)
}
