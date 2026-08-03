package com.iris.alarm.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChallengeResolutionTest {

    private data class Caps(
        override val hasFrontCamera: Boolean = true,
        override val hasBackCamera: Boolean = true,
        override val hasLightSensor: Boolean = true,
    ) : DeviceCapabilities

    @Test
    fun `a fully equipped device runs what was asked for`() {
        VisionChallenge.entries.forEach { requested ->
            assertEquals(requested, resolveChallenge(requested, Caps()))
        }
    }

    @Test
    fun `no light sensor falls back to a camera challenge`() {
        val caps = Caps(hasLightSensor = false)

        assertEquals(VisionChallenge.ANCHOR, resolveChallenge(VisionChallenge.LUMEN, caps))
    }

    @Test
    fun `no front camera falls back for the smile challenge`() {
        val caps = Caps(hasFrontCamera = false)

        assertEquals(VisionChallenge.ANCHOR, resolveChallenge(VisionChallenge.SMILE, caps))
    }

    @Test
    fun `no cameras at all leaves the light sensor`() {
        val caps = Caps(hasFrontCamera = false, hasBackCamera = false)

        assertEquals(VisionChallenge.LUMEN, resolveChallenge(VisionChallenge.SMILE, caps))
        assertEquals(VisionChallenge.LUMEN, resolveChallenge(VisionChallenge.ANCHOR, caps))
    }

    @Test
    fun `a device with nothing resolves to null so the caller offers an escape`() {
        val caps = Caps(hasFrontCamera = false, hasBackCamera = false, hasLightSensor = false)

        VisionChallenge.entries.forEach { requested ->
            assertNull(resolveChallenge(requested, caps))
        }
    }

    @Test
    fun `camera refusal falls back to the light sensor when there is one`() {
        assertEquals(VisionChallenge.LUMEN, resolveWithoutCamera(Caps()))
    }

    @Test
    fun `camera refusal with no light sensor has no fallback`() {
        assertNull(resolveWithoutCamera(Caps(hasLightSensor = false)))
    }
}
