package com.iris.alarm.ui.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iris.alarm.domain.model.Alarm
import com.iris.alarm.domain.model.VisionChallenge
import com.iris.alarm.ui.components.challengeIcon
import com.iris.alarm.ui.components.formatClock
import com.iris.alarm.ui.theme.IrisTheme
import com.iris.alarm.ui.theme.IrisType
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.delay

@Composable
fun DashboardScreen(
    onAddAlarm: () -> Unit,
    onEditAlarm: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Selection lives here rather than in the ViewModel: it is screen state that
    // should not survive leaving the screen, and it is keyed by id so an alarm
    // deleted from elsewhere cannot leave a phantom selected.
    var selected by rememberSaveable { mutableStateOf(emptySet<Long>()) }
    val selecting = selected.isNotEmpty()

    // Drop ids that no longer exist, so "3 SELECTED" can never outlive its rows.
    LaunchedEffect(state.alarms) {
        if (selected.isEmpty()) return@LaunchedEffect
        val living = state.alarms.mapTo(mutableSetOf()) { it.id }
        selected = selected intersect living
    }

    BackHandler(enabled = selecting) { selected = emptySet() }

    DashboardContent(
        state = state,
        exactAlarmsAllowed = viewModel.canScheduleExact(),
        selected = selected,
        onFixExactAlarms = viewModel::openExactAlarmSettings,
        onAddAlarm = onAddAlarm,
        onEditAlarm = { id -> if (!selecting) onEditAlarm(id) },
        onOpenSettings = onOpenSettings,
        onToggle = viewModel::toggle,
        onConfirmAwake = viewModel::confirmAwake,
        onToggleSelected = { alarm ->
            selected = if (alarm.id in selected) selected - alarm.id else selected + alarm.id
        },
        onSelectAll = { selected = state.alarms.mapTo(mutableSetOf()) { it.id } },
        onClearSelection = { selected = emptySet() },
        onDeleteSelected = {
            viewModel.delete(state.alarms.filter { it.id in selected })
            selected = emptySet()
        },
        modifier = modifier,
    )
}

