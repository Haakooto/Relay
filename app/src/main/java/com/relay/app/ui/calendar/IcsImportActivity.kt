package com.relay.app.ui.calendar

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.relay.app.R
import com.relay.app.appContainer
import com.relay.app.data.event.CalendarEntity
import com.relay.app.data.schedule.RingOptions
import com.relay.app.data.schedule.TargetKind
import com.relay.app.event.Ics
import com.relay.app.event.IcsFile
import com.relay.app.event.ReminderDraft
import com.relay.app.ui.theme.RelayTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * Import a calendar file (#33): opened from the calendars sheet, or with "Open with" / "Share" from
 * another app. It's exported, so the file is untrusted (principles §6.2, §6.6): only content and
 * file addresses, at most [Ics.MAX_BYTES], parsed with Ics's limits. Shows what it found and asks
 * which calendar to put it in; nothing is saved until Import.
 */
class IcsImportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val uri = source(intent)
        val container = appContainer
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val calendars by container.calendars.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
            val file by produceState<Result<IcsFile>?>(null) { value = load(uri) }
            val s = settings ?: return@setContent
            RelayTheme(s.theme) {
                Surface(Modifier.fillMaxSize()) {
                    ImportScreen(
                        file, calendars.filter { !it.isSubscribed }, s.lastCalendarId,
                        onCancel = { finish() },
                        onImport = { f, calendar -> import(f, calendar) },
                    )
                }
            }
        }
    }

    private fun source(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_SEND -> intent.let { IntentCompat.getParcelableExtra(it, Intent.EXTRA_STREAM, Uri::class.java) }
        else -> intent?.data
    }?.takeIf { it.scheme == "content" || it.scheme == "file" }

    /** The file's text, refusing anything over the limit before reading it all. */
    private suspend fun load(uri: Uri?): Result<IcsFile> = withContext(Dispatchers.IO) {
        runCatching {
            requireNotNull(uri) { "no file" }
            val bytes = contentResolver.openInputStream(uri)!!.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(BUFFER)
                while (out.size() <= Ics.MAX_BYTES) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            }
            require(bytes.size <= Ics.MAX_BYTES) { "too big" }
            Ics.read(bytes.decodeToString(), ZoneId.systemDefault())
        }
    }

    private fun import(file: IcsFile, calendar: CalendarEntity) = lifecycleScope.launch {
        val container = appContainer
        val settings = container.settings.current()
        val ring =
            RingOptions(settings.defaultAlarmSoundUri, true, settings.defaultRampSeconds, settings.defaultSnoozeMinutes, null)
        // A file's reminder rings only in a calendar whose reminders ring; otherwise it notifies.
        val target = if (calendar.defaultTarget == TargetKind.RING) TargetKind.RING else TargetKind.NOTIFY
        val zone = ZoneId.systemDefault()
        val result = container.events.import(
            file.events, calendar.id,
            toEntity = { e, id, created ->
                Ics.toEntity(e, calendar.id, id, created, zone) { rule -> getString(R.string.ics_rule_not_kept, rule) }
            },
            reminder = { lead -> ReminderDraft(lead, target, null, "{}", ring) },
        )
        container.settings.setLastCalendarId(calendar.id)
        Toast.makeText(this@IcsImportActivity, getString(R.string.ics_imported, result.added, result.updated), Toast.LENGTH_LONG).show()
        finish()
    }
}

@Composable
private fun ImportScreen(
    file: Result<IcsFile>?,
    calendars: List<CalendarEntity>,
    lastCalendarId: String?,
    onCancel: () -> Unit,
    onImport: (IcsFile, CalendarEntity) -> Unit,
) {
    var target by remember(calendars) { mutableStateOf(calendars.firstOrNull { it.id == lastCalendarId } ?: calendars.firstOrNull()) }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.ics_import_title), style = MaterialTheme.typography.titleLarge)
        val loaded = file?.getOrNull()
        when {
            file == null -> Text(stringResource(R.string.ics_reading))
            loaded == null -> Text(stringResource(R.string.ics_unreadable), color = MaterialTheme.colorScheme.error)
            else -> {
                val found = loaded.events.size
                Text(pluralStringResource(R.plurals.ics_found, found, found), style = MaterialTheme.typography.bodyLarge)
                if (loaded.skipped > 0) Text(pluralStringResource(R.plurals.ics_skipped, loaded.skipped, loaded.skipped))
                loaded.events.take(PREVIEW).forEach { Text("• " + it.title.ifBlank { stringResource(R.string.calendar_untitled) }) }
                if (loaded.events.size > PREVIEW) Text("…")
                CalendarPicker(calendars, target?.id) { id -> target = calendars.firstOrNull { it.id == id } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
            Button(
                onClick = { val t = target; if (loaded != null && t != null) onImport(loaded, t) },
                enabled = loaded != null && loaded.events.isNotEmpty() && target != null,
            ) { Text(stringResource(R.string.ics_import)) }
        }
    }
}

private const val PREVIEW = 5
private const val BUFFER = 64 * 1024
