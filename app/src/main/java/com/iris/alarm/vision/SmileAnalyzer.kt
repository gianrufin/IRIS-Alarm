package com.iris.alarm.vision

import android.annotation.SuppressLint
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.iris.alarm.domain.model.ChallengeThresholds

/**
 * Front-camera detector for the "Mirror Iris" challenge: a smile above
 * [ChallengeThresholds.SMILE_PROBABILITY] with both eyes open, held continuously
 * for [ChallengeThresholds.SMILE_HOLD_MILLIS].
 *
 * The hold is measured against the wall clock rather than a frame count so the
 * required 3 seconds is the same on a phone dropping frames as on one that is not.
 */
class SmileAnalyzer(
    private val onProgress: (ChallengeProgress) -> Unit,
) : VisionAnalyzer {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.25f)
            .build(),
    )

    /** Wall-clock time the current qualifying smile started, or null while broken. */
    private var smileStartedAt: Long? = null
    private var solved = false

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || solved) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        detector.process(image)
            .addOnSuccessListener { faces -> onFaces(faces) }
            .addOnFailureListener { smileStartedAt = null }
            // The frame must be closed on every path or the analyser stalls after
            // the imageQueueDepth is exhausted.
            .addOnCompleteListener { imageProxy.close() }
    }

    private fun onFaces(faces: List<Face>) {
        if (solved) return

        // Largest face wins — the user, not someone in the background.
        val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
        val smile = face?.smilingProbability
        val leftEye = face?.leftEyeOpenProbability
        val rightEye = face?.rightEyeOpenProbability

        if (face == null || smile == null || leftEye == null || rightEye == null) {
            smileStartedAt = null
            onProgress(
                ChallengeProgress(
                    fraction = 0f,
                    readout = "--",
                    hint = "NO FACE DETECTED",
                ),
            )
            return
        }

        val eyesOpen = leftEye > ChallengeThresholds.EYE_OPEN_PROBABILITY &&
            rightEye > ChallengeThresholds.EYE_OPEN_PROBABILITY
        val smiling = smile > ChallengeThresholds.SMILE_PROBABILITY

        if (!smiling || !eyesOpen) {
            smileStartedAt = null
            onProgress(
                ChallengeProgress(
                    fraction = 0f,
                    readout = formatProbability(smile),
                    hint = if (!eyesOpen) "OPEN YOUR EYES" else "SMILE WIDER",
                ),
            )
            return
        }

        val now = System.currentTimeMillis()
        val startedAt = smileStartedAt ?: now.also { smileStartedAt = it }
        val held = now - startedAt
        val fraction = (held.toFloat() / ChallengeThresholds.SMILE_HOLD_MILLIS).coerceIn(0f, 1f)

        if (held >= ChallengeThresholds.SMILE_HOLD_MILLIS) {
            solved = true
            onProgress(ChallengeProgress(1f, formatProbability(smile), "GOOD MORNING", solved = true))
        } else {
            val remaining = ChallengeThresholds.SMILE_HOLD_MILLIS - held
            onProgress(
                ChallengeProgress(
                    fraction = fraction,
                    readout = formatProbability(smile),
                    hint = "HOLD IT · ${(remaining / 1000) + 1}",
                ),
            )
        }
    }

    private fun formatProbability(value: Float): String =
        "${(value * 100).toInt()}%"

    override fun close() {
        detector.close()
    }
}
