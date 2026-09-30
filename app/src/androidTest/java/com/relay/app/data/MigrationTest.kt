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

    private companion object {
        const val TEST_DB = "migration-test"
        const val LATEST_VERSION = 3
    }
}
