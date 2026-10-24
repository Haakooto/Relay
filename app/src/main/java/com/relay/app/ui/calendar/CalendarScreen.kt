package com.relay.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.relay.app.R
import com.relay.app.data.event.EventKind
import com.relay.app.data.schedule.TargetKind
import com.relay.app.event.CalendarEntry
import com.relay.app.event.EntryKind
import com.relay.app.event.GRID_DAYS
import com.relay.app.guide.GuideSection
import com.relay.app.ui.common.PaneHeader
import com.relay.app.util.AppLocale
import com.relay.app.util.RelativeText
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

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
    var adding by remember { mutableStateOf(false) }
    val dayEntries = s.entries[s.selected].orEmpty()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { PaneHeader(R.string.pane_calendar, GuideSection.CALENDAR) }
            item { ShowFilter(s.showAll, viewModel::setShowAll) }
            item { MonthBar(s, viewModel) }
            item { MonthGrid(s, viewModel) }
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
            items(dayEntries, key = { "${it.kind}:${it.id}" }) { entry ->
                EntryRow(entry, s.is24h) { if (entry.kind == EntryKind.ALARM) onOpenAlarm(entry.id) else onOpenItem(entry.id) }
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            FloatingActionButton(onClick = { adding = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.calendar_add))
            }
            DropdownMenu(expanded = adding, onDismissRequest = { adding = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.calendar_new_event)) },
                    onClick = { adding = false; onNewItem(EventKind.EVENT, s.selected) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.calendar_new_reminder)) },
                    onClick = { adding = false; onNewItem(EventKind.REMINDER, s.selected) },
                )
            }
        }
    }
}

/** Events and reminders only, or everything scheduled (alarms too). Remembered in Settings. */
@Composable
private fun ShowFilter(showAll: Boolean, onChange: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !showAll, onClick = { onChange(false) }, label = { Text(stringResource(R.string.calendar_filter_mine)) })
        FilterChip(selected = showAll, onClick = { onChange(true) }, label = { Text(stringResource(R.string.calendar_filter_all)) })
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

/** Six weeks; swipe sideways to turn the month. Days outside the month are dimmed but tappable. */
@Composable
private fun MonthGrid(s: CalendarState, viewModel: CalendarViewModel) {
    var drag by remember { mutableFloatStateOf(0f) }
    Column(
        Modifier.fillMaxWidth().pointerInput(s.month) {
            detectHorizontalDragGestures(
                onDragStart = { drag = 0f },
                onDragEnd = {
                    when {
                        drag < -SWIPE_PX -> viewModel.showMonth(s.month.plusMonths(1))
                        drag > SWIPE_PX -> viewModel.showMonth(s.month.minusMonths(1))
                    }
                },
            ) { _, dx -> drag += dx }
        },
    ) {
        Row {
            RelativeText.weekOrder(s.firstDay).forEach { day ->
                Text(
                    day.getDisplayName(TextStyle.SHORT, AppLocale.formatting),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        for (week in 0 until (GRID_DAYS / 7).toInt()) {
            Row {
                for (d in 0 until 7) {
                    DayCell(s, s.gridStart.plusDays(week * 7L + d), viewModel::select)
                }
            }
        }
    }
}

/** One day: its number (filled when selected, ringed when today) and up to three dots for its entries. */
@Composable
private fun RowScope.DayCell(s: CalendarState, day: LocalDate, onSelect: (LocalDate) -> Unit) {
    val count = s.entries[day]?.size ?: 0
    val isSelected = day == s.selected
    val label = RelativeText.date(LocalContext.current, day, s.today)
    val description = pluralStringResource(R.plurals.calendar_day_items, count, label, count)
    Column(
        Modifier.weight(1f).heightIn(min = 48.dp)
            .clickable { onSelect(day) }
            .clearAndSetSemantics { contentDescription = description; selected = isSelected },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val ring = if (day == s.today && !isSelected) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier
        Box(
            Modifier.padding(top = 4.dp).size(32.dp)
                .then(if (isSelected) Modifier.background(MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                .then(ring),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (day == s.today) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    day.month == s.month.month -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                },
            )
        }
        Row(Modifier.padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(minOf(count, MAX_DOTS)) {
                Box(Modifier.size(5.dp).background(MaterialTheme.colorScheme.secondary, CircleShape))
            }
        }
    }
}

@Composable
private fun EntryRow(entry: CalendarEntry, is24h: Boolean, onClick: () -> Unit) {
    val time = if (entry.continued) {
        stringResource(R.string.calendar_continues)
    } else {
        CalendarText.span(entry.startMinute, entry.endMinute, is24h)
    }
    val title = entry.title.ifBlank {
        stringResource(
            when (entry.kind) {
                EntryKind.ALARM -> R.string.calendar_alarm
                EntryKind.REMINDER -> R.string.calendar_kind_reminder
                EntryKind.EVENT -> R.string.calendar_untitled
            },
        )
    }
    val detail = when (entry.kind) {
        EntryKind.EVENT -> entry.reminderLead?.let {
            stringResource(R.string.calendar_reminder_lead, CalendarText.lead(it, entry.startMinute == null && !entry.continued, is24h))
        }
        EntryKind.REMINDER -> stringResource(R.string.calendar_kind_reminder)
        EntryKind.ALARM ->
            stringResource(if (entry.target == TargetKind.RING) R.string.calendar_alarm else R.string.calendar_scheduled_start)
    }
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(time, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(112.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                detail?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private const val MAX_DOTS = 3
private const val SWIPE_PX = 120f
