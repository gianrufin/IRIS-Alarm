package com.iris.alarm.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PomodoroPhase(val label: String, val minutes: Int) {
    FOCUS("FOCUS", 25),
    SHORT_BREAK("BREAK", 5),
    LONG_BREAK("LONG BREAK", 15),
}

data class PomodoroState(
    val phase: PomodoroPhase = PomodoroPhase.FOCUS,
    val remainingMillis: Long = PomodoroPhase.FOCUS.minutes * 60_000L,
    val running: Boolean = false,
    /** Completed focus blocks in this run, which is what earns a long break. */
    val completedFocusBlocks: Int = 0,
) {
    val fraction: Float
        get() {
            val total = phase.minutes * 60_000f
            return 1f - (remainingMillis / total).coerceIn(0f, 1f)
        }
}

@HiltViewModel
class PomodoroViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(PomodoroState())
    val state: StateFlow<PomodoroState> = _state.asStateFlow()

    private var tickJob: Job? = null
    private var endsAt = 0L

    fun toggle() {
        if (_state.value.running) pause() else start()
    }

    private fun start() {
        endsAt = System.nanoTime() / 1_000_000 + _state.value.remainingMillis
        _state.value = _state.value.copy(running = true)

        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (true) {
                val now = System.nanoTime() / 1_000_000
                val left = (endsAt - now).coerceAtLeast(0L)
                _state.value = _state.value.copy(remainingMillis = left)
                if (left == 0L) {
                    advance(automatic = true)
                    return@launch
                }
                delay(TICK_MILLIS)
            }
        }
    }

    private fun pause() {
        tickJob?.cancel()
        tickJob = null
        _state.value = _state.value.copy(running = false)
    }

    /**
     * Moves to the next phase. Four focus blocks earn the long break, which is
     * the only part of the technique that actually needs counting.
     */
    fun advance(automatic: Boolean = false) {
        tickJob?.cancel()
        tickJob = null

        val current = _state.value
        val completed = current.completedFocusBlocks +
            if (current.phase == PomodoroPhase.FOCUS) 1 else 0

        val next = when {
            current.phase != PomodoroPhase.FOCUS -> PomodoroPhase.FOCUS
            completed % BLOCKS_PER_LONG_BREAK == 0 -> PomodoroPhase.LONG_BREAK
            else -> PomodoroPhase.SHORT_BREAK
        }

        _state.value = PomodoroState(
            phase = next,
            remainingMillis = next.minutes * 60_000L,
            // A phase that ended on its own does not silently roll into the next
            // one: the user decides when the break starts.
            running = false,
            completedFocusBlocks = completed,
        )
        if (!automatic) return
    }

    fun reset() {
        tickJob?.cancel()
        tickJob = null
        _state.value = PomodoroState()
    }

    override fun onCleared() {
        tickJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val TICK_MILLIS = 200L
        const val BLOCKS_PER_LONG_BREAK = 4
    }
}
