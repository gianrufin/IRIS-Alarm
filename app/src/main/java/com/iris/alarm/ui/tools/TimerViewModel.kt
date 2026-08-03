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

data class TimerState(
    val durationMillis: Long = 5 * 60_000L,
    val remainingMillis: Long = 5 * 60_000L,
    val running: Boolean = false,
    val finished: Boolean = false,
) {
    val fraction: Float
        get() = if (durationMillis <= 0) 0f else {
            1f - (remainingMillis.toFloat() / durationMillis).coerceIn(0f, 1f)
        }
}

/**
 * A plain countdown. Like the stopwatch it counts against the monotonic clock,
 * so it stays accurate whatever the tick does.
 *
 * It is deliberately *not* an alarm: it only rings while the app is in front of
 * you. Anything that has to survive a locked, dozing phone goes through
 * AlarmManager, and mixing the two would quietly make people trust this to wake
 * them up.
 */
@HiltViewModel
class TimerViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    private var tickJob: Job? = null
    private var endsAt = 0L

    fun setDuration(millis: Long) {
        if (_state.value.running) return
        val clamped = millis.coerceIn(MIN_DURATION, MAX_DURATION)
        _state.value = TimerState(durationMillis = clamped, remainingMillis = clamped)
    }

    fun adjust(deltaMillis: Long) = setDuration(_state.value.durationMillis + deltaMillis)

    fun toggle() {
        if (_state.value.running) pause() else start()
    }

    private fun start() {
        val state = _state.value
        val remaining = if (state.finished) state.durationMillis else state.remainingMillis
        if (remaining <= 0) return

        endsAt = System.nanoTime() / 1_000_000 + remaining
        _state.value = state.copy(
            running = true,
            finished = false,
            remainingMillis = remaining,
        )

        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (true) {
                val now = System.nanoTime() / 1_000_000
                val left = (endsAt - now).coerceAtLeast(0L)
                _state.value = _state.value.copy(remainingMillis = left)
                if (left == 0L) {
                    _state.value = _state.value.copy(running = false, finished = true)
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

    fun reset() {
        tickJob?.cancel()
        tickJob = null
        _state.value = _state.value.copy(
            remainingMillis = _state.value.durationMillis,
            running = false,
            finished = false,
        )
    }

    override fun onCleared() {
        tickJob?.cancel()
        super.onCleared()
    }

    companion object {
        val PRESETS = listOf(60_000L, 3 * 60_000L, 5 * 60_000L, 10 * 60_000L, 20 * 60_000L)
        const val MIN_DURATION = 10_000L
        const val MAX_DURATION = 5 * 60 * 60_000L
        private const val TICK_MILLIS = 100L
    }
}
