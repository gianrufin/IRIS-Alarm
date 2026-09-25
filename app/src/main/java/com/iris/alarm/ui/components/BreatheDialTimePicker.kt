package com.iris.alarm.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Shortest circular delta between two angles measured in turns [0..1).
 * Prevents full backward spin when wrapping past 12 o'clock.
 */
private fun shortestAngleDelta(fromTurns: Float, toTurns: Float): Float {
    var delta = (toTurns - fromTurns) % 1.0f
    if (delta > 0.5f) delta -= 1.0f
    if (delta < -0.5f) delta += 1.0f
    return delta
}

private enum class ActiveDialRing {
    HOUR,
    MINUTE,
}

/**
 * Concentric Circular Dial Time Picker with Amber 'Breathe' Animation.
 *
 * Features:
 * - Primary outer ring for 60 minutes with fine graduations and highlighted markers (:00, :15, :30, :45).
 * - Inner concentric ring for hours (1..12 or 0..23) with prominent typography.
 * - Center aperture core featuring a living radial amber 'breathe' light pulse effect.
 * - Glowing orbital selector nodes connected by an illuminated laser alignment reticle.
 * - Independent continuous scrubbing on both rings with magnetic snap and haptic feedback.
 * - Live Circadian status ("☀️ Morning Horizon", "🌙 Night Horizon") & countdown pill ("Rings in X hr Y min").
 * - Quick-adjustment action chips (+15m, +30m, +1h, Snap :00).
 */
