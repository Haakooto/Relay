package com.relay.app.data.clock

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A city in the Clock pane's list (spec §08). */
@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey val id: String,
    val zoneId: String,
    val city: String,
    val sortOrder: Long,
)

/** One placed home-screen widget (spec §09). [spec] is [com.relay.app.widget.WidgetSpec] JSON. */
@Entity(tableName = "widget_configs")
data class WidgetConfigEntity(
    @PrimaryKey val appWidgetId: Int,
    val spec: String,
)
