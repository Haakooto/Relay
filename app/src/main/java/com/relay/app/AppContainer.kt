package com.relay.app

import android.content.Context
import com.relay.app.data.RelayDatabase
import com.relay.app.data.settings.SettingsRepository

/** Manual dependency container, one per process. Held by [RelayApplication]. */
class AppContainer(context: Context) {
    val database: RelayDatabase by lazy { RelayDatabase.build(context) }
    val settings: SettingsRepository by lazy { SettingsRepository(context) }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as RelayApplication).container
