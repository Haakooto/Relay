package com.clockr.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clockr.app.R
import com.clockr.app.domain.BlockKind
import com.clockr.app.domain.LibraryHeader
import com.clockr.app.domain.LibraryItem
import com.clockr.app.run.ActiveRunSnapshot
import com.clockr.app.run.ActiveRunState
import com.clockr.app.run.RunPhase
import com.clockr.app.run.TimerRunService
import com.clockr.app.ui.theme.ClockrTheme
import com.clockr.app.ui.timers.NEW_CHAIN_ROUTE_ID
import com.clockr.app.ui.timers.NEW_TIMER_ROUTE_ID
import com.clockr.app.ui.timers.TimerLibraryViewModel
import com.clockr.app.util.formatDuration
import com.clockr.app.util.formatElapsedClock
import kotlinx.coroutines.delay

/** One row of the flattened, orderable library list — a header section title, or a block under
 *  (or above, if [LibraryItem.headerId] is null) one. */
private sealed interface LibraryRow {
    val key: String
    data class HeaderRow(val header: LibraryHeader) : LibraryRow {
        override val key get() = "h:${header.id}"
    }
    data class BlockRow(val item: LibraryItem) : LibraryRow {
        override val key get() = "b:${item.id}"
    }
}

/** Ungrouped blocks first (where new ones land), then each header and its blocks in order —
 *  both already sorted by sortOrder coming out of the ViewModel. */
private fun buildRows(items: List<LibraryItem>, headers: List<LibraryHeader>): List<LibraryRow> = buildList {
    items.filter { it.headerId == null }.forEach { add(LibraryRow.BlockRow(it)) }
    for (header in headers) {
        add(LibraryRow.HeaderRow(header))
        items.filter { it.headerId == header.id }.forEach { add(LibraryRow.BlockRow(it)) }
    }
}

/** Fractional-indexing helper for drag-drop and up/down reordering: a value strictly between its
 *  new neighbors, or spaced off the one neighbor that exists, or "now" if the section is empty. */
private fun sortOrderBetween(before: Long?, after: Long?): Long = when {
    before != null && after != null -> before + (after - before) / 2
    before != null -> before + 1_000_000L
    after != null -> after - 1_000_000L
    else -> System.currentTimeMillis()
}