@Composable
fun BreatheDialTimePicker(
    hour: Int,
    minute: Int,
    use24Hour: Boolean,
    onTimeChange: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onChange by rememberUpdatedState(onTimeChange)
    val currentHour by rememberUpdatedState(hour)
    val currentMinute by rememberUpdatedState(minute)

    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    // Circadian theme colors
    val circadian = circadianThemeForHour(hour)
    val amberPrimary = Color(0xFFFFB74D) // IRIS Signature Amber
    val amberSecondary = Color(0xFFFF9100) // Solar Gold
    val amberDeep = Color(0xFFFF6D00) // Neon Flame

    // Active ring during gesture
    var activeRing by remember { mutableStateOf<ActiveDialRing?>(null) }

    val hourCount = if (use24Hour) 24 else 12
    val displayHour = if (use24Hour) hour else to12Hour(hour)
    val targetHourTurns = (if (use24Hour) hour else displayHour % 12) / hourCount.toFloat()
    val targetMinuteTurns = minute / 60f

    // Continuous angles that track across wrap-arounds without reverse spinning
    var continuousHourTurns by remember { mutableFloatStateOf(targetHourTurns) }
    var continuousMinuteTurns by remember { mutableFloatStateOf(targetMinuteTurns) }

    // Synchronize angles when props change externally
    LaunchedEffect(targetHourTurns, activeRing) {
        if (activeRing != ActiveDialRing.HOUR) {
            continuousHourTurns += shortestAngleDelta(continuousHourTurns, targetHourTurns)
        }
    }
    LaunchedEffect(targetMinuteTurns, activeRing) {
        if (activeRing != ActiveDialRing.MINUTE) {
            continuousMinuteTurns += shortestAngleDelta(continuousMinuteTurns, targetMinuteTurns)
        }
    }

    // Breathe animation effect: gentle rhythmic amber expansion in the center core
    val infiniteTransition = rememberInfiniteTransition(label = "breatheAnimation")
    val breatheWave by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breatheWaveFloat",
    )

    // Smooth animated angles for orbital nodes
    val animatedMinuteTurns by animateFloatAsState(
        targetValue = continuousMinuteTurns,
        animationSpec = if (activeRing == ActiveDialRing.MINUTE) {
            spring(dampingRatio = 0.98f, stiffness = Spring.StiffnessHigh)
        } else {
            spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
        },
        label = "animatedMinuteTurns",
    )
    val animatedHourTurns by animateFloatAsState(
        targetValue = continuousHourTurns,
        animationSpec = if (activeRing == ActiveDialRing.HOUR) {
            spring(dampingRatio = 0.98f, stiffness = Spring.StiffnessHigh)
        } else {
            spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
        },
        label = "animatedHourTurns",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("breathe_dial_time_picker")
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ==========================================
        // 1. CIRCADIAN ATMOSPHERE & LIVE COUNTDOWN
        // ==========================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Circadian Pill
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = amberPrimary.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, amberPrimary.copy(alpha = 0.35f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val icon = when (hour) {
                        in 5..11 -> "☀️"
                        in 12..17 -> "⚡"
                        else -> "🌙"
                    }
                    Text(text = icon, fontSize = 12.sp)
                    Text(
                        text = circadian.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        ),
                        color = amberPrimary,
                    )
                }
            }

            // Live Time Remaining Countdown
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Canvas(modifier = Modifier.size(7.dp)) {
                        drawCircle(
                            color = amberPrimary.copy(alpha = 0.4f + 0.6f * breatheWave),
                            radius = size.minDimension / 2f,
                        )
                    }
                    Text(
                        text = formatTimeUntil(hour, minute),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        // ==========================================
        // 2. CONCENTRIC CIRCULAR DIAL WITH BREATHE CORE
        // ==========================================
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            val diameter = with(density) { minOf(maxWidth, maxHeight).toPx() }
            val centre = Offset(diameter / 2f, diameter / 2f)

            // Ring Radii
            val minuteRadius = diameter * 0.425f
            val hourRadius = diameter * 0.285f
            val centerRadius = diameter * 0.175f
            val ringBoundary = (minuteRadius + hourRadius) / 2f
            val centerPaddingPx = with(density) { 8.dp.toPx() }
            val outerPaddingPx = with(density) { 28.dp.toPx() }

            fun turnsFrom(pos: Offset): Float =
                RadialMath.turns(pos.x - centre.x, pos.y - centre.y)

            fun ringAt(pos: Offset): ActiveDialRing? {
                val distance = hypot(pos.x - centre.x, pos.y - centre.y)
                return when {
                    distance < centerRadius + centerPaddingPx -> null // Inside Center Readout
                    distance < ringBoundary -> ActiveDialRing.HOUR
                    distance <= minuteRadius + outerPaddingPx -> ActiveDialRing.MINUTE
                    else -> null
                }
            }

            var lastMinuteTick by remember { mutableIntStateOf(minute) }
            var lastHourTick by remember { mutableIntStateOf(hour) }

            fun applyRingGesture(ring: ActiveDialRing, pos: Offset, isFinal: Boolean) {
                val turns = turnsFrom(pos)
                when (ring) {
                    ActiveDialRing.MINUTE -> {
                        val newMin = RadialMath.toMinute(turns)
                        if (newMin != lastMinuteTick) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            lastMinuteTick = newMin
                        }
                        if (isFinal) {
                            val quantized = newMin / 60f
                            continuousMinuteTurns += shortestAngleDelta(continuousMinuteTurns, quantized)
                        } else {
                            continuousMinuteTurns += shortestAngleDelta(continuousMinuteTurns, turns)
                        }
                        onChange(currentHour, newMin)
                    }
                    ActiveDialRing.HOUR -> {
                        val newHour = if (use24Hour) {
                            RadialMath.toHour24(turns)
                        } else {
                            to24Hour(RadialMath.toHour12(turns), isPm(currentHour))
                        }
                        if (newHour != lastHourTick) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            lastHourTick = newHour
                        }
                        if (isFinal) {
                            val quantized = (if (use24Hour) newHour else (to12Hour(newHour) % 12)) / hourCount.toFloat()
                            continuousHourTurns += shortestAngleDelta(continuousHourTurns, quantized)
                        } else {
                            continuousHourTurns += shortestAngleDelta(continuousHourTurns, turns)
                        }
                        onChange(newHour, currentMinute)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(hourCount, use24Hour, diameter) {
                        detectTapGestures { pos ->
                            ringAt(pos)?.let { ring ->
                                applyRingGesture(ring, pos, isFinal = true)
                            }
                        }
                    }
                    .pointerInput(hourCount, use24Hour, diameter) {
                        detectDragGestures(
                            onDragStart = { pos ->
                                activeRing = ringAt(pos)
                                activeRing?.let { ring -> applyRingGesture(ring, pos, isFinal = false) }
                            },
                            onDragEnd = {
                                val ring = activeRing
                                activeRing = null
                                if (ring == ActiveDialRing.HOUR) {
                                    val quantized = (if (use24Hour) currentHour else (to12Hour(currentHour) % 12)) / hourCount.toFloat()
                                    continuousHourTurns += shortestAngleDelta(continuousHourTurns, quantized)
                                } else if (ring == ActiveDialRing.MINUTE) {
                                    val quantized = currentMinute / 60f
                                    continuousMinuteTurns += shortestAngleDelta(continuousMinuteTurns, quantized)
                                }
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            onDragCancel = {
                                activeRing = null
                            },
                        ) { change, _ ->
                            activeRing?.let { ring -> applyRingGesture(ring, change.position, isFinal = false) }
                            change.consume()
                        }
                    },
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // -------------------------------------------------------------
                    // A. LIVING AMBER BREATHE CORE (Radial wave ripples in center)
                    // -------------------------------------------------------------
                    val breatheIntensity = if (activeRing != null) 1.25f else 1.0f
                    val pulseRadius1 = centerRadius * (1.0f + 0.22f * breatheWave * breatheIntensity)
                    val pulseAlpha1 = (0.24f + 0.16f * breatheWave) * breatheIntensity

                    // Radiant ambient glow halo
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                amberSecondary.copy(alpha = pulseAlpha1),
                                amberPrimary.copy(alpha = pulseAlpha1 * 0.4f),
                                Color.Transparent,
                            ),
                            center = centre,
                            radius = pulseRadius1 * 1.5f,
                        ),
                        radius = pulseRadius1 * 1.5f,
                        center = centre,
                    )

                    // Secondary expanding harmonic ring ripple
                    val rippleWave2 = (breatheWave + 0.45f).let { if (it > 1f) it - 1f else it }
                    val rippleRadius2 = centerRadius * (1.08f + 0.38f * rippleWave2)
                    val rippleAlpha2 = (0.32f * (1f - rippleWave2)).coerceIn(0f, 1f)
                    drawCircle(
                        color = amberPrimary.copy(alpha = rippleAlpha2),
                        radius = rippleRadius2,
                        center = centre,
                        style = Stroke(width = 1.5.dp.toPx()),
                    )

                    // -------------------------------------------------------------
                    // B. TRACK LINES (Concentric circular guides)
                    // -------------------------------------------------------------
                    // Outer Minute Track
                    drawCircle(
                        color = amberPrimary.copy(alpha = if (activeRing == ActiveDialRing.MINUTE) 0.5f else 0.2f),
                        radius = minuteRadius,
                        center = centre,
                        style = Stroke(width = 1.dp.toPx()),
                    )

                    // Inner Hour Track
                    drawCircle(
                        color = amberPrimary.copy(alpha = if (activeRing == ActiveDialRing.HOUR) 0.5f else 0.2f),
                        radius = hourRadius,
                        center = centre,
                        style = Stroke(width = 1.dp.toPx()),
                    )

                    // Active Arc on Minute Track
                    val minuteSweepAngle = animatedMinuteTurns * 360f
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                amberPrimary.copy(alpha = 0.05f),
                                amberSecondary.copy(alpha = 0.45f),
                            ),
                            center = centre,
                        ),
                        startAngle = -90f,
                        sweepAngle = minuteSweepAngle % 360f,
                        useCenter = false,
                        topLeft = Offset(centre.x - minuteRadius, centre.y - minuteRadius),
                        size = androidx.compose.ui.geometry.Size(minuteRadius * 2f, minuteRadius * 2f),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                    )

                    // -------------------------------------------------------------
                    // C. MINUTE TICKS (60 precise graduations)
                    // -------------------------------------------------------------
                    for (m in 0 until 60) {
                        val angleRad = (m / 60f * 2f * PI - PI / 2f).toFloat()
                        val cosA = cos(angleRad)
                        val sinA = sin(angleRad)

                        val isQuarter = m % 15 == 0
                        val isFive = m % 5 == 0
                        val tickLen = when {
                            isQuarter -> 10.dp.toPx()
                            isFive -> 6.dp.toPx()
                            else -> 3.dp.toPx()
                        }
                        val strokeW = when {
                            isQuarter -> 2.dp.toPx()
                            isFive -> 1.5.dp.toPx()
                            else -> 1.dp.toPx()
                        }
                        val tickColor = when {
                            isQuarter -> amberPrimary
                            isFive -> amberPrimary.copy(alpha = 0.6f)
                            else -> Color.White.copy(alpha = 0.2f)
                        }

                        val innerOffset = Offset(
                            x = centre.x + cosA * (minuteRadius - tickLen / 2f),
                            y = centre.y + sinA * (minuteRadius - tickLen / 2f),
                        )
                        val outerOffset = Offset(
                            x = centre.x + cosA * (minuteRadius + tickLen / 2f),
                            y = centre.y + sinA * (minuteRadius + tickLen / 2f),
                        )

                        drawLine(
                            color = tickColor,
                            start = innerOffset,
                            end = outerOffset,
                            strokeWidth = strokeW,
                            cap = StrokeCap.Round,
                        )

                        // Render Quarter Labels (:00, :15, :30, :45)
                        if (isQuarter) {
                            val labelText = m.toString().padStart(2, '0')
                            val labelDist = minuteRadius + 18.dp.toPx()
                            val textPos = Offset(
                                x = centre.x + cosA * labelDist,
                                y = centre.y + sinA * labelDist,
                            )
                            val isCurrentQuarter = abs(minute - m) <= 2 || (m == 0 && minute >= 58)
                            val measured = textMeasurer.measure(
                                text = labelText,
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (isCurrentQuarter) FontWeight.Black else FontWeight.Bold,
                                    color = if (isCurrentQuarter) amberPrimary else amberPrimary.copy(alpha = 0.5f),
                                ),
                            )
                            drawText(
                                textLayoutResult = measured,
                                topLeft = Offset(textPos.x - measured.size.width / 2f, textPos.y - measured.size.height / 2f),
                            )
                        }
                    }

                    // -------------------------------------------------------------
                    // D. HOUR NUMBERS & TICKS (Inner Concentric Ring)
                    // -------------------------------------------------------------
                    val stepHours = if (use24Hour) 2 else 1
                    for (h in (if (use24Hour) 0 until 24 else 1..12) step stepHours) {
                        val turn = if (use24Hour) h / 24f else (h % 12) / 12f
                        val angleRad = (turn * 2f * PI - PI / 2f).toFloat()
                        val cosA = cos(angleRad)
                        val sinA = sin(angleRad)

                        val hourText = if (use24Hour) h.toString().padStart(2, '0') else h.toString()
                        val isSelectedHour = if (use24Hour) hour == h else displayHour == h

                        val textDist = hourRadius
                        val measured = textMeasurer.measure(
                            text = hourText,
                            style = TextStyle(
                                fontSize = if (isSelectedHour) 14.sp else 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (isSelectedHour) FontWeight.Black else FontWeight.SemiBold,
                                color = if (isSelectedHour) amberPrimary else Color.White.copy(alpha = 0.45f),
                            ),
                        )
                        drawText(
                            textLayoutResult = measured,
                            topLeft = Offset(
                                centre.x + cosA * textDist - measured.size.width / 2f,
                                centre.y + sinA * textDist - measured.size.height / 2f,
                            ),
                        )
                    }

                    // -------------------------------------------------------------
                    // E. ORBITAL SELECTOR NODES & LASER ALIGNMENT RETICLE
                    // -------------------------------------------------------------
                    fun tipOffset(turns: Float, radius: Float): Offset {
                        val rad = (turns * 2f * PI - PI / 2f).toFloat()
                        return Offset(centre.x + cos(rad) * radius, centre.y + sin(rad) * radius)
                    }

                    val hourTip = tipOffset(animatedHourTurns, hourRadius)
                    val minuteTip = tipOffset(animatedMinuteTurns, minuteRadius)

                    // Laser Alignment Hairlines from Center
                    drawLine(
                        brush = Brush.radialGradient(
                            colors = listOf(amberPrimary.copy(alpha = 0.5f), Color.Transparent),
                            center = centre,
                            radius = minuteRadius,
                        ),
                        start = centre,
                        end = minuteTip,
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = amberPrimary.copy(alpha = 0.45f),
                        start = centre,
                        end = hourTip,
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )

                    // 1. Inner Hour Selector Node
                    drawCircle(
                        color = amberPrimary.copy(alpha = 0.25f),
                        radius = 16.dp.toPx(),
                        center = hourTip,
                    )
                    drawCircle(
                        color = amberPrimary,
                        radius = 8.dp.toPx(),
                        center = hourTip,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                    drawCircle(
                        color = amberDeep,
                        radius = 4.dp.toPx(),
                        center = hourTip,
                    )

                    // 2. Outer Minute Selector Node
                    drawCircle(
                        color = amberPrimary.copy(alpha = 0.3f),
                        radius = 18.dp.toPx(),
                        center = minuteTip,
                    )
                    drawCircle(
                        color = amberPrimary,
                        radius = 10.dp.toPx(),
                        center = minuteTip,
                        style = Stroke(width = 2.5.dp.toPx()),
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 4.dp.toPx(),
                        center = minuteTip,
                    )

                    // Center Pivot Dot
                    drawCircle(color = amberPrimary, radius = 3.dp.toPx(), center = centre)
                }

                // =============================================================
                // F. FROSTED GLASS CENTER READOUT CHAMBER
                // =============================================================
                val clock = formatClock(hour, minute, use24Hour)
                val centerBoxSize = with(density) { (centerRadius * 1.85f).toDp() }

                Box(
                    modifier = Modifier
                        .size(centerBoxSize)
                        .clip(CircleShape)
                        .background(Color(0xE60D1117))
                        .border(1.dp, amberPrimary.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = clock.digits,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                fontSize = 26.sp,
                                letterSpacing = 1.sp,
                            ),
                            color = amberPrimary,
                        )

                        if (!use24Hour && clock.suffix != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            // Interactive AM / PM Toggle Switch inside Center Core
                            val isPmState = isPm(hour)
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(amberPrimary.copy(alpha = 0.12f))
                                    .border(1.dp, amberPrimary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "AM",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (!isPmState) FontWeight.Black else FontWeight.Medium,
                                        fontSize = 11.sp,
                                    ),
                                    color = if (!isPmState) amberPrimary else Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (!isPmState) amberPrimary.copy(alpha = 0.25f) else Color.Transparent)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                        ) {
                                            if (isPmState) {
                                                val newHour = to24Hour(to12Hour(hour), false)
                                                onChange(newHour, minute)
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                        }
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                                Text(
                                    text = "PM",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isPmState) FontWeight.Black else FontWeight.Medium,
                                        fontSize = 11.sp,
                                    ),
                                    color = if (isPmState) amberPrimary else Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isPmState) amberPrimary.copy(alpha = 0.25f) else Color.Transparent)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                        ) {
                                            if (!isPmState) {
                                                val newHour = to24Hour(to12Hour(hour), true)
                                                onChange(newHour, minute)
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                        }
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ==========================================
        // 3. QUICK-ADJUSTMENT ACTION CHIPS
        // ==========================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            DialQuickChip(
                label = "+15m",
                testTag = "quick_chip_15m",
                accentColor = amberPrimary,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val total = hour * 60 + minute + 15
                    onChange((total / 60) % 24, total % 60)
                },
            )
            DialQuickChip(
                label = "+30m",
                testTag = "quick_chip_30m",
                accentColor = amberPrimary,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val total = hour * 60 + minute + 30
                    onChange((total / 60) % 24, total % 60)
                },
            )
            DialQuickChip(
                label = "+1h",
                testTag = "quick_chip_1h",
                accentColor = amberPrimary,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onChange((hour + 1) % 24, minute)
                },
            )
            DialQuickChip(
                label = "Snap :00",
                testTag = "quick_chip_00",
                accentColor = amberPrimary,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val newMin = 0
                    val newHour = if (minute == 0) (hour + 1) % 24 else hour
                    onChange(newHour, newMin)
                },
            )
        }
    }
}

/**
 * Quick adjustment action chip styled for IRIS amber theme.
 */
@Composable
private fun DialQuickChip(
    label: String,
    testTag: String,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)),
        onClick = onClick,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}
