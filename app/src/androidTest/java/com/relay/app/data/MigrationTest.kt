package com.relay.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Harness for spec §02's "real migrations from day one". Each schema bump adds a test here:
 * create the DB at the old version from `app/schemas/`, insert rows, run the migration, and
 * check the rows survived. Needs a device or emulator (`./gradlew :app:connectedAndroidTest`).
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RelayDatabase::class.java,
    )

    @Test
    fun createsVersion1() {
        helper.createDatabase(TEST_DB, 1).close()
    }

    @Test
    fun migratesAllTheWayToLatest() {
        helper.createDatabase(TEST_DB, 1).close()
        helper.runMigrationsAndValidate(TEST_DB, LATEST_VERSION, true, *RelayDatabase.MIGRATIONS).close()
    }

    @Test
    fun migrate1To2KeepsHeadersAndAddsSchedules() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL("INSERT INTO library_headers (id, title, sortOrder) VALUES ('h1', 'Work', 1)")
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 2, true, *RelayDatabase.MIGRATIONS).apply {
            query("SELECT title FROM library_headers WHERE id = 'h1'").use {
                assertTrue(it.moveToFirst())
                assertEquals("Work", it.getString(0))
            }
            query("SELECT COUNT(*) FROM schedules").use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
            close()
        }
    }

    @Test
    fun migrate2To3KeepsSchedulesAndAddsLibraryAndRuns() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                "INSERT INTO schedules (id, label, hour, minute, oneOffEpochDay, repeatDays, endEpochDay, remainingCount, " +
                    "afterFire, enabled, target, createdAtMillis, createdBy, snoozedUntilMillis, snoozesUsed, nextFireAtMillis, " +
                    "ring_soundUri, ring_vibrate, ring_rampSeconds, ring_snoozeMinutes, ring_maxSnoozes) VALUES " +
                    "('s1', 'Wake', 7, 0, NULL, 31, NULL, NULL, 'DISABLE', 1, 'RING', 0, 'user', NULL, 0, NULL, NULL, 1, 0, 10, NULL)",
            )
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 3, true, *RelayDatabase.MIGRATIONS).apply {
            query("SELECT label FROM schedules WHERE id = 's1'").use {
                assertTrue(it.moveToFirst())
                assertEquals("Wake", it.getString(0))
            }
            query("SELECT COUNT(*) FROM runs").use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
            close()
        }
    }

    @Test
    fun migrate3To4KeepsRunsAndAddsStopwatchesAndClocks() {
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL(
                "INSERT INTO timer_defs (id, label, durationExpr, headerId, sortOrder, sound_uri, sound_vibrate) " +
                    "VALUES ('t1', 'Tea', '4m', NULL, 1, NULL, 1)",
            )
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 4, true, *RelayDatabase.MIGRATIONS).apply {
            query("SELECT label FROM timer_defs WHERE id = 't1'").use {
                assertTrue(it.moveToFirst())
                assertEquals("Tea", it.getString(0))
            }
            query("SELECT COUNT(*) FROM stopwatches").use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
            close()
        }
    }

    @Test
    fun migrate4To5KeepsLapsAndDefaultsTags() {
        helper.createDatabase(TEST_DB, 4).apply {
            execSQL("INSERT INTO stopwatches (id, name, t0Millis, accumulatedMillis, startedAtMillis, sortOrder) VALUES ('w', 'Run', 0, 5000, NULL, 1)")
            execSQL("INSERT INTO laps (id, stopwatchId, number, elapsedMillis, note) VALUES ('l', 'w', 1, 5000, 'first')")
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 5, true, *RelayDatabase.MIGRATIONS).apply {
            query("SELECT note, tags FROM laps WHERE id = 'l'").use {
                assertTrue(it.moveToFirst())
                assertEquals("first", it.getString(0))
                assertEquals("[]", it.getString(1))
            }
            close()
        }
    }

    @Test
    fun migrate5To6KeepsAlarmsAsRinging() {
        helper.createDatabase(TEST_DB, 5).apply {
            execSQL(
                "INSERT INTO schedules (id, label, hour, minute, oneOffEpochDay, repeatDays, endEpochDay, remainingCount, " +
                    "afterFire, enabled, target, createdAtMillis, createdBy, snoozedUntilMillis, snoozesUsed, nextFireAtMillis, " +
                    "ring_soundUri, ring_vibrate, ring_rampSeconds, ring_snoozeMinutes, ring_maxSnoozes) VALUES " +
                    "('s1', 'Wake', 7, 0, NULL, 31, NULL, NULL, 'DISABLE', 1, 'RING', 0, 'user', NULL, 0, NULL, NULL, 1, 0, 10, NULL)",
            )
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 6, true, *RelayDatabase.MIGRATIONS).apply {
            query("SELECT target, targetDefId, targetSpec FROM schedules WHERE id = 's1'").use {
                assertTrue(it.moveToFirst())
                assertEquals("RING", it.getString(0))
                assertTrue(it.isNull(1))
                assertEquals("{}", it.getString(2))
            }
            close()
        }
    }

    @Test
    fun migrate6To7KeepsTimersWithoutWarning() {
        helper.createDatabase(TEST_DB, 6).apply {
            execSQL(
                "INSERT INTO timer_defs (id, label, durationExpr, sound_uri, sound_vibrate, headerId, sortOrder) " +
                    "VALUES ('t1', 'Tea', '4m', NULL, 1, NULL, 1)",
            )
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 7, true, *RelayDatabase.MIGRATIONS).apply {
            query("SELECT durationExpr, warnSeconds, warnTone FROM timer_defs WHERE id = 't1'").use {
                assertTrue(it.moveToFirst())
                assertEquals("4m", it.getString(0))
                assertEquals(0L, it.getLong(1))
                assertEquals("BEEP", it.getString(2))
            }
            close()
        }
    }

    @Test
    fun migrate7To8KeepsAlarmsAndAddsEvents() {
        helper.createDatabase(TEST_DB, 7).apply {
            execSQL(
                "INSERT INTO schedules (id, label, hour, minute, oneOffEpochDay, repeatDays, endEpochDay, remainingCount, " +
                    "afterFire, enabled, target, createdAtMillis, createdBy, snoozedUntilMillis, snoozesUsed, nextFireAtMillis, " +
                    "ring_soundUri, ring_vibrate, ring_rampSeconds, ring_snoozeMinutes, ring_maxSnoozes, targetDefId, targetSpec) " +
                    "VALUES ('s1', 'Wake', 7, 0, NULL, 31, NULL, NULL, 'DISABLE', 1, 'RING', 0, 'user', NULL, 0, NULL, NULL, " +
                    "1, 0, 10, NULL, NULL, '{}')",
            )
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 8, true, *RelayDatabase.MIGRATIONS).apply {
            val columns = "label, repeatDays, eventId, startEpochDay, repeatMonthDay, repeatMonth, leadMinutes"
            query("SELECT $columns FROM schedules WHERE id = 's1'").use {
                assertTrue(it.moveToFirst())
                assertEquals("Wake", it.getString(0))
                assertEquals(31L, it.getLong(1))
                assertTrue(it.isNull(2))
                assertTrue(it.isNull(3))
                assertTrue(it.isNull(4))
                assertTrue(it.isNull(5))
                assertEquals(0L, it.getLong(6))
            }
            query("SELECT COUNT(*) FROM events").use {
                assertTrue(it.moveToFirst())
                assertEquals(0L, it.getLong(0))
            }
            close()
        }
    }

    @Test
    fun migrate8To9PutsItemsInPersonalAndAlarmsInAlarms() {
        helper.createDatabase(TEST_DB, 8).apply {
            execSQL(
                "INSERT INTO schedules (id, label, hour, minute, oneOffEpochDay, repeatDays, endEpochDay, remainingCount, " +
                    "afterFire, enabled, target, createdAtMillis, createdBy, snoozedUntilMillis, snoozesUsed, nextFireAtMillis, " +
                    "ring_soundUri, ring_vibrate, ring_rampSeconds, ring_snoozeMinutes, ring_maxSnoozes, targetDefId, targetSpec, " +
                    "eventId, startEpochDay, repeatMonthDay, repeatMonth, leadMinutes) " +
                    "VALUES ('wake', 'Wake', 7, 0, NULL, 31, NULL, NULL, 'DISABLE', 1, 'RING', 0, 'user', NULL, 0, NULL, NULL, " +
                    "1, 0, 10, NULL, NULL, '{}', NULL, NULL, NULL, NULL, 0), " +
                    "('rem', 'Dentist', 10, 0, 20000, 0, NULL, NULL, 'DISABLE', 1, 'NOTIFY', 0, 'user', NULL, 0, NULL, NULL, " +
                    "1, 0, 10, NULL, NULL, '{}', 'e1', NULL, NULL, NULL, 15)",
            )
            execSQL(
                "INSERT INTO events (id, kind, title, note, startEpochDay, startMinute, endEpochDay, endMinute, repeatKind, " +
                    "repeatDays, untilEpochDay, createdAtMillis) " +
                    "VALUES ('e1', 'EVENT', 'Dentist', '', 20000, 600, NULL, NULL, 'NONE', 0, NULL, 0)",
            )
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 9, true, *RelayDatabase.MIGRATIONS).apply {
            query("SELECT id, calendarId FROM schedules ORDER BY id").use {
                assertTrue(it.moveToFirst())
                assertEquals("rem", it.getString(0))
                assertTrue(it.isNull(1)) // a reminder goes with its item
                assertTrue(it.moveToNext())
                assertEquals("alarms", it.getString(1))
            }
            query("SELECT calendarId, color FROM events WHERE id = 'e1'").use {
                assertTrue(it.moveToFirst())
                assertEquals("personal", it.getString(0))
                assertTrue(it.isNull(1))
            }
            query("SELECT id, defaultForAlarms FROM calendars ORDER BY sortOrder").use {
                val ids = mutableListOf<String>()
                while (it.moveToNext()) ids += it.getString(0) + ":" + it.getInt(1)
                assertEquals(listOf("personal:0", "birthdays:0", "alarms:1"), ids)
            }
            close()
        }
    }

    @Test
    fun migrate9To10KeepsCalendarsAndItemsWithoutCopies() {
        helper.createDatabase(TEST_DB, 9).apply {
            execSQL(
                "INSERT INTO events (id, kind, title, note, startEpochDay, startMinute, endEpochDay, endMinute, repeatKind, " +
                    "repeatDays, untilEpochDay, createdAtMillis, calendarId, color) " +
                    "VALUES ('e1', 'EVENT', 'Dentist', '', 20000, 600, NULL, NULL, 'NONE', 0, NULL, 0, 'personal', NULL)",
            )
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 10, true, *RelayDatabase.MIGRATIONS).apply {
            query("SELECT title, calendarId, providerEventId FROM events WHERE id = 'e1'").use {
                assertTrue(it.moveToFirst())
                assertEquals("Dentist", it.getString(0))
                assertEquals("personal", it.getString(1))
                assertTrue(it.isNull(2))
            }
            query("SELECT COUNT(*) FROM calendars WHERE exportCalendarId IS NULL").use {
                assertTrue(it.moveToFirst())
                assertEquals(3L, it.getLong(0))
            }
            close()
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
        const val LATEST_VERSION = 10
    }
}
