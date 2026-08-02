package com.iris.alarm.domain.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the invariant that every [HuntTarget] is a label the bundled ML Kit
 * labeler can actually emit. A target outside the model's vocabulary can never
 * reach the confidence threshold, so that alarm would ring until the auto-silence
 * timeout with no way for the user to stop it.
 *
 * The vocabulary is read from the model asset itself rather than a checked-in
 * copy, so upgrading the ML Kit dependency re-validates the targets for free.
 */
@RunWith(AndroidJUnit4::class)
class HuntTargetLabelTest {

    @Test
    fun everyHuntTargetExistsInTheModelVocabulary() {
        val labels = readModelLabels()
        assertTrue(
            "Model vocabulary looks wrong: only ${labels.size} labels",
            labels.size > 100,
        )

        val missing = HuntTarget.entries.filterNot { target ->
            labels.any { it.equals(target.mlKitLabel, ignoreCase = true) }
        }

        assertTrue(
            "These hunt targets are not in the labeler's vocabulary and could " +
                "never be found: ${missing.map(HuntTarget::mlKitLabel)}",
            missing.isEmpty(),
        )
    }

    /**
     * The model is a TFLite flatbuffer with a zip of associated files appended;
     * `0-labels-en.txt` inside it is the English vocabulary, one label per line.
     */
    private fun readModelLabels(): List<String> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val assetPath = context.assets.list(MODEL_DIR)
            ?.firstOrNull()
            ?.let { "$MODEL_DIR/$it" }
            ?: error("No model asset found under $MODEL_DIR")

        // ZipFile needs random access, and an asset stream has none.
        val copy = File.createTempFile("label_model", ".zip", context.cacheDir)
        try {
            context.assets.open(assetPath).use { input ->
                copy.outputStream().use(input::copyTo)
            }
            ZipFile(copy).use { zip ->
                val entry = zip.getEntry(LABELS_ENTRY)
                    ?: error("$LABELS_ENTRY missing from the model asset")
                return zip.getInputStream(entry)
                    .bufferedReader()
                    .readLines()
                    .map(String::trim)
                    .filter(String::isNotEmpty)
            }
        } finally {
            copy.delete()
        }
    }

    private companion object {
        const val MODEL_DIR = "mlkit_label_default_model"
        const val LABELS_ENTRY = "0-labels-en.txt"
    }
}
