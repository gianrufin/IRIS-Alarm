package com.iris.alarm.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class Alarm(
    val id: Long = 0L,
    val hour: Int,
    val minute: Int,
    val label: String = "",
    /** Empty means "fire once, at the next occurrence of this time". */
    val repeatDays: Set<DayOfWeek> = emptySet(),
    val challenge: VisionChallenge = VisionChallenge.SMILE,
    /** Serialised [com.iris.alarm.vision.SceneSignature] for the anchor challenge. */
    val anchorSignature: String? = null,
    /** Absolute path to the anchor thumbnail shown as a reminder of the spot. */
    val anchorThumbnailPath: String? = null,
    val soundUri: String? = null,
    val vibrate: Boolean = true,
    val enabled: Boolean = true,
    /**
     * Per-alarm overrides of the global [IrisSettings]. Null means "follow the
     * setting" — a weekday alarm can be given a longer auto-silence without
     * making every alarm ring for half an hour.
     */
    val autoSilenceMinutes: Int? = null,
    val volumeRampSeconds: Int? = null,
) {
    val time: LocalTime get() = LocalTime.of(hour, minute)

    val isRepeating: Boolean get() = repeatDays.isNotEmpty()

    /** An anchor alarm cannot be armed until a spot has been captured. */
    val isReadyToSchedule: Boolean
        get() = challenge != VisionChallenge.ANCHOR || anchorSignature != null

    /**
     * Next wall-clock instant this alarm should fire, at or after [from].
     * Returns null when the alarm is disabled.
     *
     * Resolved against the local zone at read time so a timezone change picks up
     * the new offset on the next reschedule.
     */
    fun nextTriggerAtMillis(from: LocalDateTime = LocalDateTime.now()): Long? {
        if (!enabled) return null

        val candidate = LocalDateTime.of(from.toLocalDate(), time)
        val next = if (repeatDays.isEmpty()) {
            if (candidate.isAfter(from)) candidate else candidate.plusDays(1)
        } else {
            // Scan the next 8 days so "today, later" and "same weekday next week" both resolve.
            (0..7).asSequence()
                .map { LocalDateTime.of(from.toLocalDate().plusDays(it.toLong()), time) }
                .first { it.isAfter(from) && it.dayOfWeek in repeatDays }
        }
        return next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    companion object {
        /** A new alarm starts at the current time, so it is one nudge away. */
        fun default(): Alarm {
            val now = LocalTime.now()
            return Alarm(hour = now.hour, minute = now.minute, repeatDays = emptySet())
        }

        /** Used only by previews and tests that need a stable date. */
        fun referenceDate(): LocalDate = LocalDate.of(2026, 1, 1)
    }
}
