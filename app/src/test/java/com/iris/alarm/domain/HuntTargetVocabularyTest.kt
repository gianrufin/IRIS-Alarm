package com.iris.alarm.domain

import com.iris.alarm.domain.model.HuntTarget
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hunt can only ask for things ML Kit's default labeller can actually name.
 *
 * Its vocabulary is 447 labels and it is not the one anyone would guess — there
 * is no "toothbrush", no "mug", no "book", no "towel", no "door". A target that
 * is not in it produces a challenge that can never be satisfied, which on a
 * ringing alarm means the user sits there until the auto-silence timeout.
 *
 * The list in test resources is a verbatim copy of `0-labels-en.txt` from inside
 * `mlkit_label_default_model/mobile_ica_8bit_with_metadata_tflite` in the
 * image-labeling AAR. If the ML Kit version is bumped, re-extract it.
 */
class HuntTargetVocabularyTest {

    private val vocabulary: Set<String> = requireNotNull(
        javaClass.classLoader?.getResourceAsStream(LABELS_RESOURCE),
    ) { "Missing $LABELS_RESOURCE" }
        .bufferedReader()
        .readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()

    @Test
    fun `the label file is the one the model ships`() {
        // A truncated or wrong file would make every other assertion here
        // vacuous, so the count is checked before anything is looked up in it.
        assertEquals(447, vocabulary.size)
    }

    @Test
    fun `every hunt target is a label the model can emit`() {
        val missing = HuntTarget.entries.filterNot { it.label in vocabulary }
        assertTrue(
            "Not in the ML Kit vocabulary: ${missing.map { "${it.name}=${it.label}" }}",
            missing.isEmpty(),
        )
    }

    @Test
    fun `targets are distinct, so a swap cannot land on the same object twice`() {
        val labels = HuntTarget.entries.map { it.label }
        assertEquals(labels.size, labels.toSet().size)
    }

    @Test
    fun `every target says where to look`() {
        HuntTarget.entries.forEach { target ->
            assertTrue(target.name, target.display.isNotBlank())
            assertTrue(target.name, target.where.isNotBlank())
        }
    }

    @Test
    fun `random never returns something already offered`() {
        val first = HuntTarget.random(random = Random(7))
        repeat(200) { seed ->
            assertNotEquals(first, HuntTarget.random(setOf(first), Random(seed)))
        }
    }

    @Test
    fun `excluding everything falls back to the full pool rather than crashing`() {
        // Only reachable if the swap limit ever exceeds the pool size, but
        // "throws while an alarm is ringing" is not an acceptable way to find out.
        val target = HuntTarget.random(HuntTarget.entries.toSet())
        assertTrue(target in HuntTarget.entries)
    }

    private companion object {
        const val LABELS_RESOURCE = "mlkit_label_default_model_labels.txt"
    }
}
