package com.iris.alarm.ui.editor

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.iris.alarm.domain.model.VisionChallenge
import com.iris.alarm.ui.components.challengeIcon

/**
 * The three challenges as full-width cards.
 *
 * The selected one animates: its icon lifts and pulses inside a ring that
 * sweeps, so the choice is legible from across the room and the card that is
 * *not* chosen is visibly inert. The motion is per-challenge — the smile
 * breathes, the target sweeps like a scanner, the light glows — so the card
 * looks like what it does.
 */
@Composable
fun ChallengePicker(
    selected: VisionChallenge,
    onSelect: (VisionChallenge) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VisionChallenge.entries.forEach { challenge ->
            ChallengeCard(
                challenge = challenge,
                selected = challenge == selected,
                onClick = { onSelect(challenge) },
            )
        }
    }
}

@Composable
private fun ChallengeCard(
    challenge: VisionChallenge,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val border by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outline
        },
        label = "challengeBorder",
    )
    val lift by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "challengeLift",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.surfaceContainer
                } else {
                    Color.Transparent
                },
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = border,
                shape = RoundedCornerShape(28.dp),
            )
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        AnimatedChallengeIcon(challenge = challenge, active = selected)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = challenge.displayName.uppercase(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = challenge.description(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = challenge.effort(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .scale(0.9f + 0.1f * lift),
            )
        }
    }
}

/**
 * An icon inside a ring whose motion is specific to the challenge, so the card
 * previews the thing rather than just labelling it.
 */
@Composable
private fun AnimatedChallengeIcon(challenge: VisionChallenge, active: Boolean) {
    val transition = rememberInfiniteTransition(label = "challengeIcon")

    val pulse by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
        ),
        label = "sweep",
    )
    val glow by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )

    val accent = MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.outline
    val scale by animateFloatAsState(
        targetValue = if (active) 1f else 0.92f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "iconScale",
    )

    Box(
        modifier = Modifier.size(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(56.dp)) {
            val radius = size.minDimension / 2f - 2.dp.toPx()
            val centre = Offset(size.width / 2f, size.height / 2f)

            if (!active) {
                drawCircle(color = idle, radius = radius, style = Stroke(width = 1.dp.toPx()))
                return@Canvas
            }

            when (challenge) {
                // Breathes, like the smile being held.
                VisionChallenge.SMILE -> drawCircle(
                    color = accent,
                    radius = radius * pulse,
                    style = Stroke(width = 2.dp.toPx()),
                )

                // Sweeps, like something being searched for.
                VisionChallenge.ANCHOR -> {
                    drawCircle(
                        color = accent.copy(alpha = 0.3f),
                        radius = radius,
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                    drawArc(
                        color = accent,
                        startAngle = sweep,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = Offset(centre.x - radius, centre.y - radius),
                        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                        style = Stroke(width = 2.5.dp.toPx()),
                    )
                }

                // Sweeps a wider arc than the anchor and drifts: searching a
                // room rather than lining up on one spot.
                VisionChallenge.HUNT -> {
                    drawCircle(
                        color = accent.copy(alpha = 0.25f),
                        radius = radius,
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                    drawArc(
                        color = accent,
                        startAngle = -sweep * 1.4f,
                        sweepAngle = 150f,
                        useCenter = false,
                        topLeft = Offset(centre.x - radius * 0.78f, centre.y - radius * 0.78f),
                        size = androidx.compose.ui.geometry.Size(
                            radius * 1.56f,
                            radius * 1.56f,
                        ),
                        style = Stroke(width = 2.5.dp.toPx()),
                    )
                }

                // Ticks between two states, like a cursor waiting for an answer.
                VisionChallenge.MATH -> {
                    drawCircle(
                        color = accent,
                        radius = radius,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                    drawCircle(
                        color = accent.copy(alpha = if (pulse > 1f) 0.85f else 0.2f),
                        radius = radius * 0.24f,
                    )
                }

                // Glows, like light arriving.
                VisionChallenge.LUMEN -> {
                    drawCircle(color = accent.copy(alpha = glow * 0.3f), radius = radius)
                    drawCircle(
                        color = accent,
                        radius = radius,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
        }

        Icon(
            imageVector = challenge.challengeIcon(),
            contentDescription = null,
            tint = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(24.dp)
                .scale(scale * if (active && challenge == VisionChallenge.SMILE) pulse else 1f),
        )
    }
}

internal fun VisionChallenge.description(): String = when (this) {
    VisionChallenge.SMILE -> "Hold a smile at the front camera for 3 seconds"
    VisionChallenge.ANCHOR -> "Go back to a spot you capture now"
    VisionChallenge.HUNT -> "Find a household object the alarm names at random"
    VisionChallenge.LUMEN -> "Walk somewhere bright until the sensor clears 500 lux"
    VisionChallenge.MATH -> "Answer arithmetic on a keypad until you're awake"
}

/** What it actually costs you at 6am, which is the real difference between them. */
private fun VisionChallenge.effort(): String = when (this) {
    VisionChallenge.SMILE -> "EASIEST · CAN BE DONE IN BED"
    VisionChallenge.ANCHOR -> "HARD · GETS YOU ACROSS THE ROOM"
    VisionChallenge.HUNT -> "HARDEST · UNPREDICTABLE, SENDS YOU HUNTING"
    VisionChallenge.LUMEN -> "MEDIUM · NEEDS REAL LIGHT"
    VisionChallenge.MATH -> "MEDIUM · NO CAMERA, WORKS ANYWHERE"
}
