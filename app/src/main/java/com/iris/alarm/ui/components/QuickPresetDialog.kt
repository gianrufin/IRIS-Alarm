package com.iris.alarm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.iris.alarm.domain.model.QuickPreset

@Composable
fun QuickPresetDialog(
    preset: QuickPreset? = null,
    onDismiss: () -> Unit,
    onSave: (QuickPreset) -> Unit,
    onDelete: ((String) -> Unit)? = null,
) {
    var label by remember { mutableStateOf(preset?.label ?: "") }
    var totalMinutes by remember { mutableIntStateOf(preset?.durationMinutes ?: 15) }
    var hours by remember { mutableIntStateOf(totalMinutes / 60) }
    var minutes by remember { mutableIntStateOf(totalMinutes % 60) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = if (preset == null) "NEW QUICK SET" else "EDIT QUICK SET",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text = "Define a duration to bypass the dial and arm alarms with one touch.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label (e.g. 15m nap, 1h study)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = "DURATION",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DurationPickerField(
                        value = hours,
                        label = "HOURS",
                        range = 0..23,
                        onValueChange = {
                            hours = it
                            totalMinutes = hours * 60 + minutes
                        },
                        modifier = Modifier.weight(1f),
                    )

                    DurationPickerField(
                        value = minutes,
                        label = "MINUTES",
                        range = 0..59,
                        onValueChange = {
                            minutes = it
                            totalMinutes = hours * 60 + minutes
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                // Quick duration bump shortcuts
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(15, 30, 45, 60, 90).forEach { mins ->
                        val isSelected = totalMinutes == mins
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable {
                                    totalMinutes = mins
                                    hours = mins / 60
                                    minutes = mins % 60
                                    if (label.isBlank() || label.endsWith("m") || label.endsWith("h")) {
                                        label = if (mins >= 60 && mins % 60 == 0) "${mins / 60}h" else "${mins}m"
                                    }
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (mins >= 60 && mins % 60 == 0) "${mins / 60}h" else "${mins}m",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (preset != null && onDelete != null) {
                        Text(
                            text = "DELETE",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .clickable {
                                    onDelete(preset.id)
                                    onDismiss()
                                }
                                .padding(12.dp),
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = "CANCEL",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clickable(onClick = onDismiss)
                            .padding(12.dp),
                    )

                    Button(
                        onClick = {
                            val computedMinutes = (hours * 60 + minutes).coerceAtLeast(1)
                            val finalLabel = label.ifBlank {
                                if (computedMinutes >= 60 && computedMinutes % 60 == 0) {
                                    "${computedMinutes / 60}h timer"
                                } else {
                                    "${computedMinutes}m timer"
                                }
                            }
                            onSave(
                                QuickPreset(
                                    id = preset?.id ?: java.util.UUID.randomUUID().toString(),
                                    label = finalLabel,
                                    durationMinutes = computedMinutes,
                                )
                            )
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text("SAVE")
                    }
                }
            }
        }
    }
}

@Composable
private fun DurationPickerField(
    value: Int,
    label: String,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { onValueChange((value - 1).coerceIn(range)) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text("-", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = value.toString().padStart(2, '0'),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { onValueChange((value + 1).coerceIn(range)) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text("+", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
