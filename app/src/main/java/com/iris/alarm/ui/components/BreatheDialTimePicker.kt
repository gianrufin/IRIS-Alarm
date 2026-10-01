package com.iris.alarm.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Simplified, fluid vertical roller time picker.
 *
 * Combines the effortless readability and simplicity of the iOS alarm picker
 * with distinct fluid kinetic spring physics, dynamic optical scaling,
 * an ambient breathing aura lens, and Iris typography and warm amber theme.
 */
@Composable
fun BreatheDialTimePicker(
    hour: Int,
    minute: Int,
    use24Hour: Boolean,
    onTimeChange: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    FluidTimePicker(
        hour = hour,
        minute = minute,
        use24Hour = use24Hour,
        onTimeChange = onTimeChange,
        modifier = modifier,
    )
}

/**
 * Modern fluid time picker with kinetic vertical roller columns.
 */
@Composable
fun FluidTimePicker(
    hour: Int,
    minute: Int,
    use24Hour: Boolean,
    onTimeChange: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val primaryColor = MaterialTheme.colorScheme.primary

    // Always keep track of latest props to prevent closures capturing stale hours/minutes
    val currentHour by rememberUpdatedState(hour)
    val currentMinute by rememberUpdatedState(minute)
    val currentOnTimeChange by rememberUpdatedState(onTimeChange)

    // Ambient breathing pulse for the center aura lens and separator
    val infiniteTransition = rememberInfiniteTransition(label = "breathePulse")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseGlow",
    )

    val itemHeight = 44.dp
    val pickerHeight = itemHeight * 4.2f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("dial_time_picker")
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ==========================================
        // 1. FLUID ROLLER CONTAINER WITH AURA LENS
        // ==========================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(pickerHeight)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(24.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Central Fluid Aura Lens (Floating Selection Pill)
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(itemHeight + 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                primaryColor.copy(alpha = 0.08f * pulseGlow),
                                primaryColor.copy(alpha = 0.16f * pulseGlow),
                                primaryColor.copy(alpha = 0.08f * pulseGlow),
                            ),
                        ),
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(
                            listOf(
                                primaryColor.copy(alpha = 0.3f),
                                primaryColor.copy(alpha = 0.75f * pulseGlow),
                                primaryColor.copy(alpha = 0.3f),
                            ),
                        ),
                        shape = RoundedCornerShape(16.dp),
                    ),
            )

            // The Fluid Roller Columns
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (use24Hour) {
                    // Hours (00..23)
                    FluidRollerColumn(
                        value = currentHour,
                        modulus = 24,
                        startAtZero = true,
                        itemHeight = itemHeight,
                        columnWidth = 84.dp,
                        primaryColor = primaryColor,
                        testTag = "roller_hour",
                        onValueSelected = { newHour ->
                            currentOnTimeChange(newHour, currentMinute)
                        },
                    )

                    FluidColonSeparator(
                        pulseGlow = pulseGlow,
                        primaryColor = primaryColor,
                    )

                    // Minutes (00..59)
                    FluidRollerColumn(
                        value = currentMinute,
                        modulus = 60,
                        startAtZero = true,
                        itemHeight = itemHeight,
                        columnWidth = 84.dp,
                        primaryColor = primaryColor,
                        testTag = "roller_minute",
                        onValueSelected = { newMin ->
                            currentOnTimeChange(currentHour, newMin)
                        },
                    )
                } else {
                    val hour12 = to12Hour(currentHour)
                    val isPm = isPm(currentHour)

                    // Hours (1..12)
                    FluidRollerColumn(
                        value = hour12,
                        modulus = 12,
                        startAtZero = false,
                        itemHeight = itemHeight,
                        columnWidth = 72.dp,
                        primaryColor = primaryColor,
                        testTag = "roller_hour",
                        onValueSelected = { newHour12 ->
                            val latestIsPm = isPm(currentHour)
                            val new24 = to24Hour(newHour12, latestIsPm)
                            currentOnTimeChange(new24, currentMinute)
                        },
                    )

                    FluidColonSeparator(
                        pulseGlow = pulseGlow,
                        primaryColor = primaryColor,
                    )

                    // Minutes (00..59)
                    FluidRollerColumn(
                        value = currentMinute,
                        modulus = 60,
                        startAtZero = true,
                        itemHeight = itemHeight,
                        columnWidth = 72.dp,
                        primaryColor = primaryColor,
                        testTag = "roller_minute",
                        onValueSelected = { newMin ->
                            currentOnTimeChange(currentHour, newMin)
                        },
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    // Fluid AM / PM Toggle Pill
                    FluidAmPmToggle(
                        isPm = isPm,
                        itemHeight = itemHeight,
                        primaryColor = primaryColor,
                        onToggle = { newIsPm ->
                            val current12 = to12Hour(currentHour)
                            val new24 = to24Hour(current12, newIsPm)
                            currentOnTimeChange(new24, currentMinute)
                        },
                    )
                }
            }

            // Top and Bottom Soft Fade Masks (Fluid depth falloff)
            FluidGradientScrims(height = pickerHeight)
        }

        // ==========================================
        // 2. LIVE COUNTDOWN & QUICK ADJUSTMENT CHIPS
        // ==========================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Live "Rings in X hr Y min" status pill
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    primaryColor.copy(alpha = 0.3f),
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(primaryColor.copy(alpha = pulseGlow)),
                    )
                    AnimatedContent(
                        targetState = formatCountdownUntil(hour, minute),
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(150)) },
                        label = "countdownText",
                    ) { text ->
                        Text(
                            text = text,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // Quick Micro-Nudge Action Chips
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                QuickNudgeChip(
                    text = "-15m",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val total = (currentHour * 60 + currentMinute - 15 + 1440) % 1440
                        currentOnTimeChange(total / 60, total % 60)
                    },
                )
                QuickNudgeChip(
                    text = "+15m",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val total = (currentHour * 60 + currentMinute + 15) % 1440
                        currentOnTimeChange(total / 60, total % 60)
                    },
                )
                QuickNudgeChip(
                    text = "+1h",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentOnTimeChange((currentHour + 1) % 24, currentMinute)
                    },
                )
                QuickNudgeChip(
                    text = ":00",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val targetHour = if (currentMinute == 0) (currentHour + 1) % 24 else currentHour
                        currentOnTimeChange(targetHour, 0)
                    },
                )
            }
        }
    }
}

