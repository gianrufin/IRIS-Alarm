package com.iris.alarm.ui.challenge

import androidx.camera.core.CameraSelector
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.iris.alarm.alarm.AlarmForegroundService
import com.iris.alarm.domain.model.Alarm
import com.iris.alarm.domain.model.ChallengeThresholds
import com.iris.alarm.domain.model.VisionChallenge
import com.iris.alarm.ui.components.ClockText
import com.iris.alarm.ui.components.challengeRing
import com.iris.alarm.ui.components.formatClock
import com.iris.alarm.ui.components.rememberAnchorThumbnail
import com.iris.alarm.ui.theme.IrisTheme
import com.iris.alarm.ui.theme.IrisType
import com.iris.alarm.vision.AnchorAnalyzer
import com.iris.alarm.vision.ChallengeProgress
import com.iris.alarm.vision.SceneSignature
import com.iris.alarm.vision.SmileAnalyzer
import com.iris.alarm.vision.VisionAnalyzer
import java.time.LocalTime
import kotlinx.coroutines.delay

/**
 * The ringing surface.
 *
 * The camera fills the screen behind a scrim and the instruction sits dead
 * centre in the largest type on the screen — someone half awake should be able
 * to read "OPEN YOUR EYES WIDER" without focusing on anything else. The progress
 * ring traces the edge of the whole display, so the screen itself fills up as
 * the challenge is satisfied.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ChallengeScreen(
    alarm: Alarm?,
    use24Hour: Boolean,
    onChallengeSolved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChallengeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isWakeCheck by AlarmForegroundService.ringingIsWakeCheck.collectAsStateWithLifecycle()
    var splashDone by remember { mutableStateOf(false) }

    val challenge = state.challenge
    val progress = state.progress
    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)
    val needsCamera = challenge != VisionChallenge.LUMEN

    LaunchedEffect(alarm?.id, alarm?.challenge) {
        viewModel.start(alarm)
    }

    LaunchedEffect(progress.solved) {
        if (!progress.solved) return@LaunchedEffect
        // Let the confirmation land before the screen disappears.
        delay(SUCCESS_DWELL_MILLIS)
        onChallengeSolved()
    }

    if (!splashDone) {
        AlarmSplash(
            alarm = alarm,
            use24Hour = use24Hour,
            isWakeCheck = isWakeCheck,
            onFinished = { splashDone = true },
            modifier = modifier,
        )
        return
    }

    val ringFraction by animateFloatAsState(
        targetValue = progress.fraction,
        animationSpec = tween(durationMillis = 260),
        label = "ringFraction",
    )
    val ringColor by animateColorAsState(
        targetValue = if (progress.solved) {
            MaterialTheme.colorScheme.secondary
        } else {
            MaterialTheme.colorScheme.primary
        },
        label = "ringColor",
    )
    val successScale by animateFloatAsState(
        targetValue = if (progress.solved) 1.12f else 1f,
        animationSpec = tween(durationMillis = 320),
        label = "successScale",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when {
            !needsCamera -> Unit

            cameraPermission.status.isGranted -> DetectorSurface(
                challenge = challenge,
                alarm = alarm,
                onProgress = viewModel::report,
            )

            else -> Unit
        }

        // Darkened so white type stays readable over whatever the camera sees.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.82f),
                            Color.Black.copy(alpha = 0.55f),
                            Color.Black.copy(alpha = 0.86f),
                        ),
                    ),
                ),
        )

        // The progress ring traces the edge of the display itself.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .challengeRing(
                    fraction = ringFraction,
                    trackColor = Color.White.copy(alpha = 0.12f),
                    progressColor = ringColor,
                    cornerRadius = 0.dp,
                    strokeWidth = 5.dp,
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Header(
                alarm = alarm,
                challenge = challenge,
                use24Hour = use24Hour,
                notice = state.notice ?: "WAKE CHECK".takeIf { isWakeCheck },
            )

            // The instruction, centred and largest on screen.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.scale(successScale),
            ) {
                if (challenge == VisionChallenge.ANCHOR) {
                    AnchorReminder(alarm)
                }

                Instruction(
                    text = instructionFor(state, challenge, cameraPermission.status.isGranted),
                    solved = progress.solved,
                )

                Metric(progress = progress, challenge = challenge)
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (needsCamera && !cameraPermission.status.isGranted) {
                    CameraPermissionActions(
                        onGrant = cameraPermission::launchPermissionRequest,
                        onUseAnother = viewModel::onCameraUnavailable,
                    )
                }

                if (state.escapeAllowed) {
                    // Nothing on this device can run a challenge. Ringing forever
                    // with no way out is a worse failure than a skipped challenge.
                    EscapeButton(onDismiss = onChallengeSolved)
                }

                Text(
                    text = challenge.displayName.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The centred instruction. Every state of every challenge lands here. */
@Composable
private fun Instruction(text: String, solved: Boolean) {
    val pulse by rememberInfiniteTransition(label = "instruction").animateFloat(
        initialValue = 0.86f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "instructionPulse",
    )

    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 3 })
                .togetherWith(fadeOut(tween(160)) + slideOutVertically(tween(200)) { -it / 3 })
        },
        label = "instructionText",
    ) { instruction ->
        Text(
            text = instruction,
            style = IrisType.VisionPrompt,
            color = if (solved) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.onBackground
            },
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (solved) 1f else pulse),
        )
    }
}

