package com.iris.alarm

import android.app.Application
import com.iris.alarm.alarm.AlarmNotifications
import com.iris.alarm.tools.ToolsService
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class IrisApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Created up front so the channel exists before the first alarm fires,
        // including the case where the app is launched only by the boot receiver.
        AlarmNotifications.ensureChannel(this)
        ToolsService.ensureChannel(this)
    }
}
