package com.iris.alarm.ui

import android.content.Intent
import android.provider.AlarmClock

/**
 * Where the app should open, worked out from the intent that launched it.
 *
 * Covers both the launcher shortcut and the standard `AlarmClock` intents the
 * assistant sends for "set an alarm", so the two cannot drift apart.
 *
 * IRIS deliberately does not answer `SET_TIMER` or `SHOW_TIMERS`: it has no
 * timer to open. Registering for an intent it cannot honour would put it in the
 * assistant's chooser and then do nothing useful when picked.
 */
sealed interface EntryPoint {
    data object Home : EntryPoint
    data object Alarms : EntryPoint

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

        AlarmClock.ACTION_SHOW_ALARMS -> EntryPoint.Alarms

        // Launcher shortcut.
        ACTION_SHORTCUT_NEW_ALARM -> EntryPoint.NewAlarm(null, null, null, skipUi = false)

        else -> EntryPoint.Home
    }
}
