package com.relay.app

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.mutableStateOf
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.relay.app.ui.RelayApp
import com.relay.app.ui.theme.RelayTheme

class MainActivity : ComponentActivity() {
    /**
     * A widget tap asks for the Clock pane. Consumed once: v0.5 re-read the launch intent on every
     * recreate (language, rotation, theme) and kept jumping back to Clock.
     */
    private var openClock = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) openClock.value = consumeOpenClock(intent)
        enableEdgeToEdge()
        val settingsFlow = appContainer.settings.settings
        setContent {
            // Null until DataStore's first read (a few ms), so a dark-theme user never sees a
            // light frame flash on launch.
            val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = null)
            settings?.let {
                RelayTheme(theme = it.theme) {
                    RelayApp(openClock = openClock.value, onClockOpened = { openClock.value = false })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (consumeOpenClock(intent)) openClock.value = true
    }

    private fun consumeOpenClock(intent: Intent?): Boolean {
        val open = intent?.getBooleanExtra(EXTRA_OPEN_CLOCK, false) == true
        intent?.removeExtra(EXTRA_OPEN_CLOCK)
        return open
    }

    companion object {
        const val EXTRA_OPEN_CLOCK = "openClock"
    }
}
