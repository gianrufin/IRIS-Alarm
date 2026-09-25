package com.iris.alarm.ui.components

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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.StrokeCap
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
import kotlin.math.sin
import kotlinx.coroutines.launch

/**
 * Concept 2: The Kinetic 3D Drum Wheel Time Picker.
 *
 * Features:
 * - Vertical rolling drum wheels for Hours, Minutes, and AM/PM with 3D cylindrical projection.
 * - Hardware-accelerated perspective scaling, spherical arc translation, and dynamic rotationX.
 * - Center glowing aperture lens (viewfinder reticle) with circadian accent illumination.
 * - Inertial kinetic dragging with velocity tracking, fling decay, and magnetic spring detent snap.
 * - Haptic feedback ticks on each notched transition.
 * - Circadian atmosphere badge matching current selected hour (Morning, Daylight, Night).
 * - Live "Time Until Alarm" countdown pill with pulsing status indicator.
 * - Quick-adjustment action chips (+15m, +30m, +1h, Snap :00).
 */
@Composable
fun KineticDrumTimePicker(
    hour: Int,
    minute: Int,
    use24Hour: Boolean,
    onTimeChange: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val circadian = circadianThemeForHour(hour)
    val animatedPrimary by animateColorAsState(
        targetValue = circadian.primary,
        animationSpec = tween(400),
        label = "circadianPrimary",
    )
    val animatedSecondary by animateColorAsState(
        targetValue = circadian.secondary,
        animationSpec = tween(400),
        label = "circadianSecondary",
    )
    val haptic = LocalHapticFeedback.current

    // Pulsing indicator for live alarm countdown
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("kinetic_drum_time_picker")
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ==========================================
        // 1. CIRCADIAN ATMOSPHERE & COUNTDOWN HEADER
        // ==========================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Circadian pill (e.g. ☀️ Morning Horizon)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = animatedPrimary.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    animatedPrimary.copy(alpha = 0.4f),
                ),
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
                        color = animatedPrimary,
                    )
                }
            }

            // Live Time Remaining Countdown Pill
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
                    // Pulsing radar dot
                    Canvas(modifier = Modifier.size(7.dp)) {
                        drawCircle(
                            color = animatedPrimary.copy(alpha = pulseAlpha),
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
        // 2. HERO DIGITAL TIME DISPLAY
        // ==========================================
        val clock = formatClock(hour, minute, use24Hour)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Subtle ambient backdrop glow
            Canvas(
                modifier = Modifier
                    .size(width = 240.dp, height = 70.dp),
            ) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            animatedPrimary.copy(alpha = 0.22f),
                            Color.Transparent,
                        ),
                    ),
                    radius = size.width / 1.6f,
                )
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = clock.digits,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (clock.suffix != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        modifier = Modifier.padding(bottom = 8.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = animatedPrimary.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            animatedPrimary.copy(alpha = 0.5f),
                        ),
                    ) {
                        Text(
                            text = clock.suffix,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp,
                            ),
                            color = animatedPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        modifier = Modifier.padding(bottom = 8.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ) {
                        Text(
                            text = "24H",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ==========================================
        // 3. KINETIC 3D CYLINDER DRUM WHEEL CHAMBER
        // ==========================================
        val drumHeight = 220.dp
        val apertureHeight = 52.dp

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(drumHeight),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            ),
            tonalElevation = 2.dp,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                // Background subtle radial illumination
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                animatedPrimary.copy(alpha = 0.08f),
                                Color.Transparent,
                            ),
                        ),
                        radius = size.width / 2.2f,
                    )
                }

                // Center Aperture Viewfinder Lens
                ApertureLens(
                    height = apertureHeight,
                    accentColor = animatedPrimary,
                    secondaryColor = animatedSecondary,
                )

                // The 3D Drum Wheels
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (use24Hour) {
                        // Hour Drum (00..23)
                        CylindricalDrum(
                            value = hour,
                            modulus = 24,
                            startAtZero = true,
                            itemHeight = 46.dp,
                            drumWidth = 92.dp,
                            accentColor = animatedPrimary,
                            testTag = "drum_hour",
                            onValueSelected = { newHour ->
                                onTimeChange(newHour, minute)
                            },
                        )

                        // Glowing Colon Separator
                        ColonSeparator(accentColor = animatedPrimary)

                        // Minute Drum (00..59)
                        CylindricalDrum(
                            value = minute,
                            modulus = 60,
                            startAtZero = true,
                            itemHeight = 46.dp,
                            drumWidth = 92.dp,
                            accentColor = animatedPrimary,
                            testTag = "drum_minute",
                            onValueSelected = { newMin ->
                                onTimeChange(hour, newMin)
                            },
                        )
                    } else {
                        // 12-Hour Drum (01..12)
                        val displayHour = to12Hour(hour)
                        val pmState = isPm(hour)

                        CylindricalDrum(
                            value = displayHour,
                            modulus = 12,
                            startAtZero = false,
                            itemHeight = 46.dp,
                            drumWidth = 74.dp,
                            accentColor = animatedPrimary,
                            testTag = "drum_hour",
                            onValueSelected = { new12h ->
                                val new24h = to24Hour(new12h, pmState)
                                onTimeChange(new24h, minute)
                            },
                        )

                        // Glowing Colon Separator
                        ColonSeparator(accentColor = animatedPrimary)

                        // Minute Drum (00..59)
                        CylindricalDrum(
                            value = minute,
                            modulus = 60,
                            startAtZero = true,
                            itemHeight = 46.dp,
                            drumWidth = 74.dp,
                            accentColor = animatedPrimary,
                            testTag = "drum_minute",
                            onValueSelected = { newMin ->
                                onTimeChange(hour, newMin)
                            },
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        // AM/PM Kinetic Drum / Toggle
                        AmPmDrum(
                            isPm = pmState,
                            accentColor = animatedPrimary,
                            drumWidth = 64.dp,
                            itemHeight = 46.dp,
                            testTag = "drum_ampm",
                            onToggle = { newIsPm ->
                                val current12 = to12Hour(hour)
                                val new24h = to24Hour(current12, newIsPm)
                                onTimeChange(new24h, minute)
                            },
                        )
                    }
                }

                // Top & Bottom Depth Scrims (Hardware cylinder housing shadows)
                HousingShadowOverlay(drumHeight = drumHeight)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ==========================================
        // 4. QUICK-ADJUSTMENT ACTION CHIPS
        // ==========================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            QuickActionChip(
                label = "+15m",
                testTag = "quick_snap_15m",
                accentColor = animatedPrimary,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val total = hour * 60 + minute + 15
                    val newHour = (total / 60) % 24
                    val newMinute = total % 60
                    onTimeChange(newHour, newMinute)
                },
            )
            QuickActionChip(
                label = "+30m",
                testTag = "quick_snap_30m",
                accentColor = animatedPrimary,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val total = hour * 60 + minute + 30
                    val newHour = (total / 60) % 24
                    val newMinute = total % 60
                    onTimeChange(newHour, newMinute)
                },
            )
            QuickActionChip(
                label = "+1h",
                testTag = "quick_snap_1h",
                accentColor = animatedPrimary,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val newHour = (hour + 1) % 24
                    onTimeChange(newHour, minute)
                },
            )
            QuickActionChip(
                label = "Snap :00",
                testTag = "quick_snap_00",
                accentColor = animatedPrimary,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val newMinute = 0
                    val newHour = if (minute == 0) (hour + 1) % 24 else hour
                    onTimeChange(newHour, newMinute)
                },
            )
        }
    }
}

