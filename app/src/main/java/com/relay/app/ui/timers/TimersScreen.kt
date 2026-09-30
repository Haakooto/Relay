package com.relay.app.ui.timers

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.relay.app.R
import com.relay.app.data.library.LibraryHeaderEntity
import com.relay.app.library.BlockKind
import com.relay.app.library.LibraryItem
import com.relay.app.ui.alarms.ReadinessBanner
import com.relay.app.util.DurationText
import kotlinx.coroutines.delay

private sealed interface LibraryRow {
    val key: String

    data class HeaderRow(val header: LibraryHeaderEntity) : LibraryRow {
        override val key get() = "header:${header.id}"
    }

    data class BlockRow(val item: LibraryItem) : LibraryRow {
        override val key get() = "${item.kind}:${item.id}"
    }
}

/** Ungrouped blocks first (where new ones land), then each header and its blocks in order. */
private fun buildRows(items: List<LibraryItem>, headers: List<LibraryHeaderEntity>): List<LibraryRow> = buildList {
    items.filter { it.headerId == null }.forEach { add(LibraryRow.BlockRow(it)) }
    for (header in headers) {
        add(LibraryRow.HeaderRow(header))
        items.filter { it.headerId == header.id }.forEach { add(LibraryRow.BlockRow(it)) }
    }
}

/** Fractional-indexing helper (from v0.5): a value strictly between the new neighbours. */
private fun sortOrderBetween(before: Long?, after: Long?): Long = when {
    before != null && after != null -> before + (after - before) / 2
    before != null -> before + 1_000_000L
    after != null -> after - 1_000_000L
    else -> System.currentTimeMillis()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimersScreen(onOpenTimer: (String?) -> Unit, onOpenRelay: (String?) -> Unit, viewModel: TimersViewModel = viewModel()) {
    val context = LocalContext.current
    val runs by viewModel.activeRuns.collectAsStateWithLifecycle()
    val state by viewModel.libraryState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var addingHeader by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<LibraryHeaderEntity?>(null) }

    LaunchedEffect(Unit) { viewModel.errors.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() } }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }

    val headers = state?.headers.orEmpty()
    val items = state?.items.orEmpty()
    // Live-reorderable copy, only resynced while nothing is being dragged (v0.5 pattern).
    val rows = remember { mutableStateListOf<LibraryRow>() }
    var draggedKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<String, Int>() }
    LaunchedEffect(items, headers) {
        if (draggedKey == null) {
            rows.clear()
            rows.addAll(buildRows(items, headers))
        }
    }

    fun persistDragged(key: String) {
        val index = rows.indexOfFirst { it.key == key }
        val row = rows.getOrNull(index) as? LibraryRow.BlockRow ?: return
        val headerId = (index - 1 downTo 0).map { rows[it] }.filterIsInstance<LibraryRow.HeaderRow>().firstOrNull()?.header?.id
        val prev = (rows.getOrNull(index - 1) as? LibraryRow.BlockRow)?.item?.sortOrder
        val next = (rows.getOrNull(index + 1) as? LibraryRow.BlockRow)?.item?.sortOrder
        viewModel.moveBlock(row.item, headerId, sortOrderBetween(prev, next))
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(stringResource(R.string.pane_timers), style = MaterialTheme.typography.headlineMedium) }
            item { ReadinessBanner() }
            runs.forEach { view ->
                item(key = "run:${view.run.id}") {
                    val id = view.run.id
                    RunCard(
                        view, now,
                        RunActions(
                            pause = { viewModel.pause(id) }, resume = { viewModel.resume(id) }, reset = { viewModel.reset(id) },
                            addMinute = { viewModel.addMinute(id) }, stop = { viewModel.stop(id) },
                            continueRun = { viewModel.continueRun(id) }, restart = { viewModel.restart(id) },
                        ),
                    )
                }
            }
            item { Text(stringResource(R.string.timers_library), style = MaterialTheme.typography.titleLarge) }
            if (state != null && rows.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.timers_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }
            for (row in rows.toList()) {
                when (row) {
                    is LibraryRow.HeaderRow -> item(key = row.key) {
                        val index = headers.indexOfFirst { it.id == row.header.id }
                        HeaderRowView(
                            title = row.header.title,
                            canMoveUp = index > 0,
                            canMoveDown = index in 0 until headers.lastIndex,
                            onMoveUp = {
                                viewModel.moveHeader(row.header, sortOrderBetween(headers.getOrNull(index - 2)?.sortOrder, headers[index - 1].sortOrder))
                            },
                            onMoveDown = {
                                viewModel.moveHeader(row.header, sortOrderBetween(headers[index + 1].sortOrder, headers.getOrNull(index + 2)?.sortOrder))
                            },
                            onRename = { renaming = row.header },
                            onDelete = { viewModel.deleteHeader(row.header.id) },
                        )
                    }
                    is LibraryRow.BlockRow -> item(key = row.key) {
                        val key = row.key
                        BlockCard(
                            item = row.item,
                            dragOffset = if (draggedKey == key) dragOffset else 0f,
                            dragged = draggedKey == key,
                            onClick = { if (row.item.kind == BlockKind.TIMER) onOpenTimer(row.item.id) else onOpenRelay(row.item.id) },
                            onStart = { viewModel.start(row.item) },
                            onHeight = { heights[key] = it },
                            onDragStart = { draggedKey = key; dragOffset = 0f },
                            onDragEnd = { persistDragged(key); draggedKey = null; dragOffset = 0f },
                            onDragCancel = { draggedKey = null; dragOffset = 0f },
                            onDrag = { dy ->
                                dragOffset += dy
                                val my = heights[key]
                                val i = rows.indexOfFirst { it.key == key }
                                if (my != null && i >= 0) {
                                    if (dragOffset > my / 2 && i < rows.lastIndex) {
                                        val h = heights[rows[i + 1].key] ?: my
                                        rows.add(i + 1, rows.removeAt(i))
                                        dragOffset -= h
                                    } else if (dragOffset < -my / 2 && i > 0) {
                                        val h = heights[rows[i - 1].key] ?: my
                                        rows.add(i - 1, rows.removeAt(i))
                                        dragOffset += h
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }

        Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            FloatingActionButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.timers_add))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                Text(
                    stringResource(R.string.timers_quick_start),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                FlowRow(
                    modifier = Modifier.padding(horizontal = 12.dp).widthIn(max = 280.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    (settings?.quickTimerSeconds ?: emptyList()).forEach { seconds ->
                        AssistChip(
                            onClick = { menuOpen = false; viewModel.quickStart(seconds) },
                            label = { Text(DurationText.short(seconds)) },
                        )
                    }
                }
                HorizontalDivider()
                DropdownMenuItem(text = { Text(stringResource(R.string.timers_new_timer)) }, onClick = { menuOpen = false; onOpenTimer(null) })
                DropdownMenuItem(text = { Text(stringResource(R.string.timers_new_relay)) }, onClick = { menuOpen = false; onOpenRelay(null) })
                DropdownMenuItem(text = { Text(stringResource(R.string.timers_new_header)) }, onClick = { menuOpen = false; addingHeader = true })
            }
        }
    }

    if (addingHeader) {
        TextDialog(stringResource(R.string.timers_new_header), "", onDismiss = { addingHeader = false }) {
            viewModel.addHeader(it); addingHeader = false
        }
    }
    renaming?.let { header ->
        TextDialog(stringResource(R.string.timers_rename_header), header.title, onDismiss = { renaming = null }) {
            viewModel.renameHeader(header, it); renaming = null
        }
    }
}

@Composable
private fun HeaderRowView(
    title: String, canMoveUp: Boolean, canMoveDown: Boolean,
    onMoveUp: () -> Unit, onMoveDown: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        IconButton(onClick = onMoveUp, enabled = canMoveUp) { Icon(Icons.Outlined.KeyboardArrowUp, stringResource(R.string.action_move_up)) }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) { Icon(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.action_move_down)) }
        IconButton(onClick = onRename) { Icon(Icons.Outlined.Edit, stringResource(R.string.timers_rename_header)) }
        IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
    }
}

