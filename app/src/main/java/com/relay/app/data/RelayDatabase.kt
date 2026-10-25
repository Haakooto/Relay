package com.relay.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.relay.app.data.clock.ClockDao
import com.relay.app.data.clock.LocationEntity
import com.relay.app.data.clock.WidgetConfigEntity
import com.relay.app.data.event.CalendarDao
import com.relay.app.data.event.CalendarEntity
import com.relay.app.data.event.EventDao
import com.relay.app.data.event.EventEntity
import com.relay.app.data.library.LibraryDao
import com.relay.app.data.library.LibraryHeaderDao
import com.relay.app.data.library.LibraryHeaderEntity
import com.relay.app.data.library.RelayDefEntity
import com.relay.app.data.library.TimerDefEntity
import com.relay.app.data.run.RunDao
import com.relay.app.data.run.RunEntity
import com.relay.app.data.schedule.ScheduleDao
import com.relay.app.data.schedule.ScheduleEntity
import com.relay.app.data.schedule.ScheduleExceptionEntity
import com.relay.app.data.stopwatch.LapCorrectionEntity
import com.relay.app.data.stopwatch.LapEntity
import com.relay.app.data.stopwatch.StopwatchDao
import com.relay.app.data.stopwatch.StopwatchEntity

/**
 * Schema history lives in `app/schemas/` (exported, committed). Every version bump needs a real
 * [androidx.room.migration.Migration] added to [MIGRATIONS] and a test in `MigrationTest`. Never
 * a destructive fallback: the user's device carries real data.
 */
@Database(
    entities = [
        LibraryHeaderEntity::class, ScheduleEntity::class, ScheduleExceptionEntity::class,
        TimerDefEntity::class, RelayDefEntity::class, RunEntity::class,
        StopwatchEntity::class, LapEntity::class, LocationEntity::class, WidgetConfigEntity::class,
        LapCorrectionEntity::class, EventEntity::class, CalendarEntity::class,
    ],
    version = 9,
    exportSchema = true,
)
abstract class RelayDatabase : RoomDatabase() {
    abstract fun libraryHeaderDao(): LibraryHeaderDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun libraryDao(): LibraryDao
    abstract fun runDao(): RunDao
    abstract fun stopwatchDao(): StopwatchDao
    abstract fun clockDao(): ClockDao
    abstract fun eventDao(): EventDao
    abstract fun calendarDao(): CalendarDao

