package com.relay.app.ui.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.relay.app.R
import com.relay.app.data.event.EventKind
import com.relay.app.event.EntryKind
import com.relay.app.guide.GuideSection
import com.relay.app.ui.common.PaneHeader
import com.relay.app.util.AppLocale
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The Calendar pane (spec §10b): a month grid with dots on days that have entries, and the
 * selected day's entries below. The + makes an event or a reminder on the selected day.
 */
@Composable
fun CalendarScreen(
    onOpenItem: (String) -> Unit,
    onNewItem: (EventKind, LocalDate) -> Unit,
    onOpenAlarm: (String) -> Unit,
    viewModel: CalendarViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state ?: return
    var managing by remember { mutableStateOf(false) }
    if (managing) {
        CalendarsSheet(
            s.calendars, onDismiss = { managing = false }, onShown = { id, shown -> viewModel.setShown(id, shown) },
            onSave = viewModel::saveCalendar, onDelete = { id, moveTo -> viewModel.deleteCalendar(id, moveTo) },
        )
    }
    val dayEntries = s.entries[s.selected].orEmpty()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                PaneHeader(R.string.pane_calendar, GuideSection.CALENDAR) {
                    IconButton(onClick = { managing = true }) {
                        Icon(Icons.Outlined.Layers, contentDescription = stringResource(R.string.calendars_title))
                    }
                }
            }
            item { MonthBar(s, viewModel) }
            item {
                MonthGrid(
                    s, onSelect = viewModel::select, onTurn = { viewModel.showMonth(s.month.plusMonths(it)) },
                    onOpenItem = onOpenItem,
                )
            }
            item {
                Text(
                    s.selected.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(AppLocale.formatting)),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (dayEntries.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.calendar_day_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }
            items(dayEntries, key = { "${it.kind}:${it.id}:${it.spanStart}" }) { entry ->
                EntryRow(entry, s.is24h) { if (entry.kind == EntryKind.ALARM) onOpenAlarm(entry.id) else onOpenItem(entry.id) }
            }
        }
        AddButton(Modifier.align(Alignment.BottomEnd)) { kind -> onNewItem(kind, s.selected) }
    }
}

/** + : a new event or reminder. */
@Composable
private fun AddButton(modifier: Modifier, onNew: (EventKind) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    Box(modifier.padding(16.dp)) {
        FloatingActionButton(onClick = { adding = true }) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.calendar_add))
        }
        DropdownMenu(expanded = adding, onDismissRequest = { adding = false }) {
            NEW_ITEMS.forEach { (kind, label) ->
                DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { adding = false; onNew(kind) })
            }
        }
    }
}

@Composable
private fun MonthBar(s: CalendarState, viewModel: CalendarViewModel) {
    var picking by remember { mutableStateOf(false) }
    if (picking) {
        MonthPickerDialog(s.month, onDismiss = { picking = false }) { m ->
            picking = false
            viewModel.jumpTo(m)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { viewModel.showMonth(s.month.minusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = stringResource(R.string.calendar_prev_month))
        }
        Text(
            CalendarText.month(s.month),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                .clickable(onClickLabel = stringResource(R.string.calendar_pick_month)) { picking = true }
                .wrapContentHeight(),
        )
        if (s.selected != s.today) {
            TextButton(onClick = { viewModel.select(s.today) }) { Text(stringResource(R.string.calendar_go_today)) }
        }
        IconButton(onClick = { viewModel.showMonth(s.month.plusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = stringResource(R.string.calendar_next_month))
        }
    }
}

/** The + menu: what it makes, and its label. */
private val NEW_ITEMS = listOf(
    EventKind.EVENT to R.string.calendar_new_event,
    EventKind.REMINDER to R.string.calendar_new_reminder,
)
