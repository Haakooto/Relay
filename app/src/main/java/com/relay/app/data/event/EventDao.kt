package com.relay.app.data.event

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("SELECT * FROM events")
    fun observeAll(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun get(id: String): EventEntity?

    @Query("SELECT * FROM events WHERE calendarId = :calendarId")
    suspend fun inCalendar(calendarId: String): List<EventEntity>

    @Upsert
    suspend fun upsert(event: EventEntity)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun delete(id: String)

    /** A deleted calendar's items move to another (#34). */
    @Query("UPDATE events SET calendarId = :to WHERE calendarId = :from")
    suspend fun moveCalendar(from: String, to: String)
}
