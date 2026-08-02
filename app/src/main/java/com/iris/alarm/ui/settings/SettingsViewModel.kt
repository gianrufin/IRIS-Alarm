package com.iris.alarm.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iris.alarm.domain.model.IrisSettings
import com.iris.alarm.domain.model.VisionChallenge
import com.iris.alarm.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<IrisSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = IrisSettings(),
    )

    fun setDefaultChallenge(challenge: VisionChallenge) {
        viewModelScope.launch { repository.setDefaultChallenge(challenge) }
    }

    fun setAutoSilenceMinutes(minutes: Int) {
        viewModelScope.launch { repository.setAutoSilenceMinutes(minutes) }
    }

    fun setVolumeRampSeconds(seconds: Int) {
        viewModelScope.launch { repository.setVolumeRampSeconds(seconds) }
    }

    fun setMinimumVolumePercent(percent: Int) {
        viewModelScope.launch { repository.setMinimumVolumePercent(percent) }
    }
}
