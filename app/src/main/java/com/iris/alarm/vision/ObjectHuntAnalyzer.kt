package com.iris.alarm.vision

import android.annotation.SuppressLint
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.iris.alarm.domain.model.ChallengeThresholds
import com.iris.alarm.domain.model.HuntTarget

/**
 * Rear-camera detector for the "Target Iris" challenge. Dismisses once the target
 * label clears [ChallengeThresholds.OBJECT_CONFIDENCE] on
 * [ChallengeThresholds.OBJECT_FRAME_STREAK] consecutive frames — a streak rather
 * than a single frame, so a lucky misread while the phone swings past a shelf
 * cannot end the alarm.
 */
class ObjectHuntAnalyzer(
    private val target: HuntTarget,
    private val onProgress: (ChallengeProgress) -> Unit,
) : VisionAnalyzer {

    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            // Below the pass threshold on purpose: we want to show the user a
            // rising confidence readout as they get closer, not a blank until 80%.
            .setConfidenceThreshold(0.4f)
            .build(),
    )

    private var streak = 0
    private var solved = false

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || solved) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        labeler.process(image)
            .addOnSuccessListener { labels ->
                val confidence = labels
                    .filter { it.text.equals(target.mlKitLabel, ignoreCase = true) }
                    .maxOfOrNull { it.confidence }
                    ?: 0f
                onConfidence(confidence)
            }
            .addOnFailureListener { streak = 0 }
            .addOnCompleteListener { imageProxy.close() }
    }

    private fun onConfidence(confidence: Float) {
        if (solved) return

        streak = if (confidence > ChallengeThresholds.OBJECT_CONFIDENCE) streak + 1 else 0

        if (streak >= ChallengeThresholds.OBJECT_FRAME_STREAK) {
            solved = true
            onProgress(
                ChallengeProgress(
                    fraction = 1f,
                    readout = "${(confidence * 100).toInt()}%",
                    hint = "${target.displayName} FOUND",
                    solved = true,
                ),
            )
            return
        }

        // Show whichever is further along: how close the confidence is to the bar,
        // or how far into the streak we are once it is cleared.
        val confidenceFraction = (confidence / ChallengeThresholds.OBJECT_CONFIDENCE)
            .coerceIn(0f, 1f)
        val streakFraction = streak.toFloat() / ChallengeThresholds.OBJECT_FRAME_STREAK
        onProgress(
            ChallengeProgress(
                fraction = maxOf(confidenceFraction * 0.6f, streakFraction),
                readout = "${(confidence * 100).toInt()}%",
                hint = if (streak > 0) "HOLD STEADY" else "POINT AT A ${target.displayName}",
            ),
        )
    }

    override fun close() {
        labeler.close()
    }
}
