package com.relay.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.relay.app.R
import com.relay.app.data.event.CalendarEntity
import com.relay.app.data.event.RepeatKind
import com.relay.app.data.schedule.TargetKind
import com.relay.app.ui.clock.ColorChoice
import com.relay.app.ui.common.OptionChips
import java.util.UUID

/**
 * The calendars (#34): tick which the pane shows, tap the pencil to edit one, or add one. Below,
 * the phone's own calendars (#16), read-only, with a button to let Relay see them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarsSheet(
    calendars: List<CalendarEntity>,
    phone: PhoneSheetState,
    onDismiss: () -> Unit,
    onShown: (String, Boolean) -> Unit,
    onPhoneShown: (Long, Boolean) -> Unit,
    onRequestAccess: () -> Unit,
    onSave: (CalendarEntity) -> Unit,
    onDelete: (id: String, moveTo: String) -> Unit,
    onImport: () -> Unit,
    onExport: (CalendarEntity) -> Unit,
) {
    var editing by remember { mutableStateOf<CalendarEntity?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.calendars_title), style = MaterialTheme.typography.titleLarge)
            calendars.forEach { calendar ->
                Row(
                    Modifier.fillMaxWidth().clickable { onShown(calendar.id, !calendar.shown) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = calendar.shown, onCheckedChange = { onShown(calendar.id, it) })
                    Box(Modifier.size(14.dp).background(Color(calendar.color.toInt()), CircleShape))
                    Text(
                        calendarName(calendar), style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                    )
                    IconButton(onClick = { editing = calendar }) {
                        Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.calendar_edit_named, calendarName(calendar)))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(onClick = { editing = newCalendar(calendars) }) { Text(stringResource(R.string.calendars_new)) }
                // Calendar files (#33): pick one to import.
                OutlinedButton(onClick = onImport) { Text(stringResource(R.string.ics_import_file)) }
            }
            PhoneCalendarList(phone, onPhoneShown, onRequestAccess)
        }
    }
    editing?.let { calendar ->
        CalendarEditDialog(
            calendar = calendar,
            others = calendars.filter { it.id != calendar.id },
            phone = phone,
            onRequestAccess = onRequestAccess,
            isNew = calendars.none { it.id == calendar.id },
            onDismiss = { editing = null },
            onSave = { onSave(it); editing = null },
            onDelete = { moveTo -> onDelete(calendar.id, moveTo); editing = null },
            onExport = { onExport(calendar) },
        )
    }
}

/** The phone's calendars, ticked separately from Relay's; or a button to let Relay read them. */
@Composable
private fun PhoneCalendarList(phone: PhoneSheetState, onShown: (Long, Boolean) -> Unit, onRequestAccess: () -> Unit) {
    Text(
        stringResource(R.string.calendars_phone), style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 16.dp),
    )
    if (!phone.canRead) {
        Text(stringResource(R.string.calendars_phone_why), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onRequestAccess, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.calendars_phone_allow))
        }
        return
    }
    if (phone.calendars.isEmpty()) Text(stringResource(R.string.calendars_phone_none), style = MaterialTheme.typography.bodyMedium)
    phone.calendars.forEach { calendar ->
        val on = calendar.id in phone.shown
        Row(Modifier.fillMaxWidth().clickable { onShown(calendar.id, !on) }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = on, onCheckedChange = { onShown(calendar.id, it) })
            Box(Modifier.size(14.dp).background(Color(calendar.color.toInt()), CircleShape))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(calendar.name, style = MaterialTheme.typography.bodyLarge)
                Text(calendar.account, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A new calendar: the next unused palette colour, shown, a 15-minute notifying reminder. */
private fun newCalendar(existing: List<CalendarEntity>): CalendarEntity {
    val used = existing.map { it.color }.toSet()
    val color = CALENDAR_PALETTE.map { it.toArgb().toLong() }.firstOrNull { it !in used } ?: CALENDAR_PALETTE.first().toArgb().toLong()
    return CalendarEntity(
        UUID.randomUUID().toString(), "", color, sortOrder = 0, shown = true, defaultForAlarms = false,
        defaultLeadMinutes = NEW_CALENDAR_LEAD, defaultTarget = TargetKind.NOTIFY, defaultRepeat = RepeatKind.NONE,
    )
}

/** Name, colour and the defaults new items get. Delete asks where its items and alarms go. */
@Composable
private fun CalendarEditDialog(
    calendar: CalendarEntity,
    others: List<CalendarEntity>,
    phone: PhoneSheetState,
    onRequestAccess: () -> Unit,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (CalendarEntity) -> Unit,
    onDelete: (moveTo: String) -> Unit,
    onExport: () -> Unit,
) {
    // A starting calendar's blank name is shown, and saved, as its translated one.
    val shownName = calendarName(calendar)
    var draft by remember { mutableStateOf(calendar.copy(name = if (isNew) "" else shownName)) }
    var deleting by remember { mutableStateOf(false) }
    if (deleting && others.isNotEmpty()) {
        MoveItemsDialog(others, onDismiss = { deleting = false }, onMove = { onDelete(it) })
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) R.string.calendars_new else R.string.calendar_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    draft.name, { v -> draft = draft.copy(name = v) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.calendar_name)) },
                )
                ColorChoice(stringResource(R.string.calendar_color), draft.color, CALENDAR_PALETTE, noneLabel = null) { c ->
                    c?.let { draft = draft.copy(color = it) }
                }
                CalendarDefaults(draft) { draft = it }
                ExportChoice(draft, phone, onRequestAccess) { draft = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.calendar_default_for_alarms), style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = draft.defaultForAlarms, onCheckedChange = { draft = draft.copy(defaultForAlarms = it) })
                }
                if (!isNew) {
                    Row {
                        // Its items as a calendar file, through the share sheet (#33).
                        TextButton(onClick = onExport) { Text(stringResource(R.string.ics_export)) }
                        if (others.isNotEmpty()) TextButton(onClick = { deleting = true }) { Text(stringResource(R.string.action_delete)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft.copy(name = draft.name.trim())) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** The defaults a new item in this calendar starts with. */
@Composable
private fun CalendarDefaults(draft: CalendarEntity, onChange: (CalendarEntity) -> Unit) {
    OptionChips(
        stringResource(R.string.calendar_default_reminder), DEFAULT_LEADS, draft.defaultLeadMinutes,
        label = { lead -> lead?.let { CalendarText.lead(it, allDay = false, is24h = true) } ?: stringResource(R.string.lead_none) },
        onSelect = { onChange(draft.copy(defaultLeadMinutes = it)) },
    )
    OptionChips(
        stringResource(R.string.calendar_default_target), listOf(TargetKind.NOTIFY, TargetKind.RING), draft.defaultTarget,
        label = { stringResource(if (it == TargetKind.RING) R.string.fires_ring else R.string.fires_notify) },
        onSelect = { onChange(draft.copy(defaultTarget = it)) },
    )
    OptionChips(
        stringResource(R.string.calendar_default_repeat),
        listOf(RepeatKind.NONE, RepeatKind.MONTHLY, RepeatKind.YEARLY),
        draft.defaultRepeat,
        label = {
            stringResource(
                when (it) {
                    RepeatKind.NONE -> R.string.repeat_kind_none
                    RepeatKind.WEEKLY -> R.string.repeat_kind_weekly
                    RepeatKind.MONTHLY -> R.string.repeat_kind_monthly
                    RepeatKind.YEARLY -> R.string.repeat_kind_yearly
                    RepeatKind.CUSTOM -> R.string.repeat_kind_custom
                },
            )
        },
        onSelect = { onChange(draft.copy(defaultRepeat = it)) },
    )
}

/** "Copy to phone calendar" (#16): off, or one of the phone's calendars Relay may write to. */
@Composable
private fun ExportChoice(draft: CalendarEntity, phone: PhoneSheetState, onRequestAccess: () -> Unit, onChange: (CalendarEntity) -> Unit) {
    if (!phone.canRead || !phone.canWrite) {
        Text(stringResource(R.string.calendar_copy_to_phone), style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onRequestAccess) { Text(stringResource(R.string.calendars_phone_allow)) }
        return
    }
    val writable = phone.calendars.filter { it.writable }
    OptionChips(
        stringResource(R.string.calendar_copy_to_phone), listOf<Long?>(null) + writable.map { it.id }, draft.exportCalendarId,
        label = { id -> writable.firstOrNull { it.id == id }?.name ?: stringResource(R.string.option_off) },
        onSelect = { onChange(draft.copy(exportCalendarId = it)) },
    )
}

/** "Move its items and alarms to:" one of the other calendars, then delete. */
@Composable
private fun MoveItemsDialog(others: List<CalendarEntity>, onDismiss: () -> Unit, onMove: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.calendar_delete_title)) },
        text = {
            Column {
                Text(stringResource(R.string.calendar_delete_move_to))
                others.forEach { other ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onMove(other.id) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(14.dp).background(Color(other.color.toInt()), CircleShape))
                        Text(calendarName(other), modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private const val NEW_CALENDAR_LEAD = 15
private val DEFAULT_LEADS = listOf<Int?>(null, 0, 15, 60, 24 * 60)
