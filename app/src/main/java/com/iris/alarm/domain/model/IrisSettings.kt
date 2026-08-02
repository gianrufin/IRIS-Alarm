package com.iris.alarm.domain.model

/**
 * App-wide preferences. Every field has a default that makes IRIS behave sanely
 * on a fresh install, so a missing DataStore value is never a special case.
 */
data class IrisSettings(
    /** Pre-selected challenge when creating a new alarm. */
    val defaultChallenge: VisionChallenge = VisionChallenge.SMILE,

    /** How long an unsolved alarm rings before giving up. */
    val autoSilenceMinutes: Int = DEFAULT_AUTO_SILENCE_MINUTES,

    /**
     * Seconds spent fading from near-silence to full volume. 0 starts at full
     * volume immediately.
     */
    val volumeRampSeconds: Int = DEFAULT_RAMP_SECONDS,

    /**
     * Alarm-stream volume floor as a percentage of the device maximum. An alarm
     * is useless if the stream is muted, so IRIS raises it for the duration and
     * puts it back afterwards. 0 disables the behaviour entirely.
     */
    val minimumVolumePercent: Int = DEFAULT_MINIMUM_VOLUME_PERCENT,
) {
    val autoSilenceMillis: Long get() = autoSilenceMinutes * 60_000L

    companion object {
        const val DEFAULT_AUTO_SILENCE_MINUTES = 10
        const val DEFAULT_RAMP_SECONDS = 15
        const val DEFAULT_MINIMUM_VOLUME_PERCENT = 60

        val AUTO_SILENCE_CHOICES = listOf(1, 5, 10, 15, 30)
        val RAMP_CHOICES = listOf(0, 5, 15, 30)
        val MINIMUM_VOLUME_CHOICES = listOf(0, 40, 60, 80, 100)
    }
}