/**
 * Individual kinetic vertical roller column with momentum drag,
 * elastic magnetic snap, and organic scaling.
 */
@Composable
private fun FluidRollerColumn(
    value: Int,
    modulus: Int,
    startAtZero: Boolean,
    itemHeight: Dp,
    columnWidth: Dp,
    primaryColor: Color,
    testTag: String,
    onValueSelected: (Int) -> Unit,
) {
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.toPx() }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val targetIndex = if (startAtZero) value else (value - 1)
    val animOffset = remember { Animatable(targetIndex.toFloat()) }
    val velocityTracker = remember { VelocityTracker() }

    var isInteracting by remember { mutableStateOf(false) }
    var lastReportedValue by remember { mutableIntStateOf(value) }
    var lastHapticTick by remember { mutableIntStateOf(targetIndex) }
    val updatedOnValueSelected by rememberUpdatedState(onValueSelected)

    // Synchronize if external state changes (e.g. from nudge chips or presets)
    LaunchedEffect(value, modulus, startAtZero) {
        if (value != lastReportedValue && !isInteracting) {
            lastReportedValue = value
            val target = if (startAtZero) value else (value - 1)
            val shortest = findShortestOffset(animOffset.value, target, modulus)
            animOffset.animateTo(
                targetValue = shortest,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            )
            lastHapticTick = shortest.roundToInt()
        }
    }

    Box(
        modifier = Modifier
            .width(columnWidth)
            .fillMaxSize()
            .testTag(testTag)
            .pointerInput(modulus, startAtZero) {
                detectVerticalDragGestures(
                    onDragStart = {
                        isInteracting = true
                        velocityTracker.resetTracking()
                        scope.launch { animOffset.stop() }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        val deltaUnits = -dragAmount / itemHeightPx
                        scope.launch {
                            animOffset.snapTo(animOffset.value + deltaUnits)
                            val currentInt = animOffset.value.roundToInt()
                            if (currentInt != lastHapticTick) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                lastHapticTick = currentInt
                                val resolved = if (startAtZero) {
                                    ((currentInt % modulus) + modulus) % modulus
                                } else {
                                    ((currentInt % modulus) + modulus) % modulus + 1
                                }
                                lastReportedValue = resolved
                                updatedOnValueSelected(resolved)
                            }
                        }
                    },
                    onDragEnd = {
                        val velocity = velocityTracker.calculateVelocity().y
                        val velocityUnits = -velocity / itemHeightPx
                        // Momentum fling target with clean magnetic snap (no bouncing)
                        val flingUnits = (velocityUnits * 0.12f).coerceIn(-12f, 12f)
                        val target = (animOffset.value + flingUnits).roundToInt().toFloat()
                        scope.launch {
                            animOffset.animateTo(
                                targetValue = target,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium,
                                ),
                            )
                            val finalInt = animOffset.value.roundToInt()
                            val resolved = if (startAtZero) {
                                ((finalInt % modulus) + modulus) % modulus
                            } else {
                                ((finalInt % modulus) + modulus) % modulus + 1
                            }
                            lastReportedValue = resolved
                            lastHapticTick = finalInt
                            updatedOnValueSelected(resolved)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            isInteracting = false
                        }
                    },
                    onDragCancel = {
                        val snapTarget = animOffset.value.roundToInt().toFloat()
                        scope.launch {
                            animOffset.animateTo(
                                targetValue = snapTarget,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium,
                                ),
                            )
                            isInteracting = false
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val current = animOffset.value
        val centerIndex = current.roundToInt()

        // Display items from -2 to +2 (5 visible numbers)
        for (delta in -2..2) {
            val itemRawIndex = centerIndex + delta
            val diff = itemRawIndex - current

            if (abs(diff) <= 2.2f) {
                val yOffset = with(density) { (diff * itemHeightPx).toDp() }
                val absDiff = abs(diff)

                // Organic cosine scale and opacity falloff
                val scale = (1.18f - 0.28f * (absDiff / 2.2f)).coerceIn(0.72f, 1.18f)
                val alpha = (1.0f - 0.72f * (absDiff / 2.2f).pow(1.2f)).coerceIn(0.25f, 1.0f)

                val displayValue = if (startAtZero) {
                    ((itemRawIndex % modulus) + modulus) % modulus
                } else {
                    ((itemRawIndex % modulus) + modulus) % modulus + 1
                }
                val formatted = displayValue.toString().padStart(2, '0')
                val isSelected = absDiff < 0.35f

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .graphicsLayer {
                            translationY = yOffset.toPx()
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        }
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) {
                            if (!isSelected && !isInteracting) {
                                scope.launch {
                                    isInteracting = true
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    lastReportedValue = displayValue
                                    lastHapticTick = itemRawIndex
                                    updatedOnValueSelected(displayValue)
                                    animOffset.animateTo(
                                        targetValue = itemRawIndex.toFloat(),
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMedium,
                                        ),
                                    )
                                    isInteracting = false
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = formatted,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                            letterSpacing = 1.sp,
                        ),
                        color = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * Animated breathing colon separator between hours and minutes.
 */
@Composable
private fun FluidColonSeparator(
    pulseGlow: Float,
    primaryColor: Color,
) {
    Column(
        modifier = Modifier
            .width(20.dp)
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(primaryColor.copy(alpha = pulseGlow)),
        )
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(primaryColor.copy(alpha = pulseGlow)),
        )
    }
}

/**
 * Modern fluid AM/PM toggle pill with sliding spring indicator.
 */
@Composable
private fun FluidAmPmToggle(
    isPm: Boolean,
    itemHeight: Dp,
    primaryColor: Color,
    onToggle: (Boolean) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val pillHeight = itemHeight * 2.2f
    val pillWidth = 54.dp

    Box(
        modifier = Modifier
            .width(pillWidth)
            .height(pillHeight)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                RoundedCornerShape(18.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // AM Button
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(itemHeight * 0.95f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (!isPm) primaryColor else Color.Transparent)
                    .clickable {
                        if (isPm) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggle(false)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "AM",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                    ),
                    color = if (!isPm) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // PM Button
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(itemHeight * 0.95f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isPm) primaryColor else Color.Transparent)
                    .clickable {
                        if (!isPm) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggle(true)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "PM",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                    ),
                    color = if (isPm) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Soft gradient scrims at top and bottom to create an organic depth falloff.
 */
@Composable
private fun FluidGradientScrims(height: Dp) {
    val backgroundColor = MaterialTheme.colorScheme.background
    Box(modifier = Modifier.fillMaxSize()) {
        // Top fade
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height * 0.28f)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            backgroundColor.copy(alpha = 0.85f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        // Bottom fade
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height * 0.28f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            backgroundColor.copy(alpha = 0.85f),
                        ),
                    ),
                ),
        )
    }
}

