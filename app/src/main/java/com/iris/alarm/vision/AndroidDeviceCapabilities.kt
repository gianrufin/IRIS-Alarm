package com.iris.alarm.vision

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.core.content.getSystemService
import com.iris.alarm.domain.model.DeviceCapabilities

class AndroidDeviceCapabilities(private val context: Context) : DeviceCapabilities {

    override val hasFrontCamera: Boolean
        get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)

    /**
     * Deliberately the rear-camera-only feature rather than FEATURE_CAMERA_ANY:
     * an object hunt is meant to point away from the user. Devices with no rear
     * camera are the case `resolveChallenge` exists to handle, so answering
     * "false" here is the correct outcome, not a bug to be papered over.
     */
    override val hasBackCamera: Boolean
        @SuppressLint("UnsupportedChromeOsCameraSystemFeature")
        get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)

    override val hasLightSensor: Boolean
        get() = context.getSystemService<SensorManager>()
            ?.getDefaultSensor(Sensor.TYPE_LIGHT) != null
}
