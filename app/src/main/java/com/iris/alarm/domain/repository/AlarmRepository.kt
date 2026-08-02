package com.iris.alarm.domain.repository

import com.iris.alarm.domain.model.Alarm
import kotlinx.coroutines.flow.Flow

interface AlarmRepository {
    fun observeAlarms(): Flow<List<Alarm>>

    fun observeAlarm(id: Long): Flow<Alarm?>

    suspend fun getAlarm(id: Long): Alarm?

    suspend fun getEnabledAlarms(): List<Alarm>

    /** Inserts or updates and returns the persisted id. */
    suspend fun upsert(alarm: Alarm): Long

    suspend fun delete(alarm: Alarm)

    suspend fun setEnabled(id: Long, enabled: Boolean)
}
