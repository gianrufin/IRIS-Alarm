package com.iris.alarm

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import com.iris.alarm.ui.IrisNavHost
import com.iris.alarm.ui.theme.IrisTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            IrisTheme {
                // Without POST_NOTIFICATIONS the ringing notification — and with it
                // the full-screen challenge intent — never reaches the user.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val launcher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission(),
                    ) { /* Denial is surfaced by the system; nothing to undo here. */ }
                    LaunchedEffect(Unit) {
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                IrisNavHost()
            }
        }
    }
}
