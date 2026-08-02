package com.iris.alarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.iris.alarm.domain.repository.AlarmRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * AlarmManager forgets everything across a reboot, and a time or timezone change
 * invalidates the wall-clock instants we computed. All three re-arm from Room.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: AlarmRepository

    @Inject lateinit var scheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> Unit

            else -> return
        }

        val pending = goAsync()
        scope.launch {
            try {
                val alarms = repository.getEnabledAlarms()
                scheduler.rescheduleAll(alarms)
                Log.i(TAG, "Rescheduled ${alarms.size} alarms after ${intent.action}")
            } catch (t: Throwable) {
                Log.e(TAG, "Reschedule failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
