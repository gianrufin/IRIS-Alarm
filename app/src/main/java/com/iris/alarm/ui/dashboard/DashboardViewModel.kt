package com.iris.alarm.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iris.alarm.alarm.AlarmScheduler
import com.iris.alarm.domain.model.Alarm
import com.iris.alarm.domain.usecase.DeleteAlarm
import com.iris.alarm.domain.usecase.ObserveAlarms
import com.iris.alarm.domain.usecase.SetAlarmEnabled
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val alarms: List<Alarm> = emptyList(),
    /** "RINGS IN 7H 12M", or null when nothing is armed. */
    val nextAlarmSummary: String? = null,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    observeAlarms: ObserveAlarms,
    private val setAlarmEnabled: SetAlarmEnabled,
    private val deleteAlarm: DeleteAlarm,
    private val scheduler: AlarmScheduler,
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = observeAlarms()
        .map { alarms -> DashboardUiState(alarms, summariseNext(alarms)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DashboardUiState(),
        )

    /** False when the OS has revoked exact alarms and the UI should say so. */
    fun canScheduleExact(): Boolean = scheduler.canScheduleExact

    fun openExactAlarmSettings() = scheduler.openExactAlarmSettings()

    fun toggle(alarm: Alarm, enabled: Boolean) {
        viewModelScope.launch { setAlarmEnabled(alarm, enabled) }
    }

    fun delete(alarm: Alarm) {
        viewModelScope.launch { deleteAlarm(alarm) }
    }

    private fun summariseNext(alarms: List<Alarm>): String? {
        val now = LocalDateTime.now()
        val soonest = alarms
            .mapNotNull { it.nextTriggerAtMillis(now) }
            .minOrNull()
            ?: return null

        val until = Duration.between(
            now,
            LocalDateTime.ofInstant(Instant.ofEpochMilli(soonest), ZoneId.systemDefault()),
        )
        val hours = until.toHours()
        val minutes = until.toMinutes() % 60
        return when {
            hours > 0 -> "RINGS IN ${hours}H ${minutes}M"
            minutes > 0 -> "RINGS IN ${minutes}M"
            else -> "RINGS IN LESS THAN A MINUTE"
        }
    }
}
