package com.relay.app.ui.clock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.relay.app.R
import com.relay.app.clock.ClockStyle
import com.relay.app.ui.common.rememberDiscardGuard

/** Edits the Clock pane's main clock, with a live preview on top. */
@Composable
fun ClockStyleScreen(onDone: () -> Unit, viewModel: ClockViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    val saved = remember(s.mainClockStyle) { ClockStyle.decode(s.mainClockStyle) }
    var draft by remember(saved) { mutableStateOf(saved) }
    val close = rememberDiscardGuard({ draft != saved }, onDone)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = close) { Icon(Icons.Outlined.Close, stringResource(R.string.action_cancel)) }
            Text(stringResource(R.string.clock_edit_style), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Button(onClick = { viewModel.saveStyle(draft); onDone() }) { Text(stringResource(R.string.action_save)) }
        }
        ClockFace(draft, s.clockFormat24h, Modifier.fillMaxWidth().padding(vertical = 16.dp), digitSize = 64.sp)
        ClockStyleEditor(draft) { draft = it }
    }
}
