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

    /**
     * Rear camera + ML Kit image labelling: find a household object the app
     * names at random, so it cannot be staged the night before.
     */
    HUNT,

    /** Ambient light sensor: walk to a bright place until lux clears the target. */
    LUMEN,

    /**
     * Arithmetic. The only challenge that needs no hardware at all, which makes
     * it the universal fallback — a device with no camera and no light sensor
     * can still be made to prove someone is awake.
     */
    MATH,
    ;

    val displayName: String
        get() = when (this) {
            SMILE -> "Mirror Iris"
            ANCHOR -> "Target Iris"
            HUNT -> "Hunt Iris"
            LUMEN -> "Light Iris"
            MATH -> "Math Iris"
        }

    val prompt: String
        get() = when (this) {
            SMILE -> "SMILE"
            ANCHOR -> "FIND YOUR SPOT"
            HUNT -> "FIND THE OBJECT"
            LUMEN -> "FIND LIGHT"
            MATH -> "SOLVE IT"
        }

    /** True when the challenge mounts a camera, which drives the permission flow. */
    val needsCamera: Boolean
        get() = this == SMILE || this == ANCHOR || this == HUNT
}

/** Tuning constants for the detectors, kept in one place. */
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

    /**
     * Confidence the labeller must report for the hunted object. Deliberately
     * below the 0.7 that would suit a photo-tagging app: this is a handheld
     * camera in bad morning light being waved at a sink, and a challenge that
     * cannot be passed is a worse failure than one passed slightly early.
     */
    const val HUNT_CONFIDENCE = 0.55f

    /** Consecutive qualifying frames, so a glimpse in passing does not count. */
    const val HUNT_FRAME_STREAK = 3

    /** Re-rolls allowed when the named object genuinely is not in the house. */
    const val HUNT_MAX_SWAPS = 2

    const val LUMEN_TARGET = 500f

    /** Lux must stay above target this long so a passing torch flash does not count. */
    const val LUMEN_HOLD_MILLIS = 1_500L
}
