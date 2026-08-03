package com.iris.alarm.vision

import androidx.camera.core.ImageProxy

/**
 * Reads a camera frame's luminance in *display* orientation.
 *
 * YUV_420_888's Y plane is the greyscale image, but it arrives in the sensor's
 * own orientation — on most phones that is 90° off what the user was looking at.
 * Sampling it raw stored anchor thumbnails on their side, and left the matcher
 * comparing a portrait frame against a landscape one whenever the phone was held
 * differently from at capture.
 *
 * Everything here maps a coordinate in the upright image back to the sensor
 * buffer, so callers can pretend the frame was always the right way up.
 */
class LuminancePlane private constructor(
    private val read: (Int, Int) -> Int,
    val width: Int,
    val height: Int,
) {
    fun luminanceAt(x: Int, y: Int): Int = read(
        x.coerceIn(0, width - 1),
        y.coerceIn(0, height - 1),
    )

    companion object {
        /**
         * Wraps an arbitrary sensor-orientation reader. Exposed so the rotation
         * mapping can be tested without a camera.
         */
        fun of(
            sourceWidth: Int,
            sourceHeight: Int,
            rotationDegrees: Int,
            sensor: (Int, Int) -> Int,
        ): LuminancePlane {
            // Normalised so 90 and -270 mean the same thing.
            val rotation = ((rotationDegrees % 360) + 360) % 360
            val swapped = rotation == 90 || rotation == 270

            val read: (Int, Int) -> Int = when (rotation) {
                90 -> { x, y -> sensor(y, sourceHeight - 1 - x) }
                180 -> { x, y -> sensor(sourceWidth - 1 - x, sourceHeight - 1 - y) }
                270 -> { x, y -> sensor(sourceWidth - 1 - y, x) }
                else -> { x, y -> sensor(x, y) }
            }

            return LuminancePlane(
                read = read,
                width = if (swapped) sourceHeight else sourceWidth,
                height = if (swapped) sourceWidth else sourceHeight,
            )
        }

        fun from(image: ImageProxy): LuminancePlane? {
            val plane = image.planes.firstOrNull() ?: return null
            val buffer = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val sourceWidth = image.width
            val sourceHeight = image.height
            if (sourceWidth <= 0 || sourceHeight <= 0) return null

            fun sensor(x: Int, y: Int): Int {
                val index = y * rowStride + x * pixelStride
                if (index < 0 || index >= buffer.limit()) return 0
                return buffer.get(index).toInt() and 0xFF
            }

            return of(sourceWidth, sourceHeight, image.imageInfo.rotationDegrees, ::sensor)
        }
    }
}
