package com.relay.app.clock

import com.relay.app.data.clock.ClockDao
import com.relay.app.data.clock.LocationEntity
import com.relay.app.data.clock.WidgetConfigEntity
import com.relay.app.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** Locations, the main clock's style, and per-widget configs. */
class ClockRepository(private val dao: ClockDao, private val settings: SettingsRepository) {
    fun observeLocations(): Flow<List<LocationEntity>> = dao.observeLocations()

    suspend fun addLocation(city: City) =
        dao.upsertLocation(LocationEntity(UUID.randomUUID().toString(), city.zoneId, city.name, System.currentTimeMillis()))

    suspend fun deleteLocation(id: String) = dao.deleteLocation(id)
    suspend fun moveLocation(id: String, sortOrder: Long) = dao.moveLocation(id, sortOrder)

    suspend fun setMainStyle(style: ClockStyle) = settings.setMainClockStyle(ClockStyle.encode(style))

    suspend fun widget(appWidgetId: Int): WidgetConfigEntity? = dao.widget(appWidgetId)
    suspend fun saveWidget(config: WidgetConfigEntity) = dao.upsertWidget(config)
    suspend fun deleteWidget(appWidgetId: Int) = dao.deleteWidget(appWidgetId)
}
