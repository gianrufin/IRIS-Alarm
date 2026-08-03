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
 * than sitting there until the auto-silence timeout. Returns null only when no
 * detector can run at all — the caller must then offer a manual escape.
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
            VisionChallenge.LUMEN,
        )

        VisionChallenge.ANCHOR -> listOf(
            VisionChallenge.ANCHOR,
            VisionChallenge.SMILE,
            VisionChallenge.LUMEN,
        )

        // Lumen is the "get out of bed" challenge; a camera task is a closer
        // substitute than nothing, but either camera will do.
        VisionChallenge.LUMEN -> listOf(
            VisionChallenge.LUMEN,
            VisionChallenge.ANCHOR,
            VisionChallenge.SMILE,
        )
    }

    return order.firstOrNull { capabilities.supports(it) }
}

/** As [resolveChallenge], but excluding every camera-backed challenge. */
fun resolveWithoutCamera(capabilities: DeviceCapabilities): VisionChallenge? =
    VisionChallenge.LUMEN.takeIf { capabilities.hasLightSensor }

fun DeviceCapabilities.supports(challenge: VisionChallenge): Boolean = when (challenge) {
    VisionChallenge.SMILE -> hasFrontCamera
    VisionChallenge.ANCHOR -> hasBackCamera
    VisionChallenge.LUMEN -> hasLightSensor
}
