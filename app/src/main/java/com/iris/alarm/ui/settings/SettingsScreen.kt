package com.iris.alarm.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iris.alarm.domain.model.IrisSettings
import com.iris.alarm.domain.model.VisionChallenge
import com.iris.alarm.ui.theme.IrisTheme

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    SettingsContent(
        settings = settings,
        onDefaultChallenge = viewModel::setDefaultChallenge,
        onAutoSilence = viewModel::setAutoSilenceMinutes,
        onRamp = viewModel::setVolumeRampSeconds,
        onMinimumVolume = viewModel::setMinimumVolumePercent,
        onWakeCheck = viewModel::setWakeCheckMinutes,
        onBack = onBack,
        onOpenPermissions = onOpenPermissions,
        onUse24Hour = viewModel::setUse24Hour,
        // Passed as a slot so the preview can render the screen without a
        // Hilt-injected updater behind it.
        updates = { UpdateSection() },
        modifier = modifier,
    )
}

@Composable
private fun SettingsContent(
    settings: IrisSettings,
    onDefaultChallenge: (VisionChallenge) -> Unit,
    onAutoSilence: (Int) -> Unit,
    onRamp: (Int) -> Unit,
    onMinimumVolume: (Int) -> Unit,
    onWakeCheck: (Int) -> Unit,
    onBack: () -> Unit,
    onOpenPermissions: () -> Unit,
    onUse24Hour: (Boolean) -> Unit,
    updates: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(36.dp),
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
                text = "SETTINGS",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Setting(
            title = "CLOCK",
            detail = "How times are shown across the app",
        ) {
            ChoiceRow(
                options = listOf(true, false),
                selected = settings.use24Hour,
                label = { if (it) "24 HOUR" else "12 HOUR" },
                onSelect = onUse24Hour,
            )
        }

        Setting(
            title = "DEFAULT CHALLENGE",
            detail = "Pre-selected when you create an alarm",
        ) {
            ChoiceRow(
                options = VisionChallenge.entries,
                selected = settings.defaultChallenge,
                label = { it.displayName.uppercase() },
                onSelect = onDefaultChallenge,
            )
        }

        Setting(
            title = "AUTO-SILENCE",
            detail = "How long an unsolved alarm keeps ringing",
        ) {
            ChoiceRow(
                options = IrisSettings.AUTO_SILENCE_CHOICES,
                selected = settings.autoSilenceMinutes,
                label = { "$it MIN" },
                onSelect = onAutoSilence,
            )
        }

        Setting(
            title = "VOLUME RAMP",
            detail = "Fade in from near-silence over this long",
        ) {
            ChoiceRow(
                options = IrisSettings.RAMP_CHOICES,
                selected = settings.volumeRampSeconds,
                label = { if (it == 0) "OFF" else "$it SEC" },
                onSelect = onRamp,
            )
        }

        Setting(
            title = "MINIMUM VOLUME",
            detail = "Raise the alarm stream to at least this while ringing, " +
                "then put it back. An alarm on a muted stream never wakes you.",
        ) {
            ChoiceRow(
                options = IrisSettings.MINIMUM_VOLUME_CHOICES,
                selected = settings.minimumVolumePercent,
                label = { if (it == 0) "DON'T TOUCH" else "$it%" },
                onSelect = onMinimumVolume,
            )
        }

        Setting(
            title = "WAKE CHECK",
            detail = "Ring again this long after a solved challenge, unless you " +
                "tap I'M UP. Solving a challenge proves you were awake for ten " +
                "seconds, not that you stayed awake.",
        ) {
            ChoiceRow(
                options = IrisSettings.WAKE_CHECK_CHOICES,
                selected = settings.wakeCheckMinutes,
                label = { if (it == 0) "OFF" else "$it MIN" },
                onSelect = onWakeCheck,
            )
        }

        Setting(
            title = "UPDATES",
            detail = "IRIS is side-loaded, so it updates itself from the project's " +
                "GitHub releases rather than through the Play Store.",
        ) {
            updates()
        }

        Setting(
            title = "PERMISSIONS",
            detail = "Everything the system can withhold that would stop the alarm " +
                "showing over your lock screen.",
        ) {
            Chip(text = "REVIEW PERMISSIONS", onClick = onOpenPermissions)
        }

        Box(Modifier.padding(bottom = 24.dp))
    }
}

/** A standalone tappable chip, for actions rather than choices. */
@Composable
private fun Chip(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun Setting(title: String, detail: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            val isOn = option == selected
            Box(
                modifier = Modifier
                    .border(
                        width = 1.dp,
                        color = if (isOn) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        shape = RoundedCornerShape(20.dp),
                    )
                    .background(
                        color = if (isOn) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Transparent
                        },
                        shape = RoundedCornerShape(20.dp),
                    )
                    .clickable { onSelect(option) }
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isOn) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun SettingsPreview() {
    IrisTheme(darkTheme = true) {
        SettingsContent(
            settings = IrisSettings(),
            onDefaultChallenge = {},
            onAutoSilence = {},
            onRamp = {},
            onMinimumVolume = {},
            onWakeCheck = {},
            onBack = {},
            onOpenPermissions = {},
            onUse24Hour = {},
            updates = {},
        )
    }
}
