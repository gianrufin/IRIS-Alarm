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

    private const val THUMBNAIL_SHORT = 96
    private const val THUMBNAIL_LONG = 128
    private const val DIRECTORY = "anchors"

    fun capture(context: Context, image: ImageProxy): CapturedAnchor? {
        // Read once, in display orientation, so the stored thumbnail is the way
        // up the user framed it and the signature matches what they will see.
        val plane = LuminancePlane.from(image) ?: return null
        val signature = SceneSignature.from(plane)
        val bitmap = luminanceThumbnail(plane) ?: return null
        val path = write(context, bitmap) ?: return null
        return CapturedAnchor(signature, path)
    }

    /** Removes a thumbnail that is no longer referenced by any alarm. */
    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }

    /**
     * Keeps the frame's aspect ratio rather than squashing it into a fixed box,
     * so a landscape capture is stored landscape and still looks like the place.
     */
    private fun luminanceThumbnail(plane: LuminancePlane): Bitmap? {
        if (plane.width <= 0 || plane.height <= 0) return null

        val portrait = plane.height >= plane.width
        val width = if (portrait) THUMBNAIL_SHORT else THUMBNAIL_LONG
        val height = if (portrait) THUMBNAIL_LONG else THUMBNAIL_SHORT

        val pixels = IntArray(width * height)
        for (row in 0 until height) {
            val sourceY = row * plane.height / height
            for (column in 0 until width) {
                val sourceX = column * plane.width / width
                val luminance = plane.luminanceAt(sourceX, sourceY)
                pixels[row * width + column] = Color.rgb(luminance, luminance, luminance)
            }
        }

        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
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
