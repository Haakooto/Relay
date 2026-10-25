package com.relay.app.ui.alarms

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.relay.app.R
import com.relay.app.data.schedule.WeekdayMask
import com.relay.app.guide.GuideSection
import com.relay.app.schedule.AlarmListItem
import com.relay.app.ui.common.PaneHeader
import com.relay.app.util.RelativeText
import com.relay.app.util.TimeText
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun AlarmsScreen(onOpen: (String?) -> Unit, viewModel: AlarmsViewModel = viewModel()) {
    val alarms by viewModel.alarms.collectAsStateWithLifecycle()
    val blockLabels by viewModel.blockLabels.collectAsStateWithLifecycle()
    val calendars by viewModel.calendars.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    val is24h = settings?.clockFormat24h ?: true
    val firstDay = settings?.firstDayOfWeek ?: DayOfWeek.MONDAY

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                PaneHeader(R.string.pane_alarms, GuideSection.ALARMS)
            }
            item { ReadinessBanner() }
            val list = alarms
            if (list != null && list.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.alarms_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }
            items(list.orEmpty(), key = { it.schedule.id }) { item ->
                AlarmRow(
                    item = item, is24h = is24h, firstDay = firstDay, now = now, blockLabels = blockLabels,
                    color = calendars?.let { c -> c.color(c.alarmCalendar(item.schedule)) },
                    onClick = { onOpen(item.schedule.id) },
                    onToggle = { enabled ->
                        viewModel.setEnabled(item.schedule.id, enabled) { next ->
                            armedToast(context, next)
                        }
                    },
                )
            }
        }
        FloatingActionButton(
            onClick = { onOpen(null) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.alarms_add)) }
    }
}

/** The stock-app confirmation: "Alarm set for 8 h 12 min from now." */
fun armedToast(context: android.content.Context, nextFireMillis: Long?) {
    val text = if (nextFireMillis == null) context.getString(R.string.alarm_wont_ring)
    else context.getString(R.string.alarm_set_in, RelativeText.until(context, nextFireMillis - System.currentTimeMillis()))
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}

@Composable
private fun AlarmRow(
    item: AlarmListItem, is24h: Boolean, firstDay: DayOfWeek, now: Long, blockLabels: Map<String, String>,
    color: Long?, onClick: () -> Unit, onToggle: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val s = item.schedule
    val faded = if (s.enabled) 1f else 0.5f
    val whenText = if (s.isRepeating) RelativeText.days(context, WeekdayMask.toSet(s.repeatDays), firstDay)
    else s.oneOffEpochDay?.let { RelativeText.date(context, LocalDate.ofEpochDay(it)) }.orEmpty()
    val status = when {
        !s.enabled -> null
        item.nextFire == null -> stringResource(R.string.alarm_wont_ring)
        s.snoozedUntilMillis != null && s.snoozedUntilMillis == item.nextFire.toEpochMilli() ->
            stringResource(R.string.alarm_snoozed_until, TimeText.of(item.nextFire, is24h))
        else -> stringResource(R.string.alarm_rings_in, RelativeText.until(context, item.nextFire.toEpochMilli() - now))
    }

    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        // Its calendar's colour down the side (#34), as in the Calendar pane.
        Row(Modifier.height(IntrinsicSize.Min)) {
            color?.let { Box(Modifier.width(6.dp).fillMaxHeight().background(Color(it.toInt()).copy(alpha = faded))) }
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        TimeText.of(LocalTime.of(s.hour, s.minute), is24h),
                        fontSize = 40.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = faded),
                    )
                    Text(
                        listOf(s.label, whenText).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = faded),
                    )
                    targetText(s, blockLabels)?.let {
                        Text(
                            it, style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = faded),
                        )
                    }
                    if (status != null) {
                        Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    if (item.exceptionCount > 0) {
                        Text(
                            pluralStringResource(R.plurals.alarm_exception_count, item.exceptionCount, item.exceptionCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                }
                Switch(checked = s.enabled, onCheckedChange = onToggle)
            }
        }
    }
}

/** "Starts timer Tea", "Starts a 25m timer", "Starts stopwatch Run"; null for a ringing alarm. */
@Composable
private fun targetText(s: com.relay.app.data.schedule.ScheduleEntity, blockLabels: Map<String, String>): String? {
    val spec = com.relay.app.schedule.TargetSpec.decode(s.targetSpec)
    val saved = s.targetDefId?.let { blockLabels[it] ?: stringResource(R.string.target_missing) }
    return when (s.target) {
        com.relay.app.data.schedule.TargetKind.RING -> null
        com.relay.app.data.schedule.TargetKind.NOTIFY -> stringResource(R.string.alarm_notifies)
        com.relay.app.data.schedule.TargetKind.TIMER ->
            saved?.let { stringResource(R.string.alarm_starts_timer, it) } ?: stringResource(R.string.alarm_starts_new_timer, spec.durationExpr)
        com.relay.app.data.schedule.TargetKind.RELAY ->
            saved?.let { stringResource(R.string.alarm_starts_relay, it) } ?: stringResource(R.string.alarm_starts_new_relay)
        com.relay.app.data.schedule.TargetKind.STOPWATCH ->
            spec.label.ifBlank { null }?.let { stringResource(R.string.alarm_starts_stopwatch_named, it) } ?: stringResource(R.string.alarm_starts_stopwatch)
    }
}
