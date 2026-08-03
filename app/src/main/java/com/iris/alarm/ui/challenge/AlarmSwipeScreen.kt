package com.iris.alarm.ui.challenge

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.iris.alarm.domain.model.Alarm
import com.iris.alarm.ui.components.ClockText
import com.iris.alarm.ui.components.formatClock
import com.iris.alarm.ui.theme.IrisTheme
import com.iris.alarm.ui.theme.IrisType
import java.time.LocalTime
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * What a ringing alarm shows first: the time on a card you push aside.
 *
 * Left snoozes, right goes on to the challenge. Two targets sit behind the card
 * and grow as it approaches them, so the commitment is legible before the finger
 * lifts. Deliberately a drag rather than a tap — half asleep a button is easy to
 * hit by accident, a deliberate push across the screen is not.
 */
@Composable
fun AlarmSwipeScreen(
    alarm: Alarm?,
    use24Hour: Boolean,
    isWakeCheck: Boolean,
    snoozeMinutes: Int,
    onSnooze: () -> Unit,
    onProceedToChallenge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Callbacks are read at drag end, which can be long after the gesture
    // handler was installed, so they must not be captured stale.
    val currentOnSnooze by rememberUpdatedState(onSnooze)
    val currentOnProceed by rememberUpdatedState(onProceedToChallenge)

    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val snoozeEnabled = snoozeMinutes > 0

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val commitDistance = widthPx * COMMIT_FRACTION
        val exitDistance = widthPx * 1.2f

        // Signed 0..1 towards a decision: negative snooze, positive stop.
        val progress = (offset.value / commitDistance).coerceIn(-1f, 1f)

        val breathe by rememberInfiniteTransition(label = "breathe").animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "breatheAlpha",
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (isWakeCheck) "WAKE CHECK" else "ALARM",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            Box(contentAlignment = Alignment.Center) {
                // The two decisions sit behind the card and swell as it nears them.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SwipeTarget(
                        icon = Icons.Rounded.Snooze,
                        label = if (snoozeEnabled) "SNOOZE $snoozeMinutes" else "SNOOZE OFF",
                        colour = MaterialTheme.colorScheme.primary,
                        // Only the side being approached reacts.
                        emphasis = if (snoozeEnabled) (-progress).coerceAtLeast(0f) else 0f,
                        idleAlpha = if (snoozeEnabled) breathe * 0.5f else 0.2f,
                    )
                    SwipeTarget(
                        icon = Icons.Rounded.Close,
                        label = "STOP",
                        colour = MaterialTheme.colorScheme.secondary,
                        emphasis = progress.coerceAtLeast(0f),
                        idleAlpha = breathe * 0.5f,
                    )
                }

                AlarmCard(
                    alarm = alarm,
                    use24Hour = use24Hour,
                    offset = offset.value,
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { delta ->
                                scope.launch {
                                    val next = offset.value + delta
                                    // Dragging towards a disabled snooze goes
                                    // heavy rather than being ignored outright.
                                    val damped = if (next < 0f && !snoozeEnabled) {
                                        next * 0.2f
                                    } else {
                                        next
                                    }
                                    offset.snapTo(damped)
                                }
                            },
                            // Runs at drag end and reads live state, which is what
                            // the previous version got wrong: it tested a progress
                            // value captured when the handler was installed, so it
                            // was always zero and the swipe never committed.
                            onDragStopped = { velocity ->
                                val distance = offset.value
                                val flung = abs(velocity) > FLING_VELOCITY &&
                                    // A fling has to agree with the direction
                                    // already travelled, or a flick back counts.
                                    velocity * distance > 0f
                                val committed = abs(distance) >= commitDistance || flung

                                when {
                                    committed && distance > 0f -> {
                                        offset.animateTo(exitDistance, tween(220))
                                        currentOnProceed()
                                    }

                                    committed && snoozeEnabled -> {
                                        offset.animateTo(-exitDistance, tween(220))
                                        currentOnSnooze()
                                    }

                                    else -> offset.animateTo(0f, tween(280))
                                }
                            },
                        ),
                )
            }

            Text(
                text = if (snoozeEnabled) {
                    "SWIPE LEFT TO SNOOZE   ·   RIGHT TO STOP"
                } else {
                    "SWIPE RIGHT TO STOP"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = breathe },
            )
        }
    }
}

@Composable
private fun AlarmCard(
    alarm: Alarm?,
    use24Hour: Boolean,
    offset: Float,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val clock: ClockText = formatClock(LocalTime.now(), use24Hour)
    val edge = when {
        progress > 0f -> MaterialTheme.colorScheme.secondary
        progress < 0f -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

    Column(
        modifier = modifier
            .graphicsLayer {
                translationX = offset
                // A lean, not a spin: the card is being pushed aside, not thrown.
                rotationZ = progress * MAX_TILT_DEGREES
                // It lifts very slightly out of the page as it moves.
                scaleX = 1f + abs(progress) * 0.02f
                scaleY = 1f + abs(progress) * 0.02f
            }
            .clip(RoundedCornerShape(36.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                width = if (progress == 0f) 1.dp else 2.dp,
                color = edge.copy(alpha = (0.25f + abs(progress) * 0.75f)),
                shape = RoundedCornerShape(36.dp),
            )
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = clock.digits,
            style = IrisType.ClockCompact,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        clock.suffix?.let { suffix ->
            Text(
                text = suffix,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        alarm?.label?.takeIf { it.isNotBlank() }?.let { label ->
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        Text(
            text = alarm?.challenge?.displayName?.uppercase() ?: "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}

/** One side of the decision: a ringed icon that fills in as the card nears it. */
@Composable
private fun SwipeTarget(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    colour: Color,
    emphasis: Float,
    idleAlpha: Float,
) {
    val alpha = (idleAlpha + emphasis * (1f - idleAlpha)).coerceIn(0f, 1f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.graphicsLayer {
            this.alpha = alpha
            val scale = 1f + emphasis * 0.22f
            scaleX = scale
            scaleY = scale
        },
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                // Empty until it is being chosen, then it fills.
                .background(colour.copy(alpha = emphasis * 0.9f))
                .border(2.dp, colour, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (emphasis > 0.5f) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    colour
                },
                modifier = Modifier.size(26.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colour,
            textAlign = TextAlign.Center,
        )
    }
}

/** Fraction of the screen width the card must travel to commit. */
private const val COMMIT_FRACTION = 0.32f
private const val FLING_VELOCITY = 700f
private const val MAX_TILT_DEGREES = 7f

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun AlarmSwipePreview() {
    IrisTheme(darkTheme = true) {
        AlarmSwipeScreen(
            alarm = Alarm(hour = 6, minute = 30, label = "Gym"),
            use24Hour = true,
            isWakeCheck = false,
            snoozeMinutes = 9,
            onSnooze = {},
            onProceedToChallenge = {},
        )
    }
}
