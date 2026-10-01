package com.relay.app.data.clock

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ClockDao {
    @Query("SELECT * FROM locations ORDER BY sortOrder")
    fun observeLocations(): Flow<List<LocationEntity>>

    @Upsert suspend fun upsertLocation(location: LocationEntity)
    @Query("DELETE FROM locations WHERE id = :id") suspend fun deleteLocation(id: String)
    @Query("UPDATE locations SET sortOrder = :sortOrder WHERE id = :id") suspend fun moveLocation(id: String, sortOrder: Long)

    @Query("SELECT * FROM widget_configs WHERE appWidgetId = :appWidgetId")
    suspend fun widget(appWidgetId: Int): WidgetConfigEntity?

    @Upsert suspend fun upsertWidget(config: WidgetConfigEntity)
    @Query("DELETE FROM widget_configs WHERE appWidgetId = :appWidgetId") suspend fun deleteWidget(appWidgetId: Int)
}
