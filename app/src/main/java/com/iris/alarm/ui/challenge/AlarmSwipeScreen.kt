package com.iris.alarm.ui.challenge

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
 * What a ringing alarm shows first: the time, and a card you push aside.
 *
 * Left snoozes, right goes on to the challenge. The card tilts and follows the
 * finger, and the side you are heading towards lights up — the gesture is
 * borrowed from swipe-to-decide card decks, but kept quiet: no coloured overlay
 * flooding the screen, no stamp, just the card leaning and one label brightening.
 *
 * Deliberately a *drag*, not a tap. Half asleep, a button is easy to hit by
 * accident; a deliberate push across a third of the screen is not.
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
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val snoozeEnabled = snoozeMinutes > 0

    // Fraction of the way to a decision, signed: negative left, positive right.
    val travel = with(density) { SWIPE_TRAVEL.toPx() }
    val progress = (offset.value / travel).coerceIn(-1f, 1f)

    val hint by rememberInfiniteTransition(label = "swipeHint").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "hintAlpha",
    )

    fun settle(target: Float, onCommitted: () -> Unit) {
        scope.launch {
            offset.animateTo(target, tween(durationMillis = 220))
            onCommitted()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (isWakeCheck) "WAKE CHECK" else "ALARM",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            AlarmCard(
                alarm = alarm,
                use24Hour = use24Hour,
                offset = offset.value,
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(snoozeEnabled, travel) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val decided = abs(progress) >= COMMIT_FRACTION
                                when {
                                    decided && progress > 0f ->
                                        settle(travel * 2f, onProceedToChallenge)

                                    decided && snoozeEnabled ->
                                        settle(-travel * 2f, onSnooze)

                                    // Snooze off, or not far enough: spring back.
                                    else -> scope.launch {
                                        offset.animateTo(0f, tween(durationMillis = 260))
                                    }
                                }
                            },
                            onHorizontalDrag = { _, delta ->
                                scope.launch {
                                    val next = offset.value + delta
                                    // Dragging towards a disabled snooze gets
                                    // heavy rather than being ignored outright.
                                    val damped = if (next < 0f && !snoozeEnabled) {
                                        next * 0.25f
                                    } else {
                                        next
                                    }
                                    offset.snapTo(damped)
                                }
                            },
                        )
                    },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SwipeLabel(
                    text = if (snoozeEnabled) "◀  SNOOZE $snoozeMinutes" else "SNOOZE OFF",
                    active = progress < 0f && snoozeEnabled,
                    baseAlpha = if (snoozeEnabled) hint else 0.25f,
                    emphasis = -progress,
                )
                SwipeLabel(
                    text = "STOP  ▶",
                    active = progress > 0f,
                    baseAlpha = hint,
                    emphasis = progress,
                )
            }
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

    Column(
        modifier = modifier
            .graphicsLayer {
                translationX = offset
                // A gentle lean, not a spin: the card should read as being
                // pushed aside rather than thrown.
                rotationZ = progress * MAX_TILT_DEGREES
                alpha = 1f - abs(progress) * 0.25f
            }
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = clock.digits,
            style = IrisType.Clock,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        clock.suffix?.let { suffix ->
            Text(
                text = suffix,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        alarm?.label?.takeIf { it.isNotBlank() }?.let { label ->
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Text(
            text = alarm?.challenge?.displayName?.uppercase() ?: "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun SwipeLabel(text: String, active: Boolean, baseAlpha: Float, emphasis: Float) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.alpha(
            (baseAlpha + emphasis.coerceAtLeast(0f) * 0.6f).coerceIn(0f, 1f),
        ),
    )
}

private val SWIPE_TRAVEL = 140.dp
private const val COMMIT_FRACTION = 0.75f
private const val MAX_TILT_DEGREES = 6f

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
