package com.iris.alarm.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.iris.alarm.domain.model.IrisSettings
import com.iris.alarm.domain.model.MathDifficulty
import com.iris.alarm.domain.model.QuickPreset
import com.iris.alarm.domain.model.ThemeMode
import com.iris.alarm.domain.model.VisionChallenge
import com.iris.alarm.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

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

    override val quickPresets: Flow<List<QuickPreset>> = context.dataStore.data.map { prefs ->
        val json = prefs[Keys.QUICK_PRESETS]
        if (json.isNullOrBlank()) {
            QuickPreset.DEFAULTS
        } else {
            deserializePresets(json)
        }
    }

    override suspend fun getQuickPresets(): List<QuickPreset> = quickPresets.first()

    override suspend fun saveQuickPreset(preset: QuickPreset) {
        val current = getQuickPresets().toMutableList()
        val index = current.indexOfFirst { it.id == preset.id }
        if (index >= 0) {
            current[index] = preset
        } else {
            current.add(preset)
        }
        context.dataStore.edit { it[Keys.QUICK_PRESETS] = serializePresets(current) }
    }

    override suspend fun deleteQuickPreset(id: String) {
        val current = getQuickPresets().filterNot { it.id == id }
        context.dataStore.edit { it[Keys.QUICK_PRESETS] = serializePresets(current) }
    }

    override suspend fun setOnboardingComplete(complete: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETE] = complete }
    }

    private fun serializePresets(presets: List<QuickPreset>): String {
        val array = JSONArray()
        for (preset in presets) {
            val obj = JSONObject().apply {
                put("id", preset.id)
                put("label", preset.label)
                put("durationMinutes", preset.durationMinutes)
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun deserializePresets(json: String): List<QuickPreset> {
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                QuickPreset(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    label = obj.optString("label", ""),
                    durationMinutes = obj.optInt("durationMinutes", 15),
                )
            }
        }.getOrElse { QuickPreset.DEFAULTS }
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
        val QUICK_PRESETS = stringPreferencesKey("quick_presets")
    }
}
