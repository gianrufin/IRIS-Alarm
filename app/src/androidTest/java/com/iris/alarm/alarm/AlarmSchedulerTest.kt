package com.iris.alarm.alarm

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iris.alarm.domain.model.Alarm
import java.time.DayOfWeek
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the real `AlarmManager` plumbing: that scheduling registers a
 * broadcast the OS can deliver, and that cancelling actually removes it.
 *
 * `FLAG_NO_CREATE` returning non-null is the only way to observe a registered
 * PendingIntent from the app side, and it is exactly what would break if the
 * request code or intent action ever drifted between schedule and cancel — the
 * bug class that leaves an alarm ringing after the user deletes it.
 */
@RunWith(AndroidJUnit4::class)
class AlarmSchedulerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var scheduler: AlarmScheduler

    private val alarm = Alarm(
        id = 90_001L,
        hour = 6,
        minute = 30,
        repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
    )

    @Before
    fun setUp() {
        scheduler = AlarmScheduler(context)
        scheduler.cancel(alarm.id)
        scheduler.cancelWakeCheck()
    }

    @After
    fun tearDown() {
        scheduler.cancel(alarm.id)
        scheduler.cancelWakeCheck()
    }

    @Test
    fun schedulingRegistersABroadcastAndCancellingRemovesIt() {
        assertNull("Nothing should be registered before scheduling", existingPendingIntent())

        scheduler.schedule(alarm)
        assertNotNull("schedule() did not register a pending broadcast", existingPendingIntent())

        scheduler.cancel(alarm.id)
        assertNull("cancel() left the pending broadcast behind", existingPendingIntent())
    }

    @Test
    fun schedulingADisabledAlarmCancelsInstead() {
        scheduler.schedule(alarm)
        assertNotNull(existingPendingIntent())

        scheduler.schedule(alarm.copy(enabled = false))

        assertNull("A disabled alarm must not stay armed", existingPendingIntent())
    }

    @Test
    fun theWakeCheckIsScheduledAndCancelledIndependentlyOfTheAlarm() {
        scheduler.schedule(alarm)

        scheduler.scheduleWakeCheck(alarm.id, System.currentTimeMillis() + 5 * 60_000L)
        assertNotNull("scheduleWakeCheck() did not register a check", wakeCheckPendingIntent())

        scheduler.cancelWakeCheck()

        assertNull("cancelWakeCheck() left the check armed", wakeCheckPendingIntent())
        assertNotNull(
            "Cancelling the check must not disarm the alarm itself",
            existingPendingIntent(),
        )
    }

    /**
     * The check uses a fixed request code, so scheduling a second one replaces
     * the first rather than stacking a second ring.
     */
    @Test
    fun schedulingASecondWakeCheckReplacesTheFirst() {
        scheduler.scheduleWakeCheck(alarm.id, System.currentTimeMillis() + 5 * 60_000L)
        scheduler.scheduleWakeCheck(alarm.id, System.currentTimeMillis() + 10 * 60_000L)

        scheduler.cancelWakeCheck()

        assertNull("A second check outlived the cancel", wakeCheckPendingIntent())
    }

    private fun wakeCheckPendingIntent(): PendingIntent? = PendingIntent.getBroadcast(
        context,
        AlarmContract.WAKE_CHECK_REQUEST_CODE,
        Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmContract.ACTION_WAKE_CHECK
        },
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Non-null only when a matching PendingIntent is already registered. */
    private fun existingPendingIntent(): PendingIntent? = PendingIntent.getBroadcast(
        context,
        AlarmContract.triggerRequestCode(alarm.id),
        Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmContract.ACTION_ALARM_FIRED
        },
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    )
}
