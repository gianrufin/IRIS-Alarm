package com.iris.alarm.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneSignatureTest {

    private fun signature(hash: Long, weightOnBucket: Int = 0): SceneSignature {
        val histogram = FloatArray(SceneSignature.HISTOGRAM_BUCKETS)
        histogram[weightOnBucket] = 1f
        return SceneSignature(hash, histogram)
    }

    @Test
    fun `a signature matches itself perfectly`() {
        val scene = signature(0x0F0F0F0F0F0F0F0FL)

        assertEquals(1f, scene.similarityTo(scene), 0.0001f)
    }

    @Test
    fun `an inverted hash on a different exposure scores near zero`() {
        val a = signature(0L, weightOnBucket = 0)
        val b = signature(-1L, weightOnBucket = 8)

        assertTrue("Opposites should not look alike", a.similarityTo(b) < 0.05f)
    }

    @Test
    fun `matching structure survives a different exposure`() {
        // Same room, different time of day: identical edges, shifted brightness.
        val evening = signature(0x0F0F0F0F0F0F0F0FL, weightOnBucket = 2)
        val morning = signature(0x0F0F0F0F0F0F0F0FL, weightOnBucket = 9)

        // Structure alone carries it well above "getting warmer" but short of a
        // pass, which is the intent: the user still has to line the shot up.
        val similarity = evening.similarityTo(morning)
        assertEquals(SceneSignature.STRUCTURE_WEIGHT, similarity, 0.0001f)
        assertTrue(similarity > 0.6f)
    }

    @Test
    fun `a few different bits barely move the score`() {
        val a = signature(0L)
        val b = signature(0b111L)

        // 3 of 64 bits differ, so structure scores 61/64 and exposure is identical.
        assertEquals(
            SceneSignature.STRUCTURE_WEIGHT * (61f / 64f) + (1f - SceneSignature.STRUCTURE_WEIGHT),
            a.similarityTo(b),
            0.0001f,
        )
    }

    @Test
    fun `signatures survive a round trip through storage`() {
        val original = signature(-9223372036854775807L, weightOnBucket = 5)

        val restored = SceneSignature.deserialise(original.serialise())

        assertEquals(original, restored)
    }

    @Test
    fun `malformed stored signatures deserialise to null rather than throwing`() {
        assertNull(SceneSignature.deserialise(null))
        assertNull(SceneSignature.deserialise(""))
        assertNull(SceneSignature.deserialise("not-a-hash,0.1"))
        // Right shape, wrong number of buckets.
        assertNull(SceneSignature.deserialise("12,0.1"))
    }
}
