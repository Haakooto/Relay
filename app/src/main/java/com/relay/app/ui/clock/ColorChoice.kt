package com.relay.app.ui.clock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.relay.app.R
import com.relay.app.ui.theme.Accent
import com.relay.app.ui.theme.Brass
import com.relay.app.ui.theme.Ink
import com.relay.app.ui.theme.Paper
import com.relay.app.ui.theme.Teal

/** The brand palette (spec §15) plus white and black; "Theme" (null) follows light/dark. */
private val SWATCHES = listOf(Ink, Paper, Accent, Teal, Brass, Color.White, Color.Black)

/**
 * Pick a colour: "none" (null, labelled [noneLabel]; left out when that's null), a [palette]
 * swatch, or any custom colour. Defaults to a clock element's choices: theme or the brand palette.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorChoice(
    title: String,
    value: Long?,
    palette: List<Color> = SWATCHES,
    noneLabel: String? = stringResource(R.string.color_theme),
    onChange: (Long?) -> Unit,
) {
    var custom by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (noneLabel != null) Swatch(null, selected = value == null, label = noneLabel) { onChange(null) }
            palette.forEach { c ->
                val argb = c.toArgb().toLong()
                Swatch(c, selected = value == argb) { onChange(argb) }
            }
            val isCustom = value != null && palette.none { it.toArgb().toLong() == value }
            Swatch(if (isCustom) Color(value!!.toInt()) else null, selected = isCustom, label = "+") { custom = true }
        }
    }
    if (custom) {
        CustomColorDialog(value?.let { Color(it.toInt()) } ?: Accent, onDismiss = { custom = false }) { onChange(it.toArgb().toLong()); custom = false }
    }
}

@Composable
private fun Swatch(color: Color?, selected: Boolean, label: String? = null, onClick: () -> Unit) {
    val ring = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Box(
        Modifier.size(36.dp).border(if (selected) 3.dp else 1.dp, ring, CircleShape)
            .background(color ?: MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

/** Hue / saturation / brightness sliders with a live sample. */
@Composable
private fun CustomColorDialog(initial: Color, onDismiss: () -> Unit, onPick: (Color) -> Unit) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) } }
    var h by remember { mutableFloatStateOf(hsv[0]) }
    var s by remember { mutableFloatStateOf(hsv[1]) }
    var v by remember { mutableFloatStateOf(hsv[2]) }
    val color = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.color_custom)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().height(48.dp).background(color, RoundedCornerShape(12.dp)))
                Text(stringResource(R.string.color_hue)); Slider(h, { h = it }, valueRange = 0f..360f)
                Text(stringResource(R.string.color_saturation)); Slider(s, { s = it })
                Text(stringResource(R.string.color_brightness)); Slider(v, { v = it })
            }
        },
        confirmButton = { TextButton(onClick = { onPick(color) }) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
