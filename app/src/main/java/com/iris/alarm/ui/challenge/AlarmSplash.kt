package com.iris.alarm.ui.challenge

import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.iris.alarm.domain.model.Alarm
import com.iris.alarm.domain.model.VisionChallenge
import com.iris.alarm.ui.components.ClockText
import com.iris.alarm.ui.components.challengeIcon
import com.iris.alarm.ui.components.formatClock
import com.iris.alarm.ui.editor.description
import com.iris.alarm.ui.theme.IrisTheme
import com.iris.alarm.ui.theme.IrisType
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/**
 * The bridge between "stop" and the challenge itself.
 *
 * It answers the three questions someone has three seconds after being woken —
 * *what woke me*, *what time is it*, and *what am I about to have to do* — and
 * then gets out of the way. The last one is why the challenge is previewed here
 * rather than appearing unannounced: a camera viewfinder with no preamble reads
 * as the phone malfunctioning at 6am, not as an alarm.
 *
 * The hold is skippable. A splash that cannot be skipped is a splash that gets
 * in the way of the person who is already awake.
 */
@Composable
fun AlarmSplash(
    alarm: Alarm?,
    use24Hour: Boolean,
    isWakeCheck: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var started by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    // Guarded so the timer and a tap cannot both advance the stage.
    fun finish() {
        if (finished) return
        finished = true
        onFinished()
    }

    LaunchedEffect(Unit) {
        started = true
        delay(SPLASH_MILLIS)
        finish()
    }

    val iris by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 900, easing = EaseOutQuart),
        label = "irisOpen",
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 500, delayMillis = 200),
        label = "splashContent",
    )
    // The whole centre block rises as it fades in, rather than a stray spacer
    // doing nothing at the bottom of the screen.
    val lift by animateFloatAsState(
        targetValue = if (started) 0f else 36f,
        animationSpec = tween(durationMillis = 700, delayMillis = 200, easing = FastOutSlowInEasing),
        label = "splashLift",
    )
    val footerAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 500, delayMillis = 550),
        label = "splashFooter",
    )
    // Runs the full hold, so the screen visibly has an end rather than just
    // changing when the user has stopped expecting it to.
    val elapsed by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = SPLASH_MILLIS.toInt(), easing = LinearEasing),
        label = "splashProgress",
    )

    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    val clock: ClockText = formatClock(LocalTime.now(), use24Hour)
    val challenge = alarm?.challenge ?: VisionChallenge.SMILE

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = ::finish,
            )
            .safeDrawingPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        Header(
            isWakeCheck = isWakeCheck,
            alpha = contentAlpha,
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            IrisMark(
                openFraction = iris,
                pulse = pulse,
                modifier = Modifier
                    .size(300.dp)
                    .alpha(0.5f),
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.graphicsLayer {
                    this.alpha = contentAlpha
                    translationY = lift * density
                },
            ) {
                Text(
                    text = clock.digits,
                    style = IrisType.ClockCompact,
                    color = MaterialTheme.colorScheme.onBackground,
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
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(footerAlpha),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ChallengePreview(challenge = challenge)

            // A line that empties as the hold runs out: the screen says how long
            // it intends to stay, instead of just leaving.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(MaterialTheme.colorScheme.outline),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(1f - elapsed)
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }

            Text(
                text = "TAP TO CONTINUE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Header(isWakeCheck: Boolean, alpha: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Text(
                text = if (isWakeCheck) "WAKE CHECK" else "ALARM",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Text(
            text = LocalDate.now().format(DATE_FORMAT).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        )
    }
}

/** What is about to be asked of you, before it is asked. */
@Composable
private fun ChallengePreview(challenge: VisionChallenge) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = challenge.challengeIcon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "TO STOP IT · ${challenge.displayName.uppercase()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = challenge.description(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Two concentric rings and a pupil, drawn opening outward from the centre. */
@Composable
private fun IrisMark(openFraction: Float, pulse: Float, modifier: Modifier = Modifier) {
    val ringColor = MaterialTheme.colorScheme.primary
    val pupilColor = MaterialTheme.colorScheme.onBackground

    Canvas(modifier = modifier) {
        val maxRadius = size.minDimension / 2f
        val radius = maxRadius * openFraction * pulse
        if (radius <= 0f) return@Canvas

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(ringColor.copy(alpha = 0.18f), ringColor.copy(alpha = 0f)),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = radius,
            ),
            radius = radius,
        )
        drawCircle(
            color = ringColor.copy(alpha = 0.9f * openFraction),
            radius = radius,
            style = Stroke(width = 3.dp.toPx()),
        )
        drawCircle(
            color = ringColor.copy(alpha = 0.35f * openFraction),
            radius = radius * 0.66f,
            style = Stroke(width = 1.5.dp.toPx()),
        )
        drawCircle(
            color = pupilColor.copy(alpha = 0.5f * openFraction),
            radius = radius * 0.12f,
        )
    }
}

const val SPLASH_MILLIS = 1_700L

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun SplashPreview() {
    IrisTheme(darkTheme = true) {
        AlarmSplash(
            alarm = Alarm(hour = 6, minute = 30, label = "Gym"),
            use24Hour = true,
            isWakeCheck = false,
            onFinished = {},
        )
    }
}