@Composable
private fun DashboardContent(
    state: DashboardUiState,
    exactAlarmsAllowed: Boolean,
    selected: Set<Long>,
    onFixExactAlarms: () -> Unit,
    onAddAlarm: () -> Unit,
    onEditAlarm: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onToggle: (Alarm, Boolean) -> Unit,
    onConfirmAwake: () -> Unit,
    onToggleSelected: (Alarm) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selecting = selected.isNotEmpty()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp),
    ) {
        Clock(
            nextAlarmSummary = state.nextAlarmSummary,
            use24Hour = state.use24Hour,
            onOpenSettings = onOpenSettings,
        )

        if (!exactAlarmsAllowed) {
            ExactAlarmWarning(onFix = onFixExactAlarms)
        }

        state.wakeCheckSummary?.let { summary ->
            WakeCheckBanner(summary = summary, onConfirmAwake = onConfirmAwake)
        }

        Box(modifier = Modifier.weight(1f)) {
            if (state.alarms.isEmpty()) {
                Text(
                    text = "NO ALARMS SET",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.TopStart),
                )
            } else {
                LazyColumn {
                    items(state.alarms, key = { it.id }) { alarm ->
                        AlarmRow(
                            alarm = alarm,
                            use24Hour = state.use24Hour,
                            selecting = selecting,
                            selected = alarm.id in selected,
                            onClick = {
                                if (selecting) onToggleSelected(alarm) else onEditAlarm(alarm.id)
                            },
                            onLongClick = { onToggleSelected(alarm) },
                            onToggle = { enabled -> onToggle(alarm, enabled) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }

        // The bottom action swaps wholesale: while a selection is live, "new
        // alarm" is not what anyone is reaching for.
        AnimatedContent(
            targetState = selecting,
            transitionSpec = {
                (fadeIn(tween(160)) + slideInVertically(tween(220)) { it / 3 })
                    .togetherWith(fadeOut(tween(120)))
            },
            label = "dashboardAction",
        ) { inSelection ->
            if (inSelection) {
                SelectionBar(
                    count = selected.size,
                    allSelected = selected.size == state.alarms.size,
                    onSelectAll = onSelectAll,
                    onCancel = onClearSelection,
                    onDelete = onDeleteSelected,
                )
            } else {
                Button(
                    onClick = onAddAlarm,
                    shape = RoundedCornerShape(32.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onBackground,
                        contentColor = MaterialTheme.colorScheme.background,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        // Clears the floating dock, which overlays the content.
                        .padding(top = 24.dp, bottom = 96.dp),
                ) {
                    Text(
                        text = "NEW ALARM",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                }
            }
        }
    }
}

/**
 * Replaces the "new alarm" button while rows are selected. Delete is the
 * destructive one, so it is the only thing wearing the error colour, and there
 * is always a visible way out that is not the system back gesture.
 */
@Composable
private fun SelectionBar(
    count: Int,
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 96.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = if (allSelected) "ALL $count SELECTED" else "$count SELECTED",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = if (allSelected) onCancel else onSelectAll)
                .padding(vertical = 12.dp),
        )
        Text(
            text = "CANCEL",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .clickable(onClick = onCancel)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        )
        Text(
            text = "DELETE",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.error)
                .clickable(onClick = onDelete)
                .padding(horizontal = 22.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun Clock(nextAlarmSummary: String?, use24Hour: Boolean, onOpenSettings: () -> Unit) {
    var now by remember { mutableStateOf(LocalTime.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            // Re-align to the top of the next second rather than sleeping a flat 1s,
            // so the display never drifts a visible half-second behind.
            delay(1000L - (System.currentTimeMillis() % 1000L))
        }
    }

    Column(modifier = Modifier.padding(top = 40.dp, bottom = 32.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "IRIS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "SETTINGS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable(onClick = onOpenSettings)
                    .padding(8.dp),
            )
        }
        val clock = formatClock(now, use24Hour)
        Text(
            text = clock.digits,
            style = IrisType.Clock,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth(),
        )
        clock.suffix?.let { suffix ->
            Text(
                text = suffix,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = nextAlarmSummary ?: "NOTHING ARMED",
            style = MaterialTheme.typography.labelSmall,
            color = if (nextAlarmSummary == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlarmRow(
    alarm: Alarm,
    use24Hour: Boolean,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Slides in from the left so the rows shift rather than redraw, which is
        // what makes it read as a mode rather than a different screen.
        AnimatedVisibility(
            visible = selecting,
            enter = fadeIn(tween(160)) + expandHorizontally(tween(200)),
            exit = fadeOut(tween(120)) + shrinkHorizontally(tween(180)),
        ) {
            SelectionDot(selected = selected, modifier = Modifier.padding(end = 16.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatClock(alarm.time, use24Hour).inline(),
                style = MaterialTheme.typography.displaySmall,
                color = if (alarm.enabled) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = alarm.challenge.challengeIcon(),
                    contentDescription = alarm.challenge.displayName,
                    tint = if (alarm.enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = alarm.repeatSummary(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Hidden while selecting: flipping an alarm on is not a thing anyone
        // means to do while picking rows to delete.
        AnimatedVisibility(
            visible = !selecting,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(120)),
        ) {
            Switch(
                checked = alarm.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.background,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.background,
                    uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                ),
            )
        }
    }
}

/** Empty ring when unselected, filled with a tick when selected. */
@Composable
private fun SelectionDot(selected: Boolean, modifier: Modifier = Modifier) {
    val accent by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outline
        },
        label = "selectionAccent",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.85f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "selectionScale",
    )

    Box(
        modifier = modifier
            .size(24.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(if (selected) accent else Color.Transparent)
            .border(1.5.dp, accent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.background,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

/**
 * Shown between a solved challenge and the follow-up ring. "I'M UP" is the only
 * way to cancel it, which is the whole point — the check exists precisely for
 * the user who dismissed the alarm and went back to sleep.
 */
@Composable
private fun WakeCheckBanner(summary: String, onConfirmAwake: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(28.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .padding(bottom = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = summary,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "I'M UP",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .clickable(onClick = onConfirmAwake)
                .padding(8.dp),
        )
    }
}

@Composable
private fun ExactAlarmWarning(onFix: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onFix)
            .padding(bottom = 24.dp),
    ) {
        Text(
            text = "EXACT ALARMS ARE OFF",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = "Alarms may ring late. Tap to allow exact alarms.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
        )
    }
}

/** "EVERY DAY", "MON WED FRI", or "ONCE" for a one-shot. */
internal fun Alarm.repeatSummary(): String = when {
    repeatDays.isEmpty() -> "ONCE"
    repeatDays.size == 7 -> "EVERY DAY"
    repeatDays == WEEKDAYS -> "WEEKDAYS"
    repeatDays == WEEKEND -> "WEEKEND"
    else -> DayOfWeek.values()
        .filter { it in repeatDays }
        .joinToString(" ") { it.name.take(3) }
}

private val WEEKDAYS = setOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
)
private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun DashboardPreview() {
    IrisTheme(darkTheme = true) {
        DashboardContent(
            state = DashboardUiState(
                alarms = listOf(
                    Alarm(
                        id = 1,
                        hour = 6,
                        minute = 30,
                        repeatDays = WEEKDAYS,
                        challenge = VisionChallenge.SMILE,
                    ),
                    Alarm(
                        id = 2,
                        hour = 9,
                        minute = 0,
                        challenge = VisionChallenge.LUMEN,
                        enabled = false,
                    ),
                ),
                nextAlarmSummary = "RINGS IN 7H 12M",
                wakeCheckSummary = "WAKE CHECK IN 5 MIN",
            ),
            exactAlarmsAllowed = true,
            selected = setOf(1L),
            onFixExactAlarms = {},
            onAddAlarm = {},
            onEditAlarm = {},
            onOpenSettings = {},
            onToggle = { _, _ -> },
            onConfirmAwake = {},
            onToggleSelected = {},
            onSelectAll = {},
            onClearSelection = {},
            onDeleteSelected = {},
        )
    }
}
