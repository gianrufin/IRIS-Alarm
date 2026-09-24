package com.iris.alarm.ui.challenge

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.iris.alarm.alarm.AlarmContract
import com.iris.alarm.alarm.AlarmForegroundService
import com.iris.alarm.alarm.AlarmNotifications
import com.iris.alarm.alarm.AlarmScheduler
import com.iris.alarm.domain.model.Alarm
import com.iris.alarm.domain.repository.AlarmRepository
import com.iris.alarm.domain.repository.SettingsRepository
import com.iris.alarm.ui.theme.IrisTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Full-screen challenge surface launched over the lock screen by the ringing
 * notification's full-screen intent or upcoming alarm early dismissal.
 *
 * Designed to be strictly unclosable while ringing: back button, home gestures,
 * recent tasks switching, and notification panel pulldowns are all trapped and
 * blocked until the vision challenge is solved or snooze is activated.
 */
@AndroidEntryPoint
class AlarmChallengeActivity : ComponentActivity() {

    @Inject lateinit var repository: AlarmRepository
    @Inject lateinit var scheduler: AlarmScheduler
    @Inject lateinit var settingsRepository: SettingsRepository

    private var earlyAlarm by mutableStateOf<Alarm?>(null)
    private var isEarlyDismiss: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        showOverLockScreen()
        enableEdgeToEdge()
        volumeControlStream = AudioManager.STREAM_ALARM
        super.onCreate(savedInstanceState)

        val alarmId = intent.getLongExtra(AlarmContract.EXTRA_ALARM_ID, AlarmContract.NO_ALARM_ID)
        isEarlyDismiss = intent.getBooleanExtra(AlarmContract.EXTRA_EARLY_DISMISS, false)

        if (isEarlyDismiss && alarmId != AlarmContract.NO_ALARM_ID) {
            lifecycleScope.launch {
                earlyAlarm = repository.getAlarm(alarmId)
            }
        }

        // Backing out is strictly forbidden: the only exits are solving the challenge,
        // snoozing, or auto-silence timeout.
        onBackPressedDispatcher.addCallback(this) {
            // Intentionally no-op to consume back gesture and prevent leaving
        }

        setContent {
            // The ringing screen is always dark, whatever the app theme: this is
            // a face full of phone in a dark bedroom.
            IrisTheme(darkTheme = true) {
                val serviceAlarm by AlarmForegroundService.ringingAlarm.collectAsStateWithLifecycle()
                val alarm = serviceAlarm ?: earlyAlarm
                val use24Hour by AlarmForegroundService.use24Hour.collectAsStateWithLifecycle()
                val snoozeMinutes by AlarmForegroundService.snoozeMinutes
                    .collectAsStateWithLifecycle()
                val isWakeCheck by AlarmForegroundService.ringingIsWakeCheck
                    .collectAsStateWithLifecycle()
                var challengeStarted by rememberSaveable { mutableStateOf(isEarlyDismiss) }

                AnimatedContent(
                    targetState = challengeStarted,
                    transitionSpec = {
                        // The challenge arrives from the right, following the
                        // direction the card was pushed.
                        (slideInHorizontally(tween(320)) { it } + fadeIn(tween(320)))
                            .togetherWith(
                                slideOutHorizontally(tween(280)) { -it / 4 } +
                                    fadeOut(tween(220)),
                            )
                    },
                    label = "ringingStage",
                ) { started ->
                    if (started) {
                        ChallengeScreen(
                            alarm = alarm,
                            use24Hour = use24Hour,
                            onChallengeSolved = { dismiss(alarmId) },
                        )
                    } else {
                        AlarmSwipeScreen(
                            alarm = alarm,
                            use24Hour = use24Hour,
                            isWakeCheck = isWakeCheck,
                            snoozeMinutes = snoozeMinutes,
                            onSnooze = { snooze() },
                            onProceedToChallenge = { challengeStarted = true },
                        )
                    }
                }
            }
        }

        // Nothing to show if the service is no longer ringing (auto-silenced, or the
        // notification was tapped after dismissal).
        if (!isEarlyDismiss &&
            alarmId == AlarmContract.NO_ALARM_ID &&
            AlarmForegroundService.ringingAlarmId.value == AlarmContract.NO_ALARM_ID
        ) {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        isActivityActive = true
        hideSystemBars()
    }

    override fun onPause() {
        super.onPause()
        isActivityActive = false
    }

    override fun onStop() {
        super.onStop()
        isActivityActive = false
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // If the user navigates to home while ringing, reassert challenge if overlay permission is granted
        if (isAlarmStillRinging() && android.provider.Settings.canDrawOverlays(this)) {
            window.decorView.postDelayed({
                if (isAlarmStillRinging() && !isActivityActive && !isFinishing) {
                    relaunchChallenge()
                }
            }, 1200)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (isAlarmStillRinging()) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN,
                KeyEvent.KEYCODE_VOLUME_MUTE,
                KeyEvent.KEYCODE_MUTE -> {
                    // Bypass volume decrease: hardware keys cannot decrease or mute volume while ringing
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isAlarmStillRinging()) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN,
                KeyEvent.KEYCODE_VOLUME_MUTE,
                KeyEvent.KEYCODE_MUTE -> return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (isAlarmStillRinging()) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN,
                KeyEvent.KEYCODE_VOLUME_MUTE,
                KeyEvent.KEYCODE_MUTE -> return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun isAlarmStillRinging(): Boolean =
        AlarmForegroundService.ringingAlarmId.value != AlarmContract.NO_ALARM_ID

    private fun relaunchChallenge() {
        val currentId = AlarmForegroundService.ringingAlarmId.value
        val reopenIntent = intent(this, currentId).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        }
        runCatching { startActivity(reopenIntent) }
    }

    private fun dismiss(alarmId: Long) {
        isActivityActive = false
        if (isEarlyDismiss) {
            lifecycleScope.launch {
                if (alarmId != AlarmContract.NO_ALARM_ID) {
                    val alarm = repository.getAlarm(alarmId)
                    if (alarm != null) {
                        if (alarm.isRepeating) {
                            scheduler.schedule(alarm)
                        } else {
                            repository.setEnabled(alarm.id, enabled = false)
                            scheduler.cancel(alarm.id)
                        }
                    }
                    AlarmNotifications.clearUpcoming(this@AlarmChallengeActivity, alarmId)
                }
                Toast.makeText(this@AlarmChallengeActivity, "Upcoming alarm turned off early", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            AlarmForegroundService.dismiss(this)
            finish()
        }
    }

    private fun snooze() {
        isActivityActive = false
        AlarmForegroundService.snooze(this)
        finish()
    }

    /**
     * Shows over the keyguard and locks the screen in immersive full-screen mode.
     */
    private fun showOverLockScreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun hideSystemBars() {
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    companion object {
        @Volatile
        var isActivityActive: Boolean = false
            private set

        fun intent(context: Context, alarmId: Long): Intent =
            Intent(context, AlarmChallengeActivity::class.java).apply {
                putExtra(AlarmContract.EXTRA_ALARM_ID, alarmId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
    }
}

