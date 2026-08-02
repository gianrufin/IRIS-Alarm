package com.iris.alarm.alarm

/**
 * The single source of truth for the intent surface shared by the scheduler, the
 * broadcast receiver, the foreground service and the challenge Activity.
 */
object AlarmContract {
    const val ACTION_ALARM_FIRED = "com.iris.alarm.action.ALARM_FIRED"
    const val ACTION_START = "com.iris.alarm.action.START"
    const val ACTION_DISMISS = "com.iris.alarm.action.DISMISS"

    const val EXTRA_ALARM_ID = "com.iris.alarm.extra.ALARM_ID"

    /** Alarm id used when the service is started without a backing database row. */
    const val NO_ALARM_ID = -1L

    /** Stop ringing after this long if the user never completes the challenge. */
    const val AUTO_SILENCE_MILLIS = 10 * 60 * 1000L

    /**
     * Request codes must not collide across pending-intent purposes, so each
     * purpose gets its own offset added to the alarm id.
     */
    fun triggerRequestCode(alarmId: Long): Int = (alarmId % Int.MAX_VALUE).toInt()

    fun fullScreenRequestCode(alarmId: Long): Int = 1_000_000 + triggerRequestCode(alarmId)
}
