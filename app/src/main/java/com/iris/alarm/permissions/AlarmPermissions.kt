package com.iris.alarm.permissions

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.iris.alarm.alarm.AlarmNotifications
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything the OS can take away that would stop an alarm from waking someone.
 *
 * [required] entries are the ones without which the alarm silently fails; the
 * rest degrade it. Each carries the intent that takes the user to the right
 * settings page, because none of these can be granted from inside the app.
 */
enum class AlarmPermission(val title: String, val why: String, val required: Boolean) {
    NOTIFICATIONS(
        title = "Notifications",
        why = "The ringing alarm is delivered as a notification. Without this the " +
            "alarm cannot show anything at all.",
        required = true,
    ),
    FULL_SCREEN(
        title = "Full-screen alerts",
        why = "Lets the alarm take over the screen while the phone is locked, " +
            "instead of appearing as a banner you have to find.",
        required = true,
    ),
    EXACT_ALARMS(
        title = "Exact alarms",
        why = "Without this Android may fire the alarm late — sometimes by many " +
            "minutes — to save battery.",
        required = true,
    ),
    BATTERY(
        title = "Unrestricted battery",
        why = "Battery optimisation can delay or drop an alarm while the phone is " +
            "idle overnight, which is exactly when it needs to fire.",
        required = true,
    ),
    CAMERA(
        title = "Camera",
        why = "Needed for the smile and target challenges. The light challenge " +
            "works without it.",
        required = false,
    ),
}

data class PermissionState(
    val permission: AlarmPermission,
    val granted: Boolean,
)

@Singleton
class AlarmPermissionChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun state(): List<PermissionState> =
        AlarmPermission.entries.map { PermissionState(it, isGranted(it)) }

    /** True once nothing required is missing. */
    fun allRequiredGranted(): Boolean =
        AlarmPermission.entries.filter { it.required }.all(::isGranted)

    fun isGranted(permission: AlarmPermission): Boolean = when (permission) {
        AlarmPermission.NOTIFICATIONS ->
            NotificationManagerCompat.from(context).areNotificationsEnabled()

        AlarmPermission.FULL_SCREEN -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.getSystemService<NotificationManager>()?.canUseFullScreenIntent() ?: false
        } else {
            // Below API 34 the permission is granted at install time.
            true
        }

        AlarmPermission.EXACT_ALARMS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService<AlarmManager>()?.canScheduleExactAlarms() ?: false
        } else {
            true
        }

        AlarmPermission.BATTERY ->
            context.getSystemService<PowerManager>()
                ?.isIgnoringBatteryOptimizations(context.packageName) ?: false

        AlarmPermission.CAMERA -> ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * The settings screen that grants [permission], or null when it is a runtime
     * permission the caller should request through the normal dialog instead.
     */
    fun settingsIntent(permission: AlarmPermission): Intent? = when (permission) {
        AlarmPermission.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

        AlarmPermission.FULL_SCREEN ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                Intent(
                    Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    packageUri(),
                )
            } else {
                null
            }

        AlarmPermission.EXACT_ALARMS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri())
        } else {
            null
        }

        // This one shows a system dialog rather than a settings page, which is why
        // it is the only place the app asks to be exempted directly.
        AlarmPermission.BATTERY -> Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            packageUri(),
        )

        AlarmPermission.CAMERA -> null
    }

    /** Fallback for OEMs that bury autostart controls in their own app settings. */
    fun appSettingsIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri())

    fun notificationChannelIntent(): Intent =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, AlarmNotifications.CHANNEL_ID)

    private fun packageUri(): Uri = Uri.fromParts("package", context.packageName, null)
}
