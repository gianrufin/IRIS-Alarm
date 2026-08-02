package com.iris.alarm.domain.usecase

import com.iris.alarm.alarm.AlarmScheduler
import com.iris.alarm.domain.model.Alarm
import com.iris.alarm.domain.repository.AlarmRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

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
) {
    suspend operator fun invoke(alarm: Alarm, enabled: Boolean) {
        repository.setEnabled(alarm.id, enabled)
        if (enabled) scheduler.schedule(alarm.copy(enabled = true)) else scheduler.cancel(alarm.id)
    }
}

class DeleteAlarm @Inject constructor(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
) {
    suspend operator fun invoke(alarm: Alarm) {
        scheduler.cancel(alarm.id)
        repository.delete(alarm)
    }
}

class ObserveAlarms @Inject constructor(
    private val repository: AlarmRepository,
) {
    operator fun invoke(): Flow<List<Alarm>> = repository.observeAlarms()
}
