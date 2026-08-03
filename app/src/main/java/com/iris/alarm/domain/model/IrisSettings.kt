package com.iris.alarm.domain.model

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    val displayName: String
        get() = when (this) {
            SYSTEM -> "System"
            LIGHT -> "Light"
            DARK -> "Dark"
        }
}

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

    /**
     * Minutes after a solved challenge to ring again unless the user has
     * confirmed they are up. Solving a challenge proves you were awake for ten
     * seconds, not that you stayed awake. 0 disables the check.
     */
    val wakeCheckMinutes: Int = DEFAULT_WAKE_CHECK_MINUTES,

    /** False shows 12-hour times with an AM/PM suffix. */
    val use24Hour: Boolean = true,

    val themeMode: ThemeMode = ThemeMode.SYSTEM,

    /** Minutes a swipe-to-snooze buys before the alarm returns. */
    val snoozeMinutes: Int = DEFAULT_SNOOZE_MINUTES,

    /** False until the first-run setup has been walked through. */
    val onboardingComplete: Boolean = false,
) {
    val autoSilenceMillis: Long get() = autoSilenceMinutes * 60_000L

    /**
     * Folds an alarm's per-alarm overrides over the global settings. Everything
     * downstream reads one settings object and never has to know which value
     * came from where.
     */
    fun effectiveFor(alarm: Alarm?): IrisSettings = copy(
        autoSilenceMinutes = alarm?.autoSilenceMinutes ?: autoSilenceMinutes,
        volumeRampSeconds = alarm?.volumeRampSeconds ?: volumeRampSeconds,
    )

    companion object {
        const val DEFAULT_AUTO_SILENCE_MINUTES = 10
        const val DEFAULT_RAMP_SECONDS = 15
        const val DEFAULT_MINIMUM_VOLUME_PERCENT = 60
        const val DEFAULT_WAKE_CHECK_MINUTES = 0
        const val DEFAULT_SNOOZE_MINUTES = 9

        val AUTO_SILENCE_CHOICES = listOf(1, 5, 10, 15, 30)
        val RAMP_CHOICES = listOf(0, 5, 15, 30)
        val MINIMUM_VOLUME_CHOICES = listOf(0, 40, 60, 80, 100)
        val WAKE_CHECK_CHOICES = listOf(0, 3, 5, 10, 15)
        val SNOOZE_CHOICES = listOf(0, 5, 9, 15, 20)
    }
}