/**
 * Center illuminated aperture lens framing the active digits with reticle corners.
 */
@Composable
private fun ApertureLens(
    height: Dp,
    accentColor: Color,
    secondaryColor: Color,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(accentColor.copy(alpha = 0.08f))
            .border(
                1.dp,
                Brush.horizontalGradient(
                    listOf(
                        accentColor.copy(alpha = 0.6f),
                        secondaryColor.copy(alpha = 0.6f),
                        accentColor.copy(alpha = 0.6f),
                    ),
                ),
                RoundedCornerShape(12.dp),
            ),
    ) {
        // Viewfinder reticle brackets on left & right
        Canvas(modifier = Modifier.fillMaxSize()) {
            val bracketLen = 10.dp.toPx()
            val bracketStroke = 2.dp.toPx()
            val strokeColor = accentColor.copy(alpha = 0.85f)

            // Top-left bracket ┌
            drawLine(strokeColor, Offset(4f, 4f), Offset(4f + bracketLen, 4f), bracketStroke)
            drawLine(strokeColor, Offset(4f, 4f), Offset(4f, 4f + bracketLen), bracketStroke)

            // Top-right bracket ┐
            drawLine(strokeColor, Offset(size.width - 4f, 4f), Offset(size.width - 4f - bracketLen, 4f), bracketStroke)
            drawLine(strokeColor, Offset(size.width - 4f, 4f), Offset(size.width - 4f, 4f + bracketLen), bracketStroke)

            // Bottom-left bracket └
            drawLine(strokeColor, Offset(4f, size.height - 4f), Offset(4f + bracketLen, size.height - 4f), bracketStroke)
            drawLine(strokeColor, Offset(4f, size.height - 4f), Offset(4f, size.height - 4f - bracketLen), bracketStroke)

            // Bottom-right bracket ┘
            drawLine(strokeColor, Offset(size.width - 4f, size.height - 4f), Offset(size.width - 4f - bracketLen, size.height - 4f), bracketStroke)
            drawLine(strokeColor, Offset(size.width - 4f, size.height - 4f), Offset(size.width - 4f, size.height - 4f - bracketLen), bracketStroke)
        }
    }
}

