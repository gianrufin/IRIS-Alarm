package com.iris.alarm.ui.tools

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iris.alarm.ui.EntryPoint
import com.iris.alarm.ui.theme.IrisType

/** Countdown timer. */
@Composable
fun TimerScreen(
    modifier: Modifier = Modifier,
    requested: EntryPoint.Timer? = null,
    viewModel: TimerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Applied once, so returning to the tab does not restart the timer the
    // assistant asked for half an hour ago.
    var appliedRequest by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(requested) {
        if (appliedRequest || requested == null || requested.seconds <= 0) return@LaunchedEffect
        appliedRequest = true
        viewModel.setDuration(requested.seconds * 1000L)
        if (requested.startImmediately) viewModel.toggle()
    }

    ToolScaffold(
        title = "TIMER",
        modifier = modifier,
        readout = {
            Readout(
                text = formatCountdown(state.remainingMillis),
                fraction = state.fraction,
                highlight = state.finished,
                caption = when {
                    state.finished -> "TIME"
                    state.running -> "COUNTING DOWN"
                    else -> "READY"
                },
            )
        },
    ) {
        PresetRow(
            enabled = !state.running,
            selected = state.durationMillis,
            onSelect = viewModel::setDuration,
        )

        DurationInput(
            minutes = (state.durationMillis / 60_000L).toInt(),
            seconds = ((state.durationMillis / 1000L) % 60).toInt(),
            enabled = !state.running,
            onChange = viewModel::setDuration,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToolButton(
                text = if (state.running) "PAUSE" else "START",
                primary = true,
                enabled = state.remainingMillis > 0 || state.finished,
                onClick = viewModel::toggle,
                modifier = Modifier.weight(1f),
            )
            ToolButton(
                text = "RESET",
                primary = false,
                onClick = viewModel::reset,
                modifier = Modifier.weight(1f),
            )
        }

        Text(
            text = "Timers only run while IRIS is open. Anything that has to wake " +
                "you needs an alarm.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Stopwatch with laps. */
@Composable
fun StopwatchScreen(
    modifier: Modifier = Modifier,
    viewModel: StopwatchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ToolScaffold(
        title = "STOPWATCH",
        modifier = modifier,
        readout = {
            Readout(
                text = formatStopwatch(state.elapsedMillis),
                fraction = 0f,
                highlight = false,
                caption = if (state.running) "RUNNING" else "STOPPED",
            )
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToolButton(
                text = if (state.running) "STOP" else if (state.started) "RESUME" else "START",
                primary = true,
                onClick = viewModel::toggle,
                modifier = Modifier.weight(1f),
            )
            ToolButton(
                text = if (state.running) "LAP" else "RESET",
                primary = false,
                enabled = state.started,
                onClick = { if (state.running) viewModel.lap() else viewModel.reset() },
                modifier = Modifier.weight(1f),
            )
        }

        AnimatedVisibility(visible = state.laps.isNotEmpty()) {
            LazyColumn(modifier = Modifier.height(220.dp)) {
                items(state.laps, key = { it.index }) { lap ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                    ) {
                        Text(
                            text = "LAP ${lap.index}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = formatStopwatch(lap.splitMillis),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = formatStopwatch(lap.totalMillis),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

/** Pomodoro: focus blocks with breaks, a long one every fourth. */
@Composable
fun PomodoroScreen(
    modifier: Modifier = Modifier,
    viewModel: PomodoroViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ToolScaffold(
        title = "POMODORO",
        modifier = modifier,
        readout = {
            Readout(
                text = formatCountdown(state.remainingMillis),
                fraction = state.fraction,
                highlight = state.phase != PomodoroPhase.FOCUS,
                caption = state.phase.label,
            )
        },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val perLongBreak = state.plan.blocksPerLongBreak.coerceAtLeast(1)
            repeat(perLongBreak) { index ->
                val inCycle = state.completedFocusBlocks % perLongBreak
                val done = inCycle > index ||
                    (state.completedFocusBlocks > 0 && inCycle == 0)
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            if (done) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                        ),
                )
            }
            Text(
                text = "${state.completedFocusBlocks} BLOCKS DONE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToolButton(
                text = if (state.running) "PAUSE" else "START",
                primary = true,
                onClick = viewModel::toggle,
                modifier = Modifier.weight(1f),
            )
            ToolButton(
                text = "SKIP",
                primary = false,
                onClick = { viewModel.advance() },
                modifier = Modifier.weight(1f),
            )
        }

        ChoiceStrip(
            title = "FOCUS",
            options = PomodoroPlan.FOCUS_CHOICES,
            selected = state.plan.focusMinutes,
            label = { "$it MIN" },
            onSelect = { minutes ->
                viewModel.updatePlan { it.copy(focusMinutes = minutes) }
            },
        )
        ChoiceStrip(
            title = "BREAK",
            options = PomodoroPlan.SHORT_BREAK_CHOICES,
            selected = state.plan.shortBreakMinutes,
            label = { "$it MIN" },
            onSelect = { minutes ->
                viewModel.updatePlan { it.copy(shortBreakMinutes = minutes) }
            },
        )
        ChoiceStrip(
            title = "LONG BREAK",
            options = PomodoroPlan.LONG_BREAK_CHOICES,
            selected = state.plan.longBreakMinutes,
            label = { "$it MIN" },
            onSelect = { minutes ->
                viewModel.updatePlan { it.copy(longBreakMinutes = minutes) }
            },
        )
        ChoiceStrip(
            title = "LONG BREAK EVERY",
            options = PomodoroPlan.BLOCKS_CHOICES,
            selected = state.plan.blocksPerLongBreak,
            label = { "$it BLOCKS" },
            onSelect = { blocks ->
                viewModel.updatePlan { it.copy(blocksPerLongBreak = blocks) }
            },
        )

        Text(
            text = "RESET",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clickable(onClick = viewModel::reset)
                .padding(8.dp),
        )
    }
}

@Composable
private fun ToolScaffold(
    title: String,
    modifier: Modifier = Modifier,
    readout: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            // Clears the floating dock, which overlays the content.
            .padding(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 40.dp),
        )
        readout()
        content()
    }
}

@Composable
private fun Readout(text: String, fraction: Float, highlight: Boolean, caption: String) {
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(220),
        label = "toolProgress",
    )
    val colour = if (highlight) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.onBackground
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = text, style = IrisType.Metric, color = colour)
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outline),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animated)
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetRow(enabled: Boolean, selected: Long, onSelect: (Long) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TimerViewModel.PRESETS.forEach { preset ->
            val isOn = preset == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isOn) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .border(
                        width = 1.dp,
                        color = if (isOn) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        shape = RoundedCornerShape(20.dp),
                    )
                    .clickable(enabled = enabled) { onSelect(preset) }
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "${preset / 60_000} MIN",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isOn) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun ToolButton(
    text: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val container = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant
        primary -> MaterialTheme.colorScheme.onBackground
        else -> Color.Transparent
    }
    val content = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        primary -> MaterialTheme.colorScheme.background
        else -> MaterialTheme.colorScheme.onBackground
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(32.dp))
            .background(container)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(32.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = content)
    }
}
