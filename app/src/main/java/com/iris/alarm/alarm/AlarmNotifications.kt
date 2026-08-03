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

    private fun snoozePendingIntent(context: Context, alarmId: Long): PendingIntent =
        PendingIntent.getService(
            context,
            AlarmContract.triggerRequestCode(alarmId),
            Intent(context, AlarmForegroundService::class.java).apply {
                action = AlarmContract.ACTION_SNOOZE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

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

    fun buildRingingNotification(
        context: Context,
        alarm: Alarm?,
        alarmId: Long,
        snoozeMinutes: Int = 0,
    ): Notification {
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
            .setContentIntent(fullScreenIntent)
            // When the phone is unlocked and in use Android shows this as a
            // heads-up banner instead of taking over the screen, so the two
            // decisions have to be reachable from the banner itself.
            .setFullScreenIntent(fullScreenIntent, true)
            .apply {
                if (snoozeMinutes > 0) {
                    addAction(
                        NotificationCompat.Action.Builder(
                            R.drawable.ic_iris_notification,
                            context.getString(R.string.notification_snooze, snoozeMinutes),
                            snoozePendingIntent(context, alarmId),
                        ).build(),
                    )
                }
                // "Stop" opens the challenge rather than silencing anything: a
                // notification button that killed the alarm outright would be
                // the plain off switch IRIS deliberately does not have.
                addAction(
                    NotificationCompat.Action.Builder(
                        R.drawable.ic_iris_notification,
                        context.getString(R.string.notification_stop),
                        fullScreenIntent,
                    ).build(),
                )
            }
            .build()
    }
}
