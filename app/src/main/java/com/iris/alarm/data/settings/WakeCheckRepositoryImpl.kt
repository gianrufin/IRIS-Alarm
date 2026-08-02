package com.iris.alarm.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.iris.alarm.domain.repository.WakeCheckRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class WakeCheckRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : WakeCheckRepository {

    override val pendingAt: Flow<Long?> =
        context.dataStore.data.map { it[Keys.PENDING_AT] }

    override val pendingAlarmId: Flow<Long?> =
        context.dataStore.data.map { it[Keys.PENDING_ALARM_ID] }

    override suspend fun set(alarmId: Long, atMillis: Long) {
        context.dataStore.edit {
            it[Keys.PENDING_ALARM_ID] = alarmId
            it[Keys.PENDING_AT] = atMillis
        }
    }

    override suspend fun clear() {
        context.dataStore.edit {
            it.remove(Keys.PENDING_ALARM_ID)
            it.remove(Keys.PENDING_AT)
        }
    }

    private object Keys {
        val PENDING_ALARM_ID = longPreferencesKey("wake_check_alarm_id")
        val PENDING_AT = longPreferencesKey("wake_check_at")
    }
}
