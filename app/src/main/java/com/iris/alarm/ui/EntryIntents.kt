package com.iris.alarm.ui

import android.content.Intent
import android.provider.AlarmClock

/**
 * Where the app should open, worked out from the intent that launched it.
 *
 * Covers both the launcher shortcuts and the standard `AlarmClock` intents the
 * assistant sends for "set an alarm" and "set a timer for five minutes", so the
 * two cannot drift apart.
 */
sealed interface EntryPoint {
    data object Home : EntryPoint
    data object Alarms : EntryPoint
    data object Stopwatch : EntryPoint
    data object Pomodoro : EntryPoint

    /** "Set a timer for N seconds". Zero length means just open the timer. */
    data class Timer(val seconds: Int, val label: String?, val startImmediately: Boolean) :
        EntryPoint

    /** "Set an alarm for 7:30". A null time means open the editor empty. */
    data class NewAlarm(
        val hour: Int?,
        val minute: Int?,
        val label: String?,
        val skipUi: Boolean,
    ) : EntryPoint
}

object EntryIntents {
    const val ACTION_SHORTCUT_NEW_ALARM = "com.iris.alarm.action.SHORTCUT_NEW_ALARM"
    const val ACTION_SHORTCUT_TIMER = "com.iris.alarm.action.SHORTCUT_TIMER"
    const val ACTION_SHORTCUT_STOPWATCH = "com.iris.alarm.action.SHORTCUT_STOPWATCH"
    const val ACTION_SHORTCUT_POMODORO = "com.iris.alarm.action.SHORTCUT_POMODORO"

    fun from(intent: Intent?): EntryPoint = when (intent?.action) {
        // Assistant and third-party requests.
        AlarmClock.ACTION_SET_ALARM -> EntryPoint.NewAlarm(
            hour = intent.extras?.getInt(AlarmClock.EXTRA_HOUR)
                ?.takeIf { intent.hasExtra(AlarmClock.EXTRA_HOUR) },
            minute = intent.extras?.getInt(AlarmClock.EXTRA_MINUTES)
                ?.takeIf { intent.hasExtra(AlarmClock.EXTRA_MINUTES) },
            label = intent.getStringExtra(AlarmClock.EXTRA_MESSAGE),
            // The caller asked for no interaction. IRIS still shows the editor
            // for a challenge that needs setting up, but everything else is
            // filled in and one tap from saved.
            skipUi = intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false),
        )

        AlarmClock.ACTION_SET_TIMER -> EntryPoint.Timer(
            seconds = intent.getIntExtra(AlarmClock.EXTRA_LENGTH, 0),
            label = intent.getStringExtra(AlarmClock.EXTRA_MESSAGE),
            startImmediately = intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false) ||
                intent.getIntExtra(AlarmClock.EXTRA_LENGTH, 0) > 0,
        )

        AlarmClock.ACTION_SHOW_ALARMS -> EntryPoint.Alarms
        AlarmClock.ACTION_SHOW_TIMERS -> EntryPoint.Timer(0, null, startImmediately = false)

        // Launcher shortcuts.
        ACTION_SHORTCUT_NEW_ALARM -> EntryPoint.NewAlarm(null, null, null, skipUi = false)
        ACTION_SHORTCUT_TIMER -> EntryPoint.Timer(0, null, startImmediately = false)
        ACTION_SHORTCUT_STOPWATCH -> EntryPoint.Stopwatch
        ACTION_SHORTCUT_POMODORO -> EntryPoint.Pomodoro

        else -> EntryPoint.Home
    }
}
