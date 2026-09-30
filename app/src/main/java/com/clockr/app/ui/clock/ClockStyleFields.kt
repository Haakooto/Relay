package com.clockr.app.ui.clock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.clockr.app.R
import com.clockr.app.data.ClockAnalogueVariant
import com.clockr.app.data.ClockBackground
import com.clockr.app.data.ClockColor
import com.clockr.app.data.ClockConfigEntity
import com.clockr.app.data.ClockCornerStyle
import com.clockr.app.data.ClockDigitalFont
import com.clockr.app.data.ClockStyle

/** The editable subset of [ClockConfigEntity] — everything except id/sortOrder/appWidgetId/
 *  timezoneId, which the caller (in-app screen or widget config Activity) owns separately. */
data class ClockStyleState(
    val name: String = "",
    val style: ClockStyle = ClockStyle.DIGITAL,
    val digitalFont: ClockDigitalFont = ClockDigitalFont.DEFAULT,
    val analogueVariant: ClockAnalogueVariant = ClockAnalogueVariant.CLASSIC,
    val color: ClockColor = ClockColor.INK,
    val showSeconds: Boolean = true,
    val showDate: Boolean = true,
    val background: ClockBackground = ClockBackground.FOLLOW_THEME,
    val backgroundColor: ClockColor = ClockColor.INK,
    val cornerStyle: ClockCornerStyle = ClockCornerStyle.ROUNDED,
    val borderEnabled: Boolean = false,
    val borderWidthDp: Int = 2,
)

fun ClockConfigEntity.toStyleState() = ClockStyleState(
    name = name, style = style, digitalFont = digitalFont, analogueVariant = analogueVariant,
    color = color, showSeconds = showSeconds, showDate = showDate, background = background,
    backgroundColor = backgroundColor, cornerStyle = cornerStyle, borderEnabled = borderEnabled,
    borderWidthDp = borderWidthDp,
)

fun ClockConfigEntity.withStyle(state: ClockStyleState) = copy(
    name = state.name, style = state.style, digitalFont = state.digitalFont,
    analogueVariant = state.analogueVariant, color = state.color, showSeconds = state.showSeconds,
    showDate = state.showDate, background = state.background, backgroundColor = state.backgroundColor,
    cornerStyle = state.cornerStyle, borderEnabled = state.borderEnabled, borderWidthDp = state.borderWidthDp,
)

@Composable
private fun ColorSwatch(color: ClockColor) {
    Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(color.toComposeColor()))
}

@Composable
private fun RowScope.SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Text(label, modifier = Modifier.weight(1f))
    Switch(checked = checked, onCheckedChange = onCheckedChange)
}

@Composable
private fun ClockStyle.displayName() = stringResource(
    when (this) {
        ClockStyle.DIGITAL -> R.string.clock_style_digital
        ClockStyle.ANALOGUE -> R.string.clock_style_analogue
    },
)

@Composable
private fun ClockDigitalFont.displayName() = stringResource(
    when (this) {
        ClockDigitalFont.DEFAULT -> R.string.clock_font_default
        ClockDigitalFont.SERIF -> R.string.clock_font_serif
        ClockDigitalFont.MONOSPACE -> R.string.clock_font_monospace
    },
)

@Composable
private fun ClockAnalogueVariant.displayName() = stringResource(
    when (this) {
        ClockAnalogueVariant.MINIMAL -> R.string.clock_face_minimal
        ClockAnalogueVariant.SLEEK -> R.string.clock_face_sleek
        ClockAnalogueVariant.CLASSIC -> R.string.clock_face_classic
        ClockAnalogueVariant.TECHY -> R.string.clock_face_techy
    },
)

@Composable
private fun ClockColor.displayName() = stringResource(
    when (this) {
        ClockColor.INK -> R.string.clock_color_ink
        ClockColor.ACCENT -> R.string.clock_color_accent
        ClockColor.TEAL -> R.string.clock_color_teal
        ClockColor.BRASS -> R.string.clock_color_brass
    },
)

