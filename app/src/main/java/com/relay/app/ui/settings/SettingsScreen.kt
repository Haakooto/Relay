package com.relay.app.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.relay.app.R
import com.relay.app.data.settings.ThemeSetting
import com.relay.app.ui.alarms.SNOOZE_PRESETS
import com.relay.app.ui.alarms.SNOOZE_RANGE
import com.relay.app.ui.common.NumberDialog
import com.relay.app.ui.common.NumberOptionChips
import com.relay.app.util.DurationText
import androidx.compose.material3.AssistChip
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.relay.app.ui.common.OptionChips
import com.relay.app.ui.common.SoundPickerRow
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val current = settings ?: return
    val context = LocalContext.current
    // Read on each composition: changing it recreates the activity anyway.
    val language = AppLanguage.current(context)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.pane_settings), style = MaterialTheme.typography.headlineMedium)

        SectionTitle(R.string.settings_display_title)
        Text(stringResource(R.string.settings_theme_label), style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeSetting.entries.forEach { theme ->
                FilterChip(
                    selected = current.theme == theme,
                    onClick = { viewModel.setTheme(theme) },
                    label = { Text(stringResource(theme.label())) },
                )
            }
        }
        SwitchRow(R.string.settings_24h_label, current.clockFormat24h, viewModel::setClockFormat24h)

        Text(stringResource(R.string.settings_first_day_label), style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DayOfWeek.entries.forEach { day ->
                FilterChip(
                    selected = current.firstDayOfWeek == day,
                    onClick = { viewModel.setFirstDayOfWeek(day) },
                    label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                )
            }
        }

        Text(stringResource(R.string.settings_language_label), style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppLanguage.entries.forEach { option ->
                FilterChip(
                    selected = language == option,
                    onClick = { if (option != language) AppLanguage.apply(context, option) },
                    label = { Text(stringResource(option.label())) },
                )
            }
        }

        HorizontalDivider()
        SectionTitle(R.string.settings_alarms_title)
        NumberOptionChips(
            title = stringResource(R.string.settings_default_snooze),
            presets = SNOOZE_PRESETS, value = current.defaultSnoozeMinutes, range = SNOOZE_RANGE,
            label = { stringResource(R.string.minutes_short, it) },
            onChange = viewModel::setDefaultSnoozeMinutes,
        )
        SoundPickerRow(stringResource(R.string.settings_default_alarm_sound), current.defaultAlarmSoundUri, viewModel::setDefaultAlarmSoundUri)
        OptionChips(
            title = stringResource(R.string.settings_default_ramp),
            options = listOf(0, 15, 30, 60), selected = current.defaultRampSeconds,
            label = { if (it == 0) stringResource(R.string.option_off) else stringResource(R.string.seconds_short, it) },
            onSelect = viewModel::setDefaultRampSeconds,
        )
        OptionChips(
            title = stringResource(R.string.settings_ring_timeout),
            options = listOf(5, 10, 15, 20, 30), selected = current.ringTimeoutMinutes,
            label = { stringResource(R.string.minutes_short, it) },
            onSelect = viewModel::setRingTimeoutMinutes,
        )
        OptionChips(
            title = stringResource(R.string.settings_upcoming_lead),
            options = listOf(0, 30, 60, 120, 240), selected = current.upcomingLeadMinutes,
            label = {
                when {
                    it == 0 -> stringResource(R.string.option_off)
                    it < 60 -> stringResource(R.string.minutes_short, it)
                    else -> stringResource(R.string.hours_short, it / 60)
                }
            },
            onSelect = viewModel::setUpcomingLeadMinutes,
        )

        HorizontalDivider()
        SectionTitle(R.string.settings_timers_title)
        Text(stringResource(R.string.settings_quick_timers), style = MaterialTheme.typography.bodyLarge)
        var editingQuick by remember { mutableStateOf<Int?>(null) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            current.quickTimerSeconds.forEachIndexed { i, seconds ->
                AssistChip(onClick = { editingQuick = i }, label = { Text(DurationText.short(seconds)) })
            }
        }
        editingQuick?.let { i ->
            NumberDialog(
                stringResource(R.string.settings_quick_timer_minutes), (current.quickTimerSeconds[i] / 60).toInt().coerceAtLeast(1), 1..999,
                onDismiss = { editingQuick = null },
            ) { viewModel.setQuickTimerMinutes(i, it); editingQuick = null }
        }
        SoundPickerRow(stringResource(R.string.settings_default_timer_sound), current.defaultTimerSoundUri, viewModel::setDefaultTimerSoundUri)
        NumberOptionChips(
            title = stringResource(R.string.settings_max_plan_size),
            presets = listOf(100, 300, 1000), value = current.maxPlanSize, range = 1..1000,
            label = { it.toString() },
            onChange = viewModel::setMaxPlanSize,
        )
        NumberOptionChips(
            title = stringResource(R.string.settings_long_relay),
            presets = listOf(12, 24, 48), value = current.longRelayWarningHours, range = 1..168,
            label = { stringResource(R.string.hours_short, it) },
            onChange = viewModel::setLongRelayWarningHours,
        )

        HorizontalDivider()
        SectionTitle(R.string.settings_general_title)
        SwitchRow(R.string.settings_haptics_label, current.haptics, viewModel::setHaptics)

        HorizontalDivider()
        Text(
            stringResource(R.string.settings_app_version, viewModel.versionName),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun SectionTitle(@StringRes title: Int) {
    Text(stringResource(title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SwitchRow(@StringRes label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@StringRes
private fun ThemeSetting.label(): Int = when (this) {
    ThemeSetting.SYSTEM -> R.string.settings_theme_system
    ThemeSetting.LIGHT -> R.string.settings_theme_light
    ThemeSetting.DARK -> R.string.settings_theme_dark
    ThemeSetting.AMOLED -> R.string.settings_theme_amoled
}

@StringRes
private fun AppLanguage.label(): Int = when (this) {
    AppLanguage.SYSTEM -> R.string.settings_language_system
    AppLanguage.ENGLISH -> R.string.settings_language_english
    AppLanguage.NORWEGIAN -> R.string.settings_language_norwegian
}
