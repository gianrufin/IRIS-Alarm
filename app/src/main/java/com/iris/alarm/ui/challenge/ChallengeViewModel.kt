package com.iris.alarm.ui.challenge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iris.alarm.vision.ChallengeProgress
import com.iris.alarm.vision.LumenMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Holds the live detector readout for the ringing screen. Camera analysers push
 * into [report] from their executor thread; the lux monitor is collected here
 * because it is a plain sensor stream with no CameraX lifecycle to hang off.
 */
@HiltViewModel
class ChallengeViewModel @Inject constructor(
    private val lumenMonitor: LumenMonitor,
) : ViewModel() {

    private val _progress = MutableStateFlow(ChallengeProgress())
    val progress: StateFlow<ChallengeProgress> = _progress.asStateFlow()

    private var lumenJob: Job? = null

    fun report(progress: ChallengeProgress) {
        // Once solved, ignore trailing frames so the success state cannot flicker
        // back to "no face detected" before the screen finishes closing.
        if (_progress.value.solved) return
        _progress.value = progress
    }

    fun startLumenMonitoring() {
        if (lumenJob?.isActive == true) return
        lumenJob = viewModelScope.launch {
            lumenMonitor.readings().collect { report(it) }
        }
    }

    fun lightSensorAvailable(): Boolean = lumenMonitor.isSupported()

    override fun onCleared() {
        lumenJob?.cancel()
        super.onCleared()
    }
}
