@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.clockr.app.ui.screens

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clockr.app.R
import com.clockr.app.data.ClockConfigEntity
import com.clockr.app.ui.clock.ClocksViewModel
import com.clockr.app.ui.clock.ClocksViewModelFactory
import com.clockr.app.ui.clock.NEW_CLOCK_ROUTE_ID

/** Fractional-indexing helper for up/down reordering — same convention as TimersScreen's. */
private fun sortOrderBetween(before: Long?, after: Long?): Long = when {
    before != null && after != null -> before + (after - before) / 2
    before != null -> before + 1_000_000L
    after != null -> after - 1_000_000L
    else -> System.currentTimeMillis()
}

/** The Clock pane — a small library of independently-styled clocks rather than one fixed face.
 *  Tapping a row shows its live full-screen face (that's this pane's whole point); editing its
 *  style is a separate explicit action, unlike the Timers library where tap opens the editor. */
@Composable
fun ClocksScreen(onClockClick: (String) -> Unit, onEditClick: (String) -> Unit) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: ClocksViewModel = viewModel(factory = ClocksViewModelFactory(application))
    val clocks by viewModel.clocks.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.clocks_title)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEditClick(NEW_CLOCK_ROUTE_ID) }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.clocks_new_desc))
            }
        },
    ) { padding ->
        if (clocks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.clocks_empty))
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                itemsIndexed(clocks, key = { _, clock -> clock.id }) { index, clock ->
                    ClockRow(
                        clock = clock,
                        canMoveUp = index > 0,
                        canMoveDown = index < clocks.lastIndex,
                        onMoveUp = {
                            val prev = clocks[index - 1]
                            val prevPrev = clocks.getOrNull(index - 2)
                            viewModel.moveTo(clock.id, sortOrderBetween(prevPrev?.sortOrder, prev.sortOrder))
                        },
                        onMoveDown = {
                            val next = clocks[index + 1]
                            val nextNext = clocks.getOrNull(index + 2)
                            viewModel.moveTo(clock.id, sortOrderBetween(next.sortOrder, nextNext?.sortOrder))
                        },
                        onClick = { onClockClick(clock.id) },
                        onEdit = { onEditClick(clock.id) },
                        onDelete = { viewModel.delete(clock.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ClockRow(
    clock: ClockConfigEntity,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            clock.name.ifBlank { stringResource(R.string.clocks_unnamed) },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(end = 4.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.clocks_move_up_desc))
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.clocks_move_down_desc))
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.clocks_edit_desc))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.clocks_delete_desc))
            }
        }
    }
}
