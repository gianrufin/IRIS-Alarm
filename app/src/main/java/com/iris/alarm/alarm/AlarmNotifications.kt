package com.iris.alarm.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.iris.alarm.R
import com.iris.alarm.domain.model.Alarm
import com.iris.alarm.ui.challenge.AlarmChallengeActivity

/**
 * Builds the ringing notification. The channel is deliberately silent: audio is
 * owned by [AlarmForegroundService] so we control looping and audio focus.
 */
object AlarmNotifications {

    const val CHANNEL_ID = "iris_alarm_ringing"
    const val RINGING_NOTIFICATION_ID = 4711

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_ringing_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_ringing_description)
            setSound(null, null)
            enableVibration(false)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        context.getSystemService<NotificationManager>()?.createNotificationChannel(channel)
    }

    fun buildRingingNotification(context: Context, alarm: Alarm?, alarmId: Long): Notification {
        val fullScreenIntent = PendingIntent.getActivity(
            context,
            AlarmContract.fullScreenRequestCode(alarmId),
            AlarmChallengeActivity.intent(context, alarmId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = alarm?.label?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.notification_ringing_title)
        val challenge = alarm?.challenge?.displayName
            ?: context.getString(R.string.notification_ringing_fallback_challenge)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_iris_notification)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notification_ringing_text, challenge))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            // No dismiss action: the whole point of IRIS is that the challenge is
            // the only way out, and a notification button would bypass it.
            .setContentIntent(fullScreenIntent)
            .setFullScreenIntent(fullScreenIntent, true)
            .build()
    }
}
