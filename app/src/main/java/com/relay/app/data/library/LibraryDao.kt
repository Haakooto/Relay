package com.relay.app.data.library

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {
    @Query("SELECT * FROM timer_defs ORDER BY sortOrder")
    fun observeTimers(): Flow<List<TimerDefEntity>>

    @Query("SELECT * FROM relay_defs ORDER BY sortOrder")
    fun observeRelays(): Flow<List<RelayDefEntity>>

    @Query("SELECT * FROM timer_defs")
    suspend fun timers(): List<TimerDefEntity>

    @Query("SELECT * FROM relay_defs")
    suspend fun relays(): List<RelayDefEntity>

    @Query("SELECT * FROM timer_defs WHERE id = :id")
    suspend fun timer(id: String): TimerDefEntity?

    @Query("SELECT * FROM relay_defs WHERE id = :id")
    suspend fun relay(id: String): RelayDefEntity?

    @Upsert suspend fun upsertTimer(timer: TimerDefEntity)
    @Upsert suspend fun upsertRelay(relay: RelayDefEntity)

    @Query("DELETE FROM timer_defs WHERE id = :id") suspend fun deleteTimer(id: String)
    @Query("DELETE FROM relay_defs WHERE id = :id") suspend fun deleteRelay(id: String)

    @Query("UPDATE timer_defs SET headerId = :headerId, sortOrder = :sortOrder WHERE id = :id")
    suspend fun moveTimer(id: String, headerId: String?, sortOrder: Long)

    @Query("UPDATE relay_defs SET headerId = :headerId, sortOrder = :sortOrder WHERE id = :id")
    suspend fun moveRelay(id: String, headerId: String?, sortOrder: Long)

    /** A deleted header's blocks drop back to the ungrouped section. */
    @Query("UPDATE timer_defs SET headerId = NULL WHERE headerId = :headerId")
    suspend fun ungroupTimers(headerId: String)

    @Query("UPDATE relay_defs SET headerId = NULL WHERE headerId = :headerId")
    suspend fun ungroupRelays(headerId: String)
}
