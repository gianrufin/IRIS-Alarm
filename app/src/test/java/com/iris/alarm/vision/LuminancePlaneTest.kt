package com.iris.alarm.vision

import org.junit.Assert.assertEquals
import org.junit.Test

class LuminancePlaneTest {

    /**
     * A 3x2 sensor image whose value encodes its own coordinate, so a rotation
     * can be checked by asking where a known pixel ended up.
     *
     *   0  1  2
     *  10 11 12
     */
    private fun sensor(x: Int, y: Int) = y * 10 + x

    private fun plane(rotation: Int) =
        LuminancePlane.of(sourceWidth = 3, sourceHeight = 2, rotationDegrees = rotation, ::sensor)

    @Test
    fun `an unrotated frame is passed straight through`() {
        val plane = plane(0)

        assertEquals(3, plane.width)
        assertEquals(2, plane.height)
        assertEquals(0, plane.luminanceAt(0, 0))
        assertEquals(12, plane.luminanceAt(2, 1))
    }

    @Test
    fun `90 degrees swaps the dimensions`() {
        // This is the case that stored anchor thumbnails on their side: almost
        // every phone reports 90 for the rear camera in portrait.
        val plane = plane(90)

        assertEquals(2, plane.width)
        assertEquals(3, plane.height)
    }

    @Test
    fun `90 degrees rotates clockwise`() {
        val plane = plane(90)

        // The sensor's bottom-left (10) becomes the top-left of the upright image.
        assertEquals(10, plane.luminanceAt(0, 0))
        assertEquals(0, plane.luminanceAt(1, 0))
        assertEquals(2, plane.luminanceAt(1, 2))
    }

    @Test
    fun `180 degrees flips both axes without swapping them`() {
        val plane = plane(180)

        assertEquals(3, plane.width)
        assertEquals(2, plane.height)
        assertEquals(12, plane.luminanceAt(0, 0))
        assertEquals(0, plane.luminanceAt(2, 1))
    }

    @Test
    fun `270 degrees rotates the other way`() {
        val plane = plane(270)

        assertEquals(2, plane.width)
        assertEquals(3, plane.height)
        assertEquals(2, plane.luminanceAt(0, 0))
        assertEquals(10, plane.luminanceAt(1, 2))
    }

    @Test
    fun `a negative or over-full rotation is normalised`() {
        val negative = LuminancePlane.of(3, 2, -270, ::sensor)
        val overshoot = LuminancePlane.of(3, 2, 450, ::sensor)

        // Both are 90 degrees.
        assertEquals(10, negative.luminanceAt(0, 0))
        assertEquals(10, overshoot.luminanceAt(0, 0))
    }

    @Test
    fun `reads outside the frame are clamped rather than crashing`() {
        val plane = plane(0)

        assertEquals(plane.luminanceAt(0, 0), plane.luminanceAt(-5, -5))
        assertEquals(plane.luminanceAt(2, 1), plane.luminanceAt(99, 99))
    }
}
