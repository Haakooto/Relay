package com.relay.app.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.relay.app.R

/**
 * Editors are modal (no bottom bar): leaving means saving or discarding. Back, and the returned
 * close action, go straight out when nothing changed, and ask first when something did.
 */
@Composable
fun rememberDiscardGuard(isDirty: () -> Boolean, onLeave: () -> Unit): () -> Unit {
    var asking by remember { mutableStateOf(false) }
    val attemptLeave = { if (isDirty()) asking = true else onLeave() }
    BackHandler(onBack = attemptLeave)
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(R.string.discard_title)) },
            confirmButton = { TextButton(onClick = { asking = false; onLeave() }) { Text(stringResource(R.string.discard_confirm)) } },
            dismissButton = { TextButton(onClick = { asking = false }) { Text(stringResource(R.string.discard_keep)) } },
        )
    }
    return attemptLeave
}
