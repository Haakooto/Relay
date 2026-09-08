package com.clockr.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
 */
private data class BlockPreview(
    val label: String,
    val kind: String,
    val total: String,
    val detail: String,
)

private val sampleBlocks = listOf(
    BlockPreview("Workday", "Chain", "5h 55m", "Session, 30m, Session, 60m, Work"),
    BlockPreview("Session", "Chain", "2h 00m", "repeat(4, (Work, Break))"),
    BlockPreview("Work", "Timer", "25m", "sadsong"),
    BlockPreview("Break", "Timer", "5m", "happysong"),
)

@Composable
fun TimersScreen(modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Library", style = MaterialTheme.typography.titleLarge)
            }
            items(sampleBlocks) { block -> BlockCard(block) }
        }
    }
}

@Composable
private fun BlockCard(block: BlockPreview) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(block.label, style = MaterialTheme.typography.titleLarge)
                Text(block.total, style = MaterialTheme.typography.labelSmall)
            }
            Text(
                "${block.kind} · ${block.detail}",
                style = MaterialTheme.typography.bodyLarge,
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
