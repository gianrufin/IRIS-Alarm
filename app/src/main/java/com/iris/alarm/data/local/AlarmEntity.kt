package com.iris.alarm.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.iris.alarm.domain.model.Alarm
import com.iris.alarm.domain.model.HuntTarget
import com.iris.alarm.domain.model.VisionChallenge
import java.time.DayOfWeek

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val hour: Int,
    val minute: Int,
    val label: String,
    /** Bitmask over [DayOfWeek.getValue] (Monday = bit 0 … Sunday = bit 6). */
    val repeatMask: Int,
    val challenge: VisionChallenge,
    val huntTarget: HuntTarget,
    val soundUri: String?,
    val vibrate: Boolean,
    val enabled: Boolean,
)

fun AlarmEntity.toDomain(): Alarm = Alarm(
    id = id,
    hour = hour,
    minute = minute,
    label = label,
    repeatDays = repeatMask.toDays(),
    challenge = challenge,
    huntTarget = huntTarget,
    soundUri = soundUri,
    vibrate = vibrate,
    enabled = enabled,
)

fun Alarm.toEntity(): AlarmEntity = AlarmEntity(
    id = id,
    hour = hour,
    minute = minute,
    label = label,
    repeatMask = repeatDays.toMask(),
    challenge = challenge,
    huntTarget = huntTarget,
    soundUri = soundUri,
    vibrate = vibrate,
    enabled = enabled,
)

private fun Set<DayOfWeek>.toMask(): Int =
    fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

private fun Int.toDays(): Set<DayOfWeek> =
    DayOfWeek.values().filterTo(mutableSetOf()) { this and (1 shl (it.value - 1)) != 0 }