/** Long-press and drag to move; the parent owns the reordering (from v0.5, approved on device). */
@Composable
private fun BlockCard(
    item: LibraryItem, dragOffset: Float, dragged: Boolean,
    onClick: () -> Unit, onStart: () -> Unit, onHeight: (Int) -> Unit,
    onDragStart: () -> Unit, onDragEnd: () -> Unit, onDragCancel: () -> Unit, onDrag: (Float) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (dragged) 1f else 0f)
            .graphicsLayer { translationY = dragOffset }
            .onGloballyPositioned { onHeight(it.size.height) }
            .clickable(onClick = onClick)
            .pointerInput(item.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragCancel,
                    onDrag = { change, amount -> change.consume(); onDrag(amount.y) },
                )
            },
    ) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.label, style = MaterialTheme.typography.titleLarge)
                val kind = stringResource(if (item.kind == BlockKind.RELAY) R.string.kind_relay else R.string.kind_timer)
                val total = item.totalSeconds?.let(DurationText::short) ?: stringResource(R.string.timers_broken)
                Text(
                    "$kind · $total",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.totalSeconds == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            IconButton(onClick = onStart, enabled = item.totalSeconds != null, modifier = Modifier.alpha(if (item.totalSeconds != null) 1f else 0.4f)) {
                Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.action_start))
            }
        }
    }
}

@Composable
fun TextDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text) }) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
