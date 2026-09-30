package com.relay.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.relay.app.data.library.LibraryHeaderDao
import com.relay.app.data.library.LibraryHeaderEntity
import com.relay.app.data.schedule.ScheduleDao
import com.relay.app.data.schedule.ScheduleEntity
import com.relay.app.data.schedule.ScheduleExceptionEntity

/**
 * Schema history lives in `app/schemas/` (exported, committed). Every version bump needs a real
 * [androidx.room.migration.Migration] added to [MIGRATIONS] and a test in `MigrationTest`. Never
 * a destructive fallback: the user's device carries real data.
 */
@Database(
    entities = [LibraryHeaderEntity::class, ScheduleEntity::class, ScheduleExceptionEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class RelayDatabase : RoomDatabase() {
    abstract fun libraryHeaderDao(): LibraryHeaderDao
    abstract fun scheduleDao(): ScheduleDao

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

        /** Append each migration here as the schema grows. */
        val MIGRATIONS = arrayOf(MIGRATION_1_2)

        fun build(context: Context): RelayDatabase =
            Room.databaseBuilder(context.applicationContext, RelayDatabase::class.java, FILE_NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
