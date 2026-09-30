package com.relay.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "relay_settings")

enum class ThemeSetting { SYSTEM, LIGHT, DARK, AMOLED }

/**
 * Spec §11. Language is deliberately not here: it's stored by the system's per-app language API
 * (LocaleManager), which also exposes it in system settings. Feature-specific settings are added
 * with the build step that uses them.
 */
data class AppSettings(
    val theme: ThemeSetting = ThemeSetting.SYSTEM,
    val clockFormat24h: Boolean = true,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val haptics: Boolean = true,

    // Alarms (build step 2). Snooze, sound and ramp are defaults for new alarms; each alarm keeps
    // its own copy. Ring timeout and the upcoming lead time are global.
    val defaultSnoozeMinutes: Int = 10,
    /** Null = the system default alarm sound. */
    val defaultAlarmSoundUri: String? = null,
    val defaultRampSeconds: Int = 0,
    val ringTimeoutMinutes: Int = 10,
    /** 0 = no upcoming-alarm notification. */
    val upcomingLeadMinutes: Int = 120,

    // Timers and relays (build steps 3-4).
    /** The four quick-start chips, in seconds (spec §05; stock: 1, 5, 10, 15 min). */
    val quickTimerSeconds: List<Long> = DEFAULT_QUICK_TIMERS,
    /** Null = the system default alarm sound. */
    val defaultTimerSoundUri: String? = null,
    /** Spec §06: default 300 steps, hard ceiling 1000. */
    val maxPlanSize: Int = 300,
    /** Relays longer than this get a warning in the editor. */
    val longRelayWarningHours: Int = 24,
)

val DEFAULT_QUICK_TIMERS = listOf(60L, 300L, 600L, 900L)

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore

    val settings: Flow<AppSettings> = store.data.map { prefs ->
        AppSettings(
            theme = prefs[THEME].toEnum(ThemeSetting.SYSTEM),
            clockFormat24h = prefs[CLOCK_24H] ?: true,
            firstDayOfWeek = prefs[FIRST_DAY].toEnum(DayOfWeek.MONDAY),
            haptics = prefs[HAPTICS] ?: true,
            defaultSnoozeMinutes = prefs[DEFAULT_SNOOZE] ?: 10,
            defaultAlarmSoundUri = prefs[DEFAULT_ALARM_SOUND],
            defaultRampSeconds = prefs[DEFAULT_RAMP] ?: 0,
            ringTimeoutMinutes = prefs[RING_TIMEOUT] ?: 10,
            upcomingLeadMinutes = prefs[UPCOMING_LEAD] ?: 120,
            quickTimerSeconds = prefs[QUICK_TIMERS]?.split(",")?.mapNotNull { it.toLongOrNull() }
                ?.takeIf { it.size == DEFAULT_QUICK_TIMERS.size } ?: DEFAULT_QUICK_TIMERS,
            defaultTimerSoundUri = prefs[DEFAULT_TIMER_SOUND],
            maxPlanSize = (prefs[MAX_PLAN_SIZE] ?: 300).coerceIn(1, MAX_PLAN_CEILING),
            longRelayWarningHours = prefs[LONG_RELAY_HOURS] ?: 24,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setTheme(value: ThemeSetting) = store.edit { it[THEME] = value.name }
    suspend fun setClockFormat24h(value: Boolean) = store.edit { it[CLOCK_24H] = value }
    suspend fun setFirstDayOfWeek(value: DayOfWeek) = store.edit { it[FIRST_DAY] = value.name }
    suspend fun setHaptics(value: Boolean) = store.edit { it[HAPTICS] = value }
    suspend fun setDefaultSnoozeMinutes(value: Int) = store.edit { it[DEFAULT_SNOOZE] = value }
    suspend fun setDefaultAlarmSoundUri(value: String?) = store.edit {
        if (value == null) it.remove(DEFAULT_ALARM_SOUND) else it[DEFAULT_ALARM_SOUND] = value
    }
    suspend fun setDefaultRampSeconds(value: Int) = store.edit { it[DEFAULT_RAMP] = value }
    suspend fun setRingTimeoutMinutes(value: Int) = store.edit { it[RING_TIMEOUT] = value }
    suspend fun setUpcomingLeadMinutes(value: Int) = store.edit { it[UPCOMING_LEAD] = value }
    suspend fun setQuickTimer(index: Int, seconds: Long) = store.edit { prefs ->
        val current = prefs[QUICK_TIMERS]?.split(",")?.mapNotNull { it.toLongOrNull() }
            ?.takeIf { it.size == DEFAULT_QUICK_TIMERS.size } ?: DEFAULT_QUICK_TIMERS
        prefs[QUICK_TIMERS] = current.toMutableList().also { it[index] = seconds }.joinToString(",")
    }
    suspend fun setDefaultTimerSoundUri(value: String?) = store.edit {
        if (value == null) it.remove(DEFAULT_TIMER_SOUND) else it[DEFAULT_TIMER_SOUND] = value
    }
    suspend fun setMaxPlanSize(value: Int) = store.edit { it[MAX_PLAN_SIZE] = value.coerceIn(1, MAX_PLAN_CEILING) }
    suspend fun setLongRelayWarningHours(value: Int) = store.edit { it[LONG_RELAY_HOURS] = value }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val CLOCK_24H = booleanPreferencesKey("clock_format_24h")
        val FIRST_DAY = stringPreferencesKey("first_day_of_week")
        val HAPTICS = booleanPreferencesKey("haptics")
        val DEFAULT_SNOOZE = intPreferencesKey("default_snooze_minutes")
        val DEFAULT_ALARM_SOUND = stringPreferencesKey("default_alarm_sound_uri")
        val DEFAULT_RAMP = intPreferencesKey("default_ramp_seconds")
        val RING_TIMEOUT = intPreferencesKey("ring_timeout_minutes")
        val UPCOMING_LEAD = intPreferencesKey("upcoming_lead_minutes")
        val QUICK_TIMERS = stringPreferencesKey("quick_timer_seconds")
        val DEFAULT_TIMER_SOUND = stringPreferencesKey("default_timer_sound_uri")
        val MAX_PLAN_SIZE = intPreferencesKey("max_plan_size")
        val LONG_RELAY_HOURS = intPreferencesKey("long_relay_warning_hours")
        const val MAX_PLAN_CEILING = 1000
    }
}

private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
    this?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: default
