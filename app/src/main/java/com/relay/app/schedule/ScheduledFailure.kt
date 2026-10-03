package com.relay.app.schedule

import android.content.Context
import com.relay.app.R
import com.relay.app.data.schedule.ScheduleEntity
import com.relay.app.firing.RingContext
import com.relay.app.firing.RingItem
import com.relay.app.firing.RingSource

/** When a scheduled timer, relay or stopwatch can't start, ring like an alarm and say why. */
object ScheduledFailure {
    fun item(context: Context, s: ScheduleEntity, triggerAt: Long, error: Throwable): RingItem = RingItem(
        source = RingSource.Schedule(s.id),
        context = RingContext.ALARM,
        label = s.label,
        detail = context.getString(R.string.schedule_start_failed, error.message ?: error.javaClass.simpleName),
        scheduledAtMillis = triggerAt,
        soundUri = null,
        vibrate = true,
        rampSeconds = 0,
        canSnooze = false,
    )
}
