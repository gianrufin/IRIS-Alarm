package com.iris.alarm.domain.repository

import com.iris.alarm.domain.model.IrisSettings
import com.iris.alarm.domain.model.MathDifficulty
import com.iris.alarm.domain.model.QuickPreset
import com.iris.alarm.domain.model.ThemeMode
import com.iris.alarm.domain.model.VisionChallenge
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<IrisSettings>

    /** One-shot read for callers that cannot collect, such as the alarm service. */
    suspend fun current(): IrisSettings

    /** Common alarm duration presets for 1-tap quick setting. */
    val quickPresets: Flow<List<QuickPreset>>

    suspend fun getQuickPresets(): List<QuickPreset>

    suspend fun saveQuickPreset(preset: QuickPreset)

    suspend fun deleteQuickPreset(id: String)

    suspend fun setDefaultChallenge(challenge: VisionChallenge)

    suspend fun setAutoSilenceMinutes(minutes: Int)

    suspend fun setVolumeRampSeconds(seconds: Int)

    suspend fun setMinimumVolumePercent(percent: Int)

    suspend fun setWakeCheckMinutes(minutes: Int)

    suspend fun setUse24Hour(use24Hour: Boolean)

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setSnoozeMinutes(minutes: Int)

    suspend fun setMathDifficulty(difficulty: MathDifficulty)

    suspend fun setMathProblemCount(count: Int)

    suspend fun setOnboardingComplete(complete: Boolean)
}
