package com.relay.app

import android.app.Application
import android.util.Log
import com.relay.app.widget.ClockWidget
import com.relay.app.widget.WidgetTicker
import com.relay.app.widget.calendar.CalendarWidgetTicker
import com.relay.app.widget.calendar.CalendarWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val WIDGET_REFRESH_DEBOUNCE_MS = 500L

class RelayApplication : Application() {
    lateinit var container: AppContainer
        private set

    /** For work that must outlive any single screen. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @OptIn(FlowPreview::class) // debounce
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
            runCatching { CalendarWidgetTicker.schedule(this@RelayApplication) }
                .onFailure { Log.e("RelayApplication", "calendar tick failed", it) }
        }
        // Changes only happen while the app's process runs, so the calendar widgets follow them
        // from here: one place, instead of a hook in every repository.
        appScope.launch {
            combine(container.events.observeAll(), container.schedules.observeWithExceptions(), container.settings.settings) { a, b, c ->
                Triple(a, b, c)
            }.distinctUntilChanged().debounce(WIDGET_REFRESH_DEBOUNCE_MS).collect {
                runCatching { CalendarWidgets.refreshAll(this@RelayApplication) }
                    .onFailure { Log.e("RelayApplication", "calendar widget refresh failed", it) }
            }
        }
    }
}
