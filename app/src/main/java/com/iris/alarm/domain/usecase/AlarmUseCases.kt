package com.iris.alarm.domain.usecase

import android.content.Context
import com.iris.alarm.alarm.AlarmNotifications
import com.iris.alarm.alarm.AlarmScheduler
import com.iris.alarm.domain.model.Alarm
import com.iris.alarm.domain.repository.AlarmRepository
import com.iris.alarm.domain.repository.SnoozeRepository
import com.iris.alarm.domain.repository.WakeCheckRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Persisting an alarm and arming it are one operation — splitting them is how a
 * saved alarm ends up never ringing, so the UI layer only ever sees these.
 */
class SaveAlarm @Inject constructor(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
) {
    suspend operator fun invoke(alarm: Alarm): Long {
        val id = repository.upsert(alarm)
        val saved = alarm.copy(id = id)
        if (saved.enabled) scheduler.schedule(saved) else scheduler.cancel(id)
        return id
    }
}

class SetAlarmEnabled @Inject constructor(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val snoozes: PendingSnooze,
) {
    suspend operator fun invoke(alarm: Alarm, enabled: Boolean) {
        repository.setEnabled(alarm.id, enabled)
        if (enabled) {
            scheduler.schedule(alarm.copy(enabled = true))
        } else {
            scheduler.cancel(alarm.id)
            // Switching an alarm off has to take its snooze with it, or the
            // thing the user just turned off rings anyway a few minutes later.
            snoozes.cancelFor(alarm.id)
        }
    }
}

/**
 * Cancels a pending snooze, but only the one belonging to a given alarm.
 *
 * There is a single snooze slot, so the alarm it belongs to has to be checked
 * before cancelling — otherwise deleting one alarm would silently drop a snooze
 * the user had asked for on a different one.
 */
class PendingSnooze @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scheduler: AlarmScheduler,
    private val snoozeRepository: SnoozeRepository,
) {
    suspend fun cancelFor(alarmId: Long) {
        if (!snoozeRepository.clearIfFor(alarmId)) return
        scheduler.cancelSnooze()
        AlarmNotifications.clearSnoozed(context)
    }
}

class DeleteAlarm @Inject constructor(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val wakeCheckRepository: WakeCheckRepository,
    private val snoozes: PendingSnooze,
) {
    suspend operator fun invoke(alarm: Alarm) {
        scheduler.cancel(alarm.id)
        // A snooze outliving its alarm would ring with nothing behind it.
        snoozes.cancelFor(alarm.id)
        // A check outliving its alarm would ring with nothing behind it, so the
        // deletion takes the pending check with it.
        if (wakeCheckRepository.pendingAlarmId.first() == alarm.id) {
            scheduler.cancelWakeCheck()
            wakeCheckRepository.clear()
        }
        repository.delete(alarm)
    }
}

class ObserveAlarms @Inject constructor(
    private val repository: AlarmRepository,
) {
    operator fun invoke(): Flow<List<Alarm>> = repository.observeAlarms()
}
