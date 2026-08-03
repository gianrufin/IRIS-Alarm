package com.iris.alarm.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.iris.alarm.ui.dashboard.DashboardScreen
import com.iris.alarm.ui.tools.PomodoroScreen
import com.iris.alarm.ui.tools.StopwatchScreen
import com.iris.alarm.ui.tools.TimerScreen

enum class HomeTab(val label: String, val icon: ImageVector) {
    ALARMS("Alarms", Icons.Rounded.Alarm),
    TIMER("Timer", Icons.Rounded.HourglassEmpty),
    STOPWATCH("Stopwatch", Icons.Rounded.Timer),
    POMODORO("Pomodoro", Icons.Rounded.Spa),
}

/**
 * The four things IRIS does, behind a dock that floats over the content rather
 * than sitting in a bar of its own — the alarm list keeps the full height of the
 * screen, which is what the enormous clock needs.
 */
@Composable
fun IrisHome(
    onAddAlarm: () -> Unit,
    onEditAlarm: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.ALARMS) }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                // Slide in the direction of travel along the dock.
                val forward = targetState.ordinal > initialState.ordinal
                val direction = if (forward) 1 else -1
                (
                    slideInHorizontally(tween(260)) { it * direction / 4 } +
                        fadeIn(tween(260))
                    ).togetherWith(
                    slideOutHorizontally(tween(200)) { -it * direction / 4 } +
                        fadeOut(tween(160)),
                )
            },
            label = "homeTab",
        ) { current ->
            when (current) {
                HomeTab.ALARMS -> DashboardScreen(
                    onAddAlarm = onAddAlarm,
                    onEditAlarm = onEditAlarm,
                    onOpenSettings = onOpenSettings,
                )

                HomeTab.TIMER -> TimerScreen()
                HomeTab.STOPWATCH -> StopwatchScreen()
                HomeTab.POMODORO -> PomodoroScreen()
            }
        }

        Dock(
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        )
    }
}

@Composable
private fun Dock(
    selected: HomeTab,
    onSelect: (HomeTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(32.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HomeTab.entries.forEach { entry ->
            DockItem(
                tab = entry,
                selected = entry == selected,
                onClick = { onSelect(entry) },
            )
        }
    }
}

@Composable
private fun DockItem(tab: HomeTab, selected: Boolean, onClick: () -> Unit) {
    // The selected item swells slightly rather than gaining a label, so the dock
    // stays the same width whichever tab is active.
    val size by animateDpAsState(
        targetValue = if (selected) 48.dp else 44.dp,
        label = "dockItemSize",
    )

    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(24.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp),
        )
    }
}
