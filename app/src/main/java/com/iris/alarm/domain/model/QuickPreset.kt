package com.iris.alarm.domain.model

import java.util.UUID

/**
 * A user-defined or default alarm duration preset (e.g. "15m nap", "1h study").
 * Tapping a preset creates and arms an alarm for [durationMinutes] from now in one touch,
 * bypassing the full dial interaction.
 */
data class QuickPreset(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val durationMinutes: Int,
) {
    val formattedDuration: String
        get() {
            val hours = durationMinutes / 60
            val minutes = durationMinutes % 60
            return when {
                hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
                hours > 0 -> "${hours}h"
                else -> "${minutes}m"
            }
        }

    companion object {
        val DEFAULTS = listOf(
            QuickPreset(id = "default_15m", label = "15m nap", durationMinutes = 15),
            QuickPreset(id = "default_30m", label = "30m power nap", durationMinutes = 30),
            QuickPreset(id = "default_45m", label = "45m focus", durationMinutes = 45),
            QuickPreset(id = "default_1h", label = "1h study", durationMinutes = 60),
            QuickPreset(id = "default_2h", label = "2h deep work", durationMinutes = 120),
        )
    }
}
