package com.relay.app.ui.timers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.relay.app.R
import com.relay.app.data.run.RunPhase
import com.relay.app.run.RunNotifications
import com.relay.app.run.RunView
import com.relay.app.util.DurationText

/** Controls for one live run; [now] ticks from the parent so every card shares one clock. */
data class RunActions(
    val pause: () -> Unit, val resume: () -> Unit, val reset: () -> Unit, val addMinute: () -> Unit,
    val stop: () -> Unit, val continueRun: () -> Unit, val restart: () -> Unit,
)

@Composable
fun RunCard(view: RunView, now: Long, actions: RunActions) {
    val context = LocalContext.current
    val run = view.run
    val remainingMillis = when (run.phase) {
        RunPhase.RUNNING -> ((run.stepEndsAtMillis ?: now) - now).coerceAtLeast(0) + view.afterStepMillis
        RunPhase.PAUSED -> (run.remainingMillis ?: 0) + view.afterStepMillis
        else -> 0
    }
    val stepMillis = view.countdownMillis
    val alerting = run.phase == RunPhase.AWAITING || run.phase == RunPhase.DONE

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (alerting) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(RunNotifications.titleFor(context, view), style = MaterialTheme.typography.titleLarge)
            RunNotifications.detailFor(context, view).takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
            Text(
                when (run.phase) {
                    RunPhase.AWAITING -> stringResource(R.string.run_waiting_short)
                    RunPhase.DONE -> stringResource(R.string.run_done)
                    // Round up, like a kitchen timer: it shows 0:00 only when it's over.
                    else -> DurationText.clock((remainingMillis + 999) / 1000)
                },
                fontSize = 44.sp, fontWeight = FontWeight.SemiBold,
            )
            if (!alerting && stepMillis > 0) {
                LinearProgressIndicator(
                    progress = { 1f - remainingMillis.toFloat() / stepMillis },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                when (run.phase) {
                    RunPhase.RUNNING -> {
                        Button(onClick = actions.pause) { Text(stringResource(R.string.run_pause)) }
                        OutlinedButton(onClick = actions.addMinute) { Text(stringResource(R.string.run_add_minute)) }
                        TextButton(onClick = actions.stop) { Text(stringResource(R.string.run_stop)) }
                    }
                    RunPhase.PAUSED -> {
                        Button(onClick = actions.resume) { Text(stringResource(R.string.run_resume)) }
                        OutlinedButton(onClick = actions.reset) { Text(stringResource(R.string.run_reset)) }
                        TextButton(onClick = actions.stop) { Text(stringResource(R.string.run_stop)) }
                    }
                    RunPhase.AWAITING -> {
                        Button(onClick = actions.continueRun) { Text(stringResource(R.string.run_continue)) }
                        TextButton(onClick = actions.stop) { Text(stringResource(R.string.run_stop_relay)) }
                    }
                    RunPhase.DONE -> {
                        Button(onClick = actions.stop) { Text(stringResource(R.string.run_stop)) }
                        OutlinedButton(onClick = actions.restart) { Text(stringResource(R.string.run_restart)) }
                        OutlinedButton(onClick = actions.addMinute) { Text(stringResource(R.string.run_add_minute)) }
                    }
                }
            }
        }
    }
}
