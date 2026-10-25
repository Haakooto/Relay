package com.relay.app.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.relay.app.R
import com.relay.app.guide.GuideButton
import com.relay.app.guide.GuideSection

/** Opens Settings, which has no place in the bottom bar (spec §10b). Provided by `RelayApp`. */
val LocalOpenSettings = staticCompositionLocalOf<() -> Unit> { {} }

/** The title row at the top of every main pane: its name, any pane [actions], the guide, then Settings. */
@Composable
fun PaneHeader(@StringRes title: Int, section: GuideSection, actions: @Composable RowScope.() -> Unit = {}) {
    val openSettings = LocalOpenSettings.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        actions()
        GuideButton(section)
        IconButton(onClick = openSettings) {
            Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.pane_settings))
        }
    }
}
