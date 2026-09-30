package com.relay.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.relay.app.ui.panes.PlaceholderPane
import com.relay.app.ui.timers.RelayEditScreen
import com.relay.app.ui.timers.TimerEditScreen
import com.relay.app.ui.timers.TimersScreen
import com.relay.app.ui.settings.SettingsScreen

@Composable
fun RelayApp() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                Pane.entries.forEach { pane ->
                    NavigationBarItem(
                        selected = current?.hierarchy?.any { it.route == pane.route } == true ||
                            (pane == Pane.ALARMS && current?.route?.startsWith("$ALARM_EDIT/") == true) ||
                            (pane == Pane.TIMERS && (current?.route?.startsWith("$TIMER_EDIT/") == true || current?.route?.startsWith("$RELAY_EDIT/") == true)),
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
            startDestination = Pane.ALARMS.route,
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
            composable(Pane.STOPWATCH.route) { PlaceholderPane(Pane.STOPWATCH, R.string.placeholder_stopwatch) }
            composable(Pane.CLOCK.route) { PlaceholderPane(Pane.CLOCK, R.string.placeholder_clock) }
            composable(Pane.SETTINGS.route) { SettingsScreen() }
        }
    }
}

private const val ALARM_EDIT = "alarm"
private const val TIMER_EDIT = "timer"
private const val RELAY_EDIT = "relay"
private const val NEW = "new"
