package com.iris.alarm.ui.challenge

import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.iris.alarm.domain.model.Alarm
import com.iris.alarm.ui.components.ClockText
import com.iris.alarm.ui.components.formatClock
import com.iris.alarm.ui.theme.IrisTheme
import com.iris.alarm.ui.theme.IrisType
import java.time.LocalTime
import kotlinx.coroutines.delay

/**
 * The first thing shown when an alarm fires: an opening iris over the time and
 * label, held briefly before the challenge takes over.
 *
 * It exists to make the alarm unmistakable at a glance on a lock screen — a
 * camera viewfinder appearing with no preamble reads as the phone malfunctioning
 * at 6am, not as an alarm.
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

    LaunchedEffect(Unit) {
        started = true
        delay(SPLASH_MILLIS)
        onFinished()
    }

    val iris by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 900, easing = EaseOutQuart),
        label = "irisOpen",
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 600, delayMillis = 350),
        label = "splashContent",
    )
    val lift by animateFloatAsState(
        targetValue = if (started) 0f else 40f,
        animationSpec = tween(durationMillis = 700, delayMillis = 350, easing = FastOutSlowInEasing),
        label = "splashLift",
    )

    // A slow breathing pulse keeps the screen alive during the hold.
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    val clock: ClockText = formatClock(LocalTime.now(), use24Hour)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        IrisMark(
            openFraction = iris,
            pulse = pulse,
            modifier = Modifier
                .size(320.dp)
                .alpha(0.55f),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .alpha(contentAlpha)
                .padding(24.dp),
        ) {
            Text(
                text = if (isWakeCheck) "WAKE CHECK" else "IRIS ALARM",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = clock.digits,
                style = IrisType.ClockCompact,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.scale(0.9f + 0.1f * contentAlpha),
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // The whole block rises slightly as it fades in.
        Box(modifier = Modifier.padding(top = lift.dp))
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
            color = pupilColor.copy(alpha = openFraction),
            radius = radius * 0.16f,
        )
    }
}

const val SPLASH_MILLIS = 1_700L

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
