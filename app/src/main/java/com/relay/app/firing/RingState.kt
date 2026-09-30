package com.relay.app.firing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * One thing currently ringing. Built for the Ring context (Dismiss / Snooze) in step 2; the Relay
 * contexts of spec §13 (Continue / Stop relay, Stop / Restart / +1 min) extend this in step 3.
 */
data class RingItem(
    val scheduleId: String,
    val label: String,
    /** The occurrence's scheduled time: shown, and compared with now for the late notice. */
    val scheduledAtMillis: Long,
    val soundUri: String?,
    val vibrate: Boolean,
    val rampSeconds: Int,
    val canSnooze: Boolean,
    val snoozeMinutes: Int,
    val startedAtMillis: Long = System.currentTimeMillis(),
) {
    val lateByMillis: Long get() = startedAtMillis - scheduledAtMillis
    val isLate: Boolean get() = lateByMillis > LATE_NOTICE_MILLIS

    companion object {
        const val LATE_NOTICE_MILLIS = 60_000L
    }
}

/** What's ringing right now, newest last. Owned by [RingService], observed by [RingActivity]. */
object RingState {
    private val _items = MutableStateFlow<List<RingItem>>(emptyList())
    val items: StateFlow<List<RingItem>> = _items.asStateFlow()

    internal fun add(item: RingItem) = _items.update { list -> list.filterNot { it.scheduleId == item.scheduleId } + item }
    internal fun remove(scheduleId: String) = _items.update { list -> list.filterNot { it.scheduleId == scheduleId } }
    internal fun clear() { _items.value = emptyList() }
}
