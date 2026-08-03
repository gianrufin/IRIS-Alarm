package com.iris.alarm.ui.tools

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormattingTest {

    @Test
    fun `stopwatch shows minutes, seconds and hundredths`() {
        assertEquals("00:00.00", formatStopwatch(0))
        assertEquals("00:01.23", formatStopwatch(1_230))
        assertEquals("12:34.56", formatStopwatch(12 * 60_000 + 34_560))
    }

    @Test
    fun `stopwatch grows an hours field only when it needs one`() {
        assertEquals("59:59.99", formatStopwatch(59 * 60_000 + 59_990))
        assertEquals("1:00:00.00", formatStopwatch(3_600_000))
    }

    @Test
    fun `countdown rounds up so it never shows zero while time remains`() {
        // A timer reading 00:00 with 400ms left would look broken.
        assertEquals("00:01", formatCountdown(1))
        assertEquals("00:01", formatCountdown(400))
        assertEquals("00:01", formatCountdown(1_000))
        assertEquals("00:00", formatCountdown(0))
    }

    @Test
    fun `countdown grows an hours field only when it needs one`() {
        assertEquals("59:59", formatCountdown(59 * 60_000 + 59_000))
        assertEquals("1:00:00", formatCountdown(3_600_000))
    }

    @Test
    fun `a negative remaining time is clamped rather than rendered`() {
        assertEquals("00:00", formatCountdown(-5_000))
    }
}
