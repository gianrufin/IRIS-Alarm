package com.iris.alarm.alarm

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.iris.alarm.R
import com.iris.alarm.domain.model.Alarm
import com.iris.alarm.ui.challenge.AlarmChallengeActivity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Builds the ringing notification. The channel is deliberately silent: audio is
 * owned by [AlarmForegroundService] so we control looping and audio focus.
 */
object AlarmNotifications {

    const val CHANNEL_ID = "iris_alarm_ringing"
    const val RINGING_NOTIFICATION_ID = 4711

    /** Silent and low priority: a snooze is information, not an event. */
    const val SNOOZED_CHANNEL_ID = "iris_alarm_snoozed"
    const val SNOOZED_NOTIFICATION_ID = 4712

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

    private fun ensureSnoozedChannel(context: Context) {
        val channel = NotificationChannel(
            SNOOZED_CHANNEL_ID,
            context.getString(R.string.channel_snoozed_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_snoozed_description)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        context.getSystemService<NotificationManager>()?.createNotificationChannel(channel)
    }

    /**
     * Standard alarm-clock behaviour: while a snooze is pending, say so and say
     * when. Without this a snooze is indistinguishable from having turned the
     * alarm off, which is a bad thing to be unsure about at 6am.
     */
    fun showSnoozed(context: Context, alarm: Alarm?, firesAtMillis: Long, use24Hour: Boolean) {
        ensureSnoozedChannel(context)

        val at = Instant.ofEpochMilli(firesAtMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
        val pattern = if (use24Hour) "HH:mm" else "h:mm a"
        val when24 = at.format(DateTimeFormatter.ofPattern(pattern))

        val label = alarm?.label?.takeIf { it.isNotBlank() }
        val notification = NotificationCompat.Builder(context, SNOOZED_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_iris_notification)
            .setContentTitle(context.getString(R.string.notification_snoozed_title, when24))
            .setContentText(
                if (label != null) {
                    context.getString(R.string.notification_snoozed_text, label)
                } else {
                    context.getString(R.string.notification_snoozed_text_generic)
                },
            )
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setWhen(firesAtMillis)
            .setOngoing(true)
            .setSilent(true)
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_iris_notification,
                    context.getString(R.string.notification_snoozed_cancel),
                    cancelSnoozePendingIntent(context),
                ).build(),
            )
            .build()

        // The snooze is armed whether or not it can be announced, so a refused
        // POST_NOTIFICATIONS costs the note and nothing else.
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            return
        }

        NotificationManagerCompat.from(context)
            .runCatching { notify(SNOOZED_NOTIFICATION_ID, notification) }
    }

    fun clearSnoozed(context: Context) {
        NotificationManagerCompat.from(context).cancel(SNOOZED_NOTIFICATION_ID)
    }

    private fun cancelSnoozePendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            AlarmContract.CANCEL_SNOOZE_REQUEST_CODE,
            Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmContract.ACTION_CANCEL_SNOOZE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

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
