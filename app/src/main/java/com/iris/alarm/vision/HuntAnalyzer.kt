package com.iris.alarm.vision

import android.annotation.SuppressLint
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.iris.alarm.domain.model.ChallengeThresholds
import com.iris.alarm.domain.model.HuntTarget

/**
 * Rear-camera detector for the "Hunt Iris" challenge: find the object the app
 * named this morning.
 *
 * The target is chosen at ring time from a fixed pool, not when the alarm is
 * set, so it cannot be staged on the bedside table the night before. It is also
 * the only challenge that can be defeated by simply not owning the thing, which
 * is what the swap exists for — [HuntTarget] and the view model own that, and
 * this class only ever looks for the one label it was given.
 *
 * A match must hold for [ChallengeThresholds.HUNT_FRAME_STREAK] consecutive
 * frames so a chair glimpsed on the way to the kitchen does not end the alarm.
 */
class HuntAnalyzer(
    private val target: HuntTarget,
    private val onProgress: (ChallengeProgress) -> Unit,
) : VisionAnalyzer {

    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            // Below the detector's own bar, because this reads the confidence
            // itself and applies HUNT_CONFIDENCE. Filtering twice would make the
            // threshold constant a lie.
            .setConfidenceThreshold(0.3f)
            .build(),
    )

    private var streak = 0
    private var solved = false

    /** Best confidence seen for the target so far, for the "warmer" readout. */
    private var best = 0f

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        if (solved) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            onProgress(ChallengeProgress(hint = "CAMERA NOT READY"))
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        labeler.process(image)
            .addOnSuccessListener { labels ->
                val confidence = labels
                    .firstOrNull { it.text.equals(target.label, ignoreCase = true) }
                    ?.confidence
                    ?: 0f
                report(confidence)
            }
            .addOnFailureListener {
                onProgress(ChallengeProgress(hint = "COULD NOT READ THE CAMERA"))
            }
            // Held until the labeller is done with it, or CameraX starves.
            .addOnCompleteListener { imageProxy.close() }
    }

    private fun report(confidence: Float) {
        if (solved) return
        if (confidence > best) best = confidence

        streak = if (confidence >= ChallengeThresholds.HUNT_CONFIDENCE) streak + 1 else 0

        if (streak >= ChallengeThresholds.HUNT_FRAME_STREAK) {
            solved = true
            onProgress(
                ChallengeProgress(
                    fraction = 1f,
                    readout = percent(confidence),
                    hint = "THAT'S IT",
                    solved = true,
                ),
            )
            return
        }

        onProgress(
            ChallengeProgress(
                // Scaled against the threshold, so the ring fills as the user
                // reaches the bar rather than a certainty no handheld frame hits.
                fraction = (confidence / ChallengeThresholds.HUNT_CONFIDENCE).coerceIn(0f, 1f),
                readout = percent(confidence),
                hint = hintFor(confidence),
            ),
        )
    }

    private fun hintFor(confidence: Float): String = when {
        streak > 0 -> "HOLD IT THERE"
        confidence >= WARM_CONFIDENCE -> "ALMOST — GET CLOSER"
        confidence > 0f -> "SOMETHING LIKE IT — FILL THE FRAME"
        else -> "FIND ${target.display.uppercase()}"
    }

    private fun percent(value: Float): String = "${(value * 100).toInt()}%"

    override fun close() {
        labeler.close()
    }

    private companion object {
        const val WARM_CONFIDENCE = 0.35f
    }
}
