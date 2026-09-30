package com.relay.app.firing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What is ringing: an alarm schedule, or a run (timer / relay). */
sealed interface RingSource {
    val key: String

    data class Schedule(val id: String) : RingSource {
        override val key get() = "schedule:$id"
    }

    data class Run(val id: String) : RingSource {
        override val key get() = "run:$id"
    }
}

/** The contexts of spec §13, each with its own set of actions. */
enum class RingContext { ALARM, RELAY_BOUNDARY, RUN_DONE }

enum class RingVerb { SNOOZE, DISMISS, STOP, CONTINUE, RESTART, ADD_MINUTE }

/** One thing currently ringing. */
data class RingItem(
    val source: RingSource,
    val context: RingContext,
    /** Main line; blank = a default for the context. */
    val label: String,
    /** Second line, e.g. "Step 3 of 8 · Work". */
    val detail: String?,
    /** Alarms: the occurrence's scheduled time (shown, and compared for the late notice). */
    val scheduledAtMillis: Long,
    val soundUri: String?,
    val vibrate: Boolean,
    val rampSeconds: Int,
    val canSnooze: Boolean = false,
    val snoozeMinutes: Int = 0,
    val startedAtMillis: Long = System.currentTimeMillis(),
) {
    val key: String get() = source.key
    val lateByMillis: Long get() = startedAtMillis - scheduledAtMillis
    val isLate: Boolean get() = context == RingContext.ALARM && lateByMillis > LATE_NOTICE_MILLIS

    /** In display order; the last one is the primary action. */
    val verbs: List<RingVerb>
        get() = when (context) {
            RingContext.ALARM -> listOfNotNull(RingVerb.SNOOZE.takeIf { canSnooze }, RingVerb.DISMISS)
            RingContext.RELAY_BOUNDARY -> listOf(RingVerb.STOP, RingVerb.CONTINUE)
            RingContext.RUN_DONE -> listOf(RingVerb.ADD_MINUTE, RingVerb.RESTART, RingVerb.STOP)
        }

    companion object {
        const val LATE_NOTICE_MILLIS = 60_000L
    }
}

/** What's ringing right now, newest last. Owned by [RingService], observed by [RingActivity]. */
object RingState {
    private val _items = MutableStateFlow<List<RingItem>>(emptyList())
    val items: StateFlow<List<RingItem>> = _items.asStateFlow()

    internal fun add(item: RingItem) = _items.update { list -> list.filterNot { it.key == item.key } + item }
    internal fun remove(key: String) = _items.update { list -> list.filterNot { it.key == key } }
    internal fun clear() { _items.value = emptyList() }
}
