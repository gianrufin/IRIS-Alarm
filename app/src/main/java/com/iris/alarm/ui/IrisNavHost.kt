package com.iris.alarm.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.iris.alarm.ui.dashboard.DashboardScreen
import com.iris.alarm.ui.editor.AlarmEditorScreen
import com.iris.alarm.ui.editor.AlarmEditorViewModel
import com.iris.alarm.ui.settings.SettingsScreen

private object Routes {
    const val DASHBOARD = "dashboard"
    const val SETTINGS = "settings"
    const val EDITOR = "editor"
    const val EDITOR_WITH_ARG = "$EDITOR?${AlarmEditorViewModel.ARG_ALARM_ID}={${AlarmEditorViewModel.ARG_ALARM_ID}}"

    fun editor(alarmId: Long) = "$EDITOR?${AlarmEditorViewModel.ARG_ALARM_ID}=$alarmId"
}

@Composable
fun IrisNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.DASHBOARD,
        modifier = modifier,
    ) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onAddAlarm = {
                    navController.navigate(Routes.editor(AlarmEditorViewModel.NEW_ALARM_ID))
                },
                onEditAlarm = { id -> navController.navigate(Routes.editor(id)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen()
        }

        composable(
            route = Routes.EDITOR_WITH_ARG,
            arguments = listOf(
                navArgument(AlarmEditorViewModel.ARG_ALARM_ID) {
                    type = NavType.LongType
                    defaultValue = AlarmEditorViewModel.NEW_ALARM_ID
                },
            ),
        ) {
            AlarmEditorScreen(onDone = { navController.popBackStack() })
        }
    }
}
