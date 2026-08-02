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
import com.iris.alarm.domain.repository.AlarmRepository
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var mediaPlayer: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var autoSilenceJob: Job? = null

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
                startRinging(alarmId)
            }

            AlarmContract.ACTION_DISMISS -> stopRinging()

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

            startAudio(alarm?.soundUri?.let(Uri::parse))
            if (alarm?.vibrate != false) startVibration()
        }

        autoSilenceJob?.cancel()
        autoSilenceJob = scope.launch {
            delay(AlarmContract.AUTO_SILENCE_MILLIS)
            Log.i(TAG, "Auto-silencing alarm $alarmId after timeout")
            stopRinging()
        }
    }

    private fun promoteToForeground(alarm: Alarm?, alarmId: Long) {
        val notification = AlarmNotifications.buildRingingNotification(this, alarm, alarmId)
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

    private fun startAudio(soundUri: Uri?) {
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
            .apply { acquire(AlarmContract.AUTO_SILENCE_MILLIS) }
    }

    private fun stopRinging() {
        autoSilenceJob?.cancel()
        autoSilenceJob = null

        mediaPlayer?.runCatching {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        abandonAudioFocus()
        vibrator()?.cancel()

        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null

        _ringingAlarmId.value = AlarmContract.NO_ALARM_ID
        _ringingAlarm.value = null

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

        private val _ringingAlarmId = MutableStateFlow(AlarmContract.NO_ALARM_ID)

        /** Id of the alarm currently ringing, or [AlarmContract.NO_ALARM_ID]. */
        val ringingAlarmId: StateFlow<Long> = _ringingAlarmId.asStateFlow()

        private val _ringingAlarm = MutableStateFlow<Alarm?>(null)
        val ringingAlarm: StateFlow<Alarm?> = _ringingAlarm.asStateFlow()

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
