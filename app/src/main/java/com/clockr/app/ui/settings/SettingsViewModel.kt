package com.clockr.app.ui.settings

import android.app.Application
import android.app.LocaleManager
import android.content.Intent
import android.os.LocaleList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clockr.app.alarm.AlarmScheduler
import com.clockr.app.data.AppSettings
import com.clockr.app.data.ClockrDatabase
import com.clockr.app.data.SettingsRepository
import com.clockr.app.data.ThemeSetting
import com.clockr.app.run.TimerRunService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)

    val settings: StateFlow<AppSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setTheme(value: ThemeSetting) = viewModelScope.launch { repository.setTheme(value) }
    fun setClockFormat24h(value: Boolean) = viewModelScope.launch { repository.setClockFormat24h(value) }
    fun setFirstDayOfWeek(value: DayOfWeek) = viewModelScope.launch { repository.setFirstDayOfWeek(value) }
    fun setDefaultSnoozeMinutes(value: Int) = viewModelScope.launch { repository.setDefaultSnoozeMinutes(value) }
    fun setHaptics(value: Boolean) = viewModelScope.launch { repository.setHaptics(value) }
    fun setMaxExecutionPlanSize(value: Int) = viewModelScope.launch { repository.setMaxExecutionPlanSize(value) }

    /** Applied immediately via the platform per-app language API (minSdk 33, no appcompat needed). */
    fun setLanguageTag(value: String) = viewModelScope.launch {
        repository.setLanguageTag(value)
        val localeManager = getApplication<Application>().getSystemService(LocaleManager::class.java)
        localeManager.applicationLocales = if (value.isBlank()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(value)
    }

    /**
     * For testing/starting over: cancels every scheduled alarm and stops any active timer run
     * first (otherwise a stale AlarmManager entry or foreground service would outlive the rows
     * it points at), then wipes every Room table. Settings themselves (DataStore) are untouched —
     * this clears data, not preferences.
     */
    fun resetAllData() = viewModelScope.launch {
        val app = getApplication<Application>()
        val db = ClockrDatabase.get(app)
        val scheduler = AlarmScheduler(app)
        withContext(Dispatchers.IO) {
            db.alarmDao().getAllEnabled().forEach { scheduler.cancel(it) }
        }
        app.stopService(Intent(app, TimerRunService::class.java))
        withContext(Dispatchers.IO) {
            db.clearAllTables()
        }
    }
}
