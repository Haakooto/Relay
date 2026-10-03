package com.relay.app.ui.stopwatch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.relay.app.R
import com.relay.app.stopwatch.LapView
import com.relay.app.stopwatch.StopwatchView
import com.relay.app.stopwatch.lapStats
import com.relay.app.util.DurationText

/**
 * One stopwatch's lap times as bars, in lap order (spec §07), filterable by tag, with stats
 * above. A single series in the accent colour: no legend, a faint baseline, a dashed average,
 * and tap a bar for its exact time. The lap list itself is the table view.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LapChartDialog(view: StopwatchView, onDismiss: () -> Unit) {
    var filter by remember { mutableStateOf(emptySet<String>()) }
    var selected by remember { mutableStateOf<LapView?>(null) }
    val laps = view.laps.reversed().filter { filter.isEmpty() || it.tags.any { t -> t in filter } }
    val stats = lapStats(laps)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(view.stopwatch.name.ifBlank { stringResource(R.string.stopwatch_chart_title) }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (view.tags.isNotEmpty()) {
                    // Filters in one row above everything they filter.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        view.tags.forEach { tag ->
                            val on = tag in filter
                            FilterChip(selected = on, onClick = { filter = if (on) filter - tag else filter + tag; selected = null }, label = { Text(tag) })
                        }
                    }
                }
                if (stats == null) {
                    Text(stringResource(R.string.stopwatch_chart_empty))
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile(stringResource(R.string.stats_count), stats.count.toString(), Modifier.weight(1f))
                        StatTile(stringResource(R.string.stats_average), DurationText.stopwatch(stats.averageMillis), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile(stringResource(R.string.stats_best), DurationText.stopwatch(stats.bestMillis), Modifier.weight(1f))
                        StatTile(stringResource(R.string.stats_worst), DurationText.stopwatch(stats.worstMillis), Modifier.weight(1f))
                    }
                    Text(
                        selected?.let { stringResource(R.string.stopwatch_chart_selected, it.lap.number, DurationText.stopwatch(it.deltaMillis)) }
                            ?: stringResource(R.string.stopwatch_chart_tap),
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = if (selected != null) FontFamily.Monospace else null,
                    )
                    Bars(laps, stats.averageMillis, selected) { selected = it }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}

@Composable
private fun StatTile(title: String, value: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(10.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun Bars(laps: List<LapView>, averageMillis: Long, selected: LapView?, onSelect: (LapView) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val barWidth = 18.dp
    val gap = 4.dp
    val chartHeight = 180.dp
    val max = laps.maxOf { it.deltaMillis }.coerceAtLeast(1)
    val width = (barWidth + gap) * laps.size
    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Canvas(
            Modifier.width(width).height(chartHeight).pointerInput(laps) {
                detectTapGestures { pos ->
                    val step = with(density) { (barWidth + gap).toPx() }
                    laps.getOrNull((pos.x / step).toInt())?.let(onSelect)
                }
            },
        ) {
            val step = (barWidth + gap).toPx()
            val bw = barWidth.toPx()
            val r = 4.dp.toPx()
            laps.forEachIndexed { i, lap ->
                val h = size.height * lap.deltaMillis / max
                val left = i * step + gap.toPx() / 2
                val top = size.height - h
                // Rounded at the data end only, square on the baseline.
                val path = Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            left, top, left + bw, size.height,
                            topLeftCornerRadius = CornerRadius(r), topRightCornerRadius = CornerRadius(r),
                        ),
                    )
                }
                drawPath(path, if (selected == null || selected.lap.id == lap.lap.id) colors.primary else colors.primary.copy(alpha = 0.35f))
            }
            drawLine(colors.outlineVariant, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            val avgY = size.height - size.height * averageMillis / max
            drawLine(
                colors.onSurfaceVariant, Offset(0f, avgY), Offset(size.width, avgY), strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
            )
        }
        // Lap numbers under the bars, thinned out when there are many.
        val every = when {
            laps.size <= 12 -> 1
            laps.size <= 40 -> 5
            else -> 10
        }
        Row {
            laps.forEachIndexed { i, lap ->
                Text(
                    if (i == 0 || i == laps.lastIndex || lap.lap.number % every == 0) lap.lap.number.toString() else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.width(barWidth + gap),
                )
            }
        }
    }
}
