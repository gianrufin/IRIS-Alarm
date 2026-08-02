package com.iris.alarm.data.local

import androidx.room.TypeConverter
import com.iris.alarm.domain.model.HuntTarget
import com.iris.alarm.domain.model.VisionChallenge

/**
 * Enums are stored by name rather than ordinal so reordering the enum cannot
 * silently repoint an existing alarm at a different challenge.
 */
class Converters {
    @TypeConverter
    fun challengeToString(value: VisionChallenge): String = value.name

    @TypeConverter
    fun stringToChallenge(value: String): VisionChallenge =
        runCatching { VisionChallenge.valueOf(value) }.getOrDefault(VisionChallenge.SMILE)

    @TypeConverter
    fun targetToString(value: HuntTarget): String = value.name

    @TypeConverter
    fun stringToTarget(value: String): HuntTarget =
        runCatching { HuntTarget.valueOf(value) }.getOrDefault(HuntTarget.CUP)
}
