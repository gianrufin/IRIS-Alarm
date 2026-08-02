package com.iris.alarm.vision

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.getSystemService
import com.iris.alarm.domain.model.ChallengeThresholds
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Ambient light detector for the "Light Iris" challenge. Emits a
 * [ChallengeProgress] per sensor reading and solves once lux has stayed above
 * [ChallengeThresholds.LUMEN_TARGET] for the required hold, so a torch swept past
 * the sensor does not count.
 */
class LumenMonitor(private val context: Context) {

    /** False on devices with no light sensor; the caller must offer another challenge. */
    fun isSupported(): Boolean =
        context.getSystemService<SensorManager>()
            ?.getDefaultSensor(Sensor.TYPE_LIGHT) != null

    fun readings(): Flow<ChallengeProgress> = callbackFlow {
        val sensorManager = context.getSystemService<SensorManager>()
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (sensorManager == null || sensor == null) {
            trySend(
                ChallengeProgress(
                    fraction = 0f,
                    readout = "N/A",
                    hint = "NO LIGHT SENSOR ON THIS DEVICE",
                ),
            )
            awaitClose { }
            return@callbackFlow
        }

        var aboveSince: Long? = null
        var solved = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (solved) return
                val lux = event.values.firstOrNull() ?: return
                val now = System.currentTimeMillis()

                if (lux < ChallengeThresholds.LUMEN_TARGET) {
                    aboveSince = null
                    trySend(
                        ChallengeProgress(
                            fraction = (lux / ChallengeThresholds.LUMEN_TARGET).coerceIn(0f, 1f),
                            readout = "${lux.toInt()} LUX",
                            hint = "FIND BRIGHTER LIGHT",
                        ),
                    )
                    return
                }

                val startedAt = aboveSince ?: now.also { aboveSince = it }
                val held = now - startedAt
                if (held >= ChallengeThresholds.LUMEN_HOLD_MILLIS) {
                    solved = true
                    trySend(
                        ChallengeProgress(
                            fraction = 1f,
                            readout = "${lux.toInt()} LUX",
                            hint = "GOOD MORNING",
                            solved = true,
                        ),
                    )
                } else {
                    trySend(
                        ChallengeProgress(
                            fraction = 1f,
                            readout = "${lux.toInt()} LUX",
                            hint = "HOLD IT THERE",
                        ),
                    )
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose { sensorManager.unregisterListener(listener) }
    }
}
