package com.relay.app.clock

import java.time.Instant
import java.time.ZoneId

/** A pickable place: a time zone and the city name it's known by. */
data class City(val zoneId: String, val name: String, val region: String)

/**
 * The city search list (spec §08), built from the platform's time-zone database: every
 * "Region/City" zone, named after its city. No bundled city list to go stale.
 */
object Cities {
    private val regions = setOf("Africa", "America", "Antarctica", "Arctic", "Asia", "Atlantic", "Australia", "Europe", "Indian", "Pacific")

    val all: List<City> by lazy {
        ZoneId.getAvailableZoneIds()
            .filter { it.substringBefore('/') in regions && '/' in it }
            .map { id -> City(id, id.substringAfterLast('/').replace('_', ' '), id.substringBefore('/')) }
            .sortedBy { it.name }
    }

    fun search(query: String): List<City> {
        val q = query.trim()
        if (q.isEmpty()) return all
        return all.filter { it.name.contains(q, ignoreCase = true) || it.zoneId.contains(q, ignoreCase = true) }
            .sortedBy { if (it.name.startsWith(q, ignoreCase = true)) 0 else 1 }
    }

    fun nameOf(zoneId: String): String = zoneId.substringAfterLast('/').replace('_', ' ')

    /** Offset of [zone] relative to [from], in minutes, at [at]. */
    fun offsetMinutes(zone: ZoneId, from: ZoneId, at: Instant = Instant.now()): Int =
        (zone.rules.getOffset(at).totalSeconds - from.rules.getOffset(at).totalSeconds) / 60
}
