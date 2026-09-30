package com.relay.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.relay.app.ui.RelayApp
import com.relay.app.ui.theme.RelayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settingsFlow = appContainer.settings.settings
        setContent {
            // Null until DataStore's first read (a few ms), so a dark-theme user never sees a
            // light frame flash on launch.
            val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = null)
            settings?.let {
                RelayTheme(theme = it.theme) {
                    RelayApp()
                }
            }
        }
    }
}
