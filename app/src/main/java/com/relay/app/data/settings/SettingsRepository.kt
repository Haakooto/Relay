package com.relay.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "relay_settings")

enum class ThemeSetting { SYSTEM, LIGHT, DARK, AMOLED }

/**
 * Spec §11. Language is deliberately not here: it's stored by the system's per-app language API
 * (LocaleManager), which also exposes it in system settings. Feature-specific settings (quick
 * timers, ring timeout, ...) are added with the build step that uses them.
 */
data class AppSettings(
    val theme: ThemeSetting = ThemeSetting.SYSTEM,
    val clockFormat24h: Boolean = true,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val haptics: Boolean = true,
)

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore

    val settings: Flow<AppSettings> = store.data.map { prefs ->
        AppSettings(
            theme = prefs[THEME].toEnum(ThemeSetting.SYSTEM),
            clockFormat24h = prefs[CLOCK_24H] ?: true,
            firstDayOfWeek = prefs[FIRST_DAY].toEnum(DayOfWeek.MONDAY),
            haptics = prefs[HAPTICS] ?: true,
        )
    }

    suspend fun setTheme(value: ThemeSetting) = store.edit { it[THEME] = value.name }
    suspend fun setClockFormat24h(value: Boolean) = store.edit { it[CLOCK_24H] = value }
    suspend fun setFirstDayOfWeek(value: DayOfWeek) = store.edit { it[FIRST_DAY] = value.name }
    suspend fun setHaptics(value: Boolean) = store.edit { it[HAPTICS] = value }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val CLOCK_24H = booleanPreferencesKey("clock_format_24h")
        val FIRST_DAY = stringPreferencesKey("first_day_of_week")
        val HAPTICS = booleanPreferencesKey("haptics")
    }
}

private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
    this?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: default
