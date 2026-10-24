package com.relay.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.relay.app.ui.LaunchTarget
import com.relay.app.ui.RelayApp
import com.relay.app.ui.theme.RelayTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** Where a widget or notification asked to go, once checked; cleared when RelayApp has gone there. */
    private val launchTarget = mutableStateOf<LaunchTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only on a fresh start: after a rotation the screen is already where it was sent.
        if (savedInstanceState == null) resolveLaunch(intent)
        val settingsRepository = appContainer.settings
        setContent {
            // Null until DataStore's first read (a few ms), so a dark-theme user never sees a
            // light frame flash on launch.
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
            settings?.let {
                // Read once: the start pane must not change under the NavHost when it's saved.
                val startPane = remember { it.lastPane }
                RelayTheme(theme = it.theme) {
                    RelayApp(
                        startRoute = startPane,
                        onPaneOpened = { route -> lifecycleScope.launch { settingsRepository.setLastPane(route) } },
                        launch = launchTarget.value,
                        onLaunched = { launchTarget.value = null },
                    )
                }
            }
        }
    }

    /** A widget or notification tapped while the app is already open (singleTop). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        resolveLaunch(intent)
    }

    private fun resolveLaunch(intent: Intent?) {
        lifecycleScope.launch { LaunchTarget.from(intent, appContainer)?.let { launchTarget.value = it } }
    }

    companion object {
        /** Optional: the route of the main pane to open on. */
        const val EXTRA_PANE = "pane"

        /** A calendar item's id: open its editor. */
        const val EXTRA_OPEN_ITEM = "openItem"

        /** An alarm's schedule id: open the alarm editor. */
        const val EXTRA_OPEN_ALARM = "openAlarm"

        /** An epoch day: a new event on it. */
        const val EXTRA_NEW_EVENT_DAY = "newEventDay"

        /** An epoch day: the Calendar pane on it. */
        const val EXTRA_CALENDAR_DAY = "calendarDay"
    }
}
