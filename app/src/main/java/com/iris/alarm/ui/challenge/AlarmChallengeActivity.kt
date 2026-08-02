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
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
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
            IrisTheme(darkTheme = true) {
                val alarm by AlarmForegroundService.ringingAlarm.collectAsStateWithLifecycle()
                ChallengeScreen(
                    alarm = alarm,
                    onChallengeSolved = { dismiss() },
                )
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

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService<android.app.KeyguardManager>()?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
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