    companion object {
        const val FILE_NAME = "relay.db"

        /** v2 (build step 2): schedules and their exceptions. SQL copied from schemas/2.json. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `schedules` (`id` TEXT NOT NULL, `label` TEXT NOT NULL, " +
                        "`hour` INTEGER NOT NULL, `minute` INTEGER NOT NULL, `oneOffEpochDay` INTEGER, " +
                        "`repeatDays` INTEGER NOT NULL, `endEpochDay` INTEGER, `remainingCount` INTEGER, " +
                        "`afterFire` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `target` TEXT NOT NULL, " +
                        "`createdAtMillis` INTEGER NOT NULL, `createdBy` TEXT NOT NULL, `snoozedUntilMillis` INTEGER, " +
                        "`snoozesUsed` INTEGER NOT NULL, `nextFireAtMillis` INTEGER, `ring_soundUri` TEXT, " +
                        "`ring_vibrate` INTEGER NOT NULL, `ring_rampSeconds` INTEGER NOT NULL, " +
                        "`ring_snoozeMinutes` INTEGER NOT NULL, `ring_maxSnoozes` INTEGER, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `schedule_exceptions` (`id` TEXT NOT NULL, `scheduleId` TEXT NOT NULL, " +
                        "`startEpochDay` INTEGER NOT NULL, `endEpochDay` INTEGER NOT NULL, `kind` TEXT NOT NULL, " +
                        "`retimeHour` INTEGER, `retimeMinute` INTEGER, PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`scheduleId`) REFERENCES `schedules`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_schedule_exceptions_scheduleId` ON `schedule_exceptions` (`scheduleId`)",
                )
            }
        }

        /** v3 (build steps 3-4): saved timers and relays, and persisted runs. From schemas/3.json. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `timer_defs` (`id` TEXT NOT NULL, `label` TEXT NOT NULL, " +
                        "`durationExpr` TEXT NOT NULL, `headerId` TEXT, `sortOrder` INTEGER NOT NULL, " +
                        "`sound_uri` TEXT, `sound_vibrate` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_timer_defs_label` ON `timer_defs` (`label`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `relay_defs` (`id` TEXT NOT NULL, `label` TEXT NOT NULL, " +
                        "`script` TEXT NOT NULL, `transitionMode` TEXT NOT NULL, `transitionTone` TEXT NOT NULL, " +
                        "`stepOverrides` TEXT NOT NULL, `headerId` TEXT, `sortOrder` INTEGER NOT NULL, " +
                        "`sound_uri` TEXT, `sound_vibrate` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_relay_defs_label` ON `relay_defs` (`label`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `runs` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `sourceDefId` TEXT, " +
                        "`label` TEXT NOT NULL, `plan` TEXT NOT NULL, `stepIndex` INTEGER NOT NULL, `phase` TEXT NOT NULL, " +
                        "`stepEndsAtMillis` INTEGER, `remainingMillis` INTEGER, `alertSinceMillis` INTEGER, " +
                        "`startedAtMillis` INTEGER NOT NULL, `startedBy` TEXT NOT NULL, PRIMARY KEY(`id`))",
                )
            }
        }

        /** v4 (build steps 6-7): stopwatches and laps, clock locations, widget configs. From schemas/4.json. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `stopwatches` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`t0Millis` INTEGER NOT NULL, `accumulatedMillis` INTEGER NOT NULL, `startedAtMillis` INTEGER, " +
                        "`sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `laps` (`id` TEXT NOT NULL, `stopwatchId` TEXT NOT NULL, `number` INTEGER NOT NULL, " +
                        "`elapsedMillis` INTEGER NOT NULL, `note` TEXT NOT NULL, PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`stopwatchId`) REFERENCES `stopwatches`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_laps_stopwatchId` ON `laps` (`stopwatchId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `locations` (`id` TEXT NOT NULL, `zoneId` TEXT NOT NULL, `city` TEXT NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE TABLE IF NOT EXISTS `widget_configs` (`appWidgetId` INTEGER NOT NULL, `spec` TEXT NOT NULL, PRIMARY KEY(`appWidgetId`))")
            }
        }

        /** v5: stopwatch and lap tags, and the per-stopwatch lap-correction history. From schemas/5.json. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `stopwatches` ADD COLUMN `tags` TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE `laps` ADD COLUMN `tags` TEXT NOT NULL DEFAULT '[]'")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `lap_corrections` (`id` TEXT NOT NULL, `stopwatchId` TEXT NOT NULL, " +
                        "`seq` INTEGER NOT NULL, `kind` TEXT NOT NULL, `lapNumber` INTEGER NOT NULL, `amountMillis` INTEGER NOT NULL, " +
                        "`label` TEXT NOT NULL, `ops` TEXT NOT NULL, `affected` TEXT NOT NULL, `undone` INTEGER NOT NULL, " +
                        "`createdAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`stopwatchId`) REFERENCES `stopwatches`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_lap_corrections_stopwatchId` ON `lap_corrections` (`stopwatchId`)")
            }
        }

        /** v6 (build step 5): schedules can start a timer, relay or stopwatch. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `schedules` ADD COLUMN `targetDefId` TEXT")
                db.execSQL("ALTER TABLE `schedules` ADD COLUMN `targetSpec` TEXT NOT NULL DEFAULT '{}'")
            }
        }

        /** v7: a timer can warn before its end. */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `timer_defs` ADD COLUMN `warnSeconds` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `timer_defs` ADD COLUMN `warnTone` TEXT NOT NULL DEFAULT 'BEEP'")
            }
        }

        /** v8 (spec §10b): calendar events, and schedules that remind of them. */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `schedules` ADD COLUMN `eventId` TEXT")
                db.execSQL("ALTER TABLE `schedules` ADD COLUMN `startEpochDay` INTEGER")
                db.execSQL("ALTER TABLE `schedules` ADD COLUMN `repeatMonthDay` INTEGER")
                db.execSQL("ALTER TABLE `schedules` ADD COLUMN `repeatMonth` INTEGER")
                db.execSQL("ALTER TABLE `schedules` ADD COLUMN `leadMinutes` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_schedules_eventId` ON `schedules` (`eventId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `events` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`note` TEXT NOT NULL, `startEpochDay` INTEGER NOT NULL, `startMinute` INTEGER, `endEpochDay` INTEGER, " +
                        "`endMinute` INTEGER, `repeatKind` TEXT NOT NULL, `repeatDays` INTEGER NOT NULL, `untilEpochDay` INTEGER, " +
                        "`createdAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
            }
        }

        /**
         * v9 (#34): calendars. Three to start with; calendar items go to Personal, alarms to
         * Alarms. Blank names show translated in the app. Colours are the first palette entries.
         */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `calendars` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `color` INTEGER NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL, `shown` INTEGER NOT NULL, `defaultForAlarms` INTEGER NOT NULL, " +
                        "`defaultLeadMinutes` INTEGER, `defaultTarget` TEXT NOT NULL, `defaultRepeat` TEXT NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "INSERT INTO `calendars` VALUES " +
                        "('personal', '', ${0xFF1F6FB0}, 0, 1, 0, 15, 'NOTIFY', 'NONE'), " +
                        "('birthdays', '', ${0xFFA0306B}, 1, 1, 0, 0, 'NOTIFY', 'YEARLY'), " +
                        "('alarms', '', ${0xFF7A6A4F}, 2, 0, 1, NULL, 'RING', 'NONE')",
                )
                db.execSQL("ALTER TABLE `events` ADD COLUMN `calendarId` TEXT NOT NULL DEFAULT 'personal'")
                db.execSQL("ALTER TABLE `events` ADD COLUMN `color` INTEGER")
                db.execSQL("ALTER TABLE `schedules` ADD COLUMN `calendarId` TEXT")
                db.execSQL("UPDATE `schedules` SET `calendarId` = 'alarms' WHERE `eventId` IS NULL")
            }
        }

        /** Append each migration here as the schema grows. */
        val MIGRATIONS = arrayOf(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
        )

        fun build(context: Context): RelayDatabase =
            Room.databaseBuilder(context.applicationContext, RelayDatabase::class.java, FILE_NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
