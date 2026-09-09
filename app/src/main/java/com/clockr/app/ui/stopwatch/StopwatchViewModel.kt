package com.clockr.app.ui.stopwatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Spec §05 — deliberately in-memory only, kept alive for as long as the app process is, not
 * backed by Room. A stopwatch exists whether running or paused; only deleting removes it.
 */
data class StopwatchUi(
    val id: String,
    val name: String?,
    val t0Seconds: Long,
    val accumulatedSeconds: Long,
    val startedAtMillis: Long?,
    val laps: List<Long> = emptyList(),
) {
    val isRunning: Boolean get() = startedAtMillis != null

    fun elapsedSeconds(nowMillis: Long): Long =
        t0Seconds + accumulatedSeconds + (startedAtMillis?.let { (nowMillis - it) / 1000 } ?: 0)
}

class StopwatchViewModel : ViewModel() {
    private val _stopwatches = MutableStateFlow<List<StopwatchUi>>(emptyList())
    val stopwatches: StateFlow<List<StopwatchUi>> = _stopwatches

    private val _nowMillis = MutableStateFlow(System.currentTimeMillis())
    val nowMillis: StateFlow<Long> = _nowMillis

    init {
        // Only ticks the clock while something is actually running — no point burning a coroutine
        // loop for a screen full of paused stopwatches.
        viewModelScope.launch {
            while (true) {
                delay(500)
                if (_stopwatches.value.any { it.isRunning }) {
                    _nowMillis.value = System.currentTimeMillis()
                }
            }
        }
    }

    fun add(name: String?, t0Seconds: Long) {
        _stopwatches.value = _stopwatches.value + StopwatchUi(
            id = UUID.randomUUID().toString(),
            name = name?.takeIf { it.isNotBlank() },
            t0Seconds = t0Seconds,
            accumulatedSeconds = 0,
            startedAtMillis = System.currentTimeMillis(),
        )
    }

    fun togglePause(id: String) = update(id) { sw ->
        val now = System.currentTimeMillis()
        if (sw.isRunning) {
            sw.copy(accumulatedSeconds = sw.accumulatedSeconds + (now - sw.startedAtMillis!!) / 1000, startedAtMillis = null)
        } else {
            sw.copy(startedAtMillis = now)
        }
    }

    fun lap(id: String) = update(id) { sw ->
        if (!sw.isRunning) sw else sw.copy(laps = sw.laps + sw.elapsedSeconds(System.currentTimeMillis()))
    }

    fun rename(id: String, newName: String) = update(id) { sw -> sw.copy(name = newName.takeIf { it.isNotBlank() }) }

    /** Resets to t0, not to zero (spec §05). */
    fun restart(id: String) = update(id) { sw ->
        sw.copy(accumulatedSeconds = 0, startedAtMillis = System.currentTimeMillis(), laps = emptyList())
    }

    fun delete(id: String) {
        _stopwatches.value = _stopwatches.value.filterNot { it.id == id }
    }

    private fun update(id: String, transform: (StopwatchUi) -> StopwatchUi) {
        _stopwatches.value = _stopwatches.value.map { if (it.id == id) transform(it) else it }
    }
}
