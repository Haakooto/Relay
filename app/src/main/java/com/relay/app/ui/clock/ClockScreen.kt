package com.relay.app.ui.clock

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.relay.app.R
import com.relay.app.clock.Cities
import com.relay.app.clock.ClockStyle
import com.relay.app.data.clock.LocationEntity
import com.relay.app.util.TimeText
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId

/** Stock-app layout (spec §08): one big styled clock, then the cities. */
@Composable
fun ClockScreen(onEditStyle: () -> Unit, viewModel: ClockViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val s = settings ?: return
    var adding by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000 - now % 60_000)
        }
    }
    val local = ZoneId.systemDefault()
    val home = s.homeZoneId?.let { runCatching { ZoneId.of(it) }.getOrNull() }
    // Travelling: the home zone differs from where the phone is now.
    val showHome = home != null && Cities.offsetMinutes(home, local) != 0

    // Drag-to-reorder, same pattern as the Timers library.
    val rows = remember { mutableStateListOf<LocationEntity>() }
    var dragged by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<String, Int>() }
    LaunchedEffect(locations) { if (dragged == null) { rows.clear(); rows.addAll(locations) } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.pane_clock), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = onEditStyle) { Icon(Icons.Outlined.Palette, stringResource(R.string.clock_edit_style)) }
                }
            }
            item {
                ClockFace(
                    ClockStyle.decode(s.mainClockStyle), s.clockFormat24h,
                    Modifier.fillMaxWidth().clickable(onClick = onEditStyle).padding(vertical = 24.dp),
                )
            }
            item {
                Text(
                    Cities.nameOf(local.id), style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
            }
            if (showHome) {
                item(key = "home") {
                    CityRow(stringResource(R.string.clock_home, Cities.nameOf(home!!.id)), home, local, now, s.clockFormat24h, highlighted = true)
                }
            }
            rows.toList().forEach { loc ->
                item(key = loc.id) {
                    val dismiss = rememberSwipeToDismissBoxState(confirmValueChange = {
                        if (it != SwipeToDismissBoxValue.Settled) { viewModel.delete(loc.id); true } else false
                    })
                    SwipeToDismissBox(
                        state = dismiss,
                        backgroundContent = {
                            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer, CardDefaults.shape))
                        },
                        modifier = Modifier
                            .zIndex(if (dragged == loc.id) 1f else 0f)
                            .graphicsLayer { translationY = if (dragged == loc.id) offset else 0f }
                            .onGloballyPositioned { heights[loc.id] = it.size.height }
                            .pointerInput(loc.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { dragged = loc.id; offset = 0f },
                                    onDragEnd = {
                                        val i = rows.indexOfFirst { it.id == loc.id }
                                        val before = rows.getOrNull(i - 1)?.sortOrder
                                        val after = rows.getOrNull(i + 1)?.sortOrder
                                        viewModel.move(loc.id, between(before, after))
                                        dragged = null; offset = 0f
                                    },
                                    onDragCancel = { dragged = null; offset = 0f },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        offset += amount.y
                                        val my = heights[loc.id] ?: return@detectDragGesturesAfterLongPress
                                        val i = rows.indexOfFirst { it.id == loc.id }
                                        if (offset > my / 2 && i < rows.lastIndex) {
                                            rows.add(i + 1, rows.removeAt(i)); offset -= heights[rows[i].id] ?: my
                                        } else if (offset < -my / 2 && i > 0) {
                                            rows.add(i - 1, rows.removeAt(i)); offset += heights[rows[i].id] ?: my
                                        }
                                    },
                                )
                            },
                    ) {
                        CityRow(loc.city, ZoneId.of(loc.zoneId), local, now, s.clockFormat24h)
                    }
                }
            }
            if (rows.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.clock_no_cities), style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
        }
        FloatingActionButton(onClick = { adding = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.clock_add_city))
        }
    }
    if (adding) {
        CityPickerDialog(stringResource(R.string.clock_add_city), onDismiss = { adding = false }) { viewModel.add(it); adding = false }
    }
}

@Composable
private fun CityRow(name: String, zone: ZoneId, local: ZoneId, now: Long, is24h: Boolean, highlighted: Boolean = false) {
    val context = LocalContext.current
    val at = Instant.ofEpochMilli(now)
    Card(
        Modifier.fillMaxWidth(),
        colors = if (highlighted) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else CardDefaults.cardColors(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleLarge)
                Text(
                    relativeLabel(context, zone, local, at), style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            Text(TimeText.of(at, is24h, zone), fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** "Tomorrow, +9 h" / "Today, −1 h 30 min" / "Same time" relative to the phone's zone. */
private fun relativeLabel(context: Context, zone: ZoneId, local: ZoneId, at: Instant): String {
    val minutes = Cities.offsetMinutes(zone, local, at)
    val dayDiff = at.atZone(zone).toLocalDate().toEpochDay() - at.atZone(local).toLocalDate().toEpochDay()
    val day = context.getString(
        when {
            dayDiff > 0 -> R.string.date_tomorrow
            dayDiff < 0 -> R.string.date_yesterday
            else -> R.string.date_today
        },
    )
    if (minutes == 0) return context.getString(R.string.clock_same_time)
    val sign = if (minutes > 0) "+" else "−"
    val h = kotlin.math.abs(minutes) / 60
    val m = kotlin.math.abs(minutes) % 60
    val diff = if (m == 0) context.getString(R.string.hours_short, h) else context.getString(R.string.duration_hours_minutes, h, m)
    return "$day, $sign$diff"
}

private fun between(before: Long?, after: Long?): Long = when {
    before != null && after != null -> before + (after - before) / 2
    before != null -> before + 1_000_000L
    after != null -> after - 1_000_000L
    else -> System.currentTimeMillis()
}
