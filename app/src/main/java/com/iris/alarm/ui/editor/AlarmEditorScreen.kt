package com.iris.alarm.ui.editor

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iris.alarm.domain.model.IrisSettings
import com.iris.alarm.domain.model.VisionChallenge
import com.iris.alarm.ui.components.RadialTimePicker
import com.iris.alarm.ui.components.formatClock
import com.iris.alarm.ui.components.rememberAnchorThumbnail
import java.time.DayOfWeek

/**
 * Building an alarm, one decision at a time: when, how you will have to stop it,
 * then the details.
 *
 * A single long form asked for everything at once and buried the challenge —
 * the one choice that actually distinguishes IRIS — under sound and vibration
 * toggles. Steps also give the anchor capture somewhere to belong: it is part of
 * choosing Target Iris, not a separate row further down the page.
 */
@Composable
fun AlarmEditorScreen(
    onDone: () -> Unit,
    onCaptureAnchor: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlarmEditorViewModel = hiltViewModel(),
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val use24Hour by viewModel.use24Hour.collectAsStateWithLifecycle()
    val canSave by viewModel.canSave.collectAsStateWithLifecycle()

    // Editing jumps straight to the full set of steps rather than walking an
    // existing alarm through a wizard it has already been through.
    var step by rememberSaveable { mutableIntStateOf(0) }
    val lastStep = STEPS.lastIndex

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        StepHeader(
            step = step,
            title = STEPS[step],
            isExisting = viewModel.isExisting,
            summary = draft.summaryFor(step, use24Hour),
            onBack = { if (step > 0) step-- else onDone() },
        )

        Box(modifier = Modifier.weight(1f)) {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = targetState > initialState
                    val direction = if (forward) 1 else -1
                    (
                        slideInHorizontally(tween(280)) { it * direction / 3 } +
                            fadeIn(tween(280))
                        ).togetherWith(
                        slideOutHorizontally(tween(220)) { -it * direction / 3 } +
                            fadeOut(tween(180)),
                    )
                },
                label = "editorStep",
            ) { current ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    when (current) {
                        0 -> TimeStep(
                            hour = draft.hour,
                            minute = draft.minute,
                            use24Hour = use24Hour,
                            repeatDays = draft.repeatDays,
                            onTimeChange = viewModel::setTime,
                            onToggleDay = viewModel::toggleDay,
                        )

                        1 -> ChallengeStep(
                            selected = draft.challenge,
                            anchorThumbnail = draft.anchorThumbnailPath,
                            onSelect = viewModel::setChallenge,
                            onCaptureAnchor = onCaptureAnchor,
                        )

                        else -> DetailsStep(viewModel = viewModel)
                    }

                    Box(Modifier.height(8.dp))
                }
            }
        }

        Footer(
            step = step,
            lastStep = lastStep,
            canSave = canSave,
            isExisting = viewModel.isExisting,
            onNext = { step++ },
            onSave = { viewModel.save(onDone) },
            onDelete = { viewModel.delete(onDone) },
        )
    }
}

private val STEPS = listOf("WHEN", "HOW YOU'LL STOP IT", "DETAILS")

@Composable
private fun StepHeader(
    step: Int,
    title: String,
    isExisting: Boolean,
    summary: String?,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (step == 0 && !isExisting) "CANCEL" else "BACK",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(vertical = 8.dp),
            )
            Text(
                text = "STEP ${step + 1} OF ${STEPS.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(vertical = 12.dp),
        ) {
            STEPS.indices.forEach { index ->
                Box(
                    modifier = Modifier
                        .height(3.dp)
                        .weight(1f)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (index <= step) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                        ),
                )
            }
        }

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        // Carries the earlier decisions forward, so a step is never answered
        // without the context of what came before it.
        summary?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun TimeStep(
    hour: Int,
    minute: Int,
    use24Hour: Boolean,
    repeatDays: Set<DayOfWeek>,
    onTimeChange: (Int, Int) -> Unit,
    onToggleDay: (DayOfWeek) -> Unit,
) {
    RadialTimePicker(
        hour = hour,
        minute = minute,
        use24Hour = use24Hour,
        onTimeChange = onTimeChange,
    )

    Section(title = "REPEAT") {
        DayPicker(selected = repeatDays, onToggle = onToggleDay)
    }
}

