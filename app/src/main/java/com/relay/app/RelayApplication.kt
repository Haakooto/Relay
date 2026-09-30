package com.relay.app

import android.app.Application
import android.util.Log
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
        // Safety net: a force-stop clears every pending alarm without any broadcast, so re-arm
        // (and catch up on misses) whenever the process starts.
        appScope.launch {
            runCatching { container.schedules.rescheduleAll() }
                .onFailure { Log.e("RelayApplication", "rescheduleAll failed", it) }
        }
    }
}
