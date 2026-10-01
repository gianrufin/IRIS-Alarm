package com.iris.alarm.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Kinetic Drum Time Picker entry point, powered by the unified BreatheDialTimePicker engine.
 */
@Composable
fun KineticDrumTimePicker(
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