@Composable
private fun ChallengeStep(
    selected: VisionChallenge,
    anchorThumbnail: String?,
    onSelect: (VisionChallenge) -> Unit,
    onCaptureAnchor: () -> Unit,
) {
    ChallengePicker(selected = selected, onSelect = onSelect)

    // Capturing belongs to the choice, not to a row further down the page.
    if (selected == VisionChallenge.ANCHOR) {
        Section(title = "YOUR TARGET SPOT") {
            AnchorPicker(thumbnailPath = anchorThumbnail, onCapture = onCaptureAnchor)
        }
    }
}

@Composable
private fun DetailsStep(viewModel: AlarmEditorViewModel) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val soundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.let {
            IntentCompat.getParcelableExtra(
                it,
                RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                Uri::class.java,
            )
        }
        viewModel.setSound(uri?.toString())
    }

    val soundTitle = remember(draft.soundUri) {
        val uri = draft.soundUri?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }
            .getOrNull()
            ?: "DEFAULT ALARM"
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
            onClick = { soundPicker.launch(ringtonePickerIntent(draft.soundUri)) },
        )
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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

    Section(title = "AUTO-SILENCE") {
        OverrideRow(
            options = IrisSettings.AUTO_SILENCE_CHOICES,
            selected = draft.autoSilenceMinutes,
            label = { "$it MIN" },
            onSelect = viewModel::setAutoSilenceOverride,
        )
    }

    Section(title = "VOLUME RAMP") {
        OverrideRow(
            options = IrisSettings.RAMP_CHOICES,
            selected = draft.volumeRampSeconds,
            label = { if (it == 0) "OFF" else "$it SEC" },
            onSelect = viewModel::setVolumeRampOverride,
        )
    }
}

@Composable
private fun Footer(
    step: Int,
    lastStep: Int,
    canSave: Boolean,
    isExisting: Boolean,
    onNext: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val onLast = step == lastStep
        val enabled = !onLast || canSave

        Button(
            onClick = { if (onLast) onSave() else onNext() },
            enabled = enabled,
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onBackground,
                contentColor = MaterialTheme.colorScheme.background,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = when {
                    !onLast -> "NEXT"
                    canSave -> "SAVE ALARM"
                    else -> "CAPTURE A TARGET FIRST"
                },
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(vertical = 10.dp),
            )
        }

        if (isExisting && onLast) {
            Text(
                text = "DELETE ALARM",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDelete)
                    .padding(8.dp),
            )
        }
    }
}

/** What the header shows about the decisions already taken. */
private fun com.iris.alarm.domain.model.Alarm.summaryFor(step: Int, use24Hour: Boolean): String? =
    when (step) {
        0 -> null
        1 -> formatClock(hour, minute, use24Hour).inline()
        else -> "${formatClock(hour, minute, use24Hour).inline()} · ${challenge.displayName.uppercase()}"
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
                    .background(if (isOn) MaterialTheme.colorScheme.primary else Color.Transparent)
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

/** The captured spot for an anchor alarm, or the prompt to capture one. */
@Composable
private fun AnchorPicker(thumbnailPath: String?, onCapture: () -> Unit) {
    val thumbnail = rememberAnchorThumbnail(thumbnailPath)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(28.dp))
            .clickable(onClick = onCapture)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail,
                contentDescription = "Your captured target spot",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 60.dp, height = 80.dp)
                    .clip(RoundedCornerShape(16.dp)),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (thumbnail == null) "CAPTURE A SPOT" else "RETAKE",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = if (thumbnail == null) {
                    "The alarm stops when you point the camera at this spot again"
                } else {
                    "You will have to come back here to stop the alarm"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A per-alarm override of a global setting. "DEFAULT" is a real, selectable
 * value rather than the absence of one, so the alarm can be put back to
 * following the setting after an override has been chosen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverrideRow(
    options: List<Int>,
    selected: Int?,
    label: (Int) -> String,
    onSelect: (Int?) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Chip(text = "DEFAULT", selected = selected == null, onClick = { onSelect(null) })
        options.forEach { option ->
            Chip(
                text = label(option),
                selected = selected == option,
                onClick = { onSelect(option) },
            )
        }
    }
}

@Composable
private fun Chip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
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
