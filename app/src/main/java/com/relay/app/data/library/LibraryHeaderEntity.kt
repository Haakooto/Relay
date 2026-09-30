package com.relay.app.data.library

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A user-named section in the Timers library (spec §05). Organizational only. */
@Entity(tableName = "library_headers")
data class LibraryHeaderEntity(
    @PrimaryKey val id: String,
    val title: String,
    val sortOrder: Long,
)
