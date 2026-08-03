package com.iris.alarm.ui.onboarding

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iris.alarm.permissions.AlarmPermission
import com.iris.alarm.ui.permissions.PermissionList
import com.iris.alarm.ui.permissions.PermissionsViewModel

/**
 * First-run setup.
 *
 * The permissions come first and in full, because IRIS is close to useless
 * without them: an alarm that cannot draw over the lock screen or fire exactly
 * is not an alarm. The flow can still be skipped — refusing to let someone into
 * an app they just installed is worse — but it says plainly what breaks.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    permissionsViewModel: PermissionsViewModel = hiltViewModel(),
) {
    var step by remember { mutableIntStateOf(0) }
    val states by permissionsViewModel.states.collectAsStateWithLifecycle()
    val requiredGranted = states.none { it.permission.required && !it.granted }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp),
    ) {
        StepDots(
            count = STEP_COUNT,
            current = step,
            modifier = Modifier.padding(top = 32.dp, bottom = 20.dp),
        )

        Box(modifier = Modifier.weight(1f)) {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = targetState > initialState
                    val width = if (forward) 1 else -1
                    (
                        slideInHorizontally(tween(280)) { it * width / 3 } +
                            fadeIn(tween(280))
                        ).togetherWith(
                        slideOutHorizontally(tween(220)) { -it * width / 3 } +
                            fadeOut(tween(180)),
                    )
                },
                label = "onboardingStep",
            ) { current ->
                when (current) {
                    0 -> WelcomeStep()
                    else -> PermissionStep(viewModel = permissionsViewModel)
                }
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 24.dp),
        ) {
            Button(
                onClick = { if (step < STEP_COUNT - 1) step++ else onFinished() },
                shape = RoundedCornerShape(32.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onBackground,
                    contentColor = MaterialTheme.colorScheme.background,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = when {
                        step == 0 -> "SET UP"
                        requiredGranted -> "START USING IRIS"
                        else -> "CONTINUE ANYWAY"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
            }

            if (step > 0 && !requiredGranted) {
                Text(
                    text = "Some alarms may not ring until these are granted",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "IRIS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "AN ALARM YOU\nCANNOT SLEEP\nTHROUGH",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(top = 36.dp),
        ) {
            Feature(
                title = "NO PLAIN OFF SWITCH",
                detail = "Stopping an alarm takes a smile at the camera, a walk to " +
                    "a spot you chose, or finding real light.",
            )
            Feature(
                title = "EVERYTHING ON DEVICE",
                detail = "The camera never leaves your phone. No account, no network, " +
                    "no photos stored.",
            )
            Feature(
                title = "IT NEEDS PERMISSIONS",
                detail = "Android will not let an alarm cover your lock screen unless " +
                    "you say so. That is the next screen.",
            )
        }
    }
}

@Composable
private fun Feature(title: String, detail: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PermissionStep(viewModel: PermissionsViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "PERMISSIONS",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Tap each one. Android asks for these individually — there is no " +
                "single button that can grant them.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PermissionList(viewModel = viewModel, showOemNote = true)
    }
}

@Composable
private fun StepDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
    ) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .height(3.dp)
                    .weight(1f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (index <= current) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    ),
            )
        }
    }
}

private const val STEP_COUNT = 2

/** Used by the settings entry so the same rows are not written twice. */
@Composable
internal fun Modifier.onboardingRow(onClick: () -> Unit): Modifier =
    clip(RoundedCornerShape(28.dp))
        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(28.dp))
        .clickable(onClick = onClick)
        .padding(20.dp)
