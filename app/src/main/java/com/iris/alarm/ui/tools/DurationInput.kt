package com.iris.alarm.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * An exact `MM : SS` entry, so a timer is not limited to whatever presets exist.
 *
 * Digits are filtered as they are typed rather than validated on submit — there
 * is no state in which the field holds something that is not a duration, so
 * there is nothing to reject and no error to show.
 */
@Composable
fun DurationInput(
    minutes: Int,
    seconds: Int,
    enabled: Boolean,
    onChange: (minutes: Int, seconds: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Kept as text so a half-typed "1" does not immediately become "01".
    var minuteText by remember(minutes) { mutableStateOf(minutes.toString()) }
    var secondText by remember(seconds) { mutableStateOf(seconds.toString().padStart(2, '0')) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DigitField(
            value = minuteText,
            label = "MIN",
            enabled = enabled,
            max = 300,
            onValueChange = { text ->
                minuteText = text
                onChange(text.toIntOrNull() ?: 0, secondText.toIntOrNull() ?: 0)
            },
        )
        Text(
            text = ":",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DigitField(
            value = secondText,
            label = "SEC",
            enabled = enabled,
            // 59, not 99: seconds that roll into minutes would be a surprise.
            max = 59,
            onValueChange = { text ->
                secondText = text
                onChange(minuteText.toIntOrNull() ?: 0, text.toIntOrNull() ?: 0)
            },
        )
    }
}

@Composable
private fun DigitField(
    value: String,
    label: String,
    enabled: Boolean,
    max: Int,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            val digits = raw.filter(Char::isDigit).take(3)
            val clamped = digits.toIntOrNull()?.coerceAtMost(max)
            onValueChange(
                when {
                    digits.isEmpty() -> ""
                    clamped == null -> value
                    // Preserve a typed leading zero so "05" does not jump to "5".
                    else -> if (clamped.toString().length == digits.length) digits else clamped.toString()
                },
            )
        },
        enabled = enabled,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(20.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onBackground,
            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = Modifier.width(104.dp),
    )
}

/** A labelled row of numeric choices, used by the pomodoro settings. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChoiceStrip(
    title: String,
    options: List<Int>,
    selected: Int,
    label: (Int) -> String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                val isOn = option == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
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
                            shape = RoundedCornerShape(18.dp),
                        )
                        .clickable { onSelect(option) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
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
}
