package com.clockr.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): AlarmEntity?

    @Query("SELECT * FROM alarms WHERE enabled = 1")
    suspend fun getAllEnabled(): List<AlarmEntity>

    @Upsert
    suspend fun upsert(entity: AlarmEntity)

    @Delete
    suspend fun delete(entity: AlarmEntity)
}

@Dao
interface SoundProfileDao {
    @Query("SELECT * FROM sound_profiles ORDER BY id")
    fun observeAll(): Flow<List<SoundProfileEntity>>

    @Upsert
    suspend fun upsert(entity: SoundProfileEntity)

    @Delete
    suspend fun delete(entity: SoundProfileEntity)
}

@Dao
interface TimerDefDao {
    @Query("SELECT * FROM timer_defs WHERE persistent = 1 ORDER BY label")
    fun observeAll(): Flow<List<TimerDefEntity>>

    @Query("SELECT * FROM timer_defs WHERE label = :label LIMIT 1")
    suspend fun findByLabel(label: String): TimerDefEntity?

    @Query("SELECT * FROM timer_defs WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): TimerDefEntity?

    /** Blocking on purpose — used only by [com.clockr.app.data.BlockRepository]'s synchronous
     * chainlang `BlockResolver`, which must itself be called off the main thread. */
    @Query("SELECT * FROM timer_defs WHERE label = :label LIMIT 1")
    fun findByLabelBlocking(label: String): TimerDefEntity?

    @Upsert
    suspend fun upsert(entity: TimerDefEntity)

    @Delete
    suspend fun delete(entity: TimerDefEntity)
}

@Dao
interface ChainDefDao {
    @Query("SELECT * FROM chain_defs WHERE persistent = 1 ORDER BY label")
    fun observeAll(): Flow<List<ChainDefEntity>>

    @Query("SELECT * FROM chain_defs")
    suspend fun getAll(): List<ChainDefEntity>

    /** Blocking on purpose — see [TimerDefDao.findByLabelBlocking]. */
    @Query("SELECT * FROM chain_defs")
    fun getAllBlocking(): List<ChainDefEntity>

    @Query("SELECT * FROM chain_defs WHERE label = :label LIMIT 1")
    fun findByLabelBlocking(label: String): ChainDefEntity?

    @Query("SELECT * FROM chain_defs WHERE label = :label LIMIT 1")
    suspend fun findByLabel(label: String): ChainDefEntity?

    @Query("SELECT * FROM chain_defs WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ChainDefEntity?

    @Upsert
    suspend fun upsert(entity: ChainDefEntity)

    @Delete
    suspend fun delete(entity: ChainDefEntity)
}
