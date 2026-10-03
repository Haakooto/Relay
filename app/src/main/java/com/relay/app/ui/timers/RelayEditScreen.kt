package com.relay.app.ui.timers

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.relay.app.R
import com.relay.app.data.library.StepOverride
import com.relay.app.data.library.TransitionMode
import com.relay.app.data.library.TransitionTone
import com.relay.app.run.OwnStep
import com.relay.app.run.OwnStepKind
import com.relay.app.ui.common.OptionChips
import com.relay.app.ui.common.SoundPickerRow
import com.relay.app.ui.common.rememberDiscardGuard
import com.relay.app.util.DurationText
import com.relay.chainlang.ChainLang

@Composable
fun RelayEditScreen(relayId: String?, onDone: () -> Unit) {
    val context = LocalContext.current
    val viewModel: RelayEditViewModel = viewModel(
        key = "relay-edit-${relayId ?: "new"}",
        factory = RelayEditViewModel.factory(context.applicationContext as Application, relayId),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.loaded) return
    val close = rememberDiscardGuard(viewModel::isDirty, onDone)
    var showHelp by remember { mutableStateOf(false) }
    var editingStep by remember { mutableStateOf<OwnStep?>(null) }

    state.deleteReferences?.let { refs -> DeleteDialog(state.label, refs, viewModel::cancelDelete) { viewModel.delete(onDone) } }
    if (showHelp) SyntaxHelpDialog { showHelp = false }
    editingStep?.let { step ->
        OverrideDialog(step, state.overrides[step.index], onDismiss = { editingStep = null }) {
            viewModel.setOverride(step.index, it); editingStep = null
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = close) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_cancel)) }
            Text(
                stringResource(if (state.isNew) R.string.relay_new else R.string.relay_edit),
                style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f),
            )
            com.relay.app.guide.GuideButton(com.relay.app.guide.GuideSection.RELAYS)
            IconButton(onClick = { showHelp = true }) {
                Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = stringResource(R.string.relay_syntax_help))
            }
            val valid = state.preview.error == null && state.preview.steps.isNotEmpty()
            OutlinedButton(onClick = { viewModel.start(onDone) }, enabled = valid) {
                Text(stringResource(R.string.action_start))
            }
            Button(onClick = { viewModel.save(onDone) }, enabled = valid && ChainLang.isReferenceableLabel(state.label.trim())) {
                Text(stringResource(R.string.action_save))
            }
        }
        OutlinedTextField(
            value = state.label,
            onValueChange = viewModel::setLabel,
            label = { Text(stringResource(R.string.block_label)) },
            isError = state.label.isNotBlank() && !ChainLang.isReferenceableLabel(state.label.trim()),
            supportingText = {
                Text(stringResource(if (state.label.isNotBlank() && !ChainLang.isReferenceableLabel(state.label.trim())) R.string.save_label_invalid else R.string.block_label_hint))
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        ScriptField(
            script = state.script,
            onScriptChange = viewModel::setScript,
            candidates = state.candidates,
            isError = state.preview.error != null,
            label = { Text(stringResource(R.string.relay_script)) },
        )
        Preview(state, onStepClick = { editingStep = it })

        HorizontalDivider()
        OptionChips(
            title = stringResource(R.string.relay_transition),
            options = TransitionMode.entries, selected = state.mode,
            label = { stringResource(if (it == TransitionMode.SEAMLESS) R.string.relay_seamless else R.string.relay_interaction) },
            onSelect = viewModel::setMode,
        )
        // The cue only plays at seamless boundaries: hide it when every boundary waits for you.
        val anySeamless = state.mode == TransitionMode.SEAMLESS || state.overrides.values.any { it.transition == TransitionMode.SEAMLESS }
        if (anySeamless) OptionChips(
            title = stringResource(R.string.relay_tone),
            options = TransitionTone.entries, selected = state.tone,
            label = { stringResource(toneLabel(it)) },
            onSelect = viewModel::setTone,
        )
        SoundPickerRow(stringResource(R.string.relay_default_sound), state.defaultSound.uri) {
            viewModel.setDefaultSound(state.defaultSound.copy(uri = it))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.alarm_vibrate), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = state.defaultSound.vibrate, onCheckedChange = { viewModel.setDefaultSound(state.defaultSound.copy(vibrate = it)) })
        }

        state.saveError?.let { Text(saveErrorText(it), color = MaterialTheme.colorScheme.error) }
        if (!state.isNew) TextButton(onClick = viewModel::askDelete) { Text(stringResource(R.string.action_delete)) }
    }
}

