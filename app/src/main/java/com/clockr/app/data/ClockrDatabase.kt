package com.clockr.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TimerDefEntity::class, ChainDefEntity::class, SoundProfileEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class ClockrDatabase : RoomDatabase() {
    abstract fun timerDefDao(): TimerDefDao
    abstract fun chainDefDao(): ChainDefDao
    abstract fun soundProfileDao(): SoundProfileDao

    companion object {
        @Volatile private var instance: ClockrDatabase? = null

        fun get(context: Context): ClockrDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, ClockrDatabase::class.java, "clockr.db")
                .build()
                .also { instance = it }
        }
    }
}
