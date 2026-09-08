package com.clockr.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.clockr.app.ui.screens.AlarmsScreen
import com.clockr.app.ui.screens.BlockConfigScreen
import com.clockr.app.ui.screens.ClockScreen
import com.clockr.app.ui.screens.SettingsScreen
import com.clockr.app.ui.screens.StopwatchScreen
import com.clockr.app.ui.screens.TimersScreen
import com.clockr.app.ui.theme.ClockrTheme

private enum class Pane(val route: String, val label: String) {
    Alarms("alarms", "Alarms"),
    Timers("timers", "Timers"),
    Stopwatch("stopwatch", "Stopwatch"),
    Clock("clock", "Clock"),
    Settings("settings", "Settings"),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClockrTheme {
                ClockrApp()
            }
        }
    }
}

@Composable
private fun ClockrApp() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination

                Pane.entries.forEach { pane ->
                    NavigationBarItem(
                        selected = currentRoute?.hierarchy?.any { it.route == pane.route } == true,
                        onClick = {
                            navController.navigate(pane.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(paneIcon(pane), contentDescription = pane.label) },
                        label = { Text(pane.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Pane.Timers.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Pane.Alarms.route) { AlarmsScreen() }
            composable(Pane.Timers.route) {
                TimersScreen(onBlockClick = { blockId -> navController.navigate("block_config/$blockId") })
            }
            composable(Pane.Stopwatch.route) { StopwatchScreen() }
            composable(Pane.Clock.route) { ClockScreen() }
            composable(Pane.Settings.route) { SettingsScreen() }
            composable(
                "block_config/{blockId}",
                arguments = listOf(navArgument("blockId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val blockId = backStackEntry.arguments?.getString("blockId") ?: return@composable
                BlockConfigScreen(blockId = blockId, onBack = { navController.popBackStack() })
            }
        }
    }
}

private fun paneIcon(pane: Pane) = when (pane) {
    Pane.Alarms -> Icons.Default.Alarm
    Pane.Timers -> Icons.Default.Timer
    Pane.Stopwatch -> Icons.Default.Watch
    Pane.Clock -> Icons.Default.Schedule
    Pane.Settings -> Icons.Default.Settings
}
