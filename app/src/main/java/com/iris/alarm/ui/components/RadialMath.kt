package com.iris.alarm.ui.components

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * The dial's angle maths, kept out of the composable so it can be tested.
 *
 * "Turns" are clockwise from 12 o'clock in the range 0f..1f, which is the
 * natural unit for a clock face: 0.25 is quarter past, 0.5 is half past.
 */
object RadialMath {

    /** Position relative to the dial centre, as turns clockwise from the top. */
    fun turns(dx: Float, dy: Float): Float {
        // atan2(dx, -dy) puts zero at the top and grows clockwise, which is the
        // opposite of the usual maths convention and the same as a clock.
        val turns = atan2(dx, -dy) / (2f * PI.toFloat())
        return if (turns < 0f) turns + 1f else turns
    }

    /** 0..59. Scrubs a minute at a time, so exact times need no second control. */
    fun toMinute(turns: Float): Int = (turns * 60f).roundToInt().mod(60)

    /** 0..23 for a 24-position ring. */
    fun toHour24(turns: Float): Int = (turns * 24f).roundToInt().mod(24)

    /**
     * 1..12 for a 12-position ring, where the top of the dial reads 12 rather
     * than 0 — a clock face has no zero.
     */
    fun toHour12(turns: Float): Int {
        val position = (turns * 12f).roundToInt().mod(12)
        return if (position == 0) 12 else position
    }
}
