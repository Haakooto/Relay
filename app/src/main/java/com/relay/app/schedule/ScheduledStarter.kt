package com.relay.app.schedule

import com.relay.app.data.library.SoundSpec
import com.relay.app.data.schedule.ScheduleEntity
import com.relay.app.data.schedule.TargetKind
import com.relay.app.data.settings.SettingsRepository
import com.relay.app.library.LibraryRepository
import com.relay.app.run.RelaySpec
import com.relay.app.run.TimerSpec
import com.relay.app.stopwatch.StopwatchRepository

/**
 * Starts a schedule's non-ringing target (spec §04): a saved or inline timer or relay, or a
 * stopwatch. A failure (deleted definition, broken script) comes back as a message, so the
 * caller can ring instead: a scheduled start must never fail silently.
 */
class ScheduledStarter(
    private val library: LibraryRepository,
    private val stopwatches: StopwatchRepository,
    private val settings: SettingsRepository,
) {
    suspend fun start(s: ScheduleEntity): Result<Unit> = runCatching {
        val spec = TargetSpec.decode(s.targetSpec)
        val startedBy = s.id
        when (s.target) {
            TargetKind.RING -> error("ring targets don't start anything")
            TargetKind.TIMER -> {
                if (s.targetDefId != null) {
                    library.startTimer(s.targetDefId, startedBy) ?: throw MissingTarget()
                } else {
                    val sound = SoundSpec(settings.current().defaultTimerSoundUri)
                    library.startTimerSpec(TimerSpec(spec.label.ifBlank { s.label }, spec.durationExpr, sound), startedBy = startedBy)
                }
            }
            TargetKind.RELAY -> {
                if (s.targetDefId != null) {
                    library.startRelay(s.targetDefId, startedBy) ?: throw MissingTarget()
                } else {
                    library.startRelaySpec(
                        RelaySpec(spec.label.ifBlank { s.label }, spec.script, SoundSpec(), spec.mode, spec.tone),
                        startedBy = startedBy,
                    )
                }
            }
            TargetKind.STOPWATCH -> stopwatches.create(spec.label.ifBlank { s.label }, spec.t0Millis, spec.tags)
        }
        Unit
    }

    class MissingTarget : Exception("The saved timer or relay was deleted")
}
