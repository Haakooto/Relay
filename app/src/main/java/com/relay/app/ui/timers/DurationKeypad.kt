package com.relay.app.ui.timers

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.relay.app.R
import com.relay.app.util.DurationText

/**
 * The timer length keypad: digits and the h/m/s units, plus quick adds. Android can't trim the
 * system keyboard down to these keys, so the app draws its own, like the stock clock does. It
 * types into the same relay-language expression the text field holds (bare digits are minutes).
 */
@Composable
fun DurationKeypad(
    onType: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onAdd: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            QUICK_ADDS.forEach { seconds ->
                OutlinedButton(onClick = { onAdd(seconds) }, modifier = Modifier.weight(1f)) {
                    Text("+" + DurationText.short(seconds))
                }
            }
        }
        listOf("123h", "456m", "789s").forEach { keys ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                keys.forEach { key -> Key(key.toString(), unit = !key.isDigit(), onClick = { onType(key.toString()) }) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key("C", unit = true, onClick = onClear)
            Key("0", onClick = { onType("0") })
            Key(null, unit = true, weight = 2f, onClick = onBackspace, onLongClick = onClear)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.Key(
    text: String?,
    unit: Boolean = false,
    weight: Float = 1f,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (unit) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .weight(weight)
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() },
                onLongClick = onLongClick?.let { { haptics.performHapticFeedback(HapticFeedbackType.LongPress); it() } },
            ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (text != null) Text(text, style = MaterialTheme.typography.titleLarge)
            else Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = stringResource(R.string.keypad_backspace))
        }
    }
}

private val QUICK_ADDS = listOf(60L, 300L, 600L)
