package com.iris.alarm.domain.model

/**
 * How an alarm is dismissed. Every IRIS alarm requires a real-world action —
 * there is no plain "stop" button once a challenge is attached.
 */
enum class VisionChallenge {
    /** Front camera + ML Kit face detection: hold a smile with eyes open. */
    SMILE,

    /**
     * Rear camera + scene matching: go back to the place the alarm was anchored
     * to when it was set.
     */
    ANCHOR,

    /** Ambient light sensor: walk to a bright place until lux clears the target. */
    LUMEN,
    ;

    val displayName: String
        get() = when (this) {
            SMILE -> "Mirror Iris"
            ANCHOR -> "Target Iris"
            LUMEN -> "Light Iris"
        }

    val prompt: String
        get() = when (this) {
            SMILE -> "SMILE"
            ANCHOR -> "FIND YOUR SPOT"
            LUMEN -> "FIND LIGHT"
        }
}

/** Tuning constants for the three detectors, kept in one place. */
object ChallengeThresholds {
    const val SMILE_PROBABILITY = 0.8f
    const val EYE_OPEN_PROBABILITY = 0.7f

    /** The smile must be held continuously for this long before dismissal. */
    const val SMILE_HOLD_MILLIS = 3_000L

    /**
     * How close a live frame must be to the captured anchor. Handheld framing is
     * never exact and the light will have changed since capture, so this is a
     * "clearly the same place" bar rather than a "pixel identical" one.
     */
    const val ANCHOR_SIMILARITY = 0.82f

    /** Consecutive qualifying frames required before an anchor match passes. */
    const val ANCHOR_FRAME_STREAK = 4

    const val LUMEN_TARGET = 500f

    /** Lux must stay above target this long so a passing torch flash does not count. */
    const val LUMEN_HOLD_MILLIS = 1_500L
}
