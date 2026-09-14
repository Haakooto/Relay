package com.clockr.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clockr.app.data.ThemeSetting
import com.clockr.app.ui.settings.SettingsViewModel
import com.clockr.app.ui.theme.ClockrTheme
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Reads the versionName Gradle computed at build time (git tag, or `tag+shortHash` between
 *  tags) — no BuildConfig field needed, this is just the platform's own package metadata. */
private fun appVersionName(context: android.content.Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0)).versionName
}.getOrNull() ?: "unknown"

/**
 * Deliberately a short, fixed list rather than free text: no UI string in this app is actually
 * translated yet (every screen uses hardcoded English literals, not string resources) — picking
 * a language here only changes date/number formatting until real i18n work happens. A dropdown
 * also sidesteps the bug a free-text field had: applying the locale on every keystroke via
 * LocaleManager triggers an Activity recreation per character, which read as "the screen
 * flickers and the letter never appears."
 */
private val SUPPORTED_LANGUAGES = listOf(
    "" to "System default",
    "en" to "English",
    "nb" to "Norsk",
)

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val viewModel: SettingsViewModel = viewModel()
    val settings by viewModel.settings.collectAsState()
    var showResetDialog by remember { mutableStateOf(false) }

    Scaffold(modifier = modifier) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("Display", style = MaterialTheme.typography.titleLarge)

            Text("Theme", style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeSetting.entries.forEach { option ->
                    FilterChip(
                        selected = settings.theme == option,
                        onClick = { viewModel.setTheme(option) },
                        label = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("24-hour clock", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = settings.clockFormat24h, onCheckedChange = viewModel::setClockFormat24h)
            }

            Text("First day of week", style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DayOfWeek.entries.forEach { day ->
                    FilterChip(
                        selected = settings.firstDayOfWeek == day,
                        onClick = { viewModel.setFirstDayOfWeek(day) },
                        label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                    )
                }
            }

            Text("Language", style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SUPPORTED_LANGUAGES.forEach { (tag, displayName) ->
                    FilterChip(
                        selected = settings.languageTag == tag,
                        onClick = { viewModel.setLanguageTag(tag) },
                        label = { Text(displayName) },
                    )
                }
            }

            HorizontalDivider()

            Text("Defaults", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = settings.defaultSnoozeMinutes.toString(),
                onValueChange = { it.toIntOrNull()?.let(viewModel::setDefaultSnoozeMinutes) },
                label = { Text("Default snooze length (minutes)") },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Haptics", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = settings.haptics, onCheckedChange = viewModel::setHaptics)
            }

            OutlinedTextField(
                value = settings.maxExecutionPlanSize.toString(),
                onValueChange = { it.toIntOrNull()?.let(viewModel::setMaxExecutionPlanSize) },
                label = { Text("Max execution-plan size (≤1000)") },
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()

            Text("Danger zone", style = MaterialTheme.typography.titleLarge)
            Text(
                "Deletes every alarm, timer, and chain. Can't be undone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { showResetDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Reset all data")
            }

            HorizontalDivider()

            val context = LocalContext.current
            Text(
                "Clockr ${appVersionName(context)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset all data?") },
            text = { Text("Every alarm, timer, and chain will be deleted. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetAllData()
                    showResetDialog = false
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Cancel") } },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    ClockrTheme { SettingsScreen() }
}