@Composable
private fun ClockBackground.displayName() = stringResource(
    when (this) {
        ClockBackground.FOLLOW_THEME -> R.string.clock_background_follow_theme
        ClockBackground.TRANSPARENT -> R.string.clock_background_transparent
        ClockBackground.SOLID -> R.string.clock_background_solid
    },
)

@Composable
private fun ClockCornerStyle.displayName() = stringResource(
    when (this) {
        ClockCornerStyle.SHARP -> R.string.clock_shape_sharp
        ClockCornerStyle.ROUNDED -> R.string.clock_shape_rounded
        ClockCornerStyle.PILL -> R.string.clock_shape_pill
    },
)

/**
 * The shared style-editing fields, used by both the in-app [com.clockr.app.ui.screens.ClockConfigScreen]
 * and the widget's `ClockConfigActivity` — one place for "what does a clock look like" so the two
 * surfaces' editors can't drift apart. [showWidgetFields] gates the background/shape/border
 * section, which only makes sense for a widget instance (the in-app Clock pane always follows the
 * app theme).
 */
@Composable
fun ClockStyleFields(
    state: ClockStyleState,
    onChange: (ClockStyleState) -> Unit,
    showWidgetFields: Boolean,
) {
    OutlinedTextField(
        value = state.name,
        onValueChange = { onChange(state.copy(name = it)) },
        label = { Text(stringResource(R.string.clock_name_label)) },
        modifier = Modifier.fillMaxWidth(),
    )

    Text(stringResource(R.string.clock_style_title), style = MaterialTheme.typography.bodyLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ClockStyle.entries.forEach { option ->
            FilterChip(
                selected = state.style == option,
                onClick = { onChange(state.copy(style = option)) },
                label = { Text(option.displayName()) },
            )
        }
    }

    if (state.style == ClockStyle.DIGITAL) {
        Text(stringResource(R.string.clock_font_title), style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClockDigitalFont.entries.forEach { option ->
                FilterChip(
                    selected = state.digitalFont == option,
                    onClick = { onChange(state.copy(digitalFont = option)) },
                    label = { Text(option.displayName()) },
                )
            }
        }
    } else {
        Text(stringResource(R.string.clock_face_title), style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClockAnalogueVariant.entries.forEach { option ->
                FilterChip(
                    selected = state.analogueVariant == option,
                    onClick = { onChange(state.copy(analogueVariant = option)) },
                    label = { Text(option.displayName()) },
                )
            }
        }
    }

    Text(stringResource(R.string.clock_color_title), style = MaterialTheme.typography.bodyLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ClockColor.entries.forEach { option ->
            FilterChip(
                selected = state.color == option,
                onClick = { onChange(state.copy(color = option)) },
                leadingIcon = { ColorSwatch(option) },
                label = { Text(option.displayName()) },
            )
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        SwitchRow(stringResource(R.string.clock_show_seconds_label), state.showSeconds) { onChange(state.copy(showSeconds = it)) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        SwitchRow(stringResource(R.string.clock_show_date_label), state.showDate) { onChange(state.copy(showDate = it)) }
    }

    if (showWidgetFields) {
        Text(stringResource(R.string.clock_background_title), style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClockBackground.entries.forEach { option ->
                FilterChip(
                    selected = state.background == option,
                    onClick = { onChange(state.copy(background = option)) },
                    label = { Text(option.displayName()) },
                )
            }
        }
        if (state.background == ClockBackground.SOLID) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ClockColor.entries.forEach { option ->
                    FilterChip(
                        selected = state.backgroundColor == option,
                        onClick = { onChange(state.copy(backgroundColor = option)) },
                        leadingIcon = { ColorSwatch(option) },
                        label = { Text(option.displayName()) },
                    )
                }
            }
        }

        Text(stringResource(R.string.clock_shape_title), style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClockCornerStyle.entries.forEach { option ->
                FilterChip(
                    selected = state.cornerStyle == option,
                    onClick = { onChange(state.copy(cornerStyle = option)) },
                    label = { Text(option.displayName()) },
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            SwitchRow(stringResource(R.string.clock_border_label), state.borderEnabled) { onChange(state.copy(borderEnabled = it)) }
        }
    }
}
