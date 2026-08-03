package com.iris.alarm.ui.tools

import android.content.Context
import androidx.lifecycle.ViewModel
import com.iris.alarm.tools.ToolsEngine
import com.iris.alarm.tools.ToolsService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

data class Lap(val index: Int, val splitMillis: Long, val totalMillis: Long)

data class StopwatchState(
    val elapsedMillis: Long = 0L,
    val running: Boolean = false,
    val laps: List<Lap> = emptyList(),
) {
    val started: Boolean get() = running || elapsedMillis > 0L
}

@HiltViewModel
class StopwatchViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: ToolsEngine,
) : ViewModel() {

    val state: StateFlow<StopwatchState> = engine.stopwatch

    fun toggle() {
        engine.toggleStopwatch()
        if (engine.stopwatch.value.running) ToolsService.start(context)
    }

    fun lap() = engine.lap()

    fun reset() = engine.resetStopwatch()
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
