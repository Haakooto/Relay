package com.relay.app.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.relay.app.guide.GuideButton
import com.relay.app.guide.GuideSection

/** The title row at the top of every main pane: its name, any pane [actions], then the guide. */
@Composable
fun PaneHeader(@StringRes title: Int, section: GuideSection, actions: @Composable RowScope.() -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        actions()
        GuideButton(section)
    }
}
