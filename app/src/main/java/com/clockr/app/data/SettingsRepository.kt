package com.clockr.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeSetting { SYSTEM, LIGHT, DARK, AMOLED }

/** Spec §08. */
data class AppSettings(
    val theme: ThemeSetting = ThemeSetting.SYSTEM,
    val clockFormat24h: Boolean = true,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    /** BCP-47 tag, e.g. "nb" or "en-US" — blank means follow the system. */
    val languageTag: String = "",
    val defaultSnoozeMinutes: Int = 9,
    val haptics: Boolean = true,
    val maxExecutionPlanSize: Int = 300,
)

class SettingsRepository(context: Context) {
    private val dataStore = context.settingsDataStore

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            theme = prefs[KEY_THEME]?.let { runCatching { ThemeSetting.valueOf(it) }.getOrNull() } ?: ThemeSetting.SYSTEM,
            clockFormat24h = prefs[KEY_CLOCK_FORMAT_24H] ?: true,
            firstDayOfWeek = prefs[KEY_FIRST_DAY]?.let { runCatching { DayOfWeek.valueOf(it) }.getOrNull() } ?: DayOfWeek.MONDAY,
            languageTag = prefs[KEY_LANGUAGE] ?: "",
            defaultSnoozeMinutes = prefs[KEY_DEFAULT_SNOOZE] ?: 9,
            haptics = prefs[KEY_HAPTICS] ?: true,
            maxExecutionPlanSize = (prefs[KEY_MAX_PLAN_SIZE] ?: 300).coerceIn(1, 1000),
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setTheme(value: ThemeSetting) = dataStore.edit { it[KEY_THEME] = value.name }
    suspend fun setClockFormat24h(value: Boolean) = dataStore.edit { it[KEY_CLOCK_FORMAT_24H] = value }
    suspend fun setFirstDayOfWeek(value: DayOfWeek) = dataStore.edit { it[KEY_FIRST_DAY] = value.name }
    suspend fun setLanguageTag(value: String) = dataStore.edit { it[KEY_LANGUAGE] = value }
    suspend fun setDefaultSnoozeMinutes(value: Int) = dataStore.edit { it[KEY_DEFAULT_SNOOZE] = value }
    suspend fun setHaptics(value: Boolean) = dataStore.edit { it[KEY_HAPTICS] = value }
    suspend fun setMaxExecutionPlanSize(value: Int) = dataStore.edit { it[KEY_MAX_PLAN_SIZE] = value.coerceIn(1, 1000) }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_CLOCK_FORMAT_24H = booleanPreferencesKey("clock_format_24h")
        val KEY_FIRST_DAY = stringPreferencesKey("first_day_of_week")
        val KEY_LANGUAGE = stringPreferencesKey("language_tag")
        val KEY_DEFAULT_SNOOZE = intPreferencesKey("default_snooze_minutes")
        val KEY_HAPTICS = booleanPreferencesKey("haptics")
        val KEY_MAX_PLAN_SIZE = intPreferencesKey("max_execution_plan_size")
    }
}
