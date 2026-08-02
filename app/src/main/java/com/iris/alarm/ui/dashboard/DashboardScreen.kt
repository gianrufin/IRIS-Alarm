package com.iris.alarm.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iris.alarm.domain.model.Alarm
import com.iris.alarm.domain.model.VisionChallenge
import com.iris.alarm.ui.components.challengeIcon
import com.iris.alarm.ui.theme.IrisTheme
import com.iris.alarm.ui.theme.IrisType
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
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

    DashboardContent(
        state = state,
        exactAlarmsAllowed = viewModel.canScheduleExact(),
        onFixExactAlarms = viewModel::openExactAlarmSettings,
        onAddAlarm = onAddAlarm,
        onEditAlarm = onEditAlarm,
        onOpenSettings = onOpenSettings,
        onToggle = viewModel::toggle,
        onConfirmAwake = viewModel::confirmAwake,
        modifier = modifier,
    )
}

@Composable
private fun DashboardContent(
    state: DashboardUiState,
    exactAlarmsAllowed: Boolean,
    onFixExactAlarms: () -> Unit,
    onAddAlarm: () -> Unit,
    onEditAlarm: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onToggle: (Alarm, Boolean) -> Unit,
    onConfirmAwake: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp),
    ) {
        Clock(nextAlarmSummary = state.nextAlarmSummary, onOpenSettings = onOpenSettings)

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
                            onClick = { onEditAlarm(alarm.id) },
                            onToggle = { enabled -> onToggle(alarm, enabled) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }

        Button(
            onClick = onAddAlarm,
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onBackground,
                contentColor = MaterialTheme.colorScheme.background,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
        ) {
            Text(
                text = "NEW ALARM",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun Clock(nextAlarmSummary: String?, onOpenSettings: () -> Unit) {
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
        Text(
            text = now.format(TIME_FORMAT),
            style = IrisType.Clock,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth(),
        )
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

@Composable
private fun AlarmRow(alarm: Alarm, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = alarm.time.format(TIME_FORMAT),
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

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

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
            onFixExactAlarms = {},
            onAddAlarm = {},
            onEditAlarm = {},
            onOpenSettings = {},
            onToggle = { _, _ -> },
            onConfirmAwake = {},
        )
    }
}
