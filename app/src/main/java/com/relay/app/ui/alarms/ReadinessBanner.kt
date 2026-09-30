package com.relay.app.ui.alarms

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.relay.app.R
import com.relay.app.appContainer

/** One thing that would stop an alarm from ringing properly, with its fix. */
private data class Problem(val message: Int, val action: Int, val fix: () -> Unit)

/**
 * Spec §13 permissions, surfaced where they matter: at the top of the Alarms pane, only while
 * something is actually wrong. Re-checked every time the pane resumes (after a settings trip).
 */
@Composable
fun ReadinessBanner() {
    val context = LocalContext.current
    var recheck by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { recheck++ }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { recheck++ }

    val problems = remember(recheck) { findProblems(context, onRequestNotifications = {
        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }, onChanged = { recheck++ }) }
    if (problems.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        problems.forEach { problem ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(problem.message),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = problem.fix) { Text(stringResource(problem.action)) }
            }
        }
    }
}

private fun findProblems(context: Context, onRequestNotifications: () -> Unit, onChanged: () -> Unit): List<Problem> {
    val problems = mutableListOf<Problem>()
    val pkg = Uri.parse("package:${context.packageName}")
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        problems += Problem(R.string.ready_notifications, R.string.ready_allow, onRequestNotifications)
    }
    if (!context.appContainer.alarmScheduler.canScheduleExact()) {
        problems += Problem(R.string.ready_exact, R.string.ready_fix) {
            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg))
        }
    }
    if (!context.appContainer.firingNotifications.canUseFullScreenIntent() && Build.VERSION.SDK_INT >= 34) {
        problems += Problem(R.string.ready_full_screen, R.string.ready_fix) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg))
        }
    }
    if (!context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)) {
        problems += Problem(R.string.ready_battery, R.string.ready_fix) {
            @Suppress("BatteryLife") // An alarm clock is the textbook case for this exemption.
            context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg))
        }
    }
    val audio = context.getSystemService(AudioManager::class.java)
    if (audio.getStreamVolume(AudioManager.STREAM_ALARM) == 0) {
        problems += Problem(R.string.ready_volume, R.string.ready_turn_up) {
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audio.setStreamVolume(AudioManager.STREAM_ALARM, (max * 0.7f).toInt().coerceAtLeast(1), AudioManager.FLAG_SHOW_UI)
            onChanged()
        }
    }
    return problems
}
