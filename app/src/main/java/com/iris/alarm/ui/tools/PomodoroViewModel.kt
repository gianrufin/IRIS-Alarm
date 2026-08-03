package com.iris.alarm.ui.tools

import android.content.Context
import androidx.lifecycle.ViewModel
import com.iris.alarm.tools.ToolsEngine
import com.iris.alarm.tools.ToolsService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

enum class PomodoroPhase(val label: String) {
    FOCUS("FOCUS"),
    SHORT_BREAK("BREAK"),
    LONG_BREAK("LONG BREAK"),
}

/** Phase lengths and cadence, all user-set. */
data class PomodoroPlan(
    val focusMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val blocksPerLongBreak: Int = 4,
) {
    fun minutesFor(phase: PomodoroPhase): Int = when (phase) {
        PomodoroPhase.FOCUS -> focusMinutes
        PomodoroPhase.SHORT_BREAK -> shortBreakMinutes
        PomodoroPhase.LONG_BREAK -> longBreakMinutes
    }

    fun millisFor(phase: PomodoroPhase): Long = minutesFor(phase) * 60_000L

    companion object {
        val FOCUS_CHOICES = listOf(15, 20, 25, 30, 45, 50, 60)
        val SHORT_BREAK_CHOICES = listOf(3, 5, 10, 15)
        val LONG_BREAK_CHOICES = listOf(10, 15, 20, 30)
        val BLOCKS_CHOICES = listOf(2, 3, 4, 5, 6)
    }
}

data class PomodoroState(
    val phase: PomodoroPhase = PomodoroPhase.FOCUS,
    val remainingMillis: Long = PomodoroPlan().millisFor(PomodoroPhase.FOCUS),
    val running: Boolean = false,
    /** Completed focus blocks in this run, which is what earns a long break. */
    val completedFocusBlocks: Int = 0,
    val plan: PomodoroPlan = PomodoroPlan(),
) {
    val fraction: Float
        get() {
            val total = plan.millisFor(phase).toFloat()
            if (total <= 0f) return 0f
            return 1f - (remainingMillis / total).coerceIn(0f, 1f)
        }
}

@HiltViewModel
class PomodoroViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: ToolsEngine,
) : ViewModel() {

    val state: StateFlow<PomodoroState> = engine.pomodoro

    fun toggle() {
        engine.togglePomodoro()
        if (engine.pomodoro.value.running) ToolsService.start(context)
    }

    fun advance() = engine.advancePomodoro()

    fun updatePlan(transform: (PomodoroPlan) -> PomodoroPlan) =
        engine.updatePomodoroPlan(transform)

    fun reset() = engine.resetPomodoro()
}
