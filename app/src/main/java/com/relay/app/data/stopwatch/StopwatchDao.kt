package com.relay.app.data.stopwatch

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface StopwatchDao {
    @Query("SELECT * FROM stopwatches ORDER BY sortOrder")
    fun observeAll(): Flow<List<StopwatchEntity>>

    @Query("SELECT * FROM laps ORDER BY number")
    fun observeLaps(): Flow<List<LapEntity>>

    @Query("SELECT * FROM stopwatches")
    suspend fun getAll(): List<StopwatchEntity>

    @Query("SELECT * FROM stopwatches WHERE id = :id")
    suspend fun get(id: String): StopwatchEntity?

    @Query("SELECT * FROM laps WHERE stopwatchId = :stopwatchId ORDER BY number")
    suspend fun laps(stopwatchId: String): List<LapEntity>

    @Upsert suspend fun upsert(stopwatch: StopwatchEntity)
    @Upsert suspend fun upsertLap(lap: LapEntity)

    @Query("DELETE FROM stopwatches WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM laps WHERE stopwatchId = :stopwatchId") suspend fun deleteLaps(stopwatchId: String)
    @Query("UPDATE laps SET note = :note, tags = :tags WHERE id = :lapId") suspend fun setLapDetails(lapId: String, note: String, tags: String)
    @Query("DELETE FROM laps WHERE id = :lapId") suspend fun deleteLap(lapId: String)

    @Query("SELECT * FROM lap_corrections ORDER BY seq")
    fun observeCorrections(): Flow<List<LapCorrectionEntity>>

    @Query("SELECT * FROM lap_corrections WHERE stopwatchId = :stopwatchId ORDER BY seq")
    suspend fun corrections(stopwatchId: String): List<LapCorrectionEntity>

    @Upsert suspend fun upsertCorrection(correction: LapCorrectionEntity)
    @Query("DELETE FROM lap_corrections WHERE stopwatchId = :stopwatchId AND undone = 1") suspend fun dropUndone(stopwatchId: String)
    @Query("DELETE FROM lap_corrections WHERE stopwatchId = :stopwatchId") suspend fun deleteCorrections(stopwatchId: String)
}
