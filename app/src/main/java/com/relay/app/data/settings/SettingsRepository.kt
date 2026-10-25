package com.relay.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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

    // Clock (build step 7).
    /** ClockStyle JSON for the Clock pane's main clock; null = defaults. */
    val mainClockStyle: String? = null,
    /** Null = no home clock. When set and different from the phone's zone, the Clock pane shows it. */
    val homeZoneId: String? = null,

    // Calendar (spec §10b).
    /** Before calendars (#34): show alarms too. Read once to tick the alarms calendar, then cleared. */
    val calendarShowAll: Boolean = false,
    /** The calendar a new event or reminder goes to: the last one used. Null = the first. */
    val lastCalendarId: String? = null,
    /** Phone calendars (#16) ticked in the Calendar pane, by CalendarContract id. */
    val shownPhoneCalendars: Set<Long> = emptySet(),

    // Navigation.
    /** Route of the main pane last opened; the app and its widgets open there. Null = Alarms. */
    val lastPane: String? = null,
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
            mainClockStyle = prefs[MAIN_CLOCK_STYLE],
            homeZoneId = prefs[HOME_ZONE],
            calendarShowAll = prefs[CALENDAR_SHOW_ALL] ?: false,
            lastCalendarId = prefs[LAST_CALENDAR],
            shownPhoneCalendars = prefs[SHOWN_PHONE_CALENDARS].orEmpty().mapNotNull { it.toLongOrNull() }.toSet(),
            lastPane = prefs[LAST_PANE],
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
    suspend fun setMainClockStyle(json: String) = store.edit { it[MAIN_CLOCK_STYLE] = json }
    suspend fun setHomeZoneId(value: String?) = store.edit { if (value == null) it.remove(HOME_ZONE) else it[HOME_ZONE] = value }
    suspend fun clearCalendarShowAll() = store.edit { it.remove(CALENDAR_SHOW_ALL) }
    suspend fun setLastCalendarId(id: String) = store.edit { it[LAST_CALENDAR] = id }
    suspend fun setPhoneCalendarShown(id: Long, shown: Boolean) = store.edit { prefs ->
        val now = prefs[SHOWN_PHONE_CALENDARS].orEmpty()
        prefs[SHOWN_PHONE_CALENDARS] = if (shown) now + id.toString() else now - id.toString()
    }
    suspend fun setLastPane(route: String) = store.edit { it[LAST_PANE] = route }

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
        val MAIN_CLOCK_STYLE = stringPreferencesKey("main_clock_style")
        val HOME_ZONE = stringPreferencesKey("home_zone_id")
        val CALENDAR_SHOW_ALL = booleanPreferencesKey("calendar_show_all")
        val LAST_CALENDAR = stringPreferencesKey("last_calendar_id")
        val SHOWN_PHONE_CALENDARS = stringSetPreferencesKey("shown_phone_calendars")
        val LAST_PANE = stringPreferencesKey("last_pane")
        const val MAX_PLAN_CEILING = 1000
    }
}

private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
    this?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: default
