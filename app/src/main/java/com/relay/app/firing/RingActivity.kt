package com.relay.app.firing

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.relay.app.R
import com.relay.app.appContainer
import com.relay.app.data.settings.ThemeSetting
import com.relay.app.ui.theme.RelayTheme
import com.relay.app.util.TimeText
import kotlinx.coroutines.delay

/**
 * The full-screen ringing surface (spec §04/§13): over the lock screen, wakes the screen, shows
 * everything ringing at once as a stack. Purely a view of [RingState]: all actions go to
 * [RingService], and the activity closes itself when nothing is left ringing.
 */
class RingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val settingsFlow = appContainer.settings.settings
        setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = null)
            val items by RingState.items.collectAsStateWithLifecycle()
            // Launched from the placeholder notification, possibly before the first item lands:
            // give it a moment, then close once nothing is (or ever was) ringing.
            var seenItems by remember { mutableStateOf(false) }
            LaunchedEffect(items.isEmpty()) {
                if (items.isNotEmpty()) seenItems = true
                else {
                    if (!seenItems) delay(FIRST_ITEM_WAIT_MILLIS)
                    if (RingState.items.value.isEmpty()) finish()
                }
            }
            // Ringing is always dark: it's usually seen in a dark bedroom.
            RelayTheme(theme = ThemeSetting.DARK) {
                RingScreen(
                    items = items,
                    is24h = settings?.clockFormat24h ?: true,
                    onSnooze = { send(RingService.ACTION_SNOOZE, it) },
                    onDismiss = { send(RingService.ACTION_DISMISS, it) },
                )
            }
        }
    }

    private fun send(action: String, scheduleId: String) {
        startService(RingService.actionIntent(this, action, scheduleId))
    }

    private companion object {
        const val FIRST_ITEM_WAIT_MILLIS = 5_000L
    }
}

@Composable
private fun RingScreen(items: List<RingItem>, is24h: Boolean, onSnooze: (String) -> Unit, onDismiss: (String) -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            Text(TimeText.ofMillis(now, is24h), fontSize = 72.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(32.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                reverseLayout = true, // newest at the bottom, nearest the thumb
            ) {
                items(items.reversed(), key = { it.scheduleId }) { item ->
                    RingCard(item, is24h, onSnooze, onDismiss)
                }
            }
        }
    }
}

@Composable
private fun RingCard(item: RingItem, is24h: Boolean, onSnooze: (String) -> Unit, onDismiss: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            item.label.ifBlank { stringResource(R.string.ring_default_label) },
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(TimeText.ofMillis(item.scheduledAtMillis, is24h), style = MaterialTheme.typography.bodyLarge)
        if (item.isLate) {
            Text(
                stringResource(R.string.ring_delayed_from, TimeText.ofMillis(item.scheduledAtMillis, is24h)),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            if (item.canSnooze) {
                OutlinedButton(onClick = { onSnooze(item.scheduleId) }, modifier = Modifier.weight(1f).height(56.dp)) {
                    Text(stringResource(R.string.ring_snooze_minutes, item.snoozeMinutes))
                }
            }
            Button(
                onClick = { onDismiss(item.scheduleId) },
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(),
            ) {
                Text(stringResource(R.string.action_dismiss))
            }
        }
    }
}
