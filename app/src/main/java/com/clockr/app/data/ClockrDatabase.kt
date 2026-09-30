package com.clockr.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TimerDefEntity::class, ChainDefEntity::class, SoundProfileEntity::class, AlarmEntity::class,
        LibraryHeaderEntity::class, AlarmExceptionEntity::class, ClockConfigEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class ClockrDatabase : RoomDatabase() {
    abstract fun timerDefDao(): TimerDefDao
    abstract fun chainDefDao(): ChainDefDao
    abstract fun soundProfileDao(): SoundProfileDao
    abstract fun alarmDao(): AlarmDao
    abstract fun libraryHeaderDao(): LibraryHeaderDao
    abstract fun alarmExceptionDao(): AlarmExceptionDao
    abstract fun clockConfigDao(): ClockConfigDao

    companion object {
        @Volatile private var instance: ClockrDatabase? = null

        /** Adds library-organization support (spec-adjacent, not in the original design doc):
         *  user-named, user-orderable header sections in the Timers library. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE timer_defs ADD COLUMN headerId TEXT")
                db.execSQL("ALTER TABLE timer_defs ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE chain_defs ADD COLUMN headerId TEXT")
                db.execSQL("ALTER TABLE chain_defs ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE chain_defs ADD COLUMN transitionBeepTone TEXT NOT NULL DEFAULT 'BEEP'")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS library_headers (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, sortOrder INTEGER NOT NULL)",
                )
            }
        }

        /** Spec §4.2 "generalizing: scheduled triggers" — an alarm can now target a Timer/Chain
         *  instead of firing its own dismiss/snooze notification. Both columns null on every
         *  existing row, which is exactly "plain alarm, unchanged behavior". */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN targetBlockId TEXT")
                db.execSQL("ALTER TABLE alarms ADD COLUMN targetBlockKind TEXT")
            }
        }

        /** Alarm schedule exceptions — skip or retime a repeating alarm for a date range. New
         *  table, no existing rows affected. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS alarm_exceptions (" +
                        "id TEXT NOT NULL PRIMARY KEY, alarmId TEXT NOT NULL, " +
                        "startEpochDay INTEGER NOT NULL, endEpochDay INTEGER NOT NULL, " +
                        "overrideHour INTEGER, overrideMinute INTEGER)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_alarm_exceptions_alarmId ON alarm_exceptions(alarmId)")
            }
        }

        /** Clock/widget customization — multiple independently-styled clocks, in-app and per
         *  placed widget instance. New table, no existing rows affected. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS clock_configs (" +
                        "id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, style TEXT NOT NULL, " +
                        "digitalFont TEXT NOT NULL, analogueVariant TEXT NOT NULL, color TEXT NOT NULL, " +
                        "showSeconds INTEGER NOT NULL, showDate INTEGER NOT NULL, background TEXT NOT NULL, " +
                        "backgroundColor TEXT NOT NULL, cornerStyle TEXT NOT NULL, borderEnabled INTEGER NOT NULL, " +
                        "borderWidthDp INTEGER NOT NULL, sortOrder INTEGER NOT NULL, appWidgetId INTEGER, timezoneId TEXT)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_clock_configs_appWidgetId ON clock_configs(appWidgetId)")
            }
        }

        fun get(context: Context): ClockrDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, ClockrDatabase::class.java, "clockr.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
                .also { instance = it }
        }
    }
}
