package com.iris.alarm.vision

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.core.content.getSystemService
import com.iris.alarm.domain.model.DeviceCapabilities

class AndroidDeviceCapabilities(private val context: Context) : DeviceCapabilities {

    override val hasFrontCamera: Boolean
        get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)

    override val hasBackCamera: Boolean
        get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)

    override val hasLightSensor: Boolean
        get() = context.getSystemService<SensorManager>()
            ?.getDefaultSensor(Sensor.TYPE_LIGHT) != null
}
