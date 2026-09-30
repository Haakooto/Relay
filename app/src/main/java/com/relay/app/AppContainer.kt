package com.relay.app

import android.content.Context
import com.relay.app.data.RelayDatabase
import com.relay.app.data.settings.SettingsRepository
import com.relay.app.firing.FiringNotifications
import com.relay.app.library.LibraryRepository
import com.relay.app.run.RunNotifications
import com.relay.app.run.RunRepository
import com.relay.app.run.RunScheduler
import com.relay.app.schedule.AlarmScheduler
import com.relay.app.schedule.ScheduleRepository

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
    val library: LibraryRepository by lazy {
        LibraryRepository(database.libraryDao(), database.libraryHeaderDao(), runs, settings::current)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as RelayApplication).container
