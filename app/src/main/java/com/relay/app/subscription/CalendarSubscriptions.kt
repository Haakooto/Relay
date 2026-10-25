package com.relay.app.subscription

import android.util.Log
import com.relay.app.data.event.CalendarEntity
import com.relay.app.event.CalendarRepository
import com.relay.app.event.EventRepository
import com.relay.app.event.Ics
import com.relay.app.event.ImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.ZoneId

/**
 * Subscribed calendars (#33): an .ics address fetched over the network and mirrored as a read-only
 * calendar. Relay's only network use, and only for addresses the user added (principles §1.3,
 * §6.7): https only, a time limit, a size limit, nothing sent but the request itself.
 */
class CalendarSubscriptions(
    private val calendars: CalendarRepository,
    private val events: EventRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    /** Fetches [calendar]'s address and replaces its items. */
    suspend fun refresh(calendar: CalendarEntity): Result<ImportResult> = runCatching {
        val url = requireNotNull(calendar.subscriptionUrl) { "not a subscription" }
        val zone = ZoneId.systemDefault()
        val file = Ics.read(fetch(url), zone)
        val result = events.replaceSubscribed(calendar.id, file.events) { e, id, created ->
            // Read-only, so the file's own alarms aren't Relay's to keep.
            Ics.toEntity(e, calendar.id, id, created, zone) { rule -> "RRULE:$rule" }
        }
        calendars.markRefreshed(calendar.id, nowMillis())
        result
    }.onFailure { Log.w(TAG, "subscription refresh failed", it) }

    /** Refreshes every subscription last fetched more than [STALE_MILLIS] ago. */
    suspend fun refreshStale() {
        val now = nowMillis()
        calendars.all().filter { it.isSubscribed && (it.refreshedAtMillis ?: 0) < now - STALE_MILLIS }.forEach { refresh(it) }
    }

    private suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", "text/calendar")
            check(connection.responseCode in OK_CODES) { "HTTP ${connection.responseCode}" }
            connection.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(BUFFER)
                while (out.size() <= Ics.MAX_BYTES) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                }
                check(out.size() <= Ics.MAX_BYTES) { "too big" }
                out.toByteArray().decodeToString()
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val TAG = "CalendarSubscriptions"
        private const val TIMEOUT_MILLIS = 15_000
        private const val BUFFER = 64 * 1024
        private val OK_CODES = 200..299

        /** Opening the app refreshes a subscription older than this. */
        const val STALE_MILLIS = 6 * 60 * 60 * 1000L
    }
}
