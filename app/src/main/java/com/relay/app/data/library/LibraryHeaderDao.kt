package com.relay.app.data.library

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryHeaderDao {
    @Query("SELECT * FROM library_headers ORDER BY sortOrder")
    fun observeAll(): Flow<List<LibraryHeaderEntity>>

    @Upsert
    suspend fun upsert(header: LibraryHeaderEntity)

    @Query("DELETE FROM library_headers WHERE id = :id")
    suspend fun delete(id: String)
}
