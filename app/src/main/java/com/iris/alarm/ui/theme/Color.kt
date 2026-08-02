package com.iris.alarm.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * IRIS is a pitch-dark-first palette: near-absolute black, crisp white type,
 * one muted grey for everything secondary, and a single electric accent that is
 * only ever used for *live* state (detection rings, active toggles, lux meter).
 */
object IrisColors {
    val Black = Color(0xFF000000)
    val OffBlack = Color(0xFF09090B)
    val Elevated = Color(0xFF141417)

    val White = Color(0xFFFFFFFF)
    val Muted = Color(0xFF71717A)
    val Divider = Color(0xFF27272A)

    /** Primary accent — live camera detection ring, active toggles, progress. */
    val Amber = Color(0xFFFFB703)

    /** Secondary accent — "challenge satisfied" confirmation. */
    val Neon = Color(0xFF00FF66)

    val Error = Color(0xFFFF4D4D)
}
