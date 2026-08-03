package com.iris.alarm.data.update

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import androidx.core.content.IntentCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface InstallResult {
    /** The system is showing its confirmation dialog. */
    data object AwaitingUser : InstallResult
    data object Success : InstallResult
    data class Failed(val reason: String) : InstallResult
}

/**
 * Installs a downloaded APK through [PackageInstaller].
 *
 * Android will only accept an update signed with the same key as the installed
 * app, which is why every IRIS release is signed with the repository's side-load
 * key. An APK from elsewhere — or a build signed with a debug key — is rejected
 * by the platform with INSTALL_FAILED_UPDATE_INCOMPATIBLE, and that is reported
 * verbatim rather than swallowed.
 */
@Singleton
class ApkInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _results = MutableSharedFlow<InstallResult>(extraBufferCapacity = 4)
    val results: SharedFlow<InstallResult> = _results.asSharedFlow()

    /**
     * False until the user grants "install unknown apps" for IRIS. This is the
     * usual reason an in-app update stops dead, so it is checked before anything
     * is downloaded rather than discovered at commit time.
     */
    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun install(apk: File): Result<Unit> = runCatching {
        require(apk.exists() && apk.length() > 0) { "The downloaded file is missing or empty" }

        registerReceiver()

        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL,
        ).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Skips the "app installed" landing screen after confirmation.
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_UNSPECIFIED)
            }
        }

        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite(APK_NAME, 0, apk.length()).use { output ->
                apk.inputStream().use { input -> input.copyTo(output) }
                session.fsync(output)
            }

            val intent = Intent(ACTION_INSTALL_RESULT).setPackage(context.packageName)
            val pending = PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pending.intentSender)
        }
    }

    private var receiver: BroadcastReceiver? = null

    private fun registerReceiver() {
        if (receiver != null) return

        val installReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        val confirmation = IntentCompat.getParcelableExtra(
                            intent,
                            Intent.EXTRA_INTENT,
                            Intent::class.java,
                        )
                        if (confirmation == null) {
                            emit(InstallResult.Failed("The system did not return a confirmation screen"))
                            return
                        }
                        // The session was committed from outside an Activity, so
                        // the confirmation needs its own task. The intent comes
                        // from PackageInstaller itself and arrives on a receiver
                        // registered NOT_EXPORTED, so nothing outside the app can
                        // put an intent here to be launched.
                        @SuppressLint("UnsafeIntentLaunch")
                        confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(confirmation)
                        emit(InstallResult.AwaitingUser)
                    }

                    PackageInstaller.STATUS_SUCCESS -> emit(InstallResult.Success)

                    else -> {
                        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                        Log.w(TAG, "Install failed: status=$status message=$message")
                        emit(InstallResult.Failed(message ?: "Install failed (status $status)"))
                    }
                }
            }
        }

        val filter = IntentFilter(ACTION_INSTALL_RESULT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(installReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(installReceiver, filter)
        }
        receiver = installReceiver
    }

    private fun emit(result: InstallResult) {
        _results.tryEmit(result)
    }

    private companion object {
        const val TAG = "ApkInstaller"
        const val APK_NAME = "iris-update.apk"
        const val ACTION_INSTALL_RESULT = "com.iris.alarm.action.INSTALL_RESULT"
    }
}
