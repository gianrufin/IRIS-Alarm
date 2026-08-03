package com.iris.alarm.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
    fun `no light sensor falls back to the hunt`() {
        // Lumen is the "get out of bed" challenge, and the hunt is the closest
        // substitute that also makes the user walk somewhere. The anchor only
        // sends them to one place they already know.
        val caps = Caps(hasLightSensor = false)

        assertEquals(VisionChallenge.HUNT, resolveChallenge(VisionChallenge.LUMEN, caps))
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
    fun `a device with no sensors at all still gets arithmetic`() {
        // Before MATH existed this returned null and the UI had to offer a plain
        // dismiss. Arithmetic needs no hardware, so there is now always a real
        // challenge to run and the escape hatch should never be reached.
        val caps = Caps(hasFrontCamera = false, hasBackCamera = false, hasLightSensor = false)

        VisionChallenge.entries.forEach { requested ->
            assertEquals(VisionChallenge.MATH, resolveChallenge(requested, caps))
        }
    }

    @Test
    fun `math is never substituted while any sensor challenge can run`() {
        // It is the last resort, not a convenient default — an alarm that
        // quietly downgrades to a keypad is not the app anyone installed.
        listOf(
            Caps(),
            Caps(hasFrontCamera = false),
            Caps(hasBackCamera = false),
            Caps(hasLightSensor = false),
            Caps(hasFrontCamera = false, hasBackCamera = false),
        ).forEach { caps ->
            VisionChallenge.entries
                .filter { it != VisionChallenge.MATH }
                .forEach { requested ->
                    assertNotEquals(
                        "\$requested on \$caps",
                        VisionChallenge.MATH,
                        resolveChallenge(requested, caps),
                    )
                }
        }
    }

    @Test
    fun `a hunt without a rear camera falls back rather than failing`() {
        assertEquals(
            VisionChallenge.SMILE,
            resolveChallenge(VisionChallenge.HUNT, Caps(hasBackCamera = false, hasLightSensor = false)),
        )
    }

    @Test
    fun `asking for math always gets math`() {
        assertEquals(VisionChallenge.MATH, resolveChallenge(VisionChallenge.MATH, Caps()))
    }

    @Test
    fun `camera refusal falls back to the light sensor when there is one`() {
        assertEquals(VisionChallenge.LUMEN, resolveWithoutCamera(Caps()))
    }

    @Test
    fun `camera refusal with no light sensor falls back to arithmetic`() {
        assertEquals(VisionChallenge.MATH, resolveWithoutCamera(Caps(hasLightSensor = false)))
    }
}
