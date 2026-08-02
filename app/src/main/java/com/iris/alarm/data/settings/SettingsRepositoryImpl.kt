package com.iris.alarm.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.iris.alarm.domain.model.IrisSettings
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

    private object Keys {
        val DEFAULT_CHALLENGE = stringPreferencesKey("default_challenge")
        val AUTO_SILENCE_MINUTES = intPreferencesKey("auto_silence_minutes")
        val RAMP_SECONDS = intPreferencesKey("volume_ramp_seconds")
        val MINIMUM_VOLUME_PERCENT = intPreferencesKey("minimum_volume_percent")
        val WAKE_CHECK_MINUTES = intPreferencesKey("wake_check_minutes")
    }
}
