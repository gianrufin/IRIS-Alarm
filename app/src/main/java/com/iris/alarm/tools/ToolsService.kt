package com.iris.alarm.tools

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.iris.alarm.MainActivity
import com.iris.alarm.R
import com.iris.alarm.ui.EntryIntents
import com.iris.alarm.ui.tools.formatCountdown
import com.iris.alarm.ui.tools.formatStopwatch
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Keeps a running timer, stopwatch or focus block alive and on the status bar,
 * with its controls on the notification — the way a phone's own clock behaves.
 *
 * The service holds no state of its own: [ToolsEngine] is the single source of
 * truth, and this only renders it and forwards button presses back. That is what
 * stops the notification and the screen disagreeing about whether something is
 * paused.
 */
@AndroidEntryPoint
class ToolsService : Service() {

    @Inject lateinit var engine: ToolsEngine

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)

        scope.launch {
            combine(engine.timer, engine.stopwatch, engine.pomodoro) { timer, stopwatch, pomodoro ->
                Snapshot(timer.running, stopwatch.running, pomodoro.running, render())
            }
                // Only re-post when the text or the buttons actually change; a
                // stopwatch ticking hundredths would otherwise rebuild the
                // notification sixty times a second.
                .distinctUntilChanged()
                .collect { snapshot ->
                    if (!snapshot.anyRunning) {
                        stopSelf()
                        return@collect
                    }
                    notificationManager()?.notify(NOTIFICATION_ID, snapshot.notification)
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TIMER_TOGGLE -> engine.toggleTimer()
            ACTION_TIMER_RESET -> engine.resetTimer()
            ACTION_STOPWATCH_TOGGLE -> engine.toggleStopwatch()
            ACTION_STOPWATCH_LAP -> engine.lap()
            ACTION_STOPWATCH_RESET -> engine.resetStopwatch()
            ACTION_POMODORO_TOGGLE -> engine.togglePomodoro()
            ACTION_POMODORO_SKIP -> engine.advancePomodoro()
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            render(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )

        // A tool paused from the notification leaves nothing to keep alive.
        if (!engine.anythingRunning.value) stopSelf()

        return START_STICKY
    }

    /** The notification for whichever tool is running; the timer wins if several. */
    private fun render(): Notification {
        val timer = engine.timer.value
        val stopwatch = engine.stopwatch.value
        val pomodoro = engine.pomodoro.value

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_iris_notification)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        return when {
            timer.running || timer.finished -> builder
                .setContentTitle(
                    if (timer.finished) {
                        getString(R.string.tools_timer_done)
                    } else {
                        formatCountdown(timer.remainingMillis)
                    },
                )
                .setContentText(getString(R.string.tools_timer))
                .setContentIntent(openIntent(EntryIntents.ACTION_SHORTCUT_TIMER))
                .addAction(
                    action(
                        if (timer.running) R.string.tools_pause else R.string.tools_resume,
                        ACTION_TIMER_TOGGLE,
                    ),
                )
                .addAction(action(R.string.tools_reset, ACTION_TIMER_RESET))
                .build()

            pomodoro.running -> builder
                .setContentTitle(formatCountdown(pomodoro.remainingMillis))
                .setContentText(pomodoro.phase.label)
                .setContentIntent(openIntent(EntryIntents.ACTION_SHORTCUT_POMODORO))
                .addAction(action(R.string.tools_pause, ACTION_POMODORO_TOGGLE))
                .addAction(action(R.string.tools_skip, ACTION_POMODORO_SKIP))
                .build()

            else -> builder
                // Seconds only: the notification cannot repaint fast enough for
                // hundredths, and a stuttering readout looks broken.
                .setContentTitle(formatStopwatch(stopwatch.elapsedMillis).substringBefore('.'))
                .setContentText(getString(R.string.tools_stopwatch))
                .setContentIntent(openIntent(EntryIntents.ACTION_SHORTCUT_STOPWATCH))
                .addAction(
                    action(
                        if (stopwatch.running) R.string.tools_pause else R.string.tools_resume,
                        ACTION_STOPWATCH_TOGGLE,
                    ),
                )
                .addAction(
                    if (stopwatch.running) {
                        action(R.string.tools_lap, ACTION_STOPWATCH_LAP)
                    } else {
                        action(R.string.tools_reset, ACTION_STOPWATCH_RESET)
                    },
                )
                .build()
        }
    }

    private fun action(labelRes: Int, action: String): NotificationCompat.Action =
        NotificationCompat.Action.Builder(
            R.drawable.ic_iris_notification,
            getString(labelRes),
            PendingIntent.getService(
                this,
                action.hashCode(),
                Intent(this, ToolsService::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        ).build()

    private fun openIntent(action: String): PendingIntent = PendingIntent.getActivity(
        this,
        action.hashCode(),
        Intent(this, MainActivity::class.java)
            .setAction(action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun notificationManager(): NotificationManager? = getSystemService()

    override fun onDestroy() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        scope.cancel()
        super.onDestroy()
    }

    /** What the collector compares, so identical renders do not re-post. */
    private data class Snapshot(
        val timerRunning: Boolean,
        val stopwatchRunning: Boolean,
        val pomodoroRunning: Boolean,
        val notification: Notification,
    ) {
        val anyRunning: Boolean get() = timerRunning || stopwatchRunning || pomodoroRunning

        // Notification has no useful equals, so comparison is on the flags plus
        // the text the user can actually see.
        override fun equals(other: Any?): Boolean {
            if (other !is Snapshot) return false
            return timerRunning == other.timerRunning &&
                stopwatchRunning == other.stopwatchRunning &&
                pomodoroRunning == other.pomodoroRunning &&
                notification.extras?.getCharSequence(Notification.EXTRA_TITLE) ==
                other.notification.extras?.getCharSequence(Notification.EXTRA_TITLE)
        }

        override fun hashCode(): Int {
            var result = timerRunning.hashCode()
            result = 31 * result + stopwatchRunning.hashCode()
            result = 31 * result + pomodoroRunning.hashCode()
            result = 31 * result +
                (notification.extras?.getCharSequence(Notification.EXTRA_TITLE)?.hashCode() ?: 0)
            return result
        }
    }

    companion object {
        const val CHANNEL_ID = "iris_tools"
        const val NOTIFICATION_ID = 4712

        private const val ACTION_TIMER_TOGGLE = "com.iris.alarm.action.TIMER_TOGGLE"
        private const val ACTION_TIMER_RESET = "com.iris.alarm.action.TIMER_RESET"
        private const val ACTION_STOPWATCH_TOGGLE = "com.iris.alarm.action.STOPWATCH_TOGGLE"
        private const val ACTION_STOPWATCH_LAP = "com.iris.alarm.action.STOPWATCH_LAP"
        private const val ACTION_STOPWATCH_RESET = "com.iris.alarm.action.STOPWATCH_RESET"
        private const val ACTION_POMODORO_TOGGLE = "com.iris.alarm.action.POMODORO_TOGGLE"
        private const val ACTION_POMODORO_SKIP = "com.iris.alarm.action.POMODORO_SKIP"

        fun ensureChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_tools_name),
                // Low: it should sit in the shade, never interrupt.
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.channel_tools_description)
                setShowBadge(false)
                setSound(null, null)
            }
            context.getSystemService<NotificationManager>()?.createNotificationChannel(channel)
        }

        /** Called whenever a tool starts, so the status bar picks it up. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ToolsService::class.java),
            )
        }
    }
}
