package com.iris.alarm.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iris.alarm.domain.model.Alarm
import com.iris.alarm.domain.model.HuntTarget
import com.iris.alarm.domain.model.VisionChallenge
import com.iris.alarm.domain.repository.AlarmRepository
import com.iris.alarm.domain.repository.SettingsRepository
import com.iris.alarm.domain.usecase.DeleteAlarm
import com.iris.alarm.domain.usecase.SaveAlarm
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AlarmEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: AlarmRepository,
    private val settingsRepository: SettingsRepository,
    private val saveAlarm: SaveAlarm,
    private val deleteAlarm: DeleteAlarm,
) : ViewModel() {

    private val alarmId: Long = savedStateHandle.get<Long>(ARG_ALARM_ID) ?: NEW_ALARM_ID

    private val _draft = MutableStateFlow(Alarm.default())
    val draft: StateFlow<Alarm> = _draft.asStateFlow()

    val isExisting: Boolean get() = alarmId != NEW_ALARM_ID

    init {
        viewModelScope.launch {
            if (isExisting) {
                repository.getAlarm(alarmId)?.let { _draft.value = it }
            } else {
                // A new alarm starts on the user's preferred challenge; an edit
                // must never have its saved challenge overwritten by the default.
                val default = settingsRepository.current().defaultChallenge
                _draft.value = _draft.value.copy(challenge = default)
            }
        }
    }

    fun setTime(hour: Int, minute: Int) = update { it.copy(hour = hour, minute = minute) }

    fun setLabel(label: String) = update { it.copy(label = label) }

    fun setChallenge(challenge: VisionChallenge) = update { it.copy(challenge = challenge) }

    fun setHuntTarget(target: HuntTarget) = update { it.copy(huntTarget = target) }

    fun setVibrate(vibrate: Boolean) = update { it.copy(vibrate = vibrate) }

    fun setSound(uri: String?) = update { it.copy(soundUri = uri) }

    fun toggleDay(day: DayOfWeek) = update { alarm ->
        val days = alarm.repeatDays.toMutableSet()
        if (!days.add(day)) days.remove(day)
        alarm.copy(repeatDays = days)
    }

    /** Saving always (re)enables the alarm — editing one is intent to use it. */
    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            saveAlarm(_draft.value.copy(enabled = true))
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            if (isExisting) deleteAlarm(_draft.value)
            onDeleted()
        }
    }

    private inline fun update(transform: (Alarm) -> Alarm) {
        _draft.value = transform(_draft.value)
    }

    companion object {
        const val ARG_ALARM_ID = "alarmId"
        const val NEW_ALARM_ID = -1L
    }
}
