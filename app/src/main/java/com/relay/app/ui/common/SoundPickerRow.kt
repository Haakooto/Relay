package com.relay.app.ui.common

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.relay.app.R

/** The system alarm-sound picker. Result: a sound URI, or null for "the default". */
private class PickAlarmSound : ActivityResultContract<String?, Result<String?>?>() {
    override fun createIntent(context: Context, input: String?) = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
        putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_ALARM_ALERT_URI)
        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, input?.let(Uri::parse) ?: Settings.System.DEFAULT_ALARM_ALERT_URI)
    }

    /** Null = cancelled; Result(null) = the default was picked. */
    override fun parseResult(resultCode: Int, intent: Intent?): Result<String?>? {
        if (resultCode != Activity.RESULT_OK) return null
        @Suppress("DEPRECATION")
        val uri = intent?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        return Result.success(uri?.takeIf { it != Settings.System.DEFAULT_ALARM_ALERT_URI }?.toString())
    }
}

@Composable
fun SoundPickerRow(title: String, soundUri: String?, onPicked: (String?) -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(PickAlarmSound()) { result -> result?.let { onPicked(it.getOrNull()) } }
    val defaultName = stringResource(R.string.sound_default)
    val name = remember(soundUri) {
        soundUri?.let { runCatching { RingtoneManager.getRingtone(context, Uri.parse(it))?.getTitle(context) }.getOrNull() }
            ?: defaultName
    }
    Column(
        modifier = Modifier.fillMaxWidth().clickable { launcher.launch(soundUri) }.padding(vertical = 8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    }
}