/**
 * Luminous colon separator between Hour and Minute drums.
 */
@Composable
private fun ColonSeparator(accentColor: Color) {
    Box(
        modifier = Modifier
            .width(20.dp)
            .height(52.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = ":",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
            ),
            color = accentColor,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 3D Cylindrical Drum implementation with continuous inertial scrolling,
 * hardware perspective transformation, infinite modulo wrapping, and magnetic snap.
 */
@Composable
private fun CylindricalDrum(
    value: Int,
    modulus: Int,
    startAtZero: Boolean,
    itemHeight: Dp,
    drumWidth: Dp,
    accentColor: Color,
    testTag: String,
    onValueSelected: (Int) -> Unit,
) {
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.toPx() }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // Canonical internal index offset
    val initialOffset = remember(value) {
        if (startAtZero) value.toFloat() else (value - 1).toFloat()
    }
    val animOffset = remember { Animatable(initialOffset) }
    val currentTargetValue by rememberUpdatedState(value)

    // Sync if external target changes via buttons or presets
    LaunchedEffect(value, modulus, startAtZero) {
        val targetIndex = if (startAtZero) value else (value - 1)
        val currentMod = if (startAtZero) {
            ((animOffset.value.roundToInt() % modulus) + modulus) % modulus
        } else {
            ((animOffset.value.roundToInt() % modulus) + modulus) % modulus
        }
        if (currentMod != targetIndex) {
            // Find shortest directional path to target to avoid spinning around the world
            val closest = findShortestTargetOffset(
                currentOffset = animOffset.value,
                targetModulo = targetIndex,
                modulus = modulus,
            )
            animOffset.animateTo(
                targetValue = closest,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
    }

    var lastHapticTick by remember { mutableIntStateOf(animOffset.value.roundToInt()) }
    val velocityTracker = remember { VelocityTracker() }

    Box(
        modifier = Modifier
            .width(drumWidth)
            .fillMaxSize()
            .testTag(testTag)
            .pointerInput(modulus, startAtZero) {
                detectVerticalDragGestures(
                    onDragStart = {
                        scope.launch { animOffset.stop() }
                        velocityTracker.resetTracking()
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
                                onValueSelected(resolved)
                            }
                        }
                    },
                    onDragEnd = {
                        val velocity = velocityTracker.calculateVelocity().y
                        val velocityUnits = -velocity / itemHeightPx
                        val flingTarget = (animOffset.value + velocityUnits * 0.16f).roundToInt().toFloat()
                        scope.launch {
                            animOffset.animateTo(
                                targetValue = flingTarget,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessLow,
                                ),
                            )
                            val finalInt = animOffset.value.roundToInt()
                            val resolved = if (startAtZero) {
                                ((finalInt % modulus) + modulus) % modulus
                            } else {
                                ((finalInt % modulus) + modulus) % modulus + 1
                            }
                            onValueSelected(resolved)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                    onDragCancel = {
                        val snapTarget = animOffset.value.roundToInt().toFloat()
                        scope.launch {
                            animOffset.animateTo(
                                targetValue = snapTarget,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            )
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val current = animOffset.value
        val centerIndex = current.roundToInt()

        // Render 7 items (-3..+3) around the center aperture
        for (delta in -3..3) {
            val itemRawIndex = centerIndex + delta
            val diff = itemRawIndex - current

            // Only calculate items that are physically on the visible half of the cylinder
            if (abs(diff) <= 3.2f) {
                val stepAngleDeg = 26f
                val angleRad = (diff * stepAngleDeg) * (PI.toFloat() / 180f)
                val angleDeg = diff * stepAngleDeg

                // Cylindrical 3D perspective projection formulas:
                // Y offset foreshortened along circular arc
                val radiusPx = itemHeightPx / sin(stepAngleDeg * PI.toFloat() / 180f)
                val yOffsetPx = radiusPx * sin(angleRad)

                // Depth scaling and alpha fade
                val cosVal = cos(angleRad).coerceIn(0.1f, 1f)
                val scale = cosVal.pow(0.75f).coerceIn(0.55f, 1f)
                val alpha = cosVal.pow(2.2f).coerceIn(0.12f, 1f)
                val rotationX = -angleDeg

                // Value computation with modulo wrapping
                val itemValue = if (startAtZero) {
                    ((itemRawIndex % modulus) + modulus) % modulus
                } else {
                    ((itemRawIndex % modulus) + modulus) % modulus + 1
                }

                val text = itemValue.toString().padStart(2, '0')
                val proximity = (1f - (abs(diff) / 0.85f)).coerceIn(0f, 1f)
                val isCenter = abs(diff) < 0.45f

                val textColor = if (isCenter) {
                    accentColor
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                }

                val fontWeight = if (isCenter) FontWeight.ExtraBold else FontWeight.Medium

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .graphicsLayer {
                            translationY = yOffsetPx
                            scaleX = scale
                            scaleY = scale
                            this.rotationX = rotationX
                            this.alpha = alpha
                            cameraDistance = 12f * density.density
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            scope.launch {
                                animOffset.animateTo(
                                    targetValue = itemRawIndex.toFloat(),
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                                )
                                onValueSelected(itemValue)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = fontWeight,
                            fontSize = 28.sp,
                            letterSpacing = 1.sp,
                        ),
                        color = textColor,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * 12-Hour AM/PM rolling drum and toggle column with matching 3D cylinder perspective.
 */
@Composable
private fun AmPmDrum(
    isPm: Boolean,
    accentColor: Color,
    drumWidth: Dp,
    itemHeight: Dp,
    testTag: String,
    onToggle: (Boolean) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.toPx() }
    val targetOffset = if (isPm) 1f else 0f
    val animOffset = remember { Animatable(targetOffset) }

    LaunchedEffect(isPm) {
        if (animOffset.targetValue != targetOffset) {
            animOffset.animateTo(
                targetValue = targetOffset,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
    }

    Box(
        modifier = Modifier
            .width(drumWidth)
            .fillMaxSize()
            .testTag(testTag)
            .pointerInput(isPm) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        val newPm = animOffset.value > 0.5f
                        if (newPm != isPm) {
                            onToggle(newPm)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        } else {
                            scope.launch {
                                animOffset.animateTo(if (isPm) 1f else 0f)
                            }
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        val delta = -dragAmount / 80f
                        scope.launch {
                            animOffset.snapTo((animOffset.value + delta).coerceIn(0f, 1f))
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val current = animOffset.value

        // AM Item
        val amDiff = 0f - current
        val amAngle = amDiff * 30f
        val amScale = cos(amAngle * PI.toFloat() / 180f).coerceIn(0.6f, 1f)
        val amAlpha = cos(amAngle * PI.toFloat() / 180f).pow(2f).coerceIn(0.2f, 1f)

        Box(
            modifier = Modifier
                .height(itemHeight)
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = amDiff * itemHeightPx
                    scaleX = amScale
                    scaleY = amScale
                    rotationX = -amAngle
                    alpha = amAlpha
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    if (isPm) {
                        onToggle(false)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "AM",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = if (!isPm) FontWeight.Black else FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                ),
                color = if (!isPm) accentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }

        // PM Item
        val pmDiff = 1f - current
        val pmAngle = pmDiff * 30f
        val pmScale = cos(pmAngle * PI.toFloat() / 180f).coerceIn(0.6f, 1f)
        val pmAlpha = cos(pmAngle * PI.toFloat() / 180f).pow(2f).coerceIn(0.2f, 1f)

        Box(
            modifier = Modifier
                .height(itemHeight)
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = pmDiff * itemHeightPx
                    scaleX = pmScale
                    scaleY = pmScale
                    rotationX = -pmAngle
                    alpha = pmAlpha
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    if (!isPm) {
                        onToggle(true)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "PM",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = if (isPm) FontWeight.Black else FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                ),
                color = if (isPm) accentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}

/**
 * Top and bottom depth scrim overlays to simulate hardware enclosure shadows.
 */
@Composable
private fun HousingShadowOverlay(drumHeight: Dp) {
    val scrimHeight = 52.dp
    val surfaceColor = MaterialTheme.colorScheme.surface

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // Top fade into cylinder shadow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(scrimHeight)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            surfaceColor,
                            surfaceColor.copy(alpha = 0.7f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        // Bottom fade into cylinder shadow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(scrimHeight)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            surfaceColor.copy(alpha = 0.7f),
                            surfaceColor,
                        ),
                    ),
                ),
        )
    }
}

/**
 * Quick adjustment action chip with tactile click.
 */
@Composable
private fun QuickActionChip(
    label: String,
    testTag: String,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            accentColor.copy(alpha = 0.3f),
        ),
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

/**
 * Finds the shortest directional offset modulo N to prevent unnecessary multi-revolution spinning.
 */
private fun findShortestTargetOffset(
    currentOffset: Float,
    targetModulo: Int,
    modulus: Int,
): Float {
    val currentInt = currentOffset.roundToInt()
    var bestMatch = currentInt.toFloat()
    var minDistance = Float.MAX_VALUE

    for (k in -2..2) {
        val candidate = ((targetModulo % modulus) + modulus) % modulus + k * modulus
        val dist = abs(candidate - currentOffset)
        if (dist < minDistance) {
            minDistance = dist
            bestMatch = candidate.toFloat()
        }
    }
    return bestMatch
}
