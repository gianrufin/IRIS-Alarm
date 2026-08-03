package com.iris.alarm.ui.components

import java.time.LocalTime

/**
 * A clock reading split into the digits and the AM/PM suffix, so the hero clock
 * can typeset the suffix small beside enormous digits instead of shrinking the
 * whole line to fit "06:30 AM".
 */
data class ClockText(val digits: String, val suffix: String?) {
    /** Single-line form, for rows where the suffix does not get its own slot. */
    fun inline(): String = if (suffix == null) digits else "$digits $suffix"
}

fun formatClock(hour: Int, minute: Int, use24Hour: Boolean): ClockText {
    val paddedMinute = minute.toString().padStart(2, '0')
    if (use24Hour) {
        return ClockText("${hour.toString().padStart(2, '0')}:$paddedMinute", suffix = null)
    }

    // 0 and 12 both display as 12; everything past noon wraps.
    val display = when {
        hour % 12 == 0 -> 12
        else -> hour % 12
    }
    return ClockText("$display:$paddedMinute", if (hour < 12) "AM" else "PM")
}

fun formatClock(time: LocalTime, use24Hour: Boolean): ClockText =
    formatClock(time.hour, time.minute, use24Hour)

/** Converts a 12-hour wheel selection back to the 0..23 hour the model stores. */
fun to24Hour(hour12: Int, isPm: Boolean): Int = when {
    hour12 == 12 && !isPm -> 0
    hour12 == 12 && isPm -> 12
    isPm -> hour12 + 12
    else -> hour12
}

/** The 1..12 value a stored hour should show on a 12-hour wheel. */
fun to12Hour(hour24: Int): Int = if (hour24 % 12 == 0) 12 else hour24 % 12

fun isPm(hour24: Int): Boolean = hour24 >= 12
