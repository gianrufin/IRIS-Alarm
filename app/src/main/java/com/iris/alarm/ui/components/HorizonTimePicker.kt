package com.iris.alarm.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import java.time.LocalTime

/**
 * Focus modes for time pickers.
 */
enum class HorizonFocus {
    HOURS,
    MINUTES,
}

/**
 * Circadian color palette corresponding to the target time-of-day.
 */
data class CircadianTheme(
    val primary: Color,
    val secondary: Color,
    val backgroundGlow: Color,
    val label: String,
)

fun circadianThemeForHour(hour24: Int): CircadianTheme = when (hour24) {
    in 5..11 -> CircadianTheme(
        primary = Color(0xFFFFB74D), // Solar Gold
        secondary = Color(0xFFFF7043), // Dawn Coral
        backgroundGlow = Color(0x33FFB74D),
        label = "Morning Horizon",
    )
    in 12..17 -> CircadianTheme(
        primary = Color(0xFF4DD0E1), // Daylight Cyan
        secondary = Color(0xFF29B6F6), // Sky Blue
        backgroundGlow = Color(0x334DD0E1),
        label = "Daylight Horizon",
    )
    else -> CircadianTheme(
        primary = Color(0xFFB388FF), // Iris Violet
        secondary = Color(0xFF7C4DFF), // Midnight Deep
        backgroundGlow = Color(0x337C4DFF),
        label = "Night Horizon",
    )
}

/**
 * Computes human-friendly remaining time from now until the target hour:minute.
 */
fun formatTimeUntil(targetHour: Int, targetMinute: Int): String {
    val now = LocalTime.now()
    val nowMinutes = now.hour * 60 + now.minute
    val targetMinutes = targetHour * 60 + targetMinute
    val diff = if (targetMinutes > nowMinutes) {
        targetMinutes - nowMinutes
    } else {
        (24 * 60 - nowMinutes) + targetMinutes
    }
    val hours = diff / 60
    val mins = diff % 60
    return when {
        diff == 0 -> "Rings in 24 hrs"
        hours > 0 && mins > 0 -> "Rings in $hours hr $mins min"
        hours > 0 -> "Rings in $hours hr"
        else -> "Rings in $mins min"
    }
}

/**
 * Legacy/Alternative entry point that delegates to the Kinetic Drum Time Picker.
 */
@Composable
fun HorizonTimePicker(
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
