package com.relay.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.relay.app.ui.RelayApp
import com.relay.app.ui.theme.RelayTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settingsRepository = appContainer.settings
        setContent {
            // Null until DataStore's first read (a few ms), so a dark-theme user never sees a
            // light frame flash on launch.
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
            settings?.let {
                // Read once: the start pane must not change under the NavHost when it's saved.
                // A reminder notification asks for its pane; RelayApp ignores anything unknown.
                val startPane = remember { intent?.getStringExtra(EXTRA_PANE) ?: it.lastPane }
                RelayTheme(theme = it.theme) {
                    RelayApp(
                        startRoute = startPane,
                        onPaneOpened = { route -> lifecycleScope.launch { settingsRepository.setLastPane(route) } },
                    )
                }
            }
        }
    }

    companion object {
        /** Optional: the route of the main pane to open on. */
        const val EXTRA_PANE = "pane"
    }
}
