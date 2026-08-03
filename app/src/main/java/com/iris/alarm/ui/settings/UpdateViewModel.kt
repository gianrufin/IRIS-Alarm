package com.iris.alarm.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iris.alarm.BuildConfig
import com.iris.alarm.data.update.ApkInstaller
import com.iris.alarm.data.update.GitHubUpdateSource
import com.iris.alarm.data.update.InstallResult
import com.iris.alarm.domain.model.AppUpdate
import com.iris.alarm.domain.model.UpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class UpdateViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val source: GitHubUpdateSource,
    private val installer: ApkInstaller,
) : ViewModel() {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val installedVersion: String = BuildConfig.VERSION_NAME

    init {
        viewModelScope.launch {
            installer.results.collect { result ->
                _state.value = when (result) {
                    // The system dialog is up; nothing more for the app to do.
                    InstallResult.AwaitingUser -> UpdateState.Installing
                    InstallResult.Success -> UpdateState.UpToDate
                    is InstallResult.Failed -> UpdateState.Failed(result.reason)
                }
            }
        }
    }

    fun check() {
        if (_state.value is UpdateState.Checking) return
        _state.value = UpdateState.Checking

        viewModelScope.launch {
            source.latestUpdate()
                .onSuccess { update ->
                    _state.value = if (update == null) {
                        UpdateState.UpToDate
                    } else {
                        UpdateState.Available(update)
                    }
                }
                .onFailure { error ->
                    _state.value = UpdateState.Failed(
                        error.message ?: "Could not reach GitHub",
                    )
                }
        }
    }

    fun downloadAndInstall(update: AppUpdate) {
        if (!installer.canInstallPackages()) {
            // Not a failure: the user has simply never been asked. The UI turns
            // this into a button onto the right settings page.
            _state.value = UpdateState.NeedsInstallPermission(update)
            return
        }

        _state.value = UpdateState.Downloading(update, 0f)

        viewModelScope.launch {
            val destination = File(updateDirectory(), "iris-${update.versionName}.apk")

            source.download(update, destination) { fraction ->
                // Only overwrite while still downloading, so a failure reported
                // from elsewhere is not clobbered by a late progress callback.
                val current = _state.value
                if (current is UpdateState.Downloading) {
                    _state.value = current.copy(fraction = fraction)
                }
            }
                .onSuccess { file ->
                    _state.value = UpdateState.ReadyToInstall(update)
                    installer.install(file).onFailure { error ->
                        _state.value = UpdateState.Failed(
                            error.message ?: "Could not start the installer",
                        )
                    }
                }
                .onFailure { error ->
                    _state.value = UpdateState.Failed(
                        error.message ?: "Download failed",
                    )
                }
        }
    }

    fun dismissError() {
        _state.value = UpdateState.Idle
    }

    /** The "install unknown apps" page for IRIS specifically, not the whole list. */
    fun installPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.fromParts("package", context.packageName, null))

    /**
     * Called when the user comes back from that page. Granting is asynchronous
     * from the app's point of view — there is no result to read — so the only
     * honest thing is to re-ask the package manager and either resume or say
     * plainly that the toggle is still off.
     */
    fun onReturnedFromInstallSettings(update: AppUpdate) {
        if (installer.canInstallPackages()) {
            downloadAndInstall(update)
        } else {
            _state.value = UpdateState.NeedsInstallPermission(update)
        }
    }

    /** Cache, not files: a half-finished download is never worth keeping. */
    private fun updateDirectory(): File =
        File(context.cacheDir, "updates").apply { mkdirs() }
}
