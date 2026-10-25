package com.relay.app.ui.stopwatch

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.relay.app.R
import com.relay.app.guide.GuideSection
import com.relay.app.stopwatch.CorrectionKind
import com.relay.app.stopwatch.LapView
import com.relay.app.stopwatch.StopwatchView
import com.relay.app.ui.common.PaneHeader
import com.relay.app.util.DurationText

/** What dialog the pane is showing, if any. */
private sealed interface Dialog {
    data object New : Dialog
    data class Edit(val view: StopwatchView) : Dialog
    data class LapDetails(val view: StopwatchView, val lap: LapView) : Dialog
    data class Correct(val view: StopwatchView, val lap: LapView, val kind: CorrectionKind) : Dialog
    data class History(val id: String) : Dialog
    data class Chart(val id: String) : Dialog
}

@Composable
fun StopwatchScreen(viewModel: StopwatchViewModel = viewModel()) {
    val watches by viewModel.stopwatches.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<Dialog?>(null) }

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
            item {
                PaneHeader(R.string.pane_stopwatch, GuideSection.STOPWATCH)
            }
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
                    StopwatchCard(view, now, viewModel, onDialog = { dialog = it })
                }
            }
        }
        FloatingActionButton(onClick = { dialog = Dialog.New }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.stopwatch_add))
        }
    }

    val close = { dialog = null }
    when (val d = dialog) {
        null -> {}
        Dialog.New -> NewStopwatchDialog(close) { name, t0, tags -> viewModel.create(name, t0, tags); close() }
        is Dialog.Edit -> EditStopwatchDialog(d.view.stopwatch.name, d.view.tags, close) { name, tags ->
            viewModel.updateDetails(d.view.stopwatch.id, name, tags); close()
        }
        is Dialog.LapDetails -> LapDetailsDialog(d.lap, d.view.tags, close) { note, tags -> viewModel.setLapDetails(d.lap.lap.id, note, tags); close() }
        is Dialog.Correct -> CorrectionDialog(d.kind, d.lap, close) { amount, label, onError ->
            val id = d.view.stopwatch.id
            val lapId = d.lap.lap.id
            val done: (com.relay.app.stopwatch.EditError?) -> Unit = { e -> if (e == null) close() else onError(e) }
            when (d.kind) {
                CorrectionKind.REMOVE -> viewModel.removeLap(id, lapId, label, done)
                CorrectionKind.INSERT -> viewModel.insertLap(id, lapId, amount, label, done)
                CorrectionKind.ADJUST -> viewModel.adjustLap(id, lapId, amount, label, done)
            }
        }
        is Dialog.History -> watches?.firstOrNull { it.stopwatch.id == d.id }?.let { v ->
            CorrectionsDialog(v.corrections, v.canUndo, v.canRedo, { viewModel.undo(d.id) }, { viewModel.redo(d.id) }, close)
        } ?: close()
        is Dialog.Chart -> watches?.firstOrNull { it.stopwatch.id == d.id }?.let { v -> LapChartDialog(v, close) } ?: close()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StopwatchCard(view: StopwatchView, now: Long, viewModel: StopwatchViewModel, onDialog: (Dialog) -> Unit) {
    val s = view.stopwatch
    val id = s.id
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    s.name.ifBlank { stringResource(R.string.pane_stopwatch) },
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).clickable { onDialog(Dialog.Edit(view)) },
                )
                if (view.canUndo) IconButton(onClick = { viewModel.undo(id) }) { Icon(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.action_undo)) }
                if (view.canRedo) IconButton(onClick = { viewModel.redo(id) }) { Icon(Icons.AutoMirrored.Outlined.Redo, stringResource(R.string.action_redo)) }
                if (view.laps.isNotEmpty()) {
                    IconButton(onClick = { onDialog(Dialog.Chart(id)) }) { Icon(Icons.Outlined.BarChart, stringResource(R.string.stopwatch_chart_title)) }
                }
                IconButton(onClick = { onDialog(Dialog.Edit(view)) }) { Icon(Icons.Outlined.Edit, stringResource(R.string.stopwatch_edit)) }
                IconButton(onClick = { viewModel.delete(id) }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
            }
            Text(DurationText.signedStopwatch(view.elapsed(now)), fontSize = 44.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
            if (s.t0Millis != 0L) {
                Text(stringResource(R.string.stopwatch_started_at, DurationText.signedStopwatch(s.t0Millis)), style = MaterialTheme.typography.labelSmall)
            }
            // First line: the main controls. Second line: one-tap tagged laps.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (view.running) {
                    Button(onClick = { viewModel.lap(id) }) { Text(stringResource(R.string.stopwatch_lap)) }
                    OutlinedButton(onClick = { viewModel.pause(id) }) { Text(stringResource(R.string.run_pause)) }
                } else {
                    Button(onClick = { viewModel.resume(id) }) { Text(stringResource(R.string.run_resume)) }
                }
                TextButton(onClick = { viewModel.restart(id) }) { Text(stringResource(R.string.run_restart)) }
            }
            if (view.running && view.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    view.tags.forEach { tag ->
                        OutlinedButton(onClick = { viewModel.lap(id, listOf(tag)) }) { Text(stringResource(R.string.stopwatch_lap_tagged, tag)) }
                    }
                }
            }
            if (view.laps.isNotEmpty()) {
                HorizontalDivider()
                LapHeader()
                view.laps.forEach { lap -> LapRow(lap, onClick = { onDialog(Dialog.LapDetails(view, lap)) }, onStar = { onDialog(Dialog.History(id)) }) { kind ->
                    onDialog(Dialog.Correct(view, lap, kind))
                } }
            }
            if (view.corrections.isNotEmpty()) {
                TextButton(onClick = { onDialog(Dialog.History(id)) }) {
                    Icon(Icons.Outlined.History, null, Modifier.size(18.dp).padding(end = 4.dp))
                    Text(stringResource(R.string.corrections_count, view.corrections.count { !it.correction.undone }))
                }
            }
        }
    }
}

