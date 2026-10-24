package com.relay.app

import android.app.Application
import android.util.Log
import com.relay.app.widget.ClockWidget
import com.relay.app.widget.WidgetTicker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RelayApplication : Application() {
    lateinit var container: AppContainer
        private set

    /** For work that must outlive any single screen. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.firingNotifications.createChannels()
        container.reminderNotifications.createChannel()
        container.runNotifications.createChannel()
        container.stopwatchNotifications.createChannel()
        // Safety net: a force-stop clears every pending alarm without any broadcast, so re-arm
        // (and catch up on misses) whenever the process starts.
        appScope.launch {
            runCatching { container.schedules.rescheduleAll() }
                .onFailure { Log.e("RelayApplication", "rescheduleAll failed", it) }
            runCatching { container.runs.rearmAll() }
                .onFailure { Log.e("RelayApplication", "rearmAll failed", it) }
            runCatching { container.stopwatches.republishAll() }
                .onFailure { Log.e("RelayApplication", "stopwatch republish failed", it) }
            runCatching { ClockWidget.refreshAll(this@RelayApplication); WidgetTicker.schedule(this@RelayApplication) }
                .onFailure { Log.e("RelayApplication", "widget refresh failed", it) }
        }
    }
}
