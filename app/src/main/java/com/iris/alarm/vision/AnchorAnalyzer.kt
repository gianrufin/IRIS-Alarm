package com.iris.alarm.vision

import androidx.camera.core.ImageProxy
import com.iris.alarm.domain.model.ChallengeThresholds

/**
 * Rear-camera detector for the "Target Iris" challenge: get back to the place
 * the alarm was anchored to.
 *
 * The user photographs a spot when setting the alarm — the kitchen counter, the
 * bathroom mirror, a bookshelf across the room — and the alarm only stops once
 * the camera is looking at that spot again. Unlike hunting for a named object,
 * this cannot be satisfied from bed with whatever happens to be on the duvet.
 *
 * A match must hold for [ChallengeThresholds.ANCHOR_FRAME_STREAK] consecutive
 * frames so that a similar-looking wall swept past on the way does not count.
 */
class AnchorAnalyzer(
    private val reference: SceneSignature,
    private val onProgress: (ChallengeProgress) -> Unit,
) : VisionAnalyzer {

    private var streak = 0
    private var solved = false

    /** Smoothed so the readout does not jitter between frames. */
    private var smoothedSimilarity = 0f

    override fun analyze(imageProxy: ImageProxy) {
        if (solved) {
            imageProxy.close()
            return
        }

        val signature = try {
            SceneSignature.from(imageProxy)
        } finally {
            imageProxy.close()
        }

        if (signature == null) {
            onProgress(ChallengeProgress(hint = "CAMERA NOT READY"))
            return
        }

        val similarity = reference.similarityTo(signature)
        smoothedSimilarity = if (smoothedSimilarity == 0f) {
            similarity
        } else {
            SMOOTHING * smoothedSimilarity + (1f - SMOOTHING) * similarity
        }

        streak = if (similarity >= ChallengeThresholds.ANCHOR_SIMILARITY) streak + 1 else 0

        if (streak >= ChallengeThresholds.ANCHOR_FRAME_STREAK) {
            solved = true
            onProgress(
                ChallengeProgress(
                    fraction = 1f,
                    readout = percent(smoothedSimilarity),
                    hint = "YOU FOUND IT",
                    solved = true,
                ),
            )
            return
        }

        onProgress(
            ChallengeProgress(
                // Scaled against the threshold rather than 1.0, so the ring is
                // full when the user is at the bar rather than at a perfect match
                // they will never reach handheld.
                fraction = (smoothedSimilarity / ChallengeThresholds.ANCHOR_SIMILARITY)
                    .coerceIn(0f, 1f),
                readout = percent(smoothedSimilarity),
                hint = hintFor(smoothedSimilarity, streak),
            ),
        )
    }

    private fun hintFor(similarity: Float, streak: Int): String = when {
        streak > 0 -> "HOLD STILL"
        similarity >= ChallengeThresholds.ANCHOR_SIMILARITY - 0.08f -> "ALMOST — LINE IT UP"
        similarity >= WARM_SIMILARITY -> "GETTING WARMER"
        else -> "GO TO YOUR TARGET SPOT"
    }

    private fun percent(value: Float): String = "${(value * 100).toInt()}%"

    override fun close() = Unit

    private companion object {
        const val SMOOTHING = 0.6f
        const val WARM_SIMILARITY = 0.6f
    }
}