private val NUMBER_WIDTH = 56.dp
private val ACTION_WIDTH = 40.dp

@Composable
private fun LapHeader() {
    val faint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.lap_col_lap), style = MaterialTheme.typography.labelSmall, color = faint, modifier = Modifier.width(NUMBER_WIDTH))
        Text(stringResource(R.string.lap_col_time), style = MaterialTheme.typography.labelSmall, color = faint, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.lap_col_total), style = MaterialTheme.typography.labelSmall, color = faint)
        Box(Modifier.width(ACTION_WIDTH))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LapRow(lap: LapView, onClick: () -> Unit, onStar: () -> Unit, onCorrect: (CorrectionKind) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.width(NUMBER_WIDTH), verticalAlignment = Alignment.CenterVertically) {
                Text("#${lap.lap.number}", fontFamily = FontFamily.Monospace)
                // Marks a lap whose time was corrected; tap for the history.
                if (lap.edited) Text(" ★", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(onClick = onStar))
            }
            Text(DurationText.stopwatch(lap.deltaMillis), fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
            Text(
                DurationText.signedStopwatch(lap.lap.elapsedMillis), fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Box(Modifier.width(ACTION_WIDTH)) {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.Edit, stringResource(R.string.correction_menu), Modifier.size(18.dp)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.correction_remove)) }, onClick = { menu = false; onCorrect(CorrectionKind.REMOVE) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.correction_insert)) }, onClick = { menu = false; onCorrect(CorrectionKind.INSERT) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.correction_adjust)) }, onClick = { menu = false; onCorrect(CorrectionKind.ADJUST) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.stopwatch_note_and_tags)) }, onClick = { menu = false; onClick() })
                }
            }
        }
        // Tags as small boxes; the note on its own line, in italic serif, so the two never blur.
        if (lap.tags.isNotEmpty()) {
            FlowRow(
                Modifier.padding(start = NUMBER_WIDTH, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                lap.tags.forEach { tag ->
                    Text(
                        tag,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
        if (lap.lap.note.isNotBlank()) {
            Text(
                lap.lap.note,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                modifier = Modifier.padding(start = NUMBER_WIDTH, bottom = 4.dp),
            )
        }
    }
}
