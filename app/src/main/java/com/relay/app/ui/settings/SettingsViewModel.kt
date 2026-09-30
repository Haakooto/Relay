package com.relay.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.relay.app.appContainer
import com.relay.app.data.settings.AppSettings
import com.relay.app.data.settings.ThemeSetting
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = application.appContainer.settings

    val settings: StateFlow<AppSettings?> =
        repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val versionName: String = application.packageManager
        .getPackageInfo(application.packageName, 0).versionName ?: "?"

    fun setTheme(value: ThemeSetting) = viewModelScope.launch { repository.setTheme(value) }
    fun setClockFormat24h(value: Boolean) = viewModelScope.launch { repository.setClockFormat24h(value) }
    fun setFirstDayOfWeek(value: DayOfWeek) = viewModelScope.launch { repository.setFirstDayOfWeek(value) }
    fun setHaptics(value: Boolean) = viewModelScope.launch { repository.setHaptics(value) }
}
