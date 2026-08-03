package com.iris.alarm.data.local

import androidx.room.TypeConverter
import com.iris.alarm.domain.model.VisionChallenge

/**
 * Enums are stored by name rather than ordinal so reordering the enum cannot
 * silently repoint an existing alarm at a different challenge.
 */
class Converters {
    @TypeConverter
    fun challengeToString(value: VisionChallenge): String = value.name

    @TypeConverter
    fun stringToChallenge(value: String): VisionChallenge = when (value) {
        // Rows written before the object hunt became the place anchor. The alarm
        // has no captured spot yet, which the challenge screen handles by
        // falling back rather than by ringing forever.
        LEGACY_OBJECT_HUNT -> VisionChallenge.ANCHOR
        else -> runCatching { VisionChallenge.valueOf(value) }
            .getOrDefault(VisionChallenge.SMILE)
    }

    private companion object {
        const val LEGACY_OBJECT_HUNT = "OBJECT_HUNT"
    }
}
