package com.iris.alarm.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * The pending wake check, if any. Kept out of [SettingsRepository] because this
 * is transient state rather than a preference — it is written when an alarm is
 * dismissed and cleared the moment the user confirms they are up.
 */
interface WakeCheckRepository {
    /** Epoch millis the check will fire, or null when nothing is pending. */
    val pendingAt: Flow<Long?>

    /** Alarm the pending check belongs to, so the same challenge is repeated. */
    val pendingAlarmId: Flow<Long?>

    suspend fun set(alarmId: Long, atMillis: Long)

    suspend fun clear()
}
