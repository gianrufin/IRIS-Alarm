package com.iris.alarm.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.iris.alarm.domain.model.IrisSettings
import com.iris.alarm.domain.model.MathDifficulty
import com.iris.alarm.domain.model.ThemeMode
import com.iris.alarm.domain.model.VisionChallenge
import com.iris.alarm.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {

    override val settings: Flow<IrisSettings> = context.dataStore.data.map { prefs ->
        IrisSettings(
            defaultChallenge = prefs[Keys.DEFAULT_CHALLENGE]
                ?.let { name -> runCatching { VisionChallenge.valueOf(name) }.getOrNull() }
                ?: VisionChallenge.SMILE,
            autoSilenceMinutes = prefs[Keys.AUTO_SILENCE_MINUTES]
                ?: IrisSettings.DEFAULT_AUTO_SILENCE_MINUTES,
            volumeRampSeconds = prefs[Keys.RAMP_SECONDS]
                ?: IrisSettings.DEFAULT_RAMP_SECONDS,
            minimumVolumePercent = prefs[Keys.MINIMUM_VOLUME_PERCENT]
                ?: IrisSettings.DEFAULT_MINIMUM_VOLUME_PERCENT,
            wakeCheckMinutes = prefs[Keys.WAKE_CHECK_MINUTES]
                ?: IrisSettings.DEFAULT_WAKE_CHECK_MINUTES,
            use24Hour = prefs[Keys.USE_24_HOUR] ?: true,
            themeMode = prefs[Keys.THEME_MODE]
                ?.let { name -> runCatching { ThemeMode.valueOf(name) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            snoozeMinutes = prefs[Keys.SNOOZE_MINUTES] ?: IrisSettings.DEFAULT_SNOOZE_MINUTES,
            mathDifficulty = prefs[Keys.MATH_DIFFICULTY]
                ?.let { name -> runCatching { MathDifficulty.valueOf(name) }.getOrNull() }
                ?: MathDifficulty.MEDIUM,
            mathProblemCount = prefs[Keys.MATH_PROBLEM_COUNT]
                ?: IrisSettings.DEFAULT_MATH_PROBLEMS,
            onboardingComplete = prefs[Keys.ONBOARDING_COMPLETE] ?: false,
        )
    }

    override suspend fun current(): IrisSettings = settings.first()

    override suspend fun setDefaultChallenge(challenge: VisionChallenge) {
        context.dataStore.edit { it[Keys.DEFAULT_CHALLENGE] = challenge.name }
    }

    override suspend fun setAutoSilenceMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.AUTO_SILENCE_MINUTES] = minutes }
    }

    override suspend fun setVolumeRampSeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.RAMP_SECONDS] = seconds }
    }

    override suspend fun setMinimumVolumePercent(percent: Int) {
        context.dataStore.edit { it[Keys.MINIMUM_VOLUME_PERCENT] = percent }
    }

    override suspend fun setWakeCheckMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.WAKE_CHECK_MINUTES] = minutes }
    }

    override suspend fun setUse24Hour(use24Hour: Boolean) {
        context.dataStore.edit { it[Keys.USE_24_HOUR] = use24Hour }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    override suspend fun setSnoozeMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.SNOOZE_MINUTES] = minutes }
    }

    override suspend fun setMathDifficulty(difficulty: MathDifficulty) {
        context.dataStore.edit { it[Keys.MATH_DIFFICULTY] = difficulty.name }
    }

    override suspend fun setMathProblemCount(count: Int) {
        context.dataStore.edit { it[Keys.MATH_PROBLEM_COUNT] = count }
    }

    override suspend fun setOnboardingComplete(complete: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETE] = complete }
    }

    private object Keys {
        val DEFAULT_CHALLENGE = stringPreferencesKey("default_challenge")
        val AUTO_SILENCE_MINUTES = intPreferencesKey("auto_silence_minutes")
        val RAMP_SECONDS = intPreferencesKey("volume_ramp_seconds")
        val MINIMUM_VOLUME_PERCENT = intPreferencesKey("minimum_volume_percent")
        val WAKE_CHECK_MINUTES = intPreferencesKey("wake_check_minutes")
        val USE_24_HOUR = booleanPreferencesKey("use_24_hour")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SNOOZE_MINUTES = intPreferencesKey("snooze_minutes")
        val MATH_DIFFICULTY = stringPreferencesKey("math_difficulty")
        val MATH_PROBLEM_COUNT = intPreferencesKey("math_problem_count")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    }
}
