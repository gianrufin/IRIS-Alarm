package com.iris.alarm.domain.model

/** What the hardware can actually do, so a challenge is never mounted blind. */
interface DeviceCapabilities {
    val hasFrontCamera: Boolean
    val hasBackCamera: Boolean
    val hasLightSensor: Boolean
}

/**
 * Picks the challenge to actually run for a [requested] one.
 *
 * A ringing alarm the user physically cannot dismiss is the worst failure this
 * app has, so an unsupported challenge degrades to one the device can run rather
 * than sitting there until the auto-silence timeout.
 *
 * Since arithmetic needs no hardware, every order ends in [VisionChallenge.MATH]
 * and this never returns null in practice. The nullable return is kept so the
 * escape hatch in the UI stays wired up: it is cheap insurance against a future
 * challenge that can genuinely be unavailable everywhere.
 */
fun resolveChallenge(
    requested: VisionChallenge,
    capabilities: DeviceCapabilities,
): VisionChallenge? {
    // Preference order per requested challenge: the closest substitute first.
    val order = when (requested) {
        VisionChallenge.SMILE -> listOf(
            VisionChallenge.SMILE,
            VisionChallenge.ANCHOR,
            VisionChallenge.HUNT,
            VisionChallenge.LUMEN,
        )

        VisionChallenge.ANCHOR -> listOf(
            VisionChallenge.ANCHOR,
            // The nearest substitute for "point the rear camera at a thing".
            VisionChallenge.HUNT,
            VisionChallenge.SMILE,
            VisionChallenge.LUMEN,
        )

        VisionChallenge.HUNT -> listOf(
            VisionChallenge.HUNT,
            VisionChallenge.ANCHOR,
            VisionChallenge.SMILE,
            VisionChallenge.LUMEN,
        )

        // Lumen is the "get out of bed" challenge; a hunt is the closest thing
        // that also makes you walk somewhere.
        VisionChallenge.LUMEN -> listOf(
            VisionChallenge.LUMEN,
            VisionChallenge.HUNT,
            VisionChallenge.ANCHOR,
            VisionChallenge.SMILE,
        )

        VisionChallenge.MATH -> listOf(VisionChallenge.MATH)
    } + VisionChallenge.MATH

    return order.firstOrNull { capabilities.supports(it) }
}

/**
 * As [resolveChallenge], but excluding every camera-backed challenge — used when
 * the user has refused camera access rather than when the hardware is missing.
 */
fun resolveWithoutCamera(capabilities: DeviceCapabilities): VisionChallenge =
    if (capabilities.hasLightSensor) VisionChallenge.LUMEN else VisionChallenge.MATH

fun DeviceCapabilities.supports(challenge: VisionChallenge): Boolean = when (challenge) {
    VisionChallenge.SMILE -> hasFrontCamera
    VisionChallenge.ANCHOR -> hasBackCamera
    VisionChallenge.HUNT -> hasBackCamera
    VisionChallenge.LUMEN -> hasLightSensor
    // No hardware, no permission, nothing to be missing.
    VisionChallenge.MATH -> true
}
