package com.iris.alarm.ui.tools

import android.content.Context
import androidx.lifecycle.ViewModel
import com.iris.alarm.tools.ToolsEngine
import com.iris.alarm.tools.ToolsService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

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
 * A thin front for [ToolsEngine]. The countdown itself lives in the engine so it
 * survives leaving the screen, and so the notification's buttons and these ones
 * drive the same clock.
 */
@HiltViewModel
class TimerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: ToolsEngine,
) : ViewModel() {

    val state: StateFlow<TimerState> = engine.timer

    fun setDuration(millis: Long) = engine.setTimerDuration(millis)

    /** Sets an exact duration from a custom minutes/seconds entry. */
    fun setDuration(minutes: Int, seconds: Int) =
        engine.setTimerDuration(minutes * 60_000L + seconds * 1000L)

    fun toggle() {
        engine.toggleTimer()
        // Starting anything puts it on the status bar; the service stops itself
        // once nothing is running.
        if (engine.timer.value.running) ToolsService.start(context)
    }

    fun reset() = engine.resetTimer()

    companion object {
        val PRESETS = listOf(60_000L, 3 * 60_000L, 5 * 60_000L, 10 * 60_000L, 20 * 60_000L)
    }
}
