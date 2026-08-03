package com.iris.alarm.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import com.iris.alarm.domain.model.Alarm
import com.iris.alarm.domain.model.IrisSettings
import com.iris.alarm.domain.repository.AlarmRepository
import com.iris.alarm.domain.repository.SettingsRepository
import com.iris.alarm.domain.repository.WakeCheckRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Keeps the alarm audible and the CPU awake for as long as the challenge is
 * unsolved. It owns the [MediaPlayer], audio focus, the vibration loop and the
 * partial wake lock; the challenge UI only sends [AlarmContract.ACTION_DISMISS].
 */
@AndroidEntryPoint
class AlarmForegroundService : Service() {

    @Inject lateinit var repository: AlarmRepository

    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject lateinit var wakeCheckRepository: WakeCheckRepository

    @Inject lateinit var scheduler: AlarmScheduler

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var mediaPlayer: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var autoSilenceJob: Job? = null
    private var rampJob: Job? = null

    /** Alarm-stream volume to put back when the alarm stops, if we raised it. */
    private var restoreVolumeTo: Int? = null

    /** True when this ring is the follow-up check rather than the alarm itself. */
    private var isWakeCheck = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AlarmNotifications.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            AlarmContract.ACTION_START -> {
                val alarmId = intent.getLongExtra(
                    AlarmContract.EXTRA_ALARM_ID,
                    AlarmContract.NO_ALARM_ID,
                )
                isWakeCheck = intent.getBooleanExtra(AlarmContract.EXTRA_WAKE_CHECK, false)
                _ringingIsWakeCheck.value = isWakeCheck
                startRinging(alarmId)
            }

            AlarmContract.ACTION_DISMISS -> {
                scheduleWakeCheckIfEnabled()
                stopRinging()
            }

            AlarmContract.ACTION_SNOOZE -> {
                snooze()
                stopRinging()
            }

            else -> {
                // Restarted by the system with a null intent and no alarm context —
                // there is nothing meaningful to ring for.
                Log.w(TAG, "Started with action=${intent?.action}; stopping")
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startRinging(alarmId: Long) {
        if (_ringingAlarmId.value == alarmId && mediaPlayer != null) return

        // The notification must go up within a few seconds of startForegroundService,
        // so it is posted before the alarm row is loaded and refreshed afterwards.
        promoteToForeground(alarm = null, alarmId = alarmId)
        _ringingAlarmId.value = alarmId

        acquireWakeLock()

        scope.launch {
            val alarm = if (alarmId == AlarmContract.NO_ALARM_ID) {
                null
            } else {
                runCatching { repository.getAlarm(alarmId) }.getOrNull()
            }
            _ringingAlarm.value = alarm
            promoteToForeground(alarm, alarmId)

            val settings = runCatching { settingsRepository.current() }
                .getOrDefault(IrisSettings())
                .effectiveFor(alarm)

            _use24Hour.value = settings.use24Hour
            _snoozeMinutes.value = settings.snoozeMinutes
            // Re-post now the snooze length is known, so the banner carries its
            // action rather than appearing without one.
            promoteToForeground(alarm, alarmId, settings.snoozeMinutes)
            raiseVolumeFloor(settings.minimumVolumePercent)
            startAudio(alarm?.soundUri?.let(Uri::parse), settings.volumeRampSeconds)
            if (alarm?.vibrate != false) startVibration()

            autoSilenceJob?.cancel()
            autoSilenceJob = scope.launch {
                delay(settings.autoSilenceMillis)
                Log.i(TAG, "Auto-silencing alarm $alarmId after timeout")
                stopRinging()
            }
        }
    }

    private fun promoteToForeground(alarm: Alarm?, alarmId: Long, snoozeMinutes: Int = 0) {
        val notification =
            AlarmNotifications.buildRingingNotification(this, alarm, alarmId, snoozeMinutes)
        ServiceCompat.startForeground(
            this,
            AlarmNotifications.RINGING_NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            },
        )
    }