@Composable
private fun Metric(progress: ChallengeProgress, challenge: VisionChallenge) {
    val readout = progress.readout.ifBlank {
        when (challenge) {
            VisionChallenge.LUMEN -> "0 LUX"
            else -> "--"
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedContent(
            targetState = readout,
            transitionSpec = { fadeIn(tween(150)).togetherWith(fadeOut(tween(150))) },
            label = "metric",
        ) { value ->
            Text(
                text = value,
                style = IrisType.Metric,
                color = if (progress.solved) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
        Text(
            text = targetLabel(challenge),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun targetLabel(challenge: VisionChallenge): String = when (challenge) {
    VisionChallenge.SMILE -> "SMILE CONFIDENCE"
    VisionChallenge.ANCHOR -> "MATCH · TARGET ${(ChallengeThresholds.ANCHOR_SIMILARITY * 100).toInt()}%"
    VisionChallenge.LUMEN -> "TARGET ${ChallengeThresholds.LUMEN_TARGET.toInt()} LUX"
}

/** The captured spot, so a half-asleep user knows where they are being sent. */
@Composable
private fun AnchorReminder(alarm: Alarm?) {
    val thumbnail = rememberAnchorThumbnail(alarm?.anchorThumbnailPath) ?: return

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            bitmap = thumbnail,
            contentDescription = "Your target spot",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 96.dp, height = 128.dp)
                .clip(RoundedCornerShape(20.dp)),
        )
        Text(
            text = "GO HERE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun Header(
    alarm: Alarm?,
    challenge: VisionChallenge,
    use24Hour: Boolean,
    notice: String?,
) {
    val clock: ClockText = formatClock(LocalTime.now(), use24Hour)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = notice ?: challenge.displayName.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = if (notice != null) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
            textAlign = TextAlign.Center,
        )
        Text(
            text = clock.inline(),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        alarm?.label?.takeIf { it.isNotBlank() }?.let { label ->
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Mounts the camera and the analyser for the active challenge. The analyser is
 * remembered per challenge and closed on dispose so ML Kit's native detector is
 * released when the alarm ends.
 */
@Composable
private fun DetectorSurface(
    challenge: VisionChallenge,
    alarm: Alarm?,
    onProgress: (ChallengeProgress) -> Unit,
) {
    val reference = remember(alarm?.anchorSignature) {
        SceneSignature.deserialise(alarm?.anchorSignature)
    }

    val analyzer: VisionAnalyzer? = remember(challenge, reference) {
        when (challenge) {
            VisionChallenge.SMILE -> SmileAnalyzer(onProgress)
            VisionChallenge.ANCHOR -> reference?.let { AnchorAnalyzer(it, onProgress) }
            VisionChallenge.LUMEN -> null
        }
    }

    DisposableEffect(analyzer) {
        onDispose { analyzer?.close() }
    }

    if (analyzer == null) return

    CameraWindow(
        lensFacing = if (challenge == VisionChallenge.SMILE) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        },
        analyzer = analyzer,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun CameraPermissionActions(onGrant: () -> Unit, onUseAnother: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = onGrant,
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(text = "GRANT CAMERA ACCESS", style = MaterialTheme.typography.labelLarge)
        }
        // Covers a permanent denial, where the system dialog never appears again.
        Text(
            text = "USE ANOTHER CHALLENGE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clickable(onClick = onUseAnother)
                .padding(8.dp),
        )
    }
}

@Composable
private fun EscapeButton(onDismiss: () -> Unit) {
    Button(
        onClick = onDismiss,
        shape = RoundedCornerShape(32.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.onBackground,
            contentColor = MaterialTheme.colorScheme.background,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "DISMISS ALARM",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(vertical = 10.dp),
        )
    }
}

/**
 * The single place instruction text is decided, so every challenge — and every
 * state of every challenge, including the ones where nothing is being detected
 * yet — puts a usable instruction in the middle of the screen.
 */
private fun instructionFor(
    state: ChallengeUiState,
    challenge: VisionChallenge,
    cameraGranted: Boolean,
): String {
    if (state.escapeAllowed) return "THIS DEVICE CANNOT RUN A CHALLENGE"
    if (challenge != VisionChallenge.LUMEN && !cameraGranted) return "ALLOW THE CAMERA TO CONTINUE"
    if (state.anchorMissing) return "NO TARGET SAVED FOR THIS ALARM"

    return state.progress.hint.ifBlank {
        when (challenge) {
            VisionChallenge.SMILE -> "SMILE AT THE CAMERA"
            VisionChallenge.ANCHOR -> "GO TO YOUR TARGET SPOT"
            VisionChallenge.LUMEN -> "FIND BRIGHT LIGHT"
        }
    }
}

private const val SUCCESS_DWELL_MILLIS = 900L

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ChallengePreview() {
    IrisTheme(darkTheme = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Header(
                alarm = Alarm(hour = 6, minute = 30, label = "Gym"),
                challenge = VisionChallenge.SMILE,
                use24Hour = true,
                notice = null,
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Instruction(text = "OPEN YOUR EYES WIDER", solved = false)
                Metric(
                    progress = ChallengeProgress(0.4f, "62%", "OPEN YOUR EYES WIDER"),
                    challenge = VisionChallenge.SMILE,
                )
            }
            Text(
                text = "MIRROR IRIS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
