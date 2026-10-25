package com.relay.app.ui

import android.content.Intent
import com.relay.app.AppContainer
import com.relay.app.MainActivity
import java.time.LocalDate

/** Where a notification or widget asked the app to open, once checked (spec §10b). */
sealed interface LaunchTarget {
    data class ShowPane(val route: String) : LaunchTarget
    data class OpenItem(val id: String) : LaunchTarget
    data class OpenAlarm(val id: String) : LaunchTarget
    data class NewEvent(val day: LocalDate) : LaunchTarget

    companion object {
        private const val MAX_ID_LENGTH = 64
        private const val MAX_YEARS_AWAY = 200L

        /**
         * Reads [intent]'s extras. MainActivity is exported (it's the launcher), so any app can
         * send these: ids must name an existing item or alarm, days must be sane, and a pane must
         * be one of ours. Anything else is ignored.
         */
        suspend fun from(intent: Intent?, container: AppContainer): LaunchTarget? {
            intent ?: return null
            return item(intent, container) ?: alarm(intent, container)
                ?: day(intent, MainActivity.EXTRA_NEW_EVENT_DAY)?.let(::NewEvent)
                ?: day(intent, MainActivity.EXTRA_CALENDAR_DAY)?.let { day ->
                    container.calendarJump.value = day
                    ShowPane(Pane.CALENDAR.route)
                }
                ?: intent.getStringExtra(MainActivity.EXTRA_PANE)
                    ?.takeIf { route -> Pane.entries.any { it.route == route } }
                    ?.let(::ShowPane)
        }

        /** An item to open; one from a subscribed calendar (#33) is read-only, so its day opens instead. */
        private suspend fun item(intent: Intent, container: AppContainer): LaunchTarget? {
            val id = intent.getStringExtra(MainActivity.EXTRA_OPEN_ITEM)?.takeIf { it.length <= MAX_ID_LENGTH } ?: return null
            val event = container.events.get(id) ?: return null
            if (container.calendars.get(event.calendarId)?.isSubscribed != true) return OpenItem(id)
            container.calendarJump.value = LocalDate.ofEpochDay(event.startEpochDay)
            return ShowPane(Pane.CALENDAR.route)
        }

        private suspend fun alarm(intent: Intent, container: AppContainer): LaunchTarget? =
            intent.getStringExtra(MainActivity.EXTRA_OPEN_ALARM)
                ?.takeIf { it.length <= MAX_ID_LENGTH && container.schedules.get(it)?.let { s -> s.eventId == null } == true }
                ?.let(::OpenAlarm)

        private fun day(intent: Intent, extra: String): LocalDate? {
            if (!intent.hasExtra(extra)) return null
            val today = LocalDate.now()
            return runCatching { LocalDate.ofEpochDay(intent.getLongExtra(extra, today.toEpochDay())) }.getOrNull()
                ?.takeIf { !it.isBefore(today.minusYears(MAX_YEARS_AWAY)) && !it.isAfter(today.plusYears(MAX_YEARS_AWAY)) }
        }
    }
}
