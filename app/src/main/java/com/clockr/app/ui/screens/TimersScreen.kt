package com.clockr.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.clockr.app.ui.theme.ClockrTheme

/**
 * A block in the Timer/Chain library — a mockup stand-in for RunnableBlock (spec §03/§06).
 * The card is a summary only; content, sound profile, transition mode etc. live one tap away
 * in the config screen (spec §06 — same full-screen pattern for both Timer and Chain).
 */
data class BlockPreview(
    val id: String,
    val label: String,
    val kind: String,
    val total: String,
)

val sampleBlocks = listOf(
    BlockPreview("workday", "Workday", "Chain", "5h 55m"),
    BlockPreview("session", "Session", "Chain", "2h 00m"),
    BlockPreview("work", "Work", "Timer", "25m"),
    BlockPreview("break", "Break", "Timer", "5m"),
)

@Composable
fun TimersScreen(modifier: Modifier = Modifier, onBlockClick: (String) -> Unit = {}) {
    Scaffold(modifier = modifier) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Library", style = MaterialTheme.typography.titleLarge)
            }
            items(sampleBlocks) { block -> BlockCard(block, onClick = { onBlockClick(block.id) }) }
        }
    }
}

@Composable
private fun BlockCard(block: BlockPreview, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(block.label, style = MaterialTheme.typography.titleLarge)
            Text(
                "${block.kind} · ${block.total}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TimersScreenPreview() {
    ClockrTheme { TimersScreen() }
}
