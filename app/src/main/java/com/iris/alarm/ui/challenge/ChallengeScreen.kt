package com.iris.alarm.ui.challenge

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.iris.alarm.domain.model.Alarm
import com.iris.alarm.domain.model.VisionChallenge
import com.iris.alarm.ui.theme.IrisTheme
import com.iris.alarm.ui.theme.IrisType
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * The ringing surface. The rounded window in the middle is where the CameraX
 * preview (smile / object hunt) or the lux gauge is mounted in the next step;
 * [onChallengeSolved] is the single exit the detectors call.
 */
@Composable
fun ChallengeScreen(
    alarm: Alarm?,
    onChallengeSolved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val challenge = alarm?.challenge ?: VisionChallenge.SMILE

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = challenge.displayName.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = LocalTime.now().format(TIME_FORMAT),
                style = IrisType.ClockCompact,
                color = MaterialTheme.colorScheme.onBackground,
            )
            alarm?.label?.takeIf { it.isNotBlank() }?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(32.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = challenge.prompt,
                style = IrisType.VisionPrompt,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Text(
            text = when (challenge) {
                VisionChallenge.SMILE -> "HOLD A SMILE FOR 3 SECONDS"
                VisionChallenge.OBJECT_HUNT ->
                    "POINT AT A ${alarm?.huntTarget?.displayName ?: "CUP"}"

                VisionChallenge.LUMEN -> "REACH 500 LUX"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ChallengePreview() {
    IrisTheme(darkTheme = true) {
        ChallengeScreen(
            alarm = Alarm(hour = 6, minute = 30, label = "Gym", challenge = VisionChallenge.SMILE),
            onChallengeSolved = {},
        )
    }
}
