package com.clockr.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clockr.app.data.ThemeSetting
import com.clockr.app.ui.settings.SettingsViewModel
import com.clockr.app.ui.theme.ClockrTheme
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

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
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    ClockrTheme { SettingsScreen() }
}
