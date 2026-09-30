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
import com.relay.app.ui.panes.PlaceholderPane
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
            startDestination = Pane.ALARMS.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Pane.ALARMS.route) { PlaceholderPane(Pane.ALARMS, R.string.placeholder_alarms) }
            composable(Pane.TIMERS.route) { PlaceholderPane(Pane.TIMERS, R.string.placeholder_timers) }
            composable(Pane.STOPWATCH.route) { PlaceholderPane(Pane.STOPWATCH, R.string.placeholder_stopwatch) }
            composable(Pane.CLOCK.route) { PlaceholderPane(Pane.CLOCK, R.string.placeholder_clock) }
            composable(Pane.SETTINGS.route) { SettingsScreen() }
        }
    }
}