/**
 * Compact quick nudge chip (+15m, -15m, +1h, :00).
 */
@Composable
private fun QuickNudgeChip(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Computes shortest cyclic distance to target index to prevent multiple full rotations.
 */
private fun findShortestOffset(currentOffset: Float, targetIndex: Int, modulus: Int): Float {
    val currentRounded = currentOffset.roundToInt()
    val currentMod = ((currentRounded % modulus) + modulus) % modulus
    var diff = targetIndex - currentMod
    if (diff > modulus / 2) diff -= modulus
    if (diff < -modulus / 2) diff += modulus
    return (currentRounded + diff).toFloat()
}

/**
 * Friendly countdown to next alarm fire time.
 */
private fun formatCountdownUntil(hour: Int, minute: Int): String {
    val now = LocalTime.now()
    val target = LocalTime.of(hour, minute)
    val nowMinutes = now.hour * 60 + now.minute
    val targetMinutes = target.hour * 60 + target.minute
    val diff = (targetMinutes - nowMinutes + 1440) % 1440
    val hours = diff / 60
    val minutes = diff % 60

    return when {
        diff == 0 -> "Rings in 24 hours"
        hours == 0 -> "Rings in $minutes min"
        minutes == 0 -> "Rings in $hours hr"
        else -> "Rings in $hours hr $minutes min"
    }
}
