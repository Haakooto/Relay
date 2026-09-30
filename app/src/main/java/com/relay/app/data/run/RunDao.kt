package com.relay.app.data.run

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RunDao {
    @Query("SELECT * FROM runs ORDER BY startedAtMillis")
    fun observeAll(): Flow<List<RunEntity>>

    @Query("SELECT * FROM runs")
    suspend fun getAll(): List<RunEntity>

    @Query("SELECT * FROM runs WHERE id = :id")
    suspend fun get(id: String): RunEntity?

    @Upsert suspend fun upsert(run: RunEntity)

    @Query("DELETE FROM runs WHERE id = :id")
    suspend fun delete(id: String)
}
