package com.iris.alarm.ui.components

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/**
 * Loads an anchor thumbnail from disk. Returns null when the alarm has no anchor
 * or the file has gone — a missing thumbnail is a cosmetic loss, never a reason
 * to fail the screen it appears on.
 */
@Composable
fun rememberAnchorThumbnail(path: String?): ImageBitmap? = remember(path) {
    if (path.isNullOrBlank()) return@remember null
    val file = File(path)
    if (!file.exists()) return@remember null
    runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
}
