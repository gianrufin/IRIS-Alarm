package com.iris.alarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iris.alarm.ui.AppViewModel
import com.iris.alarm.ui.IrisNavHost
import com.iris.alarm.ui.theme.IrisTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val appViewModel: AppViewModel = hiltViewModel()
            val settings by appViewModel.settings.collectAsStateWithLifecycle()

            // Nothing is drawn until the stored theme is known, so the app never
            // flashes light before finding out the user picked dark.
            val current = settings ?: return@setContent

            // Permissions are asked for during onboarding and reviewable in
            // settings, rather than fired blind at launch.
            IrisTheme(themeMode = current.themeMode) {
                IrisNavHost(
                    onboardingComplete = current.onboardingComplete,
                    onOnboardingFinished = appViewModel::completeOnboarding,
                )
            }
        }
    }
}