@Composable
fun TimersScreen(modifier: Modifier = Modifier, onBlockClick: (String) -> Unit = {}) {
    val context = LocalContext.current
    val viewModel: TimerLibraryViewModel = viewModel()
    val items by viewModel.items.collectAsState()
    val headers by viewModel.headers.collectAsState()
    val activeRun by ActiveRunState.current.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }
    var showAddHeaderDialog by remember { mutableStateOf(false) }
    var renamingHeader by remember { mutableStateOf<LibraryHeader?>(null) }

    fun sendServiceAction(action: String) {
        context.startService(Intent(context, TimerRunService::class.java).setAction(action))
    }

    // Local, live-reorderable copy of the canonical (headers, items) state. Only resynced from
    // upstream while nothing is actively being dragged, so a drag in progress isn't clobbered by
    // a Flow re-emission (including the one our own persisted move triggers on drag end).
    val rows = remember { mutableStateListOf<LibraryRow>() }
    var draggedKey by remember { mutableStateOf<String?>(null) }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    val rowHeightsPx = remember { mutableStateMapOf<String, Int>() }

    LaunchedEffect(items, headers) {
        if (draggedKey == null) {
            val canonical = buildRows(items, headers)
            rows.clear()
            rows.addAll(canonical)
        }
    }

    fun persistDraggedBlock(key: String) {
        val finalIndex = rows.indexOfFirst { it.key == key }
        val row = rows.getOrNull(finalIndex) as? LibraryRow.BlockRow ?: return
        var newHeaderId: String? = null
        for (i in finalIndex - 1 downTo 0) {
            val r = rows[i]
            if (r is LibraryRow.HeaderRow) { newHeaderId = r.header.id; break }
        }
        val prevSortOrder = (rows.getOrNull(finalIndex - 1) as? LibraryRow.BlockRow)?.item?.sortOrder
        val nextSortOrder = (rows.getOrNull(finalIndex + 1) as? LibraryRow.BlockRow)?.item?.sortOrder
        val newSortOrder = sortOrderBetween(prevSortOrder, nextSortOrder)
        viewModel.moveBlock(row.item.id, row.item.kind, newHeaderId, newSortOrder)
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.timers_new_block_desc))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.timers_new_timer)) },
                        onClick = {
                            menuExpanded = false
                            onBlockClick(NEW_TIMER_ROUTE_ID)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.timers_new_chain)) },
                        onClick = {
                            menuExpanded = false
                            onBlockClick(NEW_CHAIN_ROUTE_ID)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.timers_new_header)) },
                        onClick = {
                            menuExpanded = false
                            showAddHeaderDialog = true
                        },
                    )
                }
            }
        },
    ) { padding ->
        if (items.isEmpty() && headers.isEmpty() && activeRun == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.timers_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                activeRun?.let { run ->
                    item {
                        ActiveRunCard(
                            run = run,
                            onStop = { sendServiceAction(TimerRunService.ACTION_STOP) },
                            onContinue = { sendServiceAction(TimerRunService.ACTION_CONTINUE) },
                            onCompleteStop = { sendServiceAction(TimerRunService.ACTION_COMPLETE_STOP) },
                            onCompleteRestart = { sendServiceAction(TimerRunService.ACTION_COMPLETE_RESTART) },
                            onCompleteAddMinute = { sendServiceAction(TimerRunService.ACTION_COMPLETE_ADD_MINUTE) },
                        )
                    }
                }
                item { Text(stringResource(R.string.timers_library_title), style = MaterialTheme.typography.titleLarge) }
                for (row in rows.toList()) {
                    when (row) {
                        is LibraryRow.HeaderRow -> item(key = row.key) {
                            val index = headers.indexOfFirst { it.id == row.header.id }
                            HeaderRowView(
                                header = row.header,
                                canMoveUp = index > 0,
                                canMoveDown = index in 0..(headers.lastIndex - 1),
                                onMoveUp = {
                                    val prev = headers[index - 1]
                                    val prevPrev = headers.getOrNull(index - 2)
                                    viewModel.moveHeader(row.header.id, sortOrderBetween(prevPrev?.sortOrder, prev.sortOrder))
                                },
                                onMoveDown = {
                                    val next = headers[index + 1]
                                    val nextNext = headers.getOrNull(index + 2)
                                    viewModel.moveHeader(row.header.id, sortOrderBetween(next.sortOrder, nextNext?.sortOrder))
                                },
                                onRename = { renamingHeader = row.header },
                                onDelete = { viewModel.deleteHeader(row.header.id) },
                            )
                        }
                        is LibraryRow.BlockRow -> item(key = row.key) {
                            val key = row.key
                            DraggableBlockCard(
                                block = row.item,
                                isDragged = draggedKey == key,
                                dragOffsetPx = if (draggedKey == key) dragOffsetPx else 0f,
                                onClick = { onBlockClick(row.item.id) },
                                onStart = { TimerRunService.start(context, row.item.id, row.item.kind) },
                                onHeightMeasured = { rowHeightsPx[key] = it },
                                onDragStart = { draggedKey = key; dragOffsetPx = 0f },
                                onDragEnd = {
                                    persistDraggedBlock(key)
                                    draggedKey = null
                                    dragOffsetPx = 0f
                                },
                                onDragCancel = { draggedKey = null; dragOffsetPx = 0f },
                                onDragDelta = { deltaY ->
                                    dragOffsetPx += deltaY
                                    val myHeight = rowHeightsPx[key]
                                    val currentIndex = rows.indexOfFirst { it.key == key }
                                    if (myHeight != null && currentIndex >= 0) {
                                        if (dragOffsetPx > myHeight / 2 && currentIndex < rows.lastIndex) {
                                            val neighborKey = rows[currentIndex + 1].key
                                            val neighborHeight = rowHeightsPx[neighborKey] ?: myHeight
                                            val moving = rows.removeAt(currentIndex)
                                            rows.add(currentIndex + 1, moving)
                                            dragOffsetPx -= neighborHeight
                                        } else if (dragOffsetPx < -myHeight / 2 && currentIndex > 0) {
                                            val neighborKey = rows[currentIndex - 1].key
                                            val neighborHeight = rowHeightsPx[neighborKey] ?: myHeight
                                            val moving = rows.removeAt(currentIndex)
                                            rows.add(currentIndex - 1, moving)
                                            dragOffsetPx += neighborHeight
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddHeaderDialog) {
        HeaderEditDialog(
            title = stringResource(R.string.timers_new_header_dialog_title),
            initialText = "",
            onConfirm = { viewModel.addHeader(it) },
            onDismiss = { showAddHeaderDialog = false },
        )
    }
    renamingHeader?.let { header ->
        HeaderEditDialog(
            title = stringResource(R.string.timers_rename_header_dialog_title),
            initialText = header.title,
            onConfirm = { viewModel.renameHeader(header.id, it) },
            onDismiss = { renamingHeader = null },
        )
    }
}

@Composable
private fun HeaderEditDialog(title: String, initialText: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.common_name_label)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) { onConfirm(text); onDismiss() } }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun HeaderRowView(
    header: LibraryHeader,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(header.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.timers_header_move_up_desc))
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.timers_header_move_down_desc))
            }
            IconButton(onClick = onRename) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.timers_header_rename_desc))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.timers_header_delete_desc))
            }
        }
    }
}

