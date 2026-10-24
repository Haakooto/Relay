package com.relay.app.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.relay.app.R
import com.relay.app.ui.clock.ColorChoice
import com.relay.app.ui.common.OptionChips

/** Background, corners and border: the part of every widget's setup screen they all share. */
@Composable
fun WidgetFrameEditor(frame: WidgetFrame, onChange: (WidgetFrame) -> Unit) {
    OptionChips(
        stringResource(R.string.widget_background), WidgetBackground.entries, frame.background,
        label = {
            stringResource(
                when (it) {
                    WidgetBackground.FOLLOW_THEME -> R.string.widget_bg_theme
                    WidgetBackground.TRANSPARENT -> R.string.widget_bg_transparent
                    WidgetBackground.SOLID -> R.string.widget_bg_solid
                    WidgetBackground.GRADIENT -> R.string.widget_bg_gradient
                },
            )
        },
        onSelect = { onChange(frame.copy(background = it)) },
    )
    if (frame.background == WidgetBackground.SOLID || frame.background == WidgetBackground.GRADIENT) {
        ColorChoice(stringResource(R.string.widget_bg_color), frame.backgroundColor) { onChange(frame.copy(backgroundColor = it)) }
    }
    if (frame.background == WidgetBackground.GRADIENT) {
        ColorChoice(stringResource(R.string.widget_bg_color2), frame.backgroundColor2) { onChange(frame.copy(backgroundColor2 = it)) }
    }
    OptionChips(
        stringResource(R.string.widget_corners), WidgetCorners.entries, frame.corners,
        label = {
            stringResource(
                when (it) {
                    WidgetCorners.SHARP -> R.string.widget_corners_sharp
                    WidgetCorners.ROUNDED -> R.string.widget_corners_rounded
                    WidgetCorners.PILL -> R.string.widget_corners_pill
                },
            )
        },
        onSelect = { onChange(frame.copy(corners = it)) },
    )
    OptionChips(
        stringResource(R.string.widget_border), BORDER_WIDTHS, frame.borderWidthDp,
        label = { if (it == 0) stringResource(R.string.option_off) else "$it dp" },
        onSelect = { onChange(frame.copy(borderWidthDp = it)) },
    )
    if (frame.borderWidthDp > 0) {
        ColorChoice(stringResource(R.string.widget_border_color), frame.borderColor) { onChange(frame.copy(borderColor = it)) }
    }
}

private val BORDER_WIDTHS = listOf(0, 1, 2, 4)
