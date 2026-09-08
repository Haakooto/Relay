package com.clockr.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.clockr.app.ui.theme.ClockrTheme

private enum class BlockKind { TIMER, CHAIN }
private enum class TransitionMode { REQUIRE_INTERACTION, SEAMLESS }
private enum class RowKind { ANON, REF, GROUP }

private data class ContentRow(
    val kind: RowKind,
    val text: String,
    val duration: String,
    val soundOverride: String? = null,
    val children: List<ContentRow> = emptyList(),
    val groupBadge: String? = null,
)

/** Mockup stand-in for RunnableBlock (spec §03) — the one screen shape used for both Timer and Chain. */
private data class BlockConfig(
    val label: String,
    val kind: BlockKind,
    val persistent: Boolean,
    val soundProfile: String,
    val duration: String? = null,
    val transitionMode: TransitionMode? = null,
    val content: List<ContentRow> = emptyList(),
)

private val sampleConfigs = mapOf(
    "workday" to BlockConfig(
        label = "Workday",
        kind = BlockKind.CHAIN,
        persistent = true,
        soundProfile = "joysong",
        transitionMode = TransitionMode.SEAMLESS,
        content = listOf(
            ContentRow(RowKind.REF, "Session", "2h 00m"),
            ContentRow(RowKind.ANON, "gap", "30m"),
            ContentRow(RowKind.REF, "Session", "2h 00m"),
            ContentRow(RowKind.ANON, "gap", "60m"),
            ContentRow(RowKind.REF, "Work", "25m"),
        ),
    ),
    "session" to BlockConfig(
        label = "Session",
        kind = BlockKind.CHAIN,
        persistent = true,
        soundProfile = "(inherited per block)",
        transitionMode = TransitionMode.SEAMLESS,
        content = listOf(
            ContentRow(
                RowKind.GROUP, "repeat", "2h 00m", groupBadge = "×4",
                children = listOf(
                    ContentRow(RowKind.REF, "Work", "25m"),
                    ContentRow(RowKind.REF, "Break", "5m"),
                ),
            ),
        ),
    ),
    "work" to BlockConfig(
        label = "Work", kind = BlockKind.TIMER, persistent = true,
        soundProfile = "sadsong", duration = "25m",
    ),
    "break" to BlockConfig(
        label = "Break", kind = BlockKind.TIMER, persistent = true,
        soundProfile = "happysong", duration = "5m",
    ),
)

@Composable
fun BlockConfigScreen(blockId: String, onBack: () -> Unit = {}) {
    val config = sampleConfigs[blockId] ?: sampleConfigs.getValue("work")
    var label by remember { mutableStateOf(config.label) }
    var persistent by remember { mutableStateOf(config.persistent) }
    var transitionMode by remember { mutableStateOf(config.transitionMode) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(config.label) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = { TextButton(onClick = onBack) { Text("Save") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (config.kind == BlockKind.TIMER) {
                item {
                    OutlinedTextField(
                        value = config.duration ?: "",
                        onValueChange = {},
                        label = { Text("Duration") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Persistent — save to library", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = persistent, onCheckedChange = { persistent = it })
                }
            }

            item {
                SoundProfileRow(
                    label = if (config.kind == BlockKind.CHAIN) "Default sound profile" else "Sound profile",
                    value = config.soundProfile,
                )
            }

            if (config.kind == BlockKind.CHAIN) {
                item {
                    Text("Transition mode", style = MaterialTheme.typography.bodyLarge)
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = transitionMode == TransitionMode.REQUIRE_INTERACTION,
                            onClick = { transitionMode = TransitionMode.REQUIRE_INTERACTION },
                            label = { Text("Require interaction") },
                        )
                        FilterChip(
                            selected = transitionMode == TransitionMode.SEAMLESS,
                            onClick = { transitionMode = TransitionMode.SEAMLESS },
                            label = { Text("Seamless") },
                        )
                    }
                }

                item { HorizontalDivider() }
                item { Text("Content", style = MaterialTheme.typography.titleLarge) }
                items(config.content) { row -> ContentRowView(row) }
            }
        }
    }
}

@Composable
private fun SoundProfileRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row {
            Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ContentRowView(row: ContentRow) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row {
                    if (row.kind == RowKind.REF) {
                        Icon(Icons.Default.Link, contentDescription = "Reference", modifier = Modifier.padding(end = 6.dp))
                    }
                    Text(row.groupBadge?.let { "${row.text}  $it" } ?: row.text, style = MaterialTheme.typography.bodyLarge)
                }
                Text(row.duration, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            row.soundOverride?.let {
                Text("override: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            row.children.forEach { child ->
                Row(modifier = Modifier.padding(start = 20.dp, top = 4.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("· ${child.text}", style = MaterialTheme.typography.bodyLarge)
                    Text(child.duration, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Chain config")
@Composable
private fun BlockConfigScreenChainPreview() {
    ClockrTheme { BlockConfigScreen("workday") }
}

@Preview(showBackground = true, name = "Timer config")
@Composable
private fun BlockConfigScreenTimerPreview() {
    ClockrTheme { BlockConfigScreen("work") }
}
