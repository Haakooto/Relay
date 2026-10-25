package com.relay.app.ui.calendar

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.relay.app.AppContainer
import com.relay.app.R
import com.relay.app.data.event.EventEntity
import com.relay.app.event.IcsWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId

/**
 * Export (#33): items as a calendar file, handed to the share sheet. Nothing leaves the phone
 * unless the user picks where it goes (principles §6.7). The file sits in the cache, readable
 * only through the share grant.
 */
object IcsShare {
    suspend fun share(context: Context, container: AppContainer, items: List<EventEntity>, name: String) {
        val leads = items.mapNotNull { e -> container.events.reminderFor(e.id)?.let { e.id to it.leadMinutes } }.toMap()
        val text = IcsWriter.write(items, leads, ZoneId.systemDefault(), Instant.now())
        val file = withContext(Dispatchers.IO) {
            File(context.cacheDir, DIRECTORY).apply { mkdirs() }.resolve(safeName(name) + ".ics").apply { writeText(text) }
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/calendar")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, context.getString(R.string.ics_export)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** A file name from a title: letters, digits and dashes only, never empty. */
    private fun safeName(name: String): String =
        name.replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').take(MAX_NAME).ifEmpty { "calendar" }

    private const val DIRECTORY = "ics"
    private const val MAX_NAME = 60
}