    /**
     * An alarm on a muted stream is no alarm at all, so the stream is lifted to
     * the configured floor for the duration and restored in [stopRinging] — the
     * user's own volume setting is borrowed, not overwritten.
     */
    private fun raiseVolumeFloor(percent: Int) {
        if (percent <= 0) return
        val audioManager = getSystemService<AudioManager>() ?: return

        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val floor = (max * percent / 100).coerceIn(1, max)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        if (current >= floor) return

        // Raising the alarm stream is refused while some Do Not Disturb policies
        // are active; ringing quietly beats crashing.
        runCatching {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, floor, 0)
            restoreVolumeTo = current
        }.onFailure { Log.w(TAG, "Could not raise the alarm stream volume", it) }
    }

    private fun restoreVolume() {
        val previous = restoreVolumeTo ?: return
        restoreVolumeTo = null
        val audioManager = getSystemService<AudioManager>() ?: return
        runCatching { audioManager.setStreamVolume(AudioManager.STREAM_ALARM, previous, 0) }
    }

    private fun startAudio(soundUri: Uri?, rampSeconds: Int) {
        val uri = soundUri
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: return

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        if (!requestAudioFocus(attributes)) {
            // Focus can be refused (e.g. an active phone call). Ring anyway — an
            // alarm that stays silent is worse than one that overlaps.
            Log.w(TAG, "Audio focus not granted; playing regardless")
        }

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(attributes)
            isLooping = true
            setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
                true
            }
            if (rampSeconds > 0) setVolume(RAMP_START_VOLUME, RAMP_START_VOLUME)
            runCatching {
                setDataSource(this@AlarmForegroundService, uri)
                prepare()
                start()
            }.onFailure {
                Log.e(TAG, "Unable to play $uri", it)
                release()
                mediaPlayer = null
            }
        }

        if (rampSeconds > 0 && mediaPlayer != null) startVolumeRamp(rampSeconds)
    }

    /**
     * Fades in over [seconds] so the alarm wakes rather than startles. The job is
     * cancelled with the scope, and every step re-reads [mediaPlayer] so a ramp
     * that outlives the player cannot touch a released one.
     */
    private fun startVolumeRamp(seconds: Int) {
        rampJob?.cancel()
        rampJob = scope.launch {
            val stepDelay = seconds * 1000L / RAMP_STEPS
            for (step in 1..RAMP_STEPS) {
                delay(stepDelay)
                val volume = RAMP_START_VOLUME +
                    (1f - RAMP_START_VOLUME) * (step.toFloat() / RAMP_STEPS)
                val player = mediaPlayer ?: return@launch
                runCatching { player.setVolume(volume, volume) }
            }
        }
    }

    private fun requestAudioFocus(attributes: AudioAttributes): Boolean {
        val audioManager = getSystemService<AudioManager>() ?: return false
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener { /* An alarm never yields focus. */ }
            .build()
        focusRequest = request
        return audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        val audioManager = getSystemService<AudioManager>() ?: return
        focusRequest?.let(audioManager::abandonAudioFocusRequest)
        focusRequest = null
    }

    private fun startVibration() {
        val vibrator = vibrator() ?: return
        if (!vibrator.hasVibrator()) return

        val timings = longArrayOf(0, 500, 500)
        val amplitudes = intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0)
        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, /* repeat = */ 0))
    }

    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService<VibratorManager>()?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService<Vibrator>()
        }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService<PowerManager>() ?: return
        wakeLock = powerManager
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .apply { acquire(AlarmContract.MAX_RINGING_MILLIS) }
    }

    /**
     * A solved challenge proves the user was awake for a few seconds, not that
     * they stayed up. When the wake check is enabled, the same challenge is
     * re-armed for later and only an explicit "I'm up" cancels it.
     *
     * A wake check never schedules another one — that would be an endless chain.
     */
    private fun scheduleWakeCheckIfEnabled() {
        if (isWakeCheck) return
        val alarmId = _ringingAlarmId.value
        if (alarmId == AlarmContract.NO_ALARM_ID) return

        scope.launch {
            val minutes = runCatching { settingsRepository.current().wakeCheckMinutes }
                .getOrDefault(0)
            if (minutes <= 0) return@launch

            val firesAt = System.currentTimeMillis() + minutes * 60_000L
            scheduler.scheduleWakeCheck(alarmId, firesAt)
            wakeCheckRepository.set(alarmId, firesAt)
        }
    }

    /**
     * Rings the same alarm again shortly. A snooze deliberately does not arm a
     * wake check: the snooze *is* the follow-up.
     */
    private fun snooze() {
        val alarmId = _ringingAlarmId.value
        if (alarmId == AlarmContract.NO_ALARM_ID) return

        scope.launch {
            val minutes = runCatching { settingsRepository.current().snoozeMinutes }
                .getOrDefault(IrisSettings.DEFAULT_SNOOZE_MINUTES)
            if (minutes <= 0) return@launch
            scheduler.scheduleSnooze(alarmId, System.currentTimeMillis() + minutes * 60_000L)
        }
    }

    private fun stopRinging() {
        autoSilenceJob?.cancel()
        autoSilenceJob = null
        rampJob?.cancel()
        rampJob = null

        mediaPlayer?.runCatching {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        abandonAudioFocus()
        restoreVolume()
        vibrator()?.cancel()

        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null

        _ringingAlarmId.value = AlarmContract.NO_ALARM_ID
        _ringingAlarm.value = null
        isWakeCheck = false
        _ringingIsWakeCheck.value = false

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopRinging()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AlarmForegroundService"
        private const val WAKE_LOCK_TAG = "iris:alarm"
        private const val RAMP_STEPS = 24
        private const val RAMP_START_VOLUME = 0.08f

        private val _ringingAlarmId = MutableStateFlow(AlarmContract.NO_ALARM_ID)

        /** Id of the alarm currently ringing, or [AlarmContract.NO_ALARM_ID]. */
        val ringingAlarmId: StateFlow<Long> = _ringingAlarmId.asStateFlow()

        private val _ringingAlarm = MutableStateFlow<Alarm?>(null)
        val ringingAlarm: StateFlow<Alarm?> = _ringingAlarm.asStateFlow()

        private val _use24Hour = MutableStateFlow(true)

        /** Clock format for the lock-screen surfaces, which have no settings access. */
        val use24Hour: StateFlow<Boolean> = _use24Hour.asStateFlow()

        private val _snoozeMinutes = MutableStateFlow(IrisSettings.DEFAULT_SNOOZE_MINUTES)

        /** Snooze length for the lock-screen surfaces, which have no settings access. */
        val snoozeMinutes: StateFlow<Int> = _snoozeMinutes.asStateFlow()

        private val _ringingIsWakeCheck = MutableStateFlow(false)

        /** True while the current ring is a follow-up check, not the alarm itself. */
        val ringingIsWakeCheck: StateFlow<Boolean> = _ringingIsWakeCheck.asStateFlow()

        /** Called when the user swipes to snooze. */
        fun snooze(context: Context) {
            context.startService(
                Intent(context, AlarmForegroundService::class.java).apply {
                    action = AlarmContract.ACTION_SNOOZE
                },
            )
        }

        /** Called by the challenge UI once a vision/sensor task has been satisfied. */
        fun dismiss(context: Context) {
            context.startService(
                Intent(context, AlarmForegroundService::class.java).apply {
                    action = AlarmContract.ACTION_DISMISS
                },
            )
        }
    }
}
