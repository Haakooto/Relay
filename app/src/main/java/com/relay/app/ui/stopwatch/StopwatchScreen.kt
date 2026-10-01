package com.relay.app.ui.stopwatch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.relay.app.R
import com.relay.app.stopwatch.LapView
import com.relay.app.stopwatch.StopwatchView
import com.relay.app.ui.timers.TextDialog
import com.relay.app.util.DurationText
import androidx.compose.runtime.withFrameMillis

@Composable
fun StopwatchScreen(viewModel: StopwatchViewModel = viewModel()) {
    val watches by viewModel.stopwatches.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<StopwatchView?>(null) }
    var editingLap by remember { mutableStateOf<LapView?>(null) }

    // Frame-synced clock, only while something is running: smooth hundredths, no idle cost.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val anyRunning = watches.orEmpty().any { it.running }
    LaunchedEffect(anyRunning) {
        while (anyRunning) {
            withFrameMillis { }
            now = System.currentTimeMillis()
        }
        now = System.currentTimeMillis()
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(stringResource(R.string.pane_stopwatch), style = MaterialTheme.typography.headlineMedium) }
            val list = watches
            if (list != null && list.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.stopwatch_empty), style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }
            list.orEmpty().forEach { view ->
                item(key = view.stopwatch.id) {
                    StopwatchCard(
                        view, now,
                        onLap = { viewModel.lap(view.stopwatch.id) },
                        onPause = { viewModel.pause(view.stopwatch.id) },
                        onResume = { viewModel.resume(view.stopwatch.id) },
                        onRestart = { viewModel.restart(view.stopwatch.id) },
                        onRename = { renaming = view },
                        onDelete = { viewModel.delete(view.stopwatch.id) },
                        onLapClick = { editingLap = it },
                    )
                }
            }
        }
        FloatingActionButton(onClick = { adding = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.stopwatch_add))
        }
    }

    if (adding) NewStopwatchDialog(onDismiss = { adding = false }) { name, t0 -> viewModel.create(name, t0); adding = false }
    renaming?.let { view ->
        TextDialog(stringResource(R.string.stopwatch_rename), view.stopwatch.name, onDismiss = { renaming = null }, allowBlank = true) {
            viewModel.rename(view.stopwatch.id, it); renaming = null
        }
    }
    editingLap?.let { lap ->
        TextDialog(stringResource(R.string.stopwatch_lap_note, lap.lap.number), lap.lap.note, onDismiss = { editingLap = null }, allowBlank = true) {
            viewModel.setLapNote(lap.lap.id, it); editingLap = null
        }
    }
}

@Composable
private fun StopwatchCard(
    view: StopwatchView, now: Long,
    onLap: () -> Unit, onPause: () -> Unit, onResume: () -> Unit, onRestart: () -> Unit,
    onRename: () -> Unit, onDelete: () -> Unit, onLapClick: (LapView) -> Unit,
) {
    val s = view.stopwatch
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    s.name.ifBlank { stringResource(R.string.pane_stopwatch) },
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).clickable(onClick = onRename),
                )
                IconButton(onClick = onRename) { Icon(Icons.Outlined.Edit, stringResource(R.string.stopwatch_rename)) }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
            }
            Text(DurationText.stopwatch(view.elapsed(now)), fontSize = 44.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
            if (s.t0Millis > 0) {
                Text(stringResource(R.string.stopwatch_started_at, DurationText.stopwatch(s.t0Millis)), style = MaterialTheme.typography.labelSmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (view.running) {
                    Button(onClick = onLap) { Text(stringResource(R.string.stopwatch_lap)) }
                    OutlinedButton(onClick = onPause) { Text(stringResource(R.string.run_pause)) }
                } else {
                    Button(onClick = onResume) { Text(stringResource(R.string.run_resume)) }
                }
                TextButton(onClick = onRestart) { Text(stringResource(R.string.run_restart)) }
            }
            if (view.laps.isNotEmpty()) HorizontalDivider()
            view.laps.forEach { lap ->
                Column(Modifier.fillMaxWidth().clickable { onLapClick(lap) }.padding(vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("#${lap.lap.number}", fontFamily = FontFamily.Monospace, modifier = Modifier.width(48.dp))
                        Text(DurationText.stopwatch(lap.deltaMillis), fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                        Text(
                            DurationText.stopwatch(lap.lap.elapsedMillis), fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                    Text(
                        lap.lap.note.ifBlank { stringResource(R.string.stopwatch_add_note) },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (lap.lap.note.isBlank()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 48.dp),
                    )
                }
            }
        }
    }
}

/** Optional name and starting offset (t0), then it starts at once. */
@Composable
private fun NewStopwatchDialog(onDismiss: () -> Unit, onStart: (String, Long) -> Unit) {
    var name by remember { mutableStateOf("") }
    var h by remember { mutableStateOf("") }
    var m by remember { mutableStateOf("") }
    var s by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stopwatch_new)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.stopwatch_name)) }, singleLine = true)
                Text(stringResource(R.string.stopwatch_t0), style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Triple(h, { v: String -> h = v }, "h"),
                        Triple(m, { v: String -> m = v }, "m"),
                        Triple(s, { v: String -> s = v }, "s"),
                    ).forEach { (value, set, unit) ->
                        OutlinedTextField(
                            value, { set(it.filter(Char::isDigit).take(3)) },
                            label = { Text(unit) }, singleLine = true, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val t0 = ((h.toLongOrNull() ?: 0) * 3600 + (m.toLongOrNull() ?: 0) * 60 + (s.toLongOrNull() ?: 0)) * 1000
                onStart(name, t0)
            }) { Text(stringResource(R.string.action_start)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
