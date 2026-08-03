package com.iris.alarm.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iris.alarm.domain.model.IrisSettings
import com.iris.alarm.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * App-level settings the whole UI tree needs before it can draw: the theme and
 * whether first-run setup is still pending.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {

    fun completeOnboarding() {
        viewModelScope.launch { repository.setOnboardingComplete(true) }
    }

    val settings: StateFlow<IrisSettings?> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        // Null until the first read lands, so the app does not paint a light
        // theme for one frame before finding out the user chose dark.
        initialValue = null,
    )
}
