package com.iris.alarm.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iris.alarm.domain.model.UpdateState

/**
 * Check for, download and install a new build without leaving the app.
 *
 * Releases come from the project's GitHub Releases and are signed with the same
 * side-load key as the installed build — Android rejects an update signed with
 * any other key, so this only works for builds published by the project's own
 * release workflow.
 */
@Composable
fun UpdateSection(
    modifier: Modifier = Modifier,
    viewModel: UpdateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "VERSION ${viewModel.installedVersion}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        AnimatedContent(
            targetState = state,
            transitionSpec = { fadeIn(tween(180)).togetherWith(fadeOut(tween(180))) },
            label = "updateState",
        ) { current ->
            when (current) {
                is UpdateState.Idle -> ActionCard(
                    title = "CHECK FOR UPDATES",
                    detail = "Looks at the project's GitHub releases",
                    onClick = viewModel::check,
                )

                is UpdateState.Checking -> StatusCard(
                    title = "CHECKING…",
                    detail = "Asking GitHub for the latest release",
                )

                is UpdateState.UpToDate -> ActionCard(
                    title = "UP TO DATE",
                    detail = "You are on the newest release. Tap to check again.",
                    onClick = viewModel::check,
                    accent = MaterialTheme.colorScheme.secondary,
                )

                is UpdateState.Available -> ActionCard(
                    title = "UPDATE TO ${current.update.versionName}",
                    detail = buildString {
                        append(formatSize(current.update.sizeBytes))
                        if (current.update.notes.isNotBlank()) {
                            append(" · ")
                            append(current.update.notes.lineSequence().first().take(80))
                        }
                    },
                    onClick = { viewModel.downloadAndInstall(current.update) },
                    accent = MaterialTheme.colorScheme.primary,
                )

                is UpdateState.Downloading -> DownloadCard(
                    version = current.update.versionName,
                    fraction = current.fraction,
                )

                is UpdateState.ReadyToInstall -> StatusCard(
                    title = "STARTING INSTALLER…",
                    detail = "Confirm the install when Android asks",
                )

                is UpdateState.Installing -> StatusCard(
                    title = "INSTALLING",
                    detail = "Confirm the install when Android asks. IRIS will " +
                        "restart when it finishes.",
                )

                is UpdateState.Failed -> ActionCard(
                    title = "UPDATE FAILED",
                    detail = current.reason,
                    onClick = viewModel::dismissError,
                    accent = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    detail: String,
    onClick: () -> Unit,
    accent: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.outline,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .border(1.dp, accent, RoundedCornerShape(28.dp))
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
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

@Composable
private fun StatusCard(title: String, detail: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(28.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
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

@Composable
private fun DownloadCard(version: String, fraction: Float) {
    val animated by animateFloatAsState(targetValue = fraction, label = "downloadProgress")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(28.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "DOWNLOADING $version · ${(animated * 100).toInt()}%",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.outline),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated)
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> "Unknown size"
    bytes >= 1_048_576 -> "${bytes / 1_048_576} MB"
    else -> "${bytes / 1024} KB"
}
