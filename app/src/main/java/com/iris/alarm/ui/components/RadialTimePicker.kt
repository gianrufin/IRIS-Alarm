package com.iris.alarm.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Radial clock-face time picker with concentric rings for minutes and hours,
 * powered by the BreatheDialTimePicker engine.
 */
@Composable
fun RadialTimePicker(
    hour: Int,
    minute: Int,
    use24Hour: Boolean,
    onTimeChange: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BreatheDialTimePicker(
        hour = hour,
        minute = minute,
        use24Hour = use24Hour,
        onTimeChange = onTimeChange,
        modifier = modifier,
    )
}
