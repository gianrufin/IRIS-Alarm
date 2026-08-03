package com.iris.alarm.ui.permissions

import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import com.iris.alarm.permissions.AlarmPermission
import com.iris.alarm.permissions.AlarmPermissionChecker
import com.iris.alarm.permissions.PermissionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class PermissionsViewModel @Inject constructor(
    private val checker: AlarmPermissionChecker,
) : ViewModel() {

    private val _states = MutableStateFlow(checker.state())
    val states: StateFlow<List<PermissionState>> = _states.asStateFlow()

    /**
     * Nothing notifies an app that a permission changed in system settings, so
     * the screen re-reads them whenever the user comes back from one.
     */
    fun refresh() {
        _states.value = checker.state()
    }

    fun settingsIntent(permission: AlarmPermission): Intent? = checker.settingsIntent(permission)

    fun appSettingsIntent(): Intent = checker.appSettingsIntent()

    /**
     * POST_NOTIFICATIONS is a runtime permission from API 33, and a settings-only
     * toggle before that.
     */
    fun notificationRuntimePermission(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.POST_NOTIFICATIONS
        } else {
            null
        }
}
