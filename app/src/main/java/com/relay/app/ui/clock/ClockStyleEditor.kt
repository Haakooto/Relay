package com.relay.app.ui.clock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.relay.app.R
import com.relay.app.clock.ClockKind
import com.relay.app.clock.ClockStyle
import com.relay.app.clock.DateStyle
import com.relay.app.clock.DigitalFont
import com.relay.app.clock.FaceStyle
import com.relay.app.clock.HandStyle
import com.relay.app.clock.HourFormat
import com.relay.app.clock.TickStyle
import com.relay.app.ui.common.OptionChips

/** Every [ClockStyle] axis (spec §08). Shared by the main clock's editor and widget setup. */
@Composable
fun ClockStyleEditor(style: ClockStyle, onChange: (ClockStyle) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OptionChips(
            stringResource(R.string.clock_kind), ClockKind.entries, style.kind,
            label = { stringResource(if (it == ClockKind.DIGITAL) R.string.clock_digital else R.string.clock_analogue) },
            onSelect = { onChange(style.copy(kind = it)) },
        )
        // One font for everything the clock writes: digits, numerals and the date.
        OptionChips(
            stringResource(R.string.clock_font), DigitalFont.entries, style.font,
            label = { stringResource(fontLabel(it)) }, onSelect = { onChange(style.copy(font = it)) },
        )
        if (style.kind == ClockKind.DIGITAL) {
            OptionChips(
                stringResource(R.string.clock_format), HourFormat.entries, style.format,
                label = {
                    stringResource(
                        when (it) {
                            HourFormat.FOLLOW_SETTING -> R.string.clock_format_follow
                            HourFormat.H12 -> R.string.clock_format_12
                            HourFormat.H24 -> R.string.clock_format_24
                        },
                    )
                },
                onSelect = { onChange(style.copy(format = it)) },
            )
            SwitchLine(stringResource(R.string.clock_seconds), style.showSeconds) { onChange(style.copy(showSeconds = it)) }
        } else {
            OptionChips(
                stringResource(R.string.clock_hands), HandStyle.entries, style.hands,
                label = {
                    stringResource(
                        when (it) {
                            HandStyle.THICK -> R.string.clock_hands_thick
                            HandStyle.THIN -> R.string.clock_hands_thin
                            HandStyle.DOT -> R.string.clock_hands_dot
                        },
                    )
                },
                onSelect = { onChange(style.copy(hands = it)) },
            )
            OptionChips(
                stringResource(R.string.clock_ticks), TickStyle.entries, style.ticks,
                label = {
                    stringResource(
                        when (it) {
                            TickStyle.NONE -> R.string.clock_ticks_none
                            TickStyle.MINUTES -> R.string.clock_ticks_minutes
                            TickStyle.HOURS -> R.string.clock_ticks_hours
                            TickStyle.QUARTERS -> R.string.clock_ticks_quarters
                        },
                    )
                },
                onSelect = { onChange(style.copy(ticks = it)) },
            )
            OptionChips(
                stringResource(R.string.clock_face), FaceStyle.entries, style.face,
                label = {
                    stringResource(
                        when (it) {
                            FaceStyle.NONE -> R.string.clock_face_none
                            FaceStyle.RING -> R.string.clock_face_ring
                            FaceStyle.FILLED -> R.string.clock_face_filled
                            FaceStyle.NUMERALS -> R.string.clock_face_numerals
                        },
                    )
                },
                onSelect = { onChange(style.copy(face = it)) },
            )
            SwitchLine(stringResource(R.string.clock_second_hand), style.secondHand) { onChange(style.copy(secondHand = it)) }
        }
        OptionChips(
            stringResource(R.string.clock_date), DateStyle.entries, style.date,
            label = {
                stringResource(
                    when (it) {
                        DateStyle.OFF -> R.string.option_off
                        DateStyle.SHORT -> R.string.clock_date_short
                        DateStyle.LONG -> R.string.clock_date_long
                    },
                )
            },
            onSelect = { onChange(style.copy(date = it)) },
        )

        HorizontalDivider()
        Text(stringResource(R.string.clock_colors), style = MaterialTheme.typography.titleLarge)
        val c = style.colors
        fun set(transform: (com.relay.app.clock.ClockColors) -> com.relay.app.clock.ClockColors) = onChange(style.copy(colors = transform(c)))
        if (style.kind == ClockKind.DIGITAL) {
            ColorChoice(stringResource(R.string.clock_color_digits), c.digits) { v -> set { it.copy(digits = v) } }
        } else {
            ColorChoice(stringResource(R.string.clock_color_hour_hand), c.hourHand) { v -> set { it.copy(hourHand = v) } }
            ColorChoice(stringResource(R.string.clock_color_minute_hand), c.minuteHand) { v -> set { it.copy(minuteHand = v) } }
            if (style.secondHand) ColorChoice(stringResource(R.string.clock_color_second_hand), c.secondHand) { v -> set { it.copy(secondHand = v) } }
            if (style.ticks != TickStyle.NONE) ColorChoice(stringResource(R.string.clock_color_ticks), c.ticks) { v -> set { it.copy(ticks = v) } }
            if (style.face != FaceStyle.NONE) ColorChoice(stringResource(R.string.clock_color_face), c.face) { v -> set { it.copy(face = v) } }
            if (style.face == FaceStyle.NUMERALS) ColorChoice(stringResource(R.string.clock_color_numerals), c.digits) { v -> set { it.copy(digits = v) } }
        }
        ColorChoice(stringResource(R.string.clock_color_secondary), c.secondary) { v -> set { it.copy(secondary = v) } }
    }
}

@Composable
private fun SwitchLine(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

fun fontLabel(font: DigitalFont) = when (font) {
    DigitalFont.SYSTEM -> R.string.font_system
    DigitalFont.SERIF -> R.string.font_serif
    DigitalFont.MONO -> R.string.font_mono
    DigitalFont.SEVEN_SEGMENT -> R.string.font_seven_segment
    DigitalFont.DISPLAY -> R.string.font_display
}
