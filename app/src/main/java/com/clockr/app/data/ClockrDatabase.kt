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
        LibraryHeaderEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class ClockrDatabase : RoomDatabase() {
    abstract fun timerDefDao(): TimerDefDao
    abstract fun chainDefDao(): ChainDefDao
    abstract fun soundProfileDao(): SoundProfileDao
    abstract fun alarmDao(): AlarmDao
    abstract fun libraryHeaderDao(): LibraryHeaderDao

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

        fun get(context: Context): ClockrDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, ClockrDatabase::class.java, "clockr.db")
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }
    }
}
