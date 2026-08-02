package com.iris.alarm.ui.challenge

import androidx.camera.core.CameraSelector
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.iris.alarm.domain.model.HuntTarget
import com.iris.alarm.domain.model.VisionChallenge
import com.iris.alarm.ui.components.challengeRing
import com.iris.alarm.ui.theme.IrisTheme
import com.iris.alarm.ui.theme.IrisType
import com.iris.alarm.vision.ChallengeProgress
import com.iris.alarm.vision.ObjectHuntAnalyzer
import com.iris.alarm.vision.SmileAnalyzer
import com.iris.alarm.vision.VisionAnalyzer
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/**
 * The ringing surface: clock, live detector window, and the readout that tells
 * the user how close they are. [onChallengeSolved] is the only exit.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ChallengeScreen(
    alarm: Alarm?,
    onChallengeSolved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChallengeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val challenge = state.challenge
    val progress = state.progress
    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)
    val isWakeCheck by AlarmForegroundService.ringingIsWakeCheck.collectAsStateWithLifecycle()
    val needsCamera = challenge != VisionChallenge.LUMEN

    LaunchedEffect(alarm?.id, alarm?.challenge) {
        viewModel.start(alarm?.challenge ?: VisionChallenge.SMILE)
    }

    LaunchedEffect(progress.solved) {
        if (!progress.solved) return@LaunchedEffect
        // Let the confirmation land before the screen disappears.
        delay(SUCCESS_DWELL_MILLIS)
        onChallengeSolved()
    }

    val ringFraction by animateFloatAsState(
        targetValue = progress.fraction,
        animationSpec = tween(durationMillis = 220),
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Header(
            alarm = alarm,
            challenge = challenge,
            notice = state.notice ?: "WAKE CHECK".takeIf { isWakeCheck },
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .challengeRing(
                    fraction = ringFraction,
                    trackColor = MaterialTheme.colorScheme.outline,
                    progressColor = ringColor,
                    cornerRadius = WINDOW_RADIUS,
                    strokeWidth = 3.dp,
                ),
            contentAlignment = Alignment.Center,
        ) {
            when {
                !needsCamera -> LumenGauge(progress)

                cameraPermission.status.isGranted -> DetectorWindow(
                    challenge = challenge,
                    alarm = alarm,
                    onProgress = viewModel::report,
                )

                else -> CameraPermissionPrompt(
                    onGrant = cameraPermission::launchPermissionRequest,
                    onUseAnother = viewModel::onCameraUnavailable,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Readout(progress = progress, challenge = challenge, alarm = alarm)

            if (state.escapeAllowed) {
                // Nothing on this device can run a challenge. Ringing forever with
                // no way out is a worse failure than letting the alarm be stopped.
                EscapeButton(onDismiss = onChallengeSolved)
            }
        }
    }
}

@Composable
private fun Header(alarm: Alarm?, challenge: VisionChallenge, notice: String? = null) {
    Column {
        Text(
            text = notice ?: challenge.displayName.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = if (notice != null) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
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
}

/**
 * Mounts the camera and the analyser for the active challenge. The analyser is
 * remembered per challenge/target and closed on dispose so ML Kit's native
 * detector is released when the alarm ends.
 */
@Composable
private fun DetectorWindow(
    challenge: VisionChallenge,
    alarm: Alarm?,
    onProgress: (ChallengeProgress) -> Unit,
) {
    val target = alarm?.huntTarget ?: HuntTarget.CUP
    val analyzer: VisionAnalyzer = remember(challenge, target) {
        when (challenge) {
            VisionChallenge.SMILE -> SmileAnalyzer(onProgress)
            else -> ObjectHuntAnalyzer(target, onProgress)
        }
    }

    DisposableEffect(analyzer) {
        onDispose { analyzer.close() }
    }

    CameraWindow(
        lensFacing = if (challenge == VisionChallenge.SMILE) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        },
        analyzer = analyzer,
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(WINDOW_RADIUS)),
    )
}

@Composable
private fun LumenGauge(progress: ChallengeProgress) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = progress.readout.ifBlank { "0 LUX" },
            style = IrisType.Metric,
            color = if (progress.solved) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.onBackground
            },
        )
        Text(
            text = "TARGET ${ChallengeThresholds.LUMEN_TARGET.toInt()}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CameraPermissionPrompt(onGrant: () -> Unit, onUseAnother: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.padding(32.dp),
    ) {
        Text(
            text = "CAMERA ACCESS NEEDED TO DISMISS THIS ALARM",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onGrant,
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(text = "GRANT ACCESS", style = MaterialTheme.typography.labelLarge)
        }
        // Covers a permanent denial, where the system dialog never appears again.
        Text(
            text = "USE ANOTHER CHALLENGE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable(onClick = onUseAnother),
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

@Composable
private fun Readout(progress: ChallengeProgress, challenge: VisionChallenge, alarm: Alarm?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = if (progress.solved) challenge.prompt else progress.readout.ifBlank { "--" },
            style = MaterialTheme.typography.headlineSmall,
            color = if (progress.solved) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.onBackground
            },
        )
        Text(
            text = progress.hint.ifBlank { defaultHint(challenge, alarm) },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun defaultHint(challenge: VisionChallenge, alarm: Alarm?): String = when (challenge) {
    VisionChallenge.SMILE -> "HOLD A SMILE FOR 3 SECONDS"
    VisionChallenge.OBJECT_HUNT -> "POINT AT A ${alarm?.huntTarget?.displayName ?: "CUP"}"
    VisionChallenge.LUMEN -> "REACH ${ChallengeThresholds.LUMEN_TARGET.toInt()} LUX"
}

private val WINDOW_RADIUS = 32.dp
private const val SUCCESS_DWELL_MILLIS = 700L
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ChallengePreview() {
    IrisTheme(darkTheme = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Header(
                alarm = Alarm(hour = 6, minute = 30, label = "Gym"),
                challenge = VisionChallenge.LUMEN,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .challengeRing(
                        fraction = 0.62f,
                        trackColor = MaterialTheme.colorScheme.outline,
                        progressColor = MaterialTheme.colorScheme.primary,
                        cornerRadius = WINDOW_RADIUS,
                        strokeWidth = 3.dp,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                LumenGauge(ChallengeProgress(0.62f, "312 LUX", "FIND BRIGHTER LIGHT"))
            }
            Readout(
                progress = ChallengeProgress(0.62f, "312 LUX", "FIND BRIGHTER LIGHT"),
                challenge = VisionChallenge.LUMEN,
                alarm = null,
            )
        }
    }
}
