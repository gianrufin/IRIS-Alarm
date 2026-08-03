package com.iris.alarm.ui.challenge

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.iris.alarm.ui.theme.IrisTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Full-screen challenge surface launched over the lock screen by the ringing
 * notification's full-screen intent.
 *
 * The vision/sensor detectors land here in the next step; for now it renders the
 * prompt for the ringing alarm and routes a completed challenge back to
 * [AlarmForegroundService].
 */
@AndroidEntryPoint
class AlarmChallengeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        showOverLockScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val alarmId = intent.getLongExtra(AlarmContract.EXTRA_ALARM_ID, AlarmContract.NO_ALARM_ID)

        // Backing out would leave the alarm ringing with no way to reach the
        // challenge; the only exits are solving it or the auto-silence timeout.
        onBackPressedDispatcher.addCallback(this) { moveTaskToBack(true) }

        setContent {
            // The ringing screen is always dark, whatever the app theme: this is
            // a face full of phone in a dark bedroom.
            IrisTheme(darkTheme = true) {
                val alarm by AlarmForegroundService.ringingAlarm.collectAsStateWithLifecycle()
                val use24Hour by AlarmForegroundService.use24Hour.collectAsStateWithLifecycle()
                val snoozeMinutes by AlarmForegroundService.snoozeMinutes
                    .collectAsStateWithLifecycle()
                val isWakeCheck by AlarmForegroundService.ringingIsWakeCheck
                    .collectAsStateWithLifecycle()
                var challengeStarted by rememberSaveable { mutableStateOf(false) }

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
                            onChallengeSolved = { dismiss() },
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
        if (alarmId == AlarmContract.NO_ALARM_ID &&
            AlarmForegroundService.ringingAlarmId.value == AlarmContract.NO_ALARM_ID
        ) {
            finish()
        }
    }

    private fun dismiss() {
        AlarmForegroundService.dismiss(this)
        finish()
    }

    private fun snooze() {
        AlarmForegroundService.snooze(this)
        finish()
    }

    /**
     * Shows over the keyguard rather than dismissing it. Asking to dismiss puts
     * the PIN prompt in front of the alarm, so the first thing a sleeping person
     * has to do is unlock the phone — the challenge is the point, not the lock.
     */
    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        fun intent(context: Context, alarmId: Long): Intent =
            Intent(context, AlarmChallengeActivity::class.java).apply {
                putExtra(AlarmContract.EXTRA_ALARM_ID, alarmId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
    }
}
