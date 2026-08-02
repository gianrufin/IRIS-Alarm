package com.iris.alarm.ui.editor

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iris.alarm.domain.model.HuntTarget
import com.iris.alarm.domain.model.VisionChallenge
import com.iris.alarm.ui.components.NumberWheel
import com.iris.alarm.ui.components.challengeIcon
import com.iris.alarm.ui.theme.IrisTheme
import com.iris.alarm.ui.theme.IrisType
import java.time.DayOfWeek

@Composable
fun AlarmEditorScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlarmEditorViewModel = hiltViewModel(),
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val soundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.let {
            IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        }
        // A null pick is "Silent"; store it as null so the service knows to fall
        // back to the system alarm tone only when nothing was ever chosen.
        viewModel.setSound(uri?.toString())
    }

    val soundTitle = remember(draft.soundUri) {
        val uri = draft.soundUri?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }
            .getOrNull()
            ?: "DEFAULT ALARM"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Text(
                text = if (viewModel.isExisting) "EDIT ALARM" else "NEW ALARM",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 32.dp),
            )

            TimeSelector(
                hour = draft.hour,
                minute = draft.minute,
                onTimeChange = viewModel::setTime,
            )

            Section(title = "REPEAT") {
                DayPicker(selected = draft.repeatDays, onToggle = viewModel::toggleDay)
            }

            Section(title = "CHALLENGE") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    VisionChallenge.entries.forEach { challenge ->
                        ChallengeOption(
                            challenge = challenge,
                            selected = draft.challenge == challenge,
                            onClick = { viewModel.setChallenge(challenge) },
                        )
                    }
                }
            }

            if (draft.challenge == VisionChallenge.OBJECT_HUNT) {
                Section(title = "TARGET") {
                    TargetPicker(
                        selected = draft.huntTarget,
                        onSelect = viewModel::setHuntTarget,
                    )
                }
            }

            Section(title = "LABEL") {
                OutlinedTextField(
                    value = draft.label,
                    onValueChange = viewModel::setLabel,
                    placeholder = {
                        Text(
                            text = "Wake up",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    textStyle = MaterialTheme.typography.titleMedium,
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Section(title = "SOUND") {
                SettingRow(
                    label = soundTitle,
                    onClick = {
                        soundPicker.launch(ringtonePickerIntent(draft.soundUri))
                    },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "VIBRATE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = draft.vibrate,
                    onCheckedChange = viewModel::setVibrate,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.background,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = MaterialTheme.colorScheme.background,
                        uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                )
            }

            if (viewModel.isExisting) {
                Text(
                    text = "DELETE ALARM",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .clickable { viewModel.delete(onDone) }
                        .padding(vertical = 8.dp),
                )
            }
        }

        Button(
            onClick = { viewModel.save(onDone) },
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onBackground,
                contentColor = MaterialTheme.colorScheme.background,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Text(
                text = "SAVE",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun TimeSelector(hour: Int, minute: Int, onTimeChange: (Int, Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        NumberWheel(
            value = hour,
            range = 0..23,
            onValueChange = { onTimeChange(it, minute) },
            modifier = Modifier.weight(1f),
        )
        Text(
            text = ":",
            style = IrisType.ClockCompact,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NumberWheel(
            value = minute,
            range = 0..59,
            onValueChange = { onTimeChange(hour, it) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DayPicker(selected: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DayOfWeek.entries.forEach { day ->
            val isOn = day in selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .size(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        if (isOn) MaterialTheme.colorScheme.primary else Color.Transparent,
                    )
                    .border(
                        width = 1.dp,
                        color = if (isOn) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        shape = RoundedCornerShape(22.dp),
                    )
                    .clickable { onToggle(day) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = day.name.take(1),
                    style = MaterialTheme.typography.labelMedium,
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

@Composable
private fun ChallengeOption(
    challenge: VisionChallenge,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                shape = RoundedCornerShape(28.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = challenge.challengeIcon(),
            contentDescription = null,
            tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(24.dp),
        )
        Column {
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
        }
    }
}

@Composable
private fun TargetPicker(selected: HuntTarget, onSelect: (HuntTarget) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HuntTarget.entries.forEach { target ->
            val isOn = target == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isOn) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .border(
                        width = 1.dp,
                        color = if (isOn) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        shape = RoundedCornerShape(20.dp),
                    )
                    .clickable { onSelect(target) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = target.displayName.take(4),
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

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@Composable
private fun SettingRow(label: String, onClick: () -> Unit) {
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    )
}

private fun VisionChallenge.description(): String = when (this) {
    VisionChallenge.SMILE -> "Hold a smile at the front camera for 3 seconds"
    VisionChallenge.OBJECT_HUNT -> "Find and point the rear camera at an object"
    VisionChallenge.LUMEN -> "Walk somewhere bright until the sensor clears 500 lux"
}

private fun ringtonePickerIntent(currentUri: String?): Intent =
    Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alarm sound")
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
        putExtra(
            RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
            currentUri?.let(Uri::parse)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
        )
    }

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ChallengeOptionPreview() {
    IrisTheme(darkTheme = true) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            VisionChallenge.entries.forEach {
                ChallengeOption(
                    challenge = it,
                    selected = it == VisionChallenge.SMILE,
                    onClick = {},
                )
            }
            DayPicker(selected = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), onToggle = {})
        }
    }
}
