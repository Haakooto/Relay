package com.relay.app

import android.content.Context
import com.relay.app.clock.ClockRepository
import com.relay.app.data.RelayDatabase
import com.relay.app.data.settings.SettingsRepository
import com.relay.app.event.CalendarRepository
import com.relay.app.event.EventRepository
import com.relay.app.firing.FiringNotifications
import com.relay.app.firing.ReminderNotifications
import com.relay.app.library.LibraryRepository
import com.relay.app.run.RunNotifications
import com.relay.app.run.RunRepository
import com.relay.app.run.RunScheduler
import com.relay.app.schedule.AlarmScheduler
import com.relay.app.schedule.ScheduleRepository
import com.relay.app.schedule.ScheduledStarter
import com.relay.app.stopwatch.StopwatchNotifications
import com.relay.app.stopwatch.StopwatchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

/** Manual dependency container, one per process. Held by [RelayApplication]. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val database: RelayDatabase by lazy { RelayDatabase.build(appContext) }
    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }
    val alarmScheduler: AlarmScheduler by lazy { AlarmScheduler(appContext) }
    val firingNotifications: FiringNotifications by lazy { FiringNotifications(appContext, settings) }
    val schedules: ScheduleRepository by lazy {
        ScheduleRepository(database.scheduleDao(), alarmScheduler, firingNotifications, settings::current)
    }
    val runNotifications: RunNotifications by lazy { RunNotifications(appContext) }
    val runs: RunRepository by lazy { RunRepository(database.runDao(), RunScheduler(appContext), runNotifications) }
    val stopwatchNotifications: StopwatchNotifications by lazy { StopwatchNotifications(appContext) }
    val stopwatches: StopwatchRepository by lazy { StopwatchRepository(database.stopwatchDao(), stopwatchNotifications) }
    val clocks: ClockRepository by lazy { ClockRepository(database.clockDao(), settings) }
    val library: LibraryRepository by lazy {
        LibraryRepository(database.libraryDao(), database.libraryHeaderDao(), runs, settings::current)
    }
    val events: EventRepository by lazy { EventRepository(database.eventDao(), schedules) }
    val calendars: CalendarRepository by lazy { CalendarRepository(database.calendarDao(), database.eventDao(), schedules) }

    /** A day the Calendar pane should show next, set when a widget opens it; taken once. */
    val calendarJump = MutableStateFlow<LocalDate?>(null)
    val reminderNotifications: ReminderNotifications by lazy { ReminderNotifications(appContext, settings, database.eventDao()) }
    val scheduledStarter: ScheduledStarter by lazy { ScheduledStarter(library, stopwatches, settings, reminderNotifications) }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as RelayApplication).container
