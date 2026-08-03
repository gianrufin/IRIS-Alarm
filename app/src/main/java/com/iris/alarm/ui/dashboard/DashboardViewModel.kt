package com.iris.alarm.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iris.alarm.alarm.AlarmScheduler
import com.iris.alarm.domain.model.Alarm
import com.iris.alarm.domain.usecase.DeleteAlarm
import com.iris.alarm.domain.usecase.ObserveAlarms
import com.iris.alarm.domain.repository.SettingsRepository
import com.iris.alarm.domain.repository.WakeCheckRepository
import com.iris.alarm.domain.usecase.SetAlarmEnabled
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val alarms: List<Alarm> = emptyList(),
    /** "RINGS IN 7H 12M", or null when nothing is armed. */
    val nextAlarmSummary: String? = null,
    /** "WAKE CHECK IN 5 MIN", or null when no check is pending. */
    val wakeCheckSummary: String? = null,
    val use24Hour: Boolean = true,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    observeAlarms: ObserveAlarms,
    private val setAlarmEnabled: SetAlarmEnabled,
    private val deleteAlarm: DeleteAlarm,
    private val wakeCheckRepository: WakeCheckRepository,
    settingsRepository: SettingsRepository,
    private val scheduler: AlarmScheduler,
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        observeAlarms(),
        wakeCheckRepository.pendingAt,
        settingsRepository.settings,
    ) { alarms, wakeCheckAt, settings ->
        DashboardUiState(
            alarms = alarms,
            nextAlarmSummary = summariseNext(alarms),
            wakeCheckSummary = summariseWakeCheck(wakeCheckAt),
            use24Hour = settings.use24Hour,
        )
    }
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

    /** "I'm up" — cancels the pending follow-up check. */
    fun confirmAwake() {
        scheduler.cancelWakeCheck()
        viewModelScope.launch { wakeCheckRepository.clear() }
    }

    private fun summariseWakeCheck(atMillis: Long?): String? {
        if (atMillis == null) return null
        val minutes = ((atMillis - System.currentTimeMillis()) / 60_000L).toInt()
        return when {
            // Already due but not yet fired: the ring is imminent either way.
            minutes <= 0 -> "WAKE CHECK DUE"
            minutes == 1 -> "WAKE CHECK IN 1 MIN"
            else -> "WAKE CHECK IN $minutes MIN"
        }
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
