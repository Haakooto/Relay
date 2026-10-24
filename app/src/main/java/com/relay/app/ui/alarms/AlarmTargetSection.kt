package com.relay.app.ui.alarms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.relay.app.R
import com.relay.app.data.library.TransitionMode
import com.relay.app.data.library.TransitionTone
import androidx.compose.ui.res.pluralStringResource
import com.relay.app.ui.timers.toneLabel
import com.relay.app.data.schedule.TargetKind
import com.relay.app.ui.common.OptionChips
import com.relay.app.ui.stopwatch.TagListEditor
import com.relay.app.ui.timers.ScriptField
import com.relay.app.ui.timers.saveErrorText
import com.relay.app.util.DurationText
import com.relay.chainlang.ChainLang

/**
 * What the alarm fires (spec §04): ring, or quietly start a timer, a relay or a stopwatch. A
 * timer or relay is picked from the library or built right here; "Save to library" turns a
 * built one into a saved one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlarmTargetSection(state: TargetDraft, editor: TargetEditor) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OptionChips(
            stringResource(R.string.fires_title), TargetKind.entries, state.target,
            label = {
                stringResource(
                    when (it) {
                        TargetKind.RING -> R.string.fires_ring
                        TargetKind.TIMER -> R.string.fires_timer
                        TargetKind.RELAY -> R.string.fires_relay
                        TargetKind.STOPWATCH -> R.string.fires_stopwatch
                    },
                )
            },
            onSelect = editor::setTarget,
        )
        when (state.target) {
            TargetKind.RING -> {}
            TargetKind.TIMER, TargetKind.RELAY -> {
                val saved = if (state.target == TargetKind.TIMER) state.savedTimers else state.savedRelays
                Text(stringResource(R.string.target_which), style = MaterialTheme.typography.bodyLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(selected = state.targetDefId == null, onClick = { editor.setTargetDef(null) }, label = { Text(stringResource(R.string.target_new)) })
                    saved.forEach { block ->
                        FilterChip(
                            selected = state.targetDefId == block.id, onClick = { editor.setTargetDef(block.id) },
                            label = { Text(block.totalSeconds?.let { "${block.label} · ${DurationText.short(it)}" } ?: block.label) },
                        )
                    }
                }
                saved.firstOrNull { it.id == state.targetDefId }?.let { block ->
                    Text(
                        when {
                            block.totalSeconds == null -> stringResource(R.string.target_broken)
                            state.target == TargetKind.RELAY && block.steps != null ->
                                pluralStringResource(R.plurals.target_length_steps, block.steps, DurationText.short(block.totalSeconds), block.steps)
                            else -> stringResource(R.string.target_length, DurationText.short(block.totalSeconds))
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (block.totalSeconds == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.targetDefId == null) {
                    if (state.target == TargetKind.TIMER) InlineTimer(state, editor) else InlineRelay(state, editor)
                    val labelOk = ChainLang.isReferenceableLabel(state.spec.label.trim())
                    val valid = editor.canSave(state)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = editor::promote, enabled = labelOk && valid) { Text(stringResource(R.string.target_save_to_library)) }
                    }
                    if (!labelOk) Text(stringResource(R.string.target_save_hint), style = MaterialTheme.typography.labelSmall)
                    state.promoteError?.let { Text(saveErrorText(it), color = MaterialTheme.colorScheme.error) }
                }
            }
            TargetKind.STOPWATCH -> InlineStopwatch(state, editor)
        }
        if (state.target != TargetKind.RING) {
            // A quiet start still deserves a cue. Previewed as it's picked.
            OptionChips(
                stringResource(R.string.target_start_tone), TransitionTone.entries, state.spec.startTone,
                label = { stringResource(toneLabel(it)) },
                onSelect = editor::setStartTone,
            )
        }
    }
}

@Composable
private fun InlineTimer(state: TargetDraft, editor: TargetEditor) {
    OutlinedTextField(
        state.spec.label, { v -> editor.setSpec { it.copy(label = v) } }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.block_label)) },
    )
    OutlinedTextField(
        state.spec.durationExpr, { v -> editor.setSpec { it.copy(durationExpr = v) } }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.timer_duration)) },
        textStyle = TextStyle(fontFamily = FontFamily.Monospace),
        isError = state.inlineTimerSeconds == null,
        supportingText = {
            Text(state.inlineTimerSeconds?.let { stringResource(R.string.timer_duration_is, DurationText.short(it)) } ?: stringResource(R.string.timer_one_duration))
        },
    )
}

@Composable
private fun InlineRelay(state: TargetDraft, editor: TargetEditor) {
    OutlinedTextField(
        state.spec.label, { v -> editor.setSpec { it.copy(label = v) } }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.block_label)) },
    )
    ScriptField(
        script = state.spec.script,
        onScriptChange = { v -> editor.setSpec { it.copy(script = v) } },
        candidates = state.labelCandidates,
        isError = state.inlineError != null,
        label = { Text(stringResource(R.string.relay_script)) },
    )
    Text(
        state.inlineError ?: state.inlineRelaySeconds?.let { stringResource(R.string.target_relay_total, DurationText.short(it)) }.orEmpty(),
        color = if (state.inlineError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyLarge,
    )
    OptionChips(
        stringResource(R.string.relay_transition), TransitionMode.entries, state.spec.mode,
        label = { stringResource(if (it == TransitionMode.SEAMLESS) R.string.relay_seamless else R.string.relay_interaction) },
        onSelect = { v -> editor.setSpec { it.copy(mode = v) } },
    )
}

@Composable
private fun InlineStopwatch(state: TargetDraft, editor: TargetEditor) {
    val spec = state.spec
    val abs = kotlin.math.abs(spec.t0Millis) / 1000
    var h by remember { mutableStateOf((abs / 3600).takeIf { it > 0 }?.toString().orEmpty()) }
    var m by remember { mutableStateOf(((abs % 3600) / 60).takeIf { it > 0 }?.toString().orEmpty()) }
    var s by remember { mutableStateOf((abs % 60).takeIf { it > 0 }?.toString().orEmpty()) }
    var negative by remember { mutableStateOf(spec.t0Millis < 0) }
    fun push() {
        val t = ((h.toLongOrNull() ?: 0) * 3600 + (m.toLongOrNull() ?: 0) * 60 + (s.toLongOrNull() ?: 0)) * 1000
        editor.setSpec { it.copy(t0Millis = if (negative) -t else t) }
    }
    OutlinedTextField(
        spec.label, { v -> editor.setSpec { it.copy(label = v) } }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.stopwatch_name)) },
    )
    Text(stringResource(R.string.stopwatch_t0), style = MaterialTheme.typography.bodyLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FilterChip(selected = negative, onClick = { negative = !negative; push() }, label = { Text("−") })
        listOf(Triple(h, { v: String -> h = v }, "h"), Triple(m, { v: String -> m = v }, "m"), Triple(s, { v: String -> s = v }, "s"))
            .forEach { (value, set, unit) ->
                OutlinedTextField(
                    value, { set(it.filter(Char::isDigit).take(3)); push() }, label = { Text(unit) }, singleLine = true,
                    modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
    }
    TagListEditor(spec.tags) { tags -> editor.setSpec { it.copy(tags = tags) } }
}
