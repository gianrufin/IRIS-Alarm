package com.iris.alarm.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class EffectiveSettingsTest {

    private val global = IrisSettings(
        autoSilenceMinutes = 10,
        volumeRampSeconds = 15,
        minimumVolumePercent = 60,
        wakeCheckMinutes = 5,
    )

    @Test
    fun `an alarm with no overrides follows the global settings`() {
        val effective = global.effectiveFor(Alarm(hour = 7, minute = 0))

        assertEquals(10, effective.autoSilenceMinutes)
        assertEquals(15, effective.volumeRampSeconds)
    }

    @Test
    fun `overrides win over the global settings`() {
        val alarm = Alarm(
            hour = 7,
            minute = 0,
            autoSilenceMinutes = 30,
            volumeRampSeconds = 0,
        )

        val effective = global.effectiveFor(alarm)

        assertEquals(30, effective.autoSilenceMinutes)
        assertEquals(0, effective.volumeRampSeconds)
    }

    @Test
    fun `a zero override is honoured rather than treated as unset`() {
        // Ramp 0 means "start at full volume", which must not fall through to the
        // global 15 seconds just because zero is falsy in other languages.
        val effective = global.effectiveFor(Alarm(hour = 7, minute = 0, volumeRampSeconds = 0))

        assertEquals(0, effective.volumeRampSeconds)
    }

    @Test
    fun `one override does not disturb the other`() {
        val effective = global.effectiveFor(Alarm(hour = 7, minute = 0, autoSilenceMinutes = 1))

        assertEquals(1, effective.autoSilenceMinutes)
        assertEquals(15, effective.volumeRampSeconds)
    }

    @Test
    fun `settings not overridable per alarm are untouched`() {
        val effective = global.effectiveFor(Alarm(hour = 7, minute = 0, autoSilenceMinutes = 1))

        assertEquals(60, effective.minimumVolumePercent)
        assertEquals(5, effective.wakeCheckMinutes)
    }

    @Test
    fun `a null alarm falls back to the global settings entirely`() {
        assertEquals(global, global.effectiveFor(null))
    }

    @Test
    fun `auto-silence is converted to millis from the effective value`() {
        val effective = global.effectiveFor(Alarm(hour = 7, minute = 0, autoSilenceMinutes = 30))

        assertEquals(30 * 60_000L, effective.autoSilenceMillis)
    }
}
