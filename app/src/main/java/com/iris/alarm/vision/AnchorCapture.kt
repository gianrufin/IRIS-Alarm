package com.iris.alarm.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.camera.core.ImageProxy
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Captured anchor: the signature that will be matched against at alarm time, and
 * a thumbnail so the user is reminded what they aimed at.
 */
data class CapturedAnchor(
    val signature: SceneSignature,
    val thumbnailPath: String,
)

/**
 * Turns the frame the user framed up into a stored anchor.
 *
 * The thumbnail is deliberately greyscale and tiny: it is drawn from the same
 * luminance plane the matcher uses, so what the user sees is what the detector
 * compares, and no colour photograph of someone's home is written to disk.
 */
object AnchorCapture {

    private const val THUMBNAIL_WIDTH = 96
    private const val THUMBNAIL_HEIGHT = 128
    private const val DIRECTORY = "anchors"

    fun capture(context: Context, image: ImageProxy): CapturedAnchor? {
        val signature = SceneSignature.from(image) ?: return null
        val bitmap = luminanceThumbnail(image) ?: return null
        val path = write(context, bitmap) ?: return null
        return CapturedAnchor(signature, path)
    }

    /** Removes a thumbnail that is no longer referenced by any alarm. */
    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }

    private fun luminanceThumbnail(image: ImageProxy): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val width = image.width
        val height = image.height
        if (width <= 0 || height <= 0) return null

        val pixels = IntArray(THUMBNAIL_WIDTH * THUMBNAIL_HEIGHT)
        for (row in 0 until THUMBNAIL_HEIGHT) {
            val sourceY = (row * height / THUMBNAIL_HEIGHT).coerceIn(0, height - 1)
            for (column in 0 until THUMBNAIL_WIDTH) {
                val sourceX = (column * width / THUMBNAIL_WIDTH).coerceIn(0, width - 1)
                val index = sourceY * rowStride + sourceX * pixelStride
                val luminance = if (index in 0 until buffer.limit()) {
                    buffer.get(index).toInt() and 0xFF
                } else {
                    0
                }
                pixels[row * THUMBNAIL_WIDTH + column] =
                    Color.rgb(luminance, luminance, luminance)
            }
        }

        return Bitmap.createBitmap(
            pixels,
            THUMBNAIL_WIDTH,
            THUMBNAIL_HEIGHT,
            Bitmap.Config.ARGB_8888,
        )
    }

    private fun write(context: Context, bitmap: Bitmap): String? {
        val directory = File(context.filesDir, DIRECTORY).apply { mkdirs() }
        val file = File(directory, "${UUID.randomUUID()}.png")
        return runCatching {
            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, output)
            }
            file.absolutePath
        }.getOrNull()
    }
}

/**
 * Keeps the signature of the most recent frame so that "capture" is instant —
 * there is no shutter round-trip between the user tapping and the frame being
 * fingerprinted.
 */
class AnchorPreviewAnalyzer(
    private val onFrame: (ImageProxy) -> Unit,
) : VisionAnalyzer {

    @Volatile
    var capturing: Boolean = false

    override fun analyze(imageProxy: ImageProxy) {
        if (!capturing) {
            imageProxy.close()
            return
        }
        capturing = false
        // The callback owns closing the frame, since it needs the pixels.
        onFrame(imageProxy)
    }

    override fun close() = Unit
}
