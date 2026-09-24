package com.iris.alarm.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch

/**
 * Focus modes for the Horizon Jog-Wheel.
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
 * Concept 3: The Horizon Jog-Wheel Time Picker.
 *
 * Features:
 * - Fluid horizontal curved arc ruler with dynamic graduations.
 * - Center illuminated laser indicator / needle.
 * - Hero digital time readout with interactive Hour/Minute focus.
 * - Live "Time Until Alarm" Horizon Pill for instant sanity checking.
 * - Dynamic circadian atmosphere matching the time of day.
 * - Smart quick-adjustment chips (+15m, +30m, +1h, Round :00).
 */
@Composable
fun HorizonTimePicker(
    hour: Int,
    minute: Int,
    use24Hour: Boolean,
    onTimeChange: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onChange by rememberUpdatedState(onTimeChange)
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var focus by remember { mutableStateOf(HorizonFocus.MINUTES) }

    val circadian = circadianThemeForHour(hour)
    val animatedAccent by animateColorAsState(circadian.primary, tween(400), label = "accentColor")
    val animatedGlow by animateColorAsState(circadian.backgroundGlow, tween(400), label = "glowColor")

    val displayHour = if (use24Hour) hour else to12Hour(hour)
    val isCurrentPm = isPm(hour)

    // Ruler scroll offset state
    val tickSpacingPx = 40f // Horizontal distance per unit tick
    val scrollOffset = remember { Animatable(0f) }
    var lastHapticIndex by remember { mutableStateOf(0) }

    // Sync scrollOffset when hour or minute changes externally
    LaunchedEffect(hour, minute, focus) {
        val targetIndex = if (focus == HorizonFocus.HOURS) {
            if (use24Hour) hour else (if (displayHour == 12) 0 else displayHour)
        } else {
            minute
        }
        val targetOffset = -targetIndex * tickSpacingPx
        if (abs(scrollOffset.value - targetOffset) > 1f) {
            scrollOffset.snapTo(targetOffset)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(animatedGlow, MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)),
                    center = Offset(500f, 150f),
                    radius = 800f,
                ),
            )
            .border(1.dp, animatedAccent.copy(alpha = 0.25f), RoundedCornerShape(28.dp))
            .padding(vertical = 20.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Horizon pill: live time until alarm
        Box(
            modifier = Modifier
                .testTag("time_until_alarm_pill")
                .clip(RoundedCornerShape(16.dp))
                .background(animatedAccent.copy(alpha = 0.12f))
                .border(1.dp, animatedAccent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(animatedAccent),
                )
                Text(
                    text = formatTimeUntil(hour, minute),
                    style = MaterialTheme.typography.labelMedium,
                    color = animatedAccent,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Hero Digital Time Display with Active Focus & AM/PM
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Hours Digits Box
            val hoursSelected = focus == HorizonFocus.HOURS
            Box(
                modifier = Modifier
                    .testTag("time_picker_hours_box")
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (hoursSelected) animatedAccent.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    )
                    .border(
                        width = if (hoursSelected) 2.dp else 1.dp,
                        color = if (hoursSelected) animatedAccent else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(18.dp),
                    )
                    .clickable {
                        focus = HorizonFocus.HOURS
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (use24Hour) hour.toString().padStart(2, '0') else displayHour.toString().padStart(2, '0'),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (hoursSelected) animatedAccent else MaterialTheme.colorScheme.onSurface,
                )
            }

            Text(
                text = ":",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = animatedAccent.copy(alpha = 0.8f),
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            // Minutes Digits Box
            val minutesSelected = focus == HorizonFocus.MINUTES
            Box(
                modifier = Modifier
                    .testTag("time_picker_minutes_box")
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (minutesSelected) animatedAccent.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    )
                    .border(
                        width = if (minutesSelected) 2.dp else 1.dp,
                        color = if (minutesSelected) animatedAccent else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(18.dp),
                    )
                    .clickable {
                        focus = HorizonFocus.MINUTES
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = minute.toString().padStart(2, '0'),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (minutesSelected) animatedAccent else MaterialTheme.colorScheme.onSurface,
                )
            }

            // AM / PM Switch (if in 12-hour mode)
            if (!use24Hour) {
                Spacer(modifier = Modifier.width(14.dp))
                Column(
                    modifier = Modifier
                        .testTag("time_picker_am_pm_toggle")
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .padding(3.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf(false, true).forEach { pm ->
                        val isSelected = pm == isCurrentPm
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) animatedAccent else Color.Transparent)
                                .clickable {
                                    if (pm != isCurrentPm) {
                                        val newHour = to24Hour(to12Hour(hour), isPm = pm)
                                        onChange(newHour, minute)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (pm) "PM" else "AM",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Focus Mode Selector Tabs (HOURS | MINUTES)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .testTag("time_picker_mode_hours")
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (focus == HorizonFocus.HOURS) animatedAccent.copy(alpha = 0.25f) else Color.Transparent)
                    .clickable {
                        focus = HorizonFocus.HOURS
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "HOURS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (focus == HorizonFocus.HOURS) animatedAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Box(
                modifier = Modifier
                    .testTag("time_picker_mode_minutes")
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (focus == HorizonFocus.MINUTES) animatedAccent.copy(alpha = 0.25f) else Color.Transparent)
                    .clickable {
                        focus = HorizonFocus.MINUTES
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "MINUTES",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (focus == HorizonFocus.MINUTES) animatedAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // The Horizon Jog-Wheel Arc & Ruler Canvas
        val textMeasurer = rememberTextMeasurer()
        val textStyle = TextStyle(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
        )

        val totalUnits = if (focus == HorizonFocus.HOURS) {
            if (use24Hour) 24 else 12
        } else {
            60
        }

        Box(
            modifier = Modifier
                .testTag("horizon_jog_wheel_canvas")
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        coroutineScope.launch {
                            val newOffset = scrollOffset.value + delta
                            scrollOffset.snapTo(newOffset)

                            val centerUnit = ((-newOffset / tickSpacingPx).roundToInt() % totalUnits).let {
                                if (it < 0) it + totalUnits else it
                            }

                            if (centerUnit != lastHapticIndex) {
                                lastHapticIndex = centerUnit
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                                if (focus == HorizonFocus.HOURS) {
                                    val newHour = if (use24Hour) {
                                        centerUnit
                                    } else {
                                        val h12 = if (centerUnit == 0) 12 else centerUnit
                                        to24Hour(h12, isCurrentPm)
                                    }
                                    onChange(newHour, minute)
                                } else {
                                    onChange(hour, centerUnit)
                                }
                            }
                        }
                    },
                    onDragStopped = {
                        // Magnetic snap to nearest unit
                        val nearestUnit = (-scrollOffset.value / tickSpacingPx).roundToInt()
                        scrollOffset.animateTo(
                            targetValue = -nearestUnit * tickSpacingPx,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        )
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val centerX = canvasWidth / 2f
                val centerY = canvasHeight / 2f

                // Curved Horizon Base Arc
                val arcRadius = canvasWidth * 0.85f
                val arcCenter = Offset(centerX, centerY + arcRadius - 40f)

                // Background horizon glow spotlight
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(animatedAccent.copy(alpha = 0.25f), Color.Transparent),
                        center = Offset(centerX, 20f),
                        radius = 80f,
                    ),
                    radius = 80f,
                    center = Offset(centerX, 20f),
                )

                // Draw graduations (ticks and numbers)
                val visibleRange = ((canvasWidth / 2f) / tickSpacingPx).toInt() + 4
                val centerIndex = (-scrollOffset.value / tickSpacingPx).toInt()

                for (i in (centerIndex - visibleRange)..(centerIndex + visibleRange)) {
                    val unit = ((i % totalUnits) + totalUnits) % totalUnits
                    val tickX = centerX + scrollOffset.value + (i * tickSpacingPx)

                    if (tickX < -20f || tickX > canvasWidth + 20f) continue

                    // Vignette alpha falloff near the sides
                    val distanceFromCenter = abs(tickX - centerX)
                    val alpha = (1f - (distanceFromCenter / (canvasWidth / 2f))).coerceIn(0f, 1f)

                    // Arc vertical displacement (convex horizon curve)
                    val curveY = ((distanceFromCenter / (canvasWidth / 2f)) * (distanceFromCenter / (canvasWidth / 2f))) * 22f

                    val isMajor = if (focus == HorizonFocus.HOURS) {
                        unit % 3 == 0 || unit == 12 || unit == 0
                    } else {
                        unit % 15 == 0
                    }

                    val isMedium = if (focus == HorizonFocus.HOURS) {
                        true
                    } else {
                        unit % 5 == 0
                    }

                    val tickHeight = when {
                        isMajor -> 32f
                        isMedium -> 20f
                        else -> 12f
                    }

                    val tickTop = 24f + curveY
                    val tickBottom = tickTop + tickHeight

                    val tickColor = when {
                        isMajor -> animatedAccent.copy(alpha = alpha)
                        isMedium -> animatedAccent.copy(alpha = alpha * 0.7f)
                        else -> Color.White.copy(alpha = alpha * 0.35f)
                    }

                    val strokeWidth = if (isMajor) 2.5f else 1.5f

                    drawLine(
                        color = tickColor,
                        start = Offset(tickX, tickTop),
                        end = Offset(tickX, tickBottom),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )

                    // Text labels for major / medium graduations
                    if (isMajor || (focus == HorizonFocus.HOURS && isMedium)) {
                        val labelText = if (focus == HorizonFocus.HOURS) {
                            if (use24Hour) unit.toString() else (if (unit == 0) "12" else unit.toString())
                        } else {
                            if (unit == 0) ":00" else ":${unit.toString().padStart(2, '0')}"
                        }

                        val measured = textMeasurer.measure(labelText, textStyle)
                        drawText(
                            textLayoutResult = measured,
                            topLeft = Offset(
                                tickX - (measured.size.width / 2f),
                                tickBottom + 6f,
                            ),
                            color = tickColor,
                        )
                    }
                }

                // Center Laser Indicator / Reticle
                val needlePath = Path().apply {
                    moveTo(centerX - 8f, 6f)
                    lineTo(centerX + 8f, 6f)
                    lineTo(centerX, 20f)
                    close()
                }

                // Needle glowing spotlight beam
                drawLine(
                    brush = Brush.verticalGradient(
                        colors = listOf(animatedAccent, animatedAccent.copy(alpha = 0f)),
                        startY = 18f,
                        endY = 75f,
                    ),
                    start = Offset(centerX, 18f),
                    end = Offset(centerX, 75f),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round,
                )

                // Laser triangle needle
                drawPath(path = needlePath, color = animatedAccent)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Smart Quick-Snap Adjustment Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuickAdjustmentChip(
                label = "+15m",
                testTag = "quick_chip_plus_15",
                accentColor = animatedAccent,
                onClick = {
                    val totalMins = (hour * 60 + minute + 15) % (24 * 60)
                    onChange(totalMins / 60, totalMins % 60)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                modifier = Modifier.weight(1f),
            )

            QuickAdjustmentChip(
                label = "+30m",
                testTag = "quick_chip_plus_30",
                accentColor = animatedAccent,
                onClick = {
                    val totalMins = (hour * 60 + minute + 30) % (24 * 60)
                    onChange(totalMins / 60, totalMins % 60)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                modifier = Modifier.weight(1f),
            )

            QuickAdjustmentChip(
                label = "+1h",
                testTag = "quick_chip_plus_60",
                accentColor = animatedAccent,
                onClick = {
                    val newHour = (hour + 1) % 24
                    onChange(newHour, minute)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                modifier = Modifier.weight(1f),
            )

            QuickAdjustmentChip(
                label = "Round :00",
                testTag = "quick_chip_round_00",
                accentColor = animatedAccent,
                onClick = {
                    val roundedMinute = if (minute >= 30) 0 else 0
                    val roundedHour = if (minute >= 30) (hour + 1) % 24 else hour
                    onChange(roundedHour, roundedMinute)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                modifier = Modifier.weight(1.2f),
            )
        }
    }
}

@Composable
private fun QuickAdjustmentChip(
    label: String,
    testTag: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .testTag(testTag)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = accentColor,
        )
    }
}
