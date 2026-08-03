package com.iris.alarm.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.iris.alarm.ui.editor.AlarmEditorScreen
import com.iris.alarm.ui.onboarding.OnboardingScreen
import com.iris.alarm.ui.editor.AlarmEditorViewModel
import com.iris.alarm.ui.editor.AnchorCaptureScreen
import com.iris.alarm.ui.permissions.PermissionsScreen
import com.iris.alarm.ui.settings.SettingsScreen

private object Routes {
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val SETTINGS = "settings"
    const val PERMISSIONS = "permissions"
    const val EDITOR = "editor"
    const val ANCHOR_CAPTURE = "anchor"
    const val EDITOR_WITH_ARG =
        "$EDITOR?${AlarmEditorViewModel.ARG_ALARM_ID}={${AlarmEditorViewModel.ARG_ALARM_ID}}"

    fun editor(alarmId: Long) = "$EDITOR?${AlarmEditorViewModel.ARG_ALARM_ID}=$alarmId"
}

private const val TRANSITION_MILLIS = 260

@Composable
fun IrisNavHost(
    onboardingComplete: Boolean,
    onOnboardingFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = if (onboardingComplete) Routes.DASHBOARD else Routes.ONBOARDING,
        modifier = modifier,
        // Screens slide in from the right and fade, so moving deeper into the app
        // reads as a direction rather than a cut.
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                tween(TRANSITION_MILLIS),
            ) + fadeIn(tween(TRANSITION_MILLIS))
        },
        exitTransition = { fadeOut(tween(TRANSITION_MILLIS / 2)) },
        popEnterTransition = { fadeIn(tween(TRANSITION_MILLIS)) },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                tween(TRANSITION_MILLIS),
            ) + fadeOut(tween(TRANSITION_MILLIS))
        },
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFinished = {
                    onOnboardingFinished()
                    navController.navigate(Routes.DASHBOARD) {
                        // Setup is done; there is nothing to come back to.
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.DASHBOARD) {
            IrisHome(
                onAddAlarm = {
                    navController.navigate(Routes.editor(AlarmEditorViewModel.NEW_ALARM_ID))
                },
                onEditAlarm = { id -> navController.navigate(Routes.editor(id)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) },
            )
        }

        composable(Routes.PERMISSIONS) {
            PermissionsScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.EDITOR_WITH_ARG,
            arguments = listOf(
                navArgument(AlarmEditorViewModel.ARG_ALARM_ID) {
                    type = NavType.LongType
                    defaultValue = AlarmEditorViewModel.NEW_ALARM_ID
                },
            ),
        ) { entry ->
            // Scoped to the editor entry so the capture screen writes the anchor
            // into the same draft the editor is showing.
            val editorViewModel: AlarmEditorViewModel = hiltViewModel(entry)

            AlarmEditorScreen(
                onDone = { navController.popBackStack() },
                onCaptureAnchor = { navController.navigate(Routes.ANCHOR_CAPTURE) },
                viewModel = editorViewModel,
            )
        }

        composable(
            route = Routes.ANCHOR_CAPTURE,
            // The camera opening up is a different kind of move, so it scales in
            // rather than sliding like the rest of the stack.
            enterTransition = { scaleIn(tween(TRANSITION_MILLIS), 0.92f) + fadeIn() },
            popExitTransition = { scaleOut(tween(TRANSITION_MILLIS), 0.92f) + fadeOut() },
        ) { entry ->
            // Keyed on this destination's own entry: remembering against the
            // controller alone would hand back a stale editor after the back
            // stack changes underneath it.
            val editorEntry = remember(entry) {
                navController.getBackStackEntry(Routes.EDITOR_WITH_ARG)
            }
            val editorViewModel: AlarmEditorViewModel = hiltViewModel(editorEntry)

            AnchorCaptureScreen(
                onCaptured = { captured ->
                    editorViewModel.setAnchor(
                        signature = captured.signature.serialise(),
                        thumbnailPath = captured.thumbnailPath,
                    )
                    navController.popBackStack()
                },
                onCancel = { navController.popBackStack() },
            )
        }
    }
}
