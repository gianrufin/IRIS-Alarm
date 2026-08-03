package com.iris.alarm.ui.editor

import androidx.camera.core.CameraSelector
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.iris.alarm.ui.challenge.CameraWindow
import com.iris.alarm.vision.AnchorCapture
import com.iris.alarm.vision.AnchorPreviewAnalyzer
import com.iris.alarm.vision.CapturedAnchor
import kotlinx.coroutines.delay

/**
 * Point the rear camera at the spot the alarm should send you to, and capture it.
 *
 * The guidance is deliberate: the anchor is matched on structure, so a blank
 * wall gives the matcher nothing to lock onto and would either never pass or
 * pass anywhere. Somewhere across the room is also the whole point — an anchor
 * captured from bed can be satisfied from bed.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AnchorCaptureScreen(
    onCaptured: (CapturedAnchor) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)
    var flash by remember { mutableStateOf(false) }

    // The analyser runs on CameraX's background executor, so the captured anchor
    // is parked in state and handed over from a LaunchedEffect. Calling back
    // straight from the analysis thread navigated off the main thread, which
    // crashed the app the moment a spot was captured.
    var captured by remember { mutableStateOf<CapturedAnchor?>(null) }

    val analyzer = remember {
        AnchorPreviewAnalyzer { image ->
            val anchor = try {
                AnchorCapture.capture(context, image)
            } finally {
                image.close()
            }
            captured = anchor
        }
    }
    DisposableEffect(analyzer) { onDispose { analyzer.close() } }

    LaunchedEffect(captured) {
        val anchor = captured ?: return@LaunchedEffect
        // Let the shutter flash register before the screen goes away.
        delay(FLASH_MILLIS)
        onCaptured(anchor)
    }

    // A failed capture must not leave the button dead.
    LaunchedEffect(flash) {
        if (!flash) return@LaunchedEffect
        delay(FLASH_MILLIS)
        flash = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (cameraPermission.status.isGranted) {
            CameraWindow(
                lensFacing = CameraSelector.LENS_FACING_BACK,
                analyzer = analyzer,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // A white blink on capture, so the tap has an unmistakable response even
        // though nothing leaves the screen.
        AnimatedVisibility(visible = flash, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "SET YOUR TARGET",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Point at a spot across the room you will have to walk " +
                        "to — a shelf, the kettle, the bathroom mirror. Avoid blank " +
                        "walls: the match needs something with shape to lock onto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!cameraPermission.status.isGranted) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "CAMERA ACCESS NEEDED TO CAPTURE A TARGET",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Button(
                        onClick = cameraPermission::launchPermissionRequest,
                        shape = RoundedCornerShape(32.dp),
                    ) {
                        Text("GRANT ACCESS", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        flash = true
                        analyzer.capturing = true
                    },
                    enabled = cameraPermission.status.isGranted,
                    shape = RoundedCornerShape(32.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "CAPTURE THIS SPOT",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                }
                Text(
                    text = "CANCEL",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onCancel)
                        .padding(8.dp),
                )
            }
        }
    }
}

private const val FLASH_MILLIS = 220L
