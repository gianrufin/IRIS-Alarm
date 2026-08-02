package com.iris.alarm.domain.repository

import com.iris.alarm.domain.model.IrisSettings
import com.iris.alarm.domain.model.VisionChallenge
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<IrisSettings>

    /** One-shot read for callers that cannot collect, such as the alarm service. */
    suspend fun current(): IrisSettings

    suspend fun setDefaultChallenge(challenge: VisionChallenge)

    suspend fun setAutoSilenceMinutes(minutes: Int)

    suspend fun setVolumeRampSeconds(seconds: Int)

    suspend fun setMinimumVolumePercent(percent: Int)

    suspend fun setWakeCheckMinutes(minutes: Int)
}
