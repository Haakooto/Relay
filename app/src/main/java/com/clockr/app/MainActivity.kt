package com.clockr.app

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.clockr.app.alarm.NotificationHelper
import com.clockr.app.data.ThemeSetting
import com.clockr.app.ui.screens.AlarmConfigScreen
import com.clockr.app.ui.screens.AlarmsScreen
import com.clockr.app.ui.screens.BlockConfigScreen
import com.clockr.app.ui.screens.ClockScreen
import com.clockr.app.ui.screens.SettingsScreen
import com.clockr.app.ui.screens.StopwatchScreen
import com.clockr.app.ui.screens.TimersScreen
import com.clockr.app.ui.settings.SettingsViewModel
import com.clockr.app.ui.theme.ClockrTheme
import com.clockr.app.widget.EXTRA_OPEN_CLOCK
import androidx.compose.runtime.LaunchedEffect

private enum class Pane(val route: String, val label: String) {
    Alarms("alarms", "Alarms"),
    Timers("timers", "Timers"),
    Stopwatch("stopwatch", "Stopwatch"),
    Clock("clock", "Clock"),
    Settings("settings", "Settings"),
}

class MainActivity : ComponentActivity() {
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way — alarms still schedule, just won't show a notification */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationHelper.ensureChannel(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        maybeRequestExactAlarmPermission()
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val settings by settingsViewModel.settings.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (settings.theme) {
                ThemeSetting.SYSTEM -> systemDark
                ThemeSetting.LIGHT -> false
                ThemeSetting.DARK, ThemeSetting.AMOLED -> true
            }
            val openClock = intent?.getBooleanExtra(EXTRA_OPEN_CLOCK, false) == true
            ClockrTheme(darkTheme = darkTheme, amoled = settings.theme == ThemeSetting.AMOLED) {
                ClockrApp(openClock = openClock)
            }
        }
    }

    /**
     * setAlarmClock() still needs this — auto-granted on API 31-32, but on 33+ (this app's
     * minSdk) it needs an explicit user grant via system settings; there's no runtime dialog
     * for it, just this one settings screen the user flips once.
     */
    private fun maybeRequestExactAlarmPermission() {
        val alarmManager = getSystemService(AlarmManager::class.java)
        if (!alarmManager.canScheduleExactAlarms()) {
            startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")),
            )
        }
    }
}

@Composable
private fun ClockrApp(openClock: Boolean = false) {
    val navController = rememberNavController()

    LaunchedEffect(openClock) {
        if (openClock) navController.navigate(Pane.Clock.route)
    }

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
            composable(Pane.Alarms.route) {
                AlarmsScreen(onAlarmClick = { alarmId -> navController.navigate("alarm_config/$alarmId") })
            }
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
            composable(
                "alarm_config/{alarmId}",
                arguments = listOf(navArgument("alarmId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val alarmId = backStackEntry.arguments?.getString("alarmId") ?: return@composable
                AlarmConfigScreen(alarmId = alarmId, onBack = { navController.popBackStack() })
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
