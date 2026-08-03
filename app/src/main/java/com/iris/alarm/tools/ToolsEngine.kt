package com.iris.alarm.tools

import com.iris.alarm.ui.tools.Lap
import com.iris.alarm.ui.tools.PomodoroPhase
import com.iris.alarm.ui.tools.PomodoroPlan
import com.iris.alarm.ui.tools.PomodoroState
import com.iris.alarm.ui.tools.StopwatchState
import com.iris.alarm.ui.tools.TimerState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The timer, stopwatch and focus clock, owned by the application rather than by
 * a screen.
 *
 * They used to live in ViewModels, which meant leaving the app quietly threw
 * away a running stopwatch. Holding them here is what lets the ongoing
 * notification keep counting, and what makes the notification's buttons and the
 * on-screen buttons act on the same object rather than two copies of the truth.
 *
 * Every reading is derived from the monotonic clock, so the tick rate only
 * decides how often the display refreshes, never how fast time passes.
 */
@Singleton
class ToolsEngine @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _timer = MutableStateFlow(TimerState())
    val timer: StateFlow<TimerState> = _timer.asStateFlow()

    private val _stopwatch = MutableStateFlow(StopwatchState())
    val stopwatch: StateFlow<StopwatchState> = _stopwatch.asStateFlow()

    private val _pomodoro = MutableStateFlow(PomodoroState())
    val pomodoro: StateFlow<PomodoroState> = _pomodoro.asStateFlow()

    /** True while anything is counting, which is what the service watches. */
    val anythingRunning: StateFlow<Boolean> = combine(
        _timer,
        _stopwatch,
        _pomodoro,
    ) { timer, stopwatch, pomodoro ->
        timer.running || stopwatch.running || pomodoro.running
    }.stateInEagerly(scope, false)

    private var timerJob: Job? = null
    private var stopwatchJob: Job? = null
    private var pomodoroJob: Job? = null

    private var timerEndsAt = 0L
    private var stopwatchStartedAt = 0L
    private var stopwatchBanked = 0L
    private var pomodoroEndsAt = 0L

    private fun now(): Long = System.nanoTime() / 1_000_000

    // Timer ------------------------------------------------------------------

    fun setTimerDuration(millis: Long) {
        if (_timer.value.running) return
        val clamped = millis.coerceIn(MIN_TIMER, MAX_TIMER)
        _timer.value = TimerState(durationMillis = clamped, remainingMillis = clamped)
    }

    fun toggleTimer() {
        if (_timer.value.running) pauseTimer() else startTimer()
    }

    private fun startTimer() {
        val state = _timer.value
        val remaining = if (state.finished) state.durationMillis else state.remainingMillis
        if (remaining <= 0) return

        timerEndsAt = now() + remaining
        _timer.value = state.copy(running = true, finished = false, remainingMillis = remaining)

        timerJob?.cancel()
        timerJob = scope.launch {
            while (true) {
                val left = (timerEndsAt - now()).coerceAtLeast(0L)
                _timer.value = _timer.value.copy(remainingMillis = left)
                if (left == 0L) {
                    _timer.value = _timer.value.copy(running = false, finished = true)
                    return@launch
                }
                delay(TICK_COARSE)
            }
        }
    }

    private fun pauseTimer() {
        timerJob?.cancel()
        timerJob = null
        _timer.value = _timer.value.copy(running = false)
    }

    fun resetTimer() {
        timerJob?.cancel()
        timerJob = null
        _timer.value = _timer.value.copy(
            remainingMillis = _timer.value.durationMillis,
            running = false,
            finished = false,
        )
    }

    // Stopwatch --------------------------------------------------------------

    fun toggleStopwatch() {
        if (_stopwatch.value.running) pauseStopwatch() else startStopwatch()
    }

    private fun startStopwatch() {
        stopwatchStartedAt = now()
        _stopwatch.value = _stopwatch.value.copy(running = true)

        stopwatchJob?.cancel()
        stopwatchJob = scope.launch {
            while (true) {
                _stopwatch.value = _stopwatch.value.copy(
                    elapsedMillis = stopwatchBanked + (now() - stopwatchStartedAt),
                )
                delay(TICK_FINE)
            }
        }
    }

    private fun pauseStopwatch() {
        stopwatchJob?.cancel()
        stopwatchJob = null
        stopwatchBanked = _stopwatch.value.elapsedMillis
        _stopwatch.value = _stopwatch.value.copy(running = false, elapsedMillis = stopwatchBanked)
    }

    fun lap() {
        val state = _stopwatch.value
        if (!state.running) return

        val previousTotal = state.laps.firstOrNull()?.totalMillis ?: 0L
        val lap = Lap(
            index = state.laps.size + 1,
            splitMillis = state.elapsedMillis - previousTotal,
            totalMillis = state.elapsedMillis,
        )
        // Newest first: the lap just taken is the one worth reading.
        _stopwatch.value = state.copy(laps = listOf(lap) + state.laps)
    }

    fun resetStopwatch() {
        stopwatchJob?.cancel()
        stopwatchJob = null
        stopwatchBanked = 0L
        _stopwatch.value = StopwatchState()
    }

    // Pomodoro ---------------------------------------------------------------

    fun togglePomodoro() {
        if (_pomodoro.value.running) pausePomodoro() else startPomodoro()
    }

    private fun startPomodoro() {
        pomodoroEndsAt = now() + _pomodoro.value.remainingMillis
        _pomodoro.value = _pomodoro.value.copy(running = true)

        pomodoroJob?.cancel()
        pomodoroJob = scope.launch {
            while (true) {
                val left = (pomodoroEndsAt - now()).coerceAtLeast(0L)
                _pomodoro.value = _pomodoro.value.copy(remainingMillis = left)
                if (left == 0L) {
                    advancePomodoro()
                    return@launch
                }
                delay(TICK_COARSE)
            }
        }
    }

    private fun pausePomodoro() {
        pomodoroJob?.cancel()
        pomodoroJob = null
        _pomodoro.value = _pomodoro.value.copy(running = false)
    }

    fun advancePomodoro() {
        pomodoroJob?.cancel()
        pomodoroJob = null

        val current = _pomodoro.value
        val completed = current.completedFocusBlocks +
            if (current.phase == PomodoroPhase.FOCUS) 1 else 0
        val plan = current.plan

        val next = when {
            current.phase != PomodoroPhase.FOCUS -> PomodoroPhase.FOCUS
            plan.blocksPerLongBreak > 0 && completed % plan.blocksPerLongBreak == 0 ->
                PomodoroPhase.LONG_BREAK

            else -> PomodoroPhase.SHORT_BREAK
        }

        _pomodoro.value = PomodoroState(
            phase = next,
            remainingMillis = plan.millisFor(next),
            // A phase that ran out does not roll into the next one on its own:
            // the user decides when the break starts.
            running = false,
            completedFocusBlocks = completed,
            plan = plan,
        )
    }

    fun updatePomodoroPlan(transform: (PomodoroPlan) -> PomodoroPlan) {
        val current = _pomodoro.value
        val plan = transform(current.plan)
        _pomodoro.value = if (current.running) {
            current.copy(plan = plan)
        } else {
            current.copy(plan = plan, remainingMillis = plan.millisFor(current.phase))
        }
    }

    fun resetPomodoro() {
        pomodoroJob?.cancel()
        pomodoroJob = null
        _pomodoro.value = PomodoroState(plan = _pomodoro.value.plan)
    }

    private companion object {
        /** Hundredths are displayed, so the stopwatch needs a frame-rate tick. */
        const val TICK_FINE = 16L
        const val TICK_COARSE = 100L
        const val MIN_TIMER = 1_000L
        const val MAX_TIMER = 5 * 60 * 60_000L
    }
}

/** `stateIn` with an eager start, without dragging in the whole flow import set. */
private fun <T> kotlinx.coroutines.flow.Flow<T>.stateInEagerly(
    scope: CoroutineScope,
    initial: T,
): StateFlow<T> {
    val state = MutableStateFlow(initial)
    scope.launch { collect { state.value = it } }
    return state.asStateFlow()
}
