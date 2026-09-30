package com.relay.app.ui.timers

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.relay.app.R
import com.relay.app.library.SaveError

/** Text for a refused save. chainlang's own messages stay English (a known i18n boundary). */
@Composable
fun saveErrorText(error: SaveError): String = when (error) {
    SaveError.LabelRequired -> stringResource(R.string.save_label_required)
    SaveError.LabelNotReferenceable -> stringResource(R.string.save_label_invalid)
    SaveError.LabelTaken -> stringResource(R.string.save_label_taken)
    is SaveError.Cycle -> stringResource(R.string.save_cycle, error.path.joinToString(" → "))
    is SaveError.Invalid -> error.message
}

/** "Line 2, col 5" for a chainlang syntax error position, or null when it has none. */
fun positionText(script: String, position: Int, lineColTemplate: (Int, Int) -> String): String? {
    if (position < 0 || position > script.length) return null
    val before = script.substring(0, position)
    val line = before.count { it == '\n' } + 1
    val col = position - (before.lastIndexOf('\n') + 1) + 1
    return lineColTemplate(line, col)
}
