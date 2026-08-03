package com.iris.alarm.ui.challenge

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.iris.alarm.domain.model.MathDifficulty
import com.iris.alarm.domain.model.MathProblem
import com.iris.alarm.ui.theme.IrisTheme
import com.iris.alarm.ui.theme.IrisType

/**
 * The arithmetic challenge: a sum, an answer field, and a keypad.
 *
 * The keypad is drawn rather than borrowed from the system IME. A soft keyboard
 * over the lock screen is at the mercy of whichever keyboard app is installed,
 * can be dismissed, and puts a number row behind a mode switch — none of which
 * is acceptable when the thing on the other side of it is a ringing alarm. These
 * keys are also far bigger than an IME's, which is the actual requirement at 6am.
 */
@Composable
fun MathPad(
    math: MathState,
    solved: Boolean,
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Driven by the wrong-answer counter rather than a boolean, so two wrong
    // answers in a row shake twice instead of the second one going unnoticed.
    val shake = remember { Animatable(0f, Float.VectorConverter) }
    LaunchedEffect(math.wrongAttempts) {
        if (math.wrongAttempts == 0) return@LaunchedEffect
        shake.snapTo(0f)
        shake.animateTo(
            targetValue = 0f,
            animationSpec = keyframes {
                durationMillis = 320
                (-14f) at 60
                14f at 130
                (-8f) at 200
                0f at 320
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { translationX = shake.value },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AnimatedContent(
            targetState = math.problem.question,
            transitionSpec = {
                (slideInHorizontally(tween(260)) { it / 2 } + fadeIn(tween(260)))
                    .togetherWith(
                        slideOutHorizontally(tween(200)) { -it / 2 } + fadeOut(tween(160)),
                    )
            },
            label = "mathQuestion",
        ) { question ->
            Text(
                text = question,
                style = IrisType.Metric,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
        }

        // Reads as a field even when empty, so there is never a moment where it
        // is unclear that something is expected here.
        Box(
            modifier = Modifier
                .widthIn(min = 160.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .border(
                    width = 1.dp,
                    color = if (solved) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    shape = RoundedCornerShape(20.dp),
                )
                .padding(horizontal = 24.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = math.entry.ifEmpty { "—" },
                style = MaterialTheme.typography.headlineMedium,
                color = if (math.entry.isEmpty()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
            )
        }

        Text(
            text = "${math.solvedCount} OF ${math.required} SOLVED",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Keypad(
            canSubmit = math.entry.isNotEmpty() && !solved,
            onDigit = onDigit,
            onBackspace = onBackspace,
            onSubmit = onSubmit,
        )
    }
}

@Composable
private fun Keypad(
    canSubmit: Boolean,
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        modifier = Modifier.widthIn(max = 320.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { digit ->
                    Key(
                        modifier = Modifier.weight(1f),
                        onClick = { onDigit(digit) },
                    ) {
                        Text(
                            text = digit.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key(modifier = Modifier.weight(1f), onClick = onBackspace) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Backspace,
                    contentDescription = "Backspace",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Key(modifier = Modifier.weight(1f), onClick = { onDigit(0) }) {
                Text(
                    text = "0",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            Key(
                modifier = Modifier.weight(1f),
                onClick = onSubmit,
                enabled = canSubmit,
                fill = if (canSubmit) MaterialTheme.colorScheme.primary else null,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Submit",
                    tint = if (canSubmit) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    },
                )
            }
        }
    }
}

@Composable
private fun Key(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
    fill: Color? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(fill ?: Color.White.copy(alpha = 0.08f))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun MathPadPreview() {
    IrisTheme(darkTheme = true) {
        MathPad(
            math = MathState(
                problem = MathProblem.generate(MathDifficulty.MEDIUM),
                entry = "26",
                solvedCount = 1,
                required = 3,
            ),
            solved = false,
            onDigit = {},
            onBackspace = {},
            onSubmit = {},
            modifier = Modifier.padding(24.dp),
        )
    }
}
