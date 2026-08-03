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

data class Lap(val index: Int, val splitMillis: Long, val totalMillis: Long)

data class StopwatchState(
    val elapsedMillis: Long = 0L,
    val running: Boolean = false,
    val laps: List<Lap> = emptyList(),
) {
    val started: Boolean get() = running || elapsedMillis > 0L
}

/**
 * Elapsed time is derived from the monotonic clock rather than accumulated from
 * a tick, so a delayed or dropped frame cannot make the stopwatch run slow. The
 * tick only decides how often the reading is refreshed.
 */
@HiltViewModel
class StopwatchViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(StopwatchState())
    val state: StateFlow<StopwatchState> = _state.asStateFlow()

    private var tickJob: Job? = null

    /** Monotonic instant the current run began, offset by time already banked. */
    private var runStartedAt = 0L
    private var bankedMillis = 0L

    fun toggle() {
        if (_state.value.running) pause() else start()
    }

    private fun start() {
        runStartedAt = System.nanoTime() / 1_000_000
        _state.value = _state.value.copy(running = true)

        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (true) {
                val now = System.nanoTime() / 1_000_000
                _state.value = _state.value.copy(elapsedMillis = bankedMillis + (now - runStartedAt))
                delay(TICK_MILLIS)
            }
        }
    }

    private fun pause() {
        tickJob?.cancel()
        tickJob = null
        bankedMillis = _state.value.elapsedMillis
        _state.value = _state.value.copy(running = false, elapsedMillis = bankedMillis)
    }

    fun lap() {
        val state = _state.value
        if (!state.running) return

        val previousTotal = state.laps.firstOrNull()?.totalMillis ?: 0L
        val lap = Lap(
            index = state.laps.size + 1,
            splitMillis = state.elapsedMillis - previousTotal,
            totalMillis = state.elapsedMillis,
        )
        // Newest first: the lap you just took is the one you want to read.
        _state.value = state.copy(laps = listOf(lap) + state.laps)
    }

    fun reset() {
        tickJob?.cancel()
        tickJob = null
        bankedMillis = 0L
        _state.value = StopwatchState()
    }

    override fun onCleared() {
        tickJob?.cancel()
        super.onCleared()
    }

    private companion object {
        /** ~60fps; the display shows hundredths, so anything slower stutters. */
        const val TICK_MILLIS = 16L
    }
}

/** `12:34.56` — minutes, seconds, hundredths, with hours only once needed. */
fun formatStopwatch(millis: Long): String {
    val hours = millis / 3_600_000
    val minutes = (millis / 60_000) % 60
    val seconds = (millis / 1000) % 60
    val hundredths = (millis % 1000) / 10
    return if (hours > 0) {
        "%d:%02d:%02d.%02d".format(hours, minutes, seconds, hundredths)
    } else {
        "%02d:%02d.%02d".format(minutes, seconds, hundredths)
    }
}

/** `01:23` or `1:01:23` — whole seconds, for countdowns. */
fun formatCountdown(millis: Long): String {
    val total = (millis.coerceAtLeast(0L) + 999) / 1000
    val hours = total / 3600
    val minutes = (total / 60) % 60
    val seconds = total % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
