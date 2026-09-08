package com.clockr.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.clockr.app.ui.theme.ClockrTheme

/**
 * Live-seconds clock face (spec §02/§07) — the default tap target for every home-screen widget,
 * and the one surface that isn't throttled to minute granularity.
 */
@Composable
fun ClockScreen(modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier) { padding ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("14:37:08", style = MaterialTheme.typography.displayLarge)
                Text("Mon, Sep 8", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ClockScreenPreview() {
    ClockrTheme { ClockScreen() }
}