@Composable
private fun Preview(state: RelayEditState, onStepClick: (OwnStep) -> Unit) {
    val preview = state.preview
    val lineCol: (Int, Int) -> String = { l, c -> "$l:$c" }
    if (preview.error != null) {
        val where = positionText(state.script, preview.errorPosition, lineCol)
        Text(
            listOfNotNull(where?.let { stringResource(R.string.relay_error_at, it) }, preview.error).joinToString(" "),
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge,
        )
        return
    }
    val total = preview.totalSeconds ?: return
    Text(
        pluralStringResource(R.plurals.relay_total, preview.planSize, DurationText.short(total), preview.planSize),
        style = MaterialTheme.typography.titleLarge,
    )
    if (total > state.longWarningHours * 3600L) {
        Text(pluralStringResource(R.plurals.relay_long_warning, state.longWarningHours, state.longWarningHours), color = MaterialTheme.colorScheme.error)
    }
    Text(stringResource(R.string.relay_tap_to_override), style = MaterialTheme.typography.labelSmall)
    preview.steps.forEach { step ->
        val override = state.overrides[step.index]
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onStepClick(step) }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${step.index + 1}.", fontFamily = FontFamily.Monospace, modifier = Modifier.padding(end = 8.dp))
            Column(Modifier.weight(1f)) {
                val name = step.label ?: stringResource(R.string.relay_anonymous_step)
                val kind = when (step.kind) {
                    OwnStepKind.RELAY -> " · " + stringResource(R.string.kind_relay)
                    else -> ""
                }
                Text("$name$kind", style = MaterialTheme.typography.bodyLarge)
                if (override != null) {
                    val parts = listOfNotNull(
                        override.soundUri?.let { stringResource(R.string.override_sound_set) },
                        override.transition?.let {
                            stringResource(R.string.override_then, stringResource(if (it == TransitionMode.SEAMLESS) R.string.relay_seamless else R.string.relay_interaction))
                        },
                    )
                    Text(parts.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Text(DurationText.short(step.seconds), fontFamily = FontFamily.Monospace)
        }
    }
}

/** Per-step override (spec §05): the step's sound, and the transition right after it. */
@Composable
private fun OverrideDialog(step: OwnStep, current: StepOverride?, onDismiss: () -> Unit, onSave: (StepOverride?) -> Unit) {
    var soundUri by remember { mutableStateOf(current?.soundUri) }
    var transition by remember { mutableStateOf(current?.transition) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(step.label ?: stringResource(R.string.relay_anonymous_step)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // A referenced relay's steps keep their own sounds; only its outgoing boundary can change.
                if (step.kind != OwnStepKind.RELAY) {
                    SoundPickerRow(stringResource(R.string.override_sound), soundUri) { soundUri = it }
                }
                OptionChips(
                    title = stringResource(R.string.override_transition),
                    options = listOf(null, TransitionMode.SEAMLESS, TransitionMode.INTERACTION),
                    selected = transition,
                    label = {
                        when (it) {
                            null -> stringResource(R.string.override_relay_default)
                            TransitionMode.SEAMLESS -> stringResource(R.string.relay_seamless)
                            TransitionMode.INTERACTION -> stringResource(R.string.relay_interaction)
                        }
                    },
                    onSelect = { transition = it },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(if (soundUri == null && transition == null) null else StepOverride(soundUri, transition))
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onSave(null) }) { Text(stringResource(R.string.override_clear)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

@Composable
private fun SyntaxHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.relay_syntax_help)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HELP.forEach { (code, text) ->
                    Text(code, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}

private val HELP = listOf(
    "(25m, 5m, 25m)" to R.string.help_list,
    "25   1h30m   45s" to R.string.help_durations,
    "(Work, Break)" to R.string.help_refs,
    "(Test2 - 2)" to R.string.help_ref_math,
    "repeat(4, (Work, Break))" to R.string.help_repeat,
    "range(1, 3, 0.5)" to R.string.help_range,
    "5m  # a comment" to R.string.help_comment,
    "5m \"stretch\"" to R.string.help_label,
    "MINUTE(NOW)   RANDOM(5, 10)" to R.string.help_time,
)

fun toneLabel(tone: TransitionTone) = when (tone) {
    TransitionTone.BEEP -> R.string.tone_beep
    TransitionTone.DOUBLE_BEEP -> R.string.tone_double_beep
    TransitionTone.CHIME -> R.string.tone_chime
    TransitionTone.PIP -> R.string.tone_pip
    TransitionTone.SILENT -> R.string.tone_silent
}
