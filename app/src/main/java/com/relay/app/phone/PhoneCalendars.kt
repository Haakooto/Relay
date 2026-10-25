package com.relay.app.phone

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.relay.app.data.event.EventEntity
import com.relay.app.event.PhoneCalendar
import com.relay.app.event.PhoneCopies
import com.relay.app.event.PhoneEvents
import com.relay.app.event.PhoneInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/**
 * The phone's calendars through CalendarContract (#16): Google and the others, synced by their own
 * apps, so Relay needs no account, OAuth or network. Reads need READ_CALENDAR, copies need
 * WRITE_CALENDAR; without them everything here quietly returns nothing (principles §6.1).
 */
class PhoneCalendars(private val context: Context) : PhoneCopies {
    private val resolver = context.contentResolver

    /** Bumped when access may have changed (the user answered a permission request). */
    private val accessChecked = MutableStateFlow(0)

    fun canRead(): Boolean = granted(Manifest.permission.READ_CALENDAR)

    fun canWrite(): Boolean = granted(Manifest.permission.WRITE_CALENDAR)

    fun accessChanged() { accessChecked.value++ }

    /** Emits now, after access changes, and whenever any phone calendar changes. */
    fun changes(): Flow<Unit> = combine(providerChanges(), accessChecked) { _, _ -> }

    private fun providerChanges(): Flow<Unit> = callbackFlow {
        trySend(Unit)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { trySend(Unit) }
        }
        resolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    /** The visible calendars on the phone, or none without access. */
    suspend fun calendars(): List<PhoneCalendar> = read(emptyList()) {
        val projection = arrayOf(
            CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        )
        val visible = "${CalendarContract.Calendars.VISIBLE} = 1"
        resolver.query(CalendarContract.Calendars.CONTENT_URI, projection, visible, null, null)?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        PhoneCalendar(
                            id = c.long(CalendarContract.Calendars._ID),
                            name = c.text(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME),
                            account = c.text(CalendarContract.Calendars.ACCOUNT_NAME),
                            color = c.int(CalendarContract.Calendars.CALENDAR_COLOR).toLong() and ARGB_MASK,
                            writable = c.int(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL) >= CONTRIBUTOR,
                        ),
                    )
                }
            }
        }.orEmpty()
    }

    /** Occurrences from [from] to [to], inclusive, in [calendarIds]; repeats are already expanded. */
    suspend fun instances(from: LocalDate, to: LocalDate, calendarIds: Set<Long>, zone: ZoneId): List<PhoneInstance> {
        if (calendarIds.isEmpty()) return emptyList()
        return read(emptyList()) {
            val begin = from.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val projection = arrayOf(
                CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.CALENDAR_ID, CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.DISPLAY_COLOR,
            )
            CalendarContract.Instances.query(resolver, projection, begin, end)?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        val calendarId = c.long(CalendarContract.Instances.CALENDAR_ID)
                        if (calendarId !in calendarIds) continue
                        add(
                            PhoneInstance(
                                eventId = c.long(CalendarContract.Instances.EVENT_ID), calendarId = calendarId,
                                title = c.text(CalendarContract.Instances.TITLE),
                                beginMillis = c.long(CalendarContract.Instances.BEGIN), endMillis = c.long(CalendarContract.Instances.END),
                                allDay = c.int(CalendarContract.Instances.ALL_DAY) == 1,
                                color = c.int(CalendarContract.Instances.DISPLAY_COLOR).toLong() and ARGB_MASK,
                            ),
                        )
                    }
                }
            }.orEmpty()
        }
    }

    /** Writes [event]'s copy into phone calendar [phoneCalendarId]: updates its copy if it has one, else adds one. */
    override suspend fun write(event: EventEntity, phoneCalendarId: Long): Long? = withContext(Dispatchers.IO) {
        if (!canWrite()) return@withContext null
        runCatching {
            val f = PhoneEvents.fields(event, ZoneId.systemDefault())
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, phoneCalendarId)
                put(CalendarContract.Events.TITLE, f.title)
                put(CalendarContract.Events.DESCRIPTION, f.description)
                put(CalendarContract.Events.DTSTART, f.dtStart)
                if (f.dtEnd != null) put(CalendarContract.Events.DTEND, f.dtEnd) else putNull(CalendarContract.Events.DTEND)
                if (f.duration != null) put(CalendarContract.Events.DURATION, f.duration) else putNull(CalendarContract.Events.DURATION)
                if (f.rrule != null) put(CalendarContract.Events.RRULE, f.rrule) else putNull(CalendarContract.Events.RRULE)
                put(CalendarContract.Events.ALL_DAY, if (f.allDay) 1 else 0)
                put(CalendarContract.Events.EVENT_TIMEZONE, f.timeZone)
            }
            val existing = event.providerEventId
            val updated = existing != null &&
                resolver.update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existing), values, null, null) > 0
            if (updated) existing else resolver.insert(CalendarContract.Events.CONTENT_URI, values)?.let(ContentUris::parseId)
        }.onFailure { Log.e(TAG, "couldn't write a calendar copy", it) }.getOrNull()
    }

    override suspend fun delete(providerEventId: Long) {
        withContext(Dispatchers.IO) {
            if (!canWrite()) return@withContext
            runCatching { resolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, providerEventId), null, null) }
                .onFailure { Log.e(TAG, "couldn't delete a calendar copy", it) }
        }
    }

    private suspend fun <T> read(fallback: T, block: () -> T): T = withContext(Dispatchers.IO) {
        if (!canRead()) return@withContext fallback
        runCatching(block).onFailure { Log.e(TAG, "couldn't read phone calendars", it) }.getOrDefault(fallback)
    }

    // Columns by name, not position, so the projections can change freely.
    private fun Cursor.long(column: String) = getLong(getColumnIndexOrThrow(column))
    private fun Cursor.int(column: String) = getInt(getColumnIndexOrThrow(column))
    private fun Cursor.text(column: String) = getString(getColumnIndexOrThrow(column)).orEmpty()

    private fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "PhoneCalendars"
        private const val ARGB_MASK = 0xFFFFFFFFL

        /** The least access that lets Relay add events: a calendar it may copy into. */
        private const val CONTRIBUTOR = CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR

        /** Opens a phone event in its calendar app. */
        fun viewIntent(eventId: Long): Intent =
            Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
