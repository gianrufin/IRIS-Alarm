package com.iris.alarm.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeFormatTest {

    @Test
    fun `24 hour times are zero padded and carry no suffix`() {
        assertEquals("01:43", formatClock(1, 43, use24Hour = true).digits)
        assertEquals("00:00", formatClock(0, 0, use24Hour = true).digits)
        assertEquals("23:59", formatClock(23, 59, use24Hour = true).digits)
        assertNull(formatClock(13, 5, use24Hour = true).suffix)
    }

    @Test
    fun `midnight and noon both display as 12 in 12 hour mode`() {
        assertEquals("12:00", formatClock(0, 0, use24Hour = false).digits)
        assertEquals("AM", formatClock(0, 0, use24Hour = false).suffix)
        assertEquals("12:00", formatClock(12, 0, use24Hour = false).digits)
        assertEquals("PM", formatClock(12, 0, use24Hour = false).suffix)
    }

    @Test
    fun `afternoon hours wrap in 12 hour mode`() {
        val evening = formatClock(19, 5, use24Hour = false)

        assertEquals("7:05", evening.digits)
        assertEquals("PM", evening.suffix)
    }

    @Test
    fun `every hour survives a round trip through the 12 hour wheel`() {
        for (hour in 0..23) {
            val roundTripped = to24Hour(to12Hour(hour), isPm(hour))
            assertEquals("Hour $hour did not survive the round trip", hour, roundTripped)
        }
    }

    @Test
    fun `toggling AM to PM moves the hour by twelve`() {
        // 1am with the PM chip tapped must become 13:00, not 25:00 or 1:00.
        assertEquals(13, to24Hour(to12Hour(1), isPm = true))
        assertEquals(1, to24Hour(to12Hour(13), isPm = false))
    }

    @Test
    fun `inline form joins the digits and suffix`() {
        assertEquals("7:05 PM", formatClock(19, 5, use24Hour = false).inline())
        assertEquals("19:05", formatClock(19, 5, use24Hour = true).inline())
    }
}
