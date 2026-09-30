package com.relay.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.relay.app.data.library.LibraryHeaderDao
import com.relay.app.data.library.LibraryHeaderEntity

/**
 * Schema history lives in `app/schemas/` (exported, committed). Every version bump needs a real
 * [androidx.room.migration.Migration] added to [MIGRATIONS] and a test in `MigrationTest`. Never
 * a destructive fallback: the user's device carries real data.
 */
@Database(
    entities = [LibraryHeaderEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class RelayDatabase : RoomDatabase() {
    abstract fun libraryHeaderDao(): LibraryHeaderDao

    companion object {
        const val FILE_NAME = "relay.db"

        /** Empty at v1. Append each migration here as the schema grows. */
        val MIGRATIONS = arrayOf<androidx.room.migration.Migration>()

        fun build(context: Context): RelayDatabase =
            Room.databaseBuilder(context.applicationContext, RelayDatabase::class.java, FILE_NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
