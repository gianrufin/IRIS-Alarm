package com.iris.alarm.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * The pending snooze, if any.
 *
 * Only one snooze exists at a time — snoozing again replaces it rather than
 * stacking — but *which* alarm it belongs to still has to be recorded. Without
 * that, deleting or disabling any alarm could only guess whether to cancel the
 * outstanding snooze, and either guess is wrong somewhere: cancel too eagerly
 * and a snooze the user asked for silently disappears; cancel too rarely and an
 * alarm rings for a row that no longer exists.
 */
interface SnoozeRepository {
    /** Epoch millis the snooze will ring, or null when nothing is pending. */
    val pendingAt: Flow<Long?>

    /** Alarm the pending snooze belongs to. */
    val pendingAlarmId: Flow<Long?>

    suspend fun set(alarmId: Long, atMillis: Long)

    suspend fun clear()

    /** Clears only when the pending snooze belongs to [alarmId]. */
    suspend fun clearIfFor(alarmId: Long): Boolean
}
