package com.iris.alarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.iris.alarm.domain.repository.AlarmRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Entry point for a fired alarm. Receivers get ~10 seconds of runtime, so this
 * does the minimum: hand off to [AlarmForegroundService], then re-arm repeating
 * alarms / disable one-shots on a background scope guarded by a wake lock.
 */
@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: AlarmRepository

    @Inject lateinit var scheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmContract.ACTION_ALARM_FIRED) return

        val alarmId = intent.getLongExtra(AlarmContract.EXTRA_ALARM_ID, AlarmContract.NO_ALARM_ID)
        Log.i(TAG, "Alarm $alarmId fired")

        ContextCompat.startForegroundService(
            context,
            Intent(context, AlarmForegroundService::class.java).apply {
                action = AlarmContract.ACTION_START
                putExtra(AlarmContract.EXTRA_ALARM_ID, alarmId)
            },
        )

        if (alarmId == AlarmContract.NO_ALARM_ID) return

        // goAsync() keeps the process alive past onReceive() so the follow-up
        // database write is not killed mid-flight.
        val pending = goAsync()
        scope.launch {
            try {
                val alarm = repository.getAlarm(alarmId) ?: return@launch
                if (alarm.isRepeating) {
                    scheduler.schedule(alarm)
                } else {
                    repository.setEnabled(alarm.id, enabled = false)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to re-arm alarm $alarmId", t)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "AlarmReceiver"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
