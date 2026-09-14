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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clockr.app.R
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

@Composable
private fun ThemeSetting.displayName(): String = stringResource(
    when (this) {
        ThemeSetting.SYSTEM -> R.string.theme_system
        ThemeSetting.LIGHT -> R.string.theme_light
        ThemeSetting.DARK -> R.string.theme_dark
        ThemeSetting.AMOLED -> R.string.theme_amoled
    },
)

/**
 * Deliberately a short, fixed list rather than free text: a dropdown sidesteps the bug a
 * free-text field had — applying the locale on every keystroke via LocaleManager triggers an
 * Activity recreation per character, which read as "the screen flickers and the letter never
 * appears." Each language's own name is shown in that language itself (convention), not
 * translated into whichever one happens to be selected right now.
 */
@Composable
private fun supportedLanguages(): List<Pair<String, String>> = listOf(
    "" to stringResource(R.string.language_system_default),
    "en" to stringResource(R.string.language_english),
    "nb" to stringResource(R.string.language_norwegian),
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
            Text(stringResource(R.string.settings_display_title), style = MaterialTheme.typography.titleLarge)

            Text(stringResource(R.string.settings_theme_label), style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeSetting.entries.forEach { option ->
                    FilterChip(
                        selected = settings.theme == option,
                        onClick = { viewModel.setTheme(option) },
                        label = { Text(option.displayName()) },
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.settings_24h_label), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = settings.clockFormat24h, onCheckedChange = viewModel::setClockFormat24h)
            }

            Text(stringResource(R.string.settings_first_day_label), style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DayOfWeek.entries.forEach { day ->
                    FilterChip(
                        selected = settings.firstDayOfWeek == day,
                        onClick = { viewModel.setFirstDayOfWeek(day) },
                        label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                    )
                }
            }

            Text(stringResource(R.string.settings_language_label), style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                supportedLanguages().forEach { (tag, displayName) ->
                    FilterChip(
                        selected = settings.languageTag == tag,
                        onClick = { viewModel.setLanguageTag(tag) },
                        label = { Text(displayName) },
                    )
                }
            }

            HorizontalDivider()

            Text(stringResource(R.string.settings_defaults_title), style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = settings.defaultSnoozeMinutes.toString(),
                onValueChange = { it.toIntOrNull()?.let(viewModel::setDefaultSnoozeMinutes) },
                label = { Text(stringResource(R.string.settings_default_snooze_label)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.settings_haptics_label), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = settings.haptics, onCheckedChange = viewModel::setHaptics)
            }

            OutlinedTextField(
                value = settings.maxExecutionPlanSize.toString(),
                onValueChange = { it.toIntOrNull()?.let(viewModel::setMaxExecutionPlanSize) },
                label = { Text(stringResource(R.string.settings_max_plan_size_label)) },
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()

            Text(stringResource(R.string.settings_danger_zone_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.settings_danger_zone_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { showResetDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.settings_reset_button))
            }

            HorizontalDivider()

            val context = LocalContext.current
            Text(
                stringResource(R.string.settings_app_version, appVersionName(context)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.settings_reset_dialog_title)) },
            text = { Text(stringResource(R.string.settings_reset_dialog_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetAllData()
                    showResetDialog = false
                }) { Text(stringResource(R.string.settings_reset_confirm)) }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    ClockrTheme { SettingsScreen() }
}
