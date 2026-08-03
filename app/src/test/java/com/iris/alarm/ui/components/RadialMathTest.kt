package com.iris.alarm.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class RadialMathTest {

    @Test
    fun `straight up is zero turns and the quadrants follow clockwise`() {
        assertEquals(0f, RadialMath.turns(dx = 0f, dy = -10f), 0.001f)
        assertEquals(0.25f, RadialMath.turns(dx = 10f, dy = 0f), 0.001f)
        assertEquals(0.5f, RadialMath.turns(dx = 0f, dy = 10f), 0.001f)
        assertEquals(0.75f, RadialMath.turns(dx = -10f, dy = 0f), 0.001f)
    }

    @Test
    fun `turns are always positive, never negative angles`() {
        // Just anticlockwise of the top must read as nearly a full turn, not -0.01.
        val justLeftOfTop = RadialMath.turns(dx = -1f, dy = -100f)

        assertEquals(true, justLeftOfTop > 0.99f && justLeftOfTop < 1f)
    }

    @Test
    fun `the clock face maps to the times you would expect`() {
        assertEquals(0, RadialMath.toMinute(0f))
        assertEquals(15, RadialMath.toMinute(0.25f))
        assertEquals(30, RadialMath.toMinute(0.5f))
        assertEquals(45, RadialMath.toMinute(0.75f))
    }

    @Test
    fun `a full turn wraps back to zero rather than overflowing`() {
        assertEquals(0, RadialMath.toMinute(1f))
        assertEquals(0, RadialMath.toHour24(1f))
        assertEquals(12, RadialMath.toHour12(1f))
    }

    @Test
    fun `the top of a 12 hour ring reads 12, not 0`() {
        assertEquals(12, RadialMath.toHour12(0f))
        assertEquals(3, RadialMath.toHour12(0.25f))
        assertEquals(6, RadialMath.toHour12(0.5f))
        assertEquals(9, RadialMath.toHour12(0.75f))
    }

    @Test
    fun `the top of a 24 hour ring reads 0`() {
        assertEquals(0, RadialMath.toHour24(0f))
        assertEquals(6, RadialMath.toHour24(0.25f))
        assertEquals(12, RadialMath.toHour24(0.5f))
        assertEquals(18, RadialMath.toHour24(0.75f))
    }

    @Test
    fun `values round to the nearest position rather than truncating`() {
        // Just short of the 20-minute mark should read 20, not 19.
        assertEquals(20, RadialMath.toMinute(0.3329f))
        assertEquals(20, RadialMath.toMinute(0.3338f))
    }

    @Test
    fun `every minute on the dial is reachable`() {
        for (minute in 0..59) {
            assertEquals(minute, RadialMath.toMinute(minute / 60f))
        }
    }

    @Test
    fun `a position slightly past the last minute wraps to zero`() {
        // 59.6 minutes rounds to 60, which is midnight on the ring, not minute 60.
        assertEquals(0, RadialMath.toMinute(59.6f / 60f))
    }
}
