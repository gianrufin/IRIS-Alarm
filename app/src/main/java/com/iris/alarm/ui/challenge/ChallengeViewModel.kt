package com.iris.alarm.ui.challenge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iris.alarm.domain.model.Alarm
import com.iris.alarm.domain.model.DeviceCapabilities
import com.iris.alarm.domain.model.VisionChallenge
import com.iris.alarm.domain.model.resolveChallenge
import com.iris.alarm.domain.model.resolveWithoutCamera
import com.iris.alarm.vision.ChallengeProgress
import com.iris.alarm.vision.LumenMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChallengeUiState(
    val challenge: VisionChallenge = VisionChallenge.SMILE,
    val progress: ChallengeProgress = ChallengeProgress(),
    /** Set when the running challenge is not the one the alarm asked for. */
    val notice: String? = null,
    /**
     * True when no detector can run on this device. The UI must then offer a
     * plain dismiss — an alarm nobody can stop is worse than a skipped challenge.
     */
    val escapeAllowed: Boolean = false,

    /** An anchor alarm whose captured spot is missing, so it cannot be matched. */
    val anchorMissing: Boolean = false,
)

/**
 * Holds the live detector readout for the ringing screen, and decides which
 * challenge can actually run here. Camera analysers push into [report] from
 * their executor thread; the lux monitor is collected here because it is a plain
 * sensor stream with no CameraX lifecycle to hang off.
 */
@HiltViewModel
class ChallengeViewModel @Inject constructor(
    private val lumenMonitor: LumenMonitor,
    private val capabilities: DeviceCapabilities,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChallengeUiState())
    val uiState: StateFlow<ChallengeUiState> = _uiState.asStateFlow()

    private var lumenJob: Job? = null

    /** Guards against a recomposition restarting an already-resolved challenge. */
    private var started = false

    /** Call once with the ringing alarm; resolves a runnable challenge and starts it. */
    fun start(alarm: Alarm?) {
        if (started) return
        started = true

        val requested = alarm?.challenge ?: VisionChallenge.SMILE

        // An anchor with no captured spot can never be matched — it would ring
        // until the auto-silence timeout. Fall back to something runnable and say
        // so, rather than presenting an impossible challenge.
        if (requested == VisionChallenge.ANCHOR && alarm?.anchorSignature == null) {
            val fallback = resolveChallenge(VisionChallenge.SMILE, capabilities)
            _uiState.value = ChallengeUiState(
                challenge = fallback ?: VisionChallenge.SMILE,
                notice = "NO TARGET SAVED · USING ${fallback?.displayName?.uppercase() ?: "NONE"}",
                escapeAllowed = fallback == null,
                anchorMissing = true,
            )
            if (fallback == VisionChallenge.LUMEN) startLumenMonitoring()
            return
        }

        val resolved = resolveChallenge(requested, capabilities)
        if (resolved == null) {
            _uiState.value = ChallengeUiState(
                challenge = requested,
                notice = "NO CAMERA OR LIGHT SENSOR ON THIS DEVICE",
                escapeAllowed = true,
            )
            return
        }

        _uiState.value = ChallengeUiState(
            challenge = resolved,
            notice = noticeFor(requested, resolved),
        )
        if (resolved == VisionChallenge.LUMEN) startLumenMonitoring()
    }

    /**
     * The user cannot or will not grant camera access. Switch to a challenge that
     * needs no camera, or unlock the manual escape if there is none.
     */
    fun onCameraUnavailable() {
        val fallback = resolveWithoutCamera(capabilities)
        if (fallback == null) {
            _uiState.value = _uiState.value.copy(
                notice = "CAMERA UNAVAILABLE AND NO LIGHT SENSOR",
                escapeAllowed = true,
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            challenge = fallback,
            progress = ChallengeProgress(),
            notice = "CAMERA UNAVAILABLE · USING ${fallback.displayName.uppercase()}",
        )
        startLumenMonitoring()
    }

    fun report(progress: ChallengeProgress) {
        // Once solved, ignore trailing frames so the success state cannot flicker
        // back to "no face detected" before the screen finishes closing.
        if (_uiState.value.progress.solved) return
        _uiState.value = _uiState.value.copy(progress = progress)
    }

    private fun startLumenMonitoring() {
        if (lumenJob?.isActive == true) return
        lumenJob = viewModelScope.launch {
            lumenMonitor.readings().collect { report(it) }
        }
    }

    private fun noticeFor(requested: VisionChallenge, resolved: VisionChallenge): String? =
        if (requested == resolved) {
            null
        } else {
            "${requested.displayName.uppercase()} UNAVAILABLE · USING ${resolved.displayName.uppercase()}"
        }

    override fun onCleared() {
        lumenJob?.cancel()
        super.onCleared()
    }
}