@Composable
private fun ActiveRunCard(
    run: ActiveRunSnapshot,
    onStop: () -> Unit,
    onContinue: () -> Unit,
    onCompleteStop: () -> Unit,
    onCompleteRestart: () -> Unit,
    onCompleteAddMinute: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(run.blockLabel, style = MaterialTheme.typography.titleLarge)
            val stepText = run.stepLabel ?: stringResource(R.string.run_step_fallback, run.stepIndex + 1)
            Text(
                if (run.stepCount > 1) {
                    stringResource(R.string.run_step_with_count, stepText, run.stepIndex + 1, run.stepCount)
                } else {
                    stepText
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            when (run.phase) {
                RunPhase.RUNNING -> {
                    var nowMillis by remember(run.stepEndAtMillis) { mutableStateOf(System.currentTimeMillis()) }
                    LaunchedEffect(run.stepEndAtMillis) {
                        while (true) {
                            nowMillis = System.currentTimeMillis()
                            delay(1000)
                        }
                    }
                    // Round up, not down: a freshly-started L-second step has already lost a few
                    // milliseconds to scheduling by the time this composes, so floor division was
                    // showing L-1 immediately — and floor also lands on a lingering "0" for the
                    // last second before the step actually ends and the alert fires.
                    val remainingMillis = (run.stepEndAtMillis - nowMillis).coerceAtLeast(0)
                    val remainingSeconds = (remainingMillis + 999) / 1000
                    Text(formatElapsedClock(remainingSeconds), style = MaterialTheme.typography.displayLarge)
                    TextButton(onClick = onStop) { Text(stringResource(R.string.action_stop)) }
                }
                RunPhase.AWAITING_INTERACTION -> {
                    Text(stringResource(R.string.run_awaiting_interaction), style = MaterialTheme.typography.bodyLarge)
                    Row {
                        TextButton(onClick = onContinue) { Text(stringResource(R.string.action_continue)) }
                        TextButton(onClick = onStop) { Text(stringResource(R.string.action_stop)) }
                    }
                }
                RunPhase.AWAITING_COMPLETION -> {
                    Text(stringResource(R.string.run_finished), style = MaterialTheme.typography.bodyLarge)
                    Row {
                        TextButton(onClick = onCompleteStop) { Text(stringResource(R.string.action_stop)) }
                        TextButton(onClick = onCompleteRestart) { Text(stringResource(R.string.action_restart)) }
                        TextButton(onClick = onCompleteAddMinute) { Text(stringResource(R.string.run_add_minute)) }
                    }
                }
            }
        }
    }
}

/**
 * Long-press-and-drag reorderable card. The parent owns the actual reordering (it has to — a
 * drag can move this card past its neighbors, which live outside this composable); this just
 * reports gesture events and renders wherever the parent says to, via [dragOffsetPx].
 */
@Composable
private fun DraggableBlockCard(
    block: LibraryItem,
    isDragged: Boolean,
    dragOffsetPx: Float,
    onClick: () -> Unit,
    onStart: () -> Unit,
    onHeightMeasured: (Int) -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDragDelta: (Float) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragged) 1f else 0f)
            .graphicsLayer { translationY = dragOffsetPx }
            .onGloballyPositioned { onHeightMeasured(it.size.height) }
            .clickable(onClick = onClick)
            .pointerInput(block.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragCancel,
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDragDelta(dragAmount.y)
                    },
                )
            },
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(block.label, style = MaterialTheme.typography.titleLarge)
                val kindLabel = stringResource(if (block.kind == BlockKind.CHAIN) R.string.block_kind_chain else R.string.block_kind_timer)
                Text(
                    stringResource(R.string.timers_block_summary, kindLabel, formatDuration(block.totalSeconds)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onStart) {
                Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.action_start))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TimersScreenPreview() {
    ClockrTheme { TimersScreen() }
}
