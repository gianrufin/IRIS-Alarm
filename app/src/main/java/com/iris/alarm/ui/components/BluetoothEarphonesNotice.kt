package com.iris.alarm.ui.components

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.getSystemService
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Checks for connected Bluetooth audio devices (A2DP, Headset, BLE audio).
 */
fun getConnectedBluetoothDeviceName(context: Context): String? {
    val audioManager = context.getSystemService<AudioManager>() ?: return null
    val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    val bt = devices.firstOrNull { device ->
        device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                (device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER))
    }
    return bt?.productName?.toString()?.takeIf { it.isNotBlank() }
}

/**
 * Casual, reassuring card with real-time Bluetooth connection feedback and
 * sleep readiness reminder.
 */
@Composable
fun BluetoothEarphonesNotice(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val connectedDevice by produceState<String?>(initialValue = getConnectedBluetoothDeviceName(context)) {
        while (isActive) {
            value = getConnectedBluetoothDeviceName(context)
            delay(2000)
        }
    }

    val isConnected = connectedDevice != null
    val indicatorColor by animateColorAsState(
        targetValue = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        label = "btIndicator",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .border(
                width = 1.dp,
                color = if (isConnected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                },
                shape = RoundedCornerShape(20.dp),
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(indicatorColor),
            )
            Text(
                text = if (isConnected) {
                    "CONNECTED: ${connectedDevice?.uppercase()}"
                } else {
                    "NO EARPHONES CONNECTED RIGHT NOW"
                },
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                ),
                color = if (isConnected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f),
            )
        }

        Text(
            text = "Make sure you're connected to Bluetooth earphones when sleeping so you don't miss your wake-up.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        )

        Text(
            text = "Safety note: If your earphones disconnect overnight, IRIS will automatically ring through the phone speaker so you never oversleep.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        )
    }
}
