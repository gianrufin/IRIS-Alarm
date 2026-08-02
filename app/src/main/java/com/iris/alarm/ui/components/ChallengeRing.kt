package com.iris.alarm.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/**
 * Traces a progress ring along the rounded outline of whatever it decorates —
 * the camera window and the lux gauge share it, so both challenges read as the
 * same gesture. [fraction] is clamped, so callers can pass raw ratios.
 */
fun Modifier.challengeRing(
    fraction: Float,
    trackColor: Color,
    progressColor: Color,
    cornerRadius: Dp,
    strokeWidth: Dp,
): Modifier = drawWithContent {
    drawContent()

    val stroke = strokeWidth.toPx()
    val inset = stroke / 2f
    val outline = Path().apply {
        addRoundRect(
            RoundRect(
                left = inset,
                top = inset,
                right = size.width - inset,
                bottom = size.height - inset,
                cornerRadius = CornerRadius(cornerRadius.toPx()),
            ),
        )
    }

    drawPath(outline, trackColor, style = Stroke(width = stroke))

    val clamped = fraction.coerceIn(0f, 1f)
    if (clamped <= 0f) return@drawWithContent

    val measure = PathMeasure().apply { setPath(outline, forceClosed = true) }
    val progress = Path()
    // startWithMoveTo keeps the segment from being joined to the previous subpath.
    measure.getSegment(0f, measure.length * clamped, progress, startWithMoveTo = true)
    drawPath(progress, progressColor, style = Stroke(width = stroke, cap = StrokeCap.Round))
}
