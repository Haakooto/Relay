package com.relay.app.ui.calendar

import android.Manifest
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.relay.app.R
import com.relay.app.data.event.CalendarEntity
import com.relay.app.data.event.EventKind
import com.relay.app.event.CalendarEntry
import com.relay.app.event.EntryKind
import com.relay.app.guide.GuideSection
import com.relay.app.phone.PhoneCalendars
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
    val open = rememberEntryOpener(s.calendars, onOpenItem, onOpenAlarm)
    if (managing) CalendarsSheetFor(s, viewModel, onDismiss = { managing = false })
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
                    onOpen = open,
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
                EntryRow(entry, s.is24h) { open(entry) }
            }
        }
        AddButton(Modifier.align(Alignment.BottomEnd)) { kind -> onNewItem(kind, s.selected) }
    }
}

/** The calendars sheet with what it needs: calendar access for phone calendars (#16), and a file picker for import (#33). */
@Composable
private fun CalendarsSheetFor(s: CalendarState, viewModel: CalendarViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // Phone calendars (#16): asked for only from here, when the user wants them.
    val askAccess = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.accessChanged()
    }
    // Calendar files (#33): the picked file goes to the import screen, which checks it.
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        context.startActivity(Intent(context, IcsImportActivity::class.java).setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
    CalendarsSheet(
        s.calendars, PhoneSheetState(s.phoneCalendars, s.shownPhone, s.canReadPhone, viewModel.canWritePhone()),
        onDismiss = onDismiss, onShown = { id, shown -> viewModel.setShown(id, shown) },
        onPhoneShown = { id, shown -> viewModel.setPhoneShown(id, shown) },
        onRequestAccess = { askAccess.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)) },
        onSave = viewModel::saveCalendar, onDelete = { id, moveTo -> viewModel.deleteCalendar(id, moveTo) },
        onImport = { pickFile.launch(arrayOf("text/calendar", "application/ics", "application/octet-stream", "text/plain")) },
        onExport = { calendar -> viewModel.exportCalendar(context, calendar, calendarName(context, calendar)) },
        subscriptions = SubscriptionActions(
            subscribe = { name, url, color -> viewModel.subscribe(name, url, color) { n -> fetchedToast(context, n) } },
            refresh = { calendar -> viewModel.refreshSubscription(calendar) { n -> fetchedToast(context, n) } },
            unsubscribe = viewModel::unsubscribe,
        ),
    )
}

/** "Fetched 12 events", or that it couldn't be fetched. */
private fun fetchedToast(context: Context, count: Int?) {
    val text = count?.let { context.resources.getQuantityString(R.plurals.subscribe_fetched, it, it) }
        ?: context.getString(R.string.subscribe_failed)
    Toast.makeText(context, text, Toast.LENGTH_LONG).show()
}

/** What tapping an entry does: its editor, the alarm editor, or for a phone event, its calendar app. */
@Composable
private fun rememberEntryOpener(
    calendars: List<CalendarEntity>,
    onOpenItem: (String) -> Unit,
    onOpenAlarm: (String) -> Unit,
): (CalendarEntry) -> Unit {
    val context = LocalContext.current
    val subscribed = calendars.filter { it.isSubscribed }.map { it.id }.toSet()
    return { entry ->
        when {
            entry.kind == EntryKind.ALARM -> onOpenAlarm(entry.id)
            entry.kind == EntryKind.PHONE ->
                entry.id.toLongOrNull()?.let { runCatching { context.startActivity(PhoneCalendars.viewIntent(it)) } }
            // A subscribed calendar's items come from its address (#33): there's nothing to edit.
            entry.calendarId in subscribed -> Toast.makeText(context, R.string.subscription_read_only, Toast.LENGTH_SHORT).show()
            else -> onOpenItem(entry.id)
        }
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
