package com.iris.alarm.vision

/**
 * What every detector reports back, whatever it is measuring.
 *
 * @param fraction 0f..1f — drives the ring around the camera window / lux gauge.
 * @param readout the live number or label under the prompt ("312 LUX", "0.91").
 * @param hint one short line telling the user what to change.
 * @param solved true exactly once, when the challenge has been satisfied.
 */
data class ChallengeProgress(
    val fraction: Float = 0f,
    val readout: String = "",
    val hint: String = "",
    val solved: Boolean = false,
)

/**
 * A camera-backed detector: an `ImageAnalysis.Analyzer` that also owns a native
 * ML Kit client, so callers must close it when the challenge screen goes away.
 */
interface VisionAnalyzer : androidx.camera.core.ImageAnalysis.Analyzer, java.io.Closeable
