package com.iris.alarm.ui.permissions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iris.alarm.permissions.AlarmPermission
import com.iris.alarm.permissions.PermissionState

/**
 * One screen for everything the OS can withhold that would stop the alarm
 * appearing over the lock screen. None of these can be granted from inside the
 * app, so each row hands off to the right system page and re-checks on return.
 */
@Composable
fun PermissionsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PermissionsViewModel = hiltViewModel(),
) {
    val states by viewModel.states.collectAsStateWithLifecycle()

    // Returning from a settings page is the only signal that anything changed.
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.refresh() }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refresh() }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refresh() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(modifier = Modifier.padding(top = 40.dp)) {
            Text(
                text = "BACK",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(vertical = 8.dp),
            )
            Text(
                text = "PERMISSIONS",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "An alarm that cannot show itself over the lock screen is " +
                    "not an alarm. Grant everything marked required.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        states.forEach { state ->
            PermissionRow(
                state = state,
                onFix = {
                    when (state.permission) {
                        AlarmPermission.CAMERA ->
                            cameraLauncher.launch(android.Manifest.permission.CAMERA)

                        AlarmPermission.NOTIFICATIONS -> {
                            val runtimePermission = viewModel.notificationRuntimePermission()
                            if (runtimePermission != null && !state.granted) {
                                notificationLauncher.launch(runtimePermission)
                            } else {
                                viewModel.settingsIntent(state.permission)
                                    ?.let(settingsLauncher::launch)
                            }
                        }

                        else -> viewModel.settingsIntent(state.permission)
                            ?.let(settingsLauncher::launch)
                    }
                },
            )
        }

        OemNote(
            onOpenAppSettings = {
                settingsLauncher.launch(viewModel.appSettingsIntent())
            },
        )

        Box(Modifier.padding(bottom = 24.dp))
    }
}

@Composable
private fun PermissionRow(state: PermissionState, onFix: () -> Unit) {
    val accent by animateColorAsState(
        targetValue = when {
            state.granted -> MaterialTheme.colorScheme.secondary
            state.permission.required -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.outline
        },
        label = "permissionAccent",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .border(1.dp, accent, RoundedCornerShape(28.dp))
            .clickable(enabled = !state.granted, onClick = onFix)
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(accent),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.permission.title.uppercase(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = state.permission.why,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = when {
                state.granted -> "ON"
                state.permission.required -> "REQUIRED"
                else -> "OPTIONAL"
            },
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
    }
}

/**
 * Some manufacturers kill background apps regardless of the standard toggles.
 * There is no API to detect or fix that, so the honest move is to say so and
 * open the app's settings page.
 */
@Composable
private fun OemNote(onOpenAppSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(28.dp))
            .clickable(onClick = onOpenAppSettings)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "XIAOMI, SAMSUNG, HUAWEI, OPPO",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "These skins add their own autostart and background limits that " +
                "Android cannot report. If the alarm does not appear over the lock " +
                "screen with everything above granted, look for \"Autostart\" or " +
                "\"Lock screen display\" in the app's system settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "OPEN APP SETTINGS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
