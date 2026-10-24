package com.relay.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.relay.app.R
import com.relay.app.ui.alarms.AlarmEditScreen
import com.relay.app.ui.alarms.AlarmsScreen
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import com.relay.app.guide.GuideScreen
import com.relay.app.guide.GuideSection
import com.relay.app.guide.LocalOpenGuide
import com.relay.app.ui.clock.ClockScreen
import com.relay.app.ui.clock.ClockStyleScreen
import com.relay.app.ui.stopwatch.StopwatchScreen
import com.relay.app.ui.timers.RelayEditScreen
import com.relay.app.ui.timers.TimerEditScreen
import com.relay.app.ui.timers.TimersScreen
import com.relay.app.ui.settings.SettingsScreen

/**
 * [startRoute]: the main pane last opened (saved through [onPaneOpened]), so the launcher icon
 * and the widgets open where the user left off. Settings isn't remembered: it's a detour.
 */
@Composable
fun RelayApp(startRoute: String? = null, onPaneOpened: (String) -> Unit = {}) {
    val navController = rememberNavController()
    val start = remember { REMEMBERED_PANES.firstOrNull { it.route == startRoute } ?: Pane.ALARMS }
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination
    LaunchedEffect(current?.route) {
        current?.route?.takeIf { route -> REMEMBERED_PANES.any { it.route == route } }?.let(onPaneOpened)
    }

    // Editors are modal: no bottom bar, so leaving means saving or discarding.
    val inEditor = current?.route?.let { route -> route == CLOCK_STYLE || (EDITOR_ROUTES + GUIDE).any { route.startsWith("$it/") } } == true

    val openGuide: (GuideSection?) -> Unit = { navController.navigate("$GUIDE/${it?.name ?: NEW}") }

    CompositionLocalProvider(LocalOpenGuide provides openGuide) {
    Scaffold(
        bottomBar = {
            if (!inEditor) NavigationBar {
                Pane.entries.forEach { pane ->
                    NavigationBarItem(
                        selected = current?.hierarchy?.any { it.route == pane.route } == true,
                        onClick = {
                            navController.navigate(pane.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(pane.icon, contentDescription = null) },
                        label = { Text(stringResource(pane.label)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = start.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Pane.ALARMS.route) {
                AlarmsScreen(onOpen = { id -> navController.navigate("$ALARM_EDIT/${id ?: NEW}") })
            }
            composable("$ALARM_EDIT/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.takeIf { it != NEW }
                AlarmEditScreen(scheduleId = id, onDone = { navController.popBackStack() })
            }
            composable(Pane.TIMERS.route) {
                TimersScreen(
                    onOpenTimer = { id -> navController.navigate("$TIMER_EDIT/${id ?: NEW}") },
                    onOpenRelay = { id -> navController.navigate("$RELAY_EDIT/${id ?: NEW}") },
                )
            }
            composable("$TIMER_EDIT/{id}") { entry ->
                TimerEditScreen(entry.arguments?.getString("id")?.takeIf { it != NEW }, onDone = { navController.popBackStack() })
            }
            composable("$RELAY_EDIT/{id}") { entry ->
                RelayEditScreen(entry.arguments?.getString("id")?.takeIf { it != NEW }, onDone = { navController.popBackStack() })
            }
            composable(Pane.STOPWATCH.route) { StopwatchScreen() }
            composable(Pane.CLOCK.route) { ClockScreen(onEditStyle = { navController.navigate(CLOCK_STYLE) }) }
            composable(CLOCK_STYLE) { ClockStyleScreen(onDone = { navController.popBackStack() }) }
            composable("$GUIDE/{section}") { entry ->
                val section = entry.arguments?.getString("section")?.let { s -> GuideSection.entries.firstOrNull { it.name == s } }
                GuideScreen(section, onBack = { navController.popBackStack() })
            }
            composable(Pane.SETTINGS.route) { SettingsScreen() }
        }
    }
    }
}

private const val ALARM_EDIT = "alarm"
private const val TIMER_EDIT = "timer"
private const val RELAY_EDIT = "relay"
private const val NEW = "new"
private const val CLOCK_STYLE = "clockstyle"
private const val GUIDE = "guide"
private val EDITOR_ROUTES = listOf(ALARM_EDIT, TIMER_EDIT, RELAY_EDIT)
private val REMEMBERED_PANES = listOf(Pane.ALARMS, Pane.TIMERS, Pane.STOPWATCH, Pane.CLOCK)
