package com.iris.alarm.alarm

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.getSystemService
import com.iris.alarm.domain.model.Alarm
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns every interaction with [AlarmManager]. Nothing else in the app is allowed
 * to build alarm pending intents, so cancellation always matches scheduling.
 */
@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val alarmManager: AlarmManager = requireNotNull(context.getSystemService())

    /**
     * True when the OS will honour exact alarms. On API 31+ the user can revoke
     * this, at which point alarms would silently degrade to inexact windows —
     * the UI is expected to surface [openExactAlarmSettings] when this is false.
     */
    val canScheduleExact: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

    /**
     * Schedules the next occurrence of [alarm], replacing any previous scheduling
     * for the same id. Disabled alarms are cancelled instead.
     */
    @SuppressLint("MissingPermission")
    fun schedule(alarm: Alarm) {
        val triggerAt = alarm.nextTriggerAtMillis()
        if (triggerAt == null) {
            cancel(alarm.id)
            return
        }

        val pendingIntent = triggerPendingIntent(alarm.id, mutable = false)
        if (canScheduleExact) {
            // Exact + allow-while-idle is what lets an alarm punch through Doze.
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent,
            )
        } else {
            // Degraded mode: better a late alarm than none while the user has not
            // granted SCHEDULE_EXACT_ALARM.
            Log.w(TAG, "Exact alarms unavailable; falling back to an inexact window")
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                WINDOW_MILLIS,
                pendingIntent,
            )
        }
    }

    fun cancel(alarmId: Long) {
        alarmManager.cancel(triggerPendingIntent(alarmId, mutable = false))
    }

    /** Re-arms everything. Used after boot, time change, and timezone change. */
    fun rescheduleAll(alarms: List<Alarm>) {
        alarms.forEach(::schedule)
    }

    fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = android.net.Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun triggerPendingIntent(alarmId: Long, mutable: Boolean): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmContract.ACTION_ALARM_FIRED
            putExtra(AlarmContract.EXTRA_ALARM_ID, alarmId)
            // Extras are not part of PendingIntent equality; the action + request
            // code pair is what makes cancel() match a previous schedule().
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE

        return PendingIntent.getBroadcast(
            context,
            AlarmContract.triggerRequestCode(alarmId),
            intent,
            flags,
        )
    }

    private companion object {
        const val TAG = "AlarmScheduler"
        const val WINDOW_MILLIS = 10 * 60 * 1000L
    }
}
