package com.iris.alarm.vision

import androidx.camera.core.ImageProxy

/**
 * A compact fingerprint of what the camera is looking at, small enough to store
 * in the alarm row and cheap enough to recompute on every frame.
 *
 * Two parts, because neither alone is enough:
 *
 * - **dHash** compares each pixel with its right-hand neighbour, so it encodes
 *   *structure* (where the edges are) and not brightness. A room at 6am is much
 *   dimmer than when the anchor was captured the night before, and a raw pixel
 *   comparison would fail on that alone.
 * - **A luminance histogram** catches the case where structure coincidentally
 *   lines up — a different wall with a similar edge layout — which dHash on its
 *   own is prone to.
 */
data class SceneSignature(
    val hash: Long,
    /** 16 normalised luminance buckets, summing to 1. */
    val histogram: FloatArray,
) {
    /**
     * 0f..1f, where 1 is identical. Structure is weighted heavier than exposure
     * because the user will be standing somewhere slightly different, at a
     * different time of day, from where they captured the anchor.
     */
    fun similarityTo(other: SceneSignature): Float {
        val matchingBits = 64 - java.lang.Long.bitCount(hash xor other.hash)
        val structure = matchingBits / 64f

        var intersection = 0f
        for (i in histogram.indices) {
            intersection += minOf(histogram[i], other.histogram[i])
        }

        return STRUCTURE_WEIGHT * structure + (1f - STRUCTURE_WEIGHT) * intersection
    }

    /** Serialised for the database: the hash, then the buckets, comma separated. */
    fun serialise(): String =
        (listOf(hash.toString()) + histogram.map { it.toString() }).joinToString(",")

    // Data class equals/hashCode would compare the FloatArray by identity.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SceneSignature) return false
        return hash == other.hash && histogram.contentEquals(other.histogram)
    }

    override fun hashCode(): Int = 31 * hash.hashCode() + histogram.contentHashCode()

    companion object {
        const val STRUCTURE_WEIGHT = 0.72f
        const val HISTOGRAM_BUCKETS = 16

        /** Width is one wider than the hash grid so each row yields 8 comparisons. */
        private const val SAMPLE_WIDTH = 9
        private const val SAMPLE_HEIGHT = 8

        fun deserialise(value: String?): SceneSignature? {
            if (value.isNullOrBlank()) return null
            val parts = value.split(",")
            if (parts.size != HISTOGRAM_BUCKETS + 1) return null
            val hash = parts[0].toLongOrNull() ?: return null
            val histogram = FloatArray(HISTOGRAM_BUCKETS) { i ->
                parts[i + 1].toFloatOrNull() ?: return null
            }
            return SceneSignature(hash, histogram)
        }

        /**
         * Reads the luminance plane of a camera frame directly. YUV_420_888's Y
         * plane *is* the greyscale image, so there is no colour conversion and no
         * Bitmap allocation on the analysis thread.
         */
        fun from(image: ImageProxy): SceneSignature? {
            val plane = image.planes.firstOrNull() ?: return null
            val buffer = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val width = image.width
            val height = image.height
            if (width <= 0 || height <= 0) return null

            fun luminanceAt(x: Int, y: Int): Int {
                val index = y * rowStride + x * pixelStride
                if (index < 0 || index >= buffer.limit()) return 0
                return buffer.get(index).toInt() and 0xFF
            }

            // Nearest-neighbour downscale to the hash grid.
            val samples = IntArray(SAMPLE_WIDTH * SAMPLE_HEIGHT)
            for (row in 0 until SAMPLE_HEIGHT) {
                val sourceY = (row * height / SAMPLE_HEIGHT).coerceIn(0, height - 1)
                for (column in 0 until SAMPLE_WIDTH) {
                    val sourceX = (column * width / SAMPLE_WIDTH).coerceIn(0, width - 1)
                    samples[row * SAMPLE_WIDTH + column] = luminanceAt(sourceX, sourceY)
                }
            }

            var hash = 0L
            var bit = 0
            for (row in 0 until SAMPLE_HEIGHT) {
                for (column in 0 until SAMPLE_WIDTH - 1) {
                    val left = samples[row * SAMPLE_WIDTH + column]
                    val right = samples[row * SAMPLE_WIDTH + column + 1]
                    if (left > right) hash = hash or (1L shl bit)
                    bit++
                }
            }

            // The histogram is sampled on a coarser grid than the frame so a 4K
            // preview costs the same as a 720p one.
            val histogram = FloatArray(HISTOGRAM_BUCKETS)
            var counted = 0
            val stepX = (width / HISTOGRAM_SAMPLE_SIDE).coerceAtLeast(1)
            val stepY = (height / HISTOGRAM_SAMPLE_SIDE).coerceAtLeast(1)
            var y = 0
            while (y < height) {
                var x = 0
                while (x < width) {
                    val bucket = luminanceAt(x, y) * HISTOGRAM_BUCKETS / 256
                    histogram[bucket.coerceIn(0, HISTOGRAM_BUCKETS - 1)]++
                    counted++
                    x += stepX
                }
                y += stepY
            }
            if (counted == 0) return null
            for (i in histogram.indices) histogram[i] /= counted

            return SceneSignature(hash, histogram)
        }

        private const val HISTOGRAM_SAMPLE_SIDE = 48
    }
}
