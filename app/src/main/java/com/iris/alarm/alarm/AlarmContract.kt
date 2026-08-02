package com.iris.alarm.alarm

/**
 * The single source of truth for the intent surface shared by the scheduler, the
 * broadcast receiver, the foreground service and the challenge Activity.
 */
object AlarmContract {
    const val ACTION_ALARM_FIRED = "com.iris.alarm.action.ALARM_FIRED"
    const val ACTION_WAKE_CHECK = "com.iris.alarm.action.WAKE_CHECK"
    const val ACTION_START = "com.iris.alarm.action.START"
    const val ACTION_DISMISS = "com.iris.alarm.action.DISMISS"

    const val EXTRA_ALARM_ID = "com.iris.alarm.extra.ALARM_ID"

    /** Marks a ring as the follow-up check rather than the alarm itself. */
    const val EXTRA_WAKE_CHECK = "com.iris.alarm.extra.WAKE_CHECK"

    /** Alarm id used when the service is started without a backing database row. */
    const val NO_ALARM_ID = -1L

    /**
     * Ceiling for the wake lock held while ringing. The actual auto-silence
     * timeout is a user setting; this only has to outlast the longest of them so
     * the lock is never released mid-alarm.
     */
    const val MAX_RINGING_MILLIS = 35 * 60 * 1000L

    /**
     * Request codes must not collide across pending-intent purposes, so each
     * purpose gets its own offset added to the alarm id.
     */
    fun triggerRequestCode(alarmId: Long): Int = (alarmId % Int.MAX_VALUE).toInt()

    fun fullScreenRequestCode(alarmId: Long): Int = 1_000_000 + triggerRequestCode(alarmId)

    /**
     * One wake check exists at a time, so it needs a single fixed request code —
     * scheduling a second must replace the first, not stack another alarm.
     */
    const val WAKE_CHECK_REQUEST_CODE = 2_000_000
}
