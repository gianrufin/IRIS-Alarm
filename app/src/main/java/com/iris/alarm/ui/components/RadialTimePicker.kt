package com.iris.alarm.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A clock-face time picker: minutes on the outer ring, hours on the inner one,
 * with the time and AM/PM in the middle.
 *
 * Both rings are draggable *and* tappable. Dragging scrubs continuously — the
 * minute ring moves a minute at a time rather than snapping to fives, so exact
 * times are reachable without a second control — and the ring you grabbed keeps
 * the gesture until you lift, so a sloppy arc cannot jump to the other ring
 * halfway round.
 */
@Composable
fun RadialTimePicker(
    hour: Int,
    minute: Int,
    use24Hour: Boolean,
    onTimeChange: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onChange by rememberUpdatedState(onTimeChange)
    val density = LocalDensity.current

    // Which ring the current gesture owns; null between gestures.
    var activeRing by remember { mutableStateOf<Ring?>(null) }

    val hourCount = if (use24Hour) 24 else 12
    val displayHour = if (use24Hour) hour else to12Hour(hour)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f),
    ) {
        val diameter = with(density) { minOf(maxWidth, maxHeight).toPx() }
        val centre = Offset(diameter / 2f, diameter / 2f)
        val minuteRadius = diameter * MINUTE_RING
        val hourRadius = diameter * HOUR_RING
        val ringBoundary = (minuteRadius + hourRadius) / 2f

        fun turnsFrom(position: Offset): Float =
            RadialMath.turns(position.x - centre.x, position.y - centre.y)

        fun apply(ring: Ring, position: Offset) {
            val turns = turnsFrom(position)
            when (ring) {
                Ring.MINUTE -> onChange(hour, RadialMath.toMinute(turns))
                Ring.HOUR -> {
                    val newHour = if (use24Hour) {
                        RadialMath.toHour24(turns)
                    } else {
                        // The ring reads 12 at the top, then 1..11 clockwise, and
                        // the meridiem the user already chose is preserved.
                        to24Hour(RadialMath.toHour12(turns), isPm(hour))
                    }
                    onChange(newHour, minute)
                }
            }
        }

        fun ringAt(position: Offset): Ring? {
            val distance = hypot(position.x - centre.x, position.y - centre.y)
            return when {
                // Inside the central readout: not a ring, so a tap on the
                // AM/PM pill is never read as a time change.
                distance < hourRadius - RING_TOLERANCE * diameter -> null
                distance < ringBoundary -> Ring.HOUR
                distance < minuteRadius + RING_TOLERANCE * diameter * 2f -> Ring.MINUTE
                else -> null
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(hourCount, use24Hour, diameter) {
                    detectTapGestures { position ->
                        ringAt(position)?.let { ring -> apply(ring, position) }
                    }
                }
                .pointerInput(hourCount, use24Hour, diameter) {
                    detectDragGestures(
                        onDragStart = { position -> activeRing = ringAt(position) },
                        onDragEnd = { activeRing = null },
                        onDragCancel = { activeRing = null },
                    ) { change, _ ->
                        activeRing?.let { ring -> apply(ring, change.position) }
                        change.consume()
                    }
                },
        ) {
            Dial(
                minuteTurns = minute / 60f,
                hourTurns = (if (use24Hour) hour else displayHour % 12) / hourCount.toFloat(),
                activeRing = activeRing,
            )

            RingLabels(
                count = if (use24Hour) 24 else 12,
                labelEvery = if (use24Hour) 2 else 1,
                radiusFraction = HOUR_RING,
                selected = if (use24Hour) hour else displayHour % 12,
                format = { index ->
                    if (use24Hour) index.toString().padStart(2, '0') else (index.takeIf { it > 0 } ?: 12).toString()
                },
            )

            RingLabels(
                count = 60,
                labelEvery = 5,
                radiusFraction = MINUTE_RING,
                selected = minute,
                format = { index -> index.toString().padStart(2, '0') },
            )

            CentreReadout(
                hour = hour,
                minute = minute,
                use24Hour = use24Hour,
                onMeridiem = { pm -> onChange(to24Hour(to12Hour(hour), pm), minute) },
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

private enum class Ring { HOUR, MINUTE }

/** The two tracks and the hands that point at the current time. */
@Composable
private fun Dial(minuteTurns: Float, hourTurns: Float, activeRing: Ring?) {
    val track = MaterialTheme.colorScheme.outline
    val hourColour by animateColorAsState(
        targetValue = if (activeRing == Ring.HOUR) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        },
        label = "hourHand",
    )
    val minuteColour by animateColorAsState(
        targetValue = if (activeRing == Ring.MINUTE) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "minuteHand",
    )

    // Springs rather than tweens: a knob you let go of should settle, not glide.
    val animatedMinute by animateFloatAsState(
        targetValue = minuteTurns,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "minuteTurns",
    )
    val animatedHour by animateFloatAsState(
        targetValue = hourTurns,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "hourTurns",
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val diameter = size.minDimension
        val centre = Offset(size.width / 2f, size.height / 2f)
        val minuteRadius = diameter * MINUTE_RING
        val hourRadius = diameter * HOUR_RING

        // The dial plate.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(track.copy(alpha = 0.18f), Color.Transparent),
                center = centre,
                radius = minuteRadius,
            ),
            radius = minuteRadius,
            center = centre,
        )
        drawCircle(
            color = track.copy(alpha = 0.5f),
            radius = minuteRadius,
            center = centre,
            style = Stroke(width = 1.dp.toPx()),
        )
        drawCircle(
            color = track.copy(alpha = 0.5f),
            radius = hourRadius,
            center = centre,
            style = Stroke(width = 1.dp.toPx()),
        )

        fun handEnd(turns: Float, radius: Float): Offset {
            val radians = (turns * 2f * PI - PI / 2f).toFloat()
            return Offset(
                centre.x + cos(radians) * radius,
                centre.y + sin(radians) * radius,
            )
        }

        // Hands, drawn from the centre out to the selected position.
        drawLine(
            color = minuteColour,
            start = centre,
            end = handEnd(animatedMinute, minuteRadius),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = hourColour,
            start = centre,
            end = handEnd(animatedHour, hourRadius),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round,
        )

        // Knobs at the ends, which is what the finger is really chasing.
        drawCircle(color = minuteColour, radius = 7.dp.toPx(), center = handEnd(animatedMinute, minuteRadius))
        drawCircle(color = hourColour, radius = 11.dp.toPx(), center = handEnd(animatedHour, hourRadius))
        drawCircle(color = hourColour, radius = 3.dp.toPx(), center = centre)
    }
}

/** Numbers laid around a ring, positioned polar-wise rather than in a grid. */
@Composable
private fun RingLabels(
    count: Int,
    labelEvery: Int,
    radiusFraction: Float,
    selected: Int,
    format: (Int) -> String,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        for (index in 0 until count step labelEvery) {
            val turns = index / count.toFloat()
            val isSelected = index == selected ||
                // A minute between labels still lights the nearest one.
                (labelEvery > 1 && selected in index until index + labelEvery)

            Text(
                text = format(index),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                },
                modifier = Modifier.layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val diameter = minOf(constraints.maxWidth, constraints.maxHeight)
                    val radius = diameter * radiusFraction
                    val radians = (turns * 2f * PI - PI / 2f).toFloat()

                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(
                            x = (constraints.maxWidth / 2f + cos(radians) * radius -
                                placeable.width / 2f).roundToInt(),
                            y = (constraints.maxHeight / 2f + sin(radians) * radius -
                                placeable.height / 2f).roundToInt(),
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun CentreReadout(
    hour: Int,
    minute: Int,
    use24Hour: Boolean,
    onMeridiem: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clock = formatClock(hour, minute, use24Hour)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = clock.digits,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )

        if (!use24Hour) {
            MeridiemSwitch(isPm = isPm(hour), onChange = onMeridiem)
        }
    }
}

/** A two-position pill, so AM/PM is one tap and always visible. */
@Composable
private fun MeridiemSwitch(isPm: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
            .padding(3.dp),
    ) {
        listOf(false, true).forEach { pm ->
            val selected = pm == isPm
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    )
                    .clickable { onChange(pm) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = if (pm) "PM" else "AM",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

private const val MINUTE_RING = 0.44f
private const val HOUR_RING = 0.30f

/** Slack around each ring so a finger does not have to be surgical. */
private const val RING_TOLERANCE = 0.05f
