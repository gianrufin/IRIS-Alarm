package com.iris.alarm.domain.model

/**
 * How an alarm is dismissed. Every IRIS alarm requires a real-world action —
 * there is no plain "stop" button once a challenge is attached.
 */
enum class VisionChallenge {
    /** Front camera + ML Kit face detection: hold a smile with eyes open. */
    SMILE,

    /** Rear camera + ML Kit image labeling: point the phone at a target object. */
    OBJECT_HUNT,

    /** Ambient light sensor: walk to a bright place until lux clears the target. */
    LUMEN,
    ;

    val displayName: String
        get() = when (this) {
            SMILE -> "Mirror Iris"
            OBJECT_HUNT -> "Target Iris"
            LUMEN -> "Light Iris"
        }

    val prompt: String
        get() = when (this) {
            SMILE -> "SMILE"
            OBJECT_HUNT -> "FIND IT"
            LUMEN -> "FIND LIGHT"
        }
}

/**
 * Targets offered for [VisionChallenge.OBJECT_HUNT].
 *
 * Every entry MUST exist in the default on-device labeler's vocabulary — a
 * target the model cannot emit is a target the user can never hunt down, and the
 * alarm would only stop at the auto-silence timeout. `HuntTargetLabelTest`
 * asserts this against the label list shipped inside the model asset; add a
 * target only after it passes.
 */
enum class HuntTarget(
    /**
     * Label as emitted by ML Kit's on-device image labeler. Matching is done on
     * this exact string, case-insensitively.
     */
    val mlKitLabel: String,
) {
    CUP("Cup"),
    SHOE("Shoe"),
    PLANT("Plant"),
    GLASSES("Glasses"),
    JACKET("Jacket"),
    ;

    val displayName: String get() = mlKitLabel.uppercase()
}

/** Tuning constants for the three detectors, kept in one place. */
object ChallengeThresholds {
    const val SMILE_PROBABILITY = 0.8f
    const val EYE_OPEN_PROBABILITY = 0.7f

    /** The smile must be held continuously for this long before dismissal. */
    const val SMILE_HOLD_MILLIS = 3_000L

    const val OBJECT_CONFIDENCE = 0.8f

    /** Consecutive qualifying frames required before an object hunt passes. */
    const val OBJECT_FRAME_STREAK = 5

    const val LUMEN_TARGET = 500f

    /** Lux must stay above target this long so a passing torch flash does not count. */
    const val LUMEN_HOLD_MILLIS = 1_500L
}
