package com.iris.alarm.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * IRIS is a pitch-dark-first palette: near-absolute black, crisp white type, one
 * muted grey for everything secondary, and a single electric accent used only
 * for *live* state (detection rings, active toggles, lux meter).
 *
 * The light values are not inversions. Amber and neon green are both far too
 * pale to carry text on white, so light mode gets darkened "ink" variants for
 * anything typographic and keeps the bright originals only as fills with dark
 * text on top.
 */
object IrisColors {
    // Dark
    val Black = Color(0xFF000000)
    val OffBlack = Color(0xFF09090B)
    val Elevated = Color(0xFF141417)
    val White = Color(0xFFFFFFFF)
    val Muted = Color(0xFF71717A)
    val Divider = Color(0xFF27272A)

    /** Primary accent — live detection ring, active toggles, progress. */
    val Amber = Color(0xFFFFB703)

    /** Secondary accent — "challenge satisfied" confirmation. */
    val Neon = Color(0xFF00FF66)

    val Error = Color(0xFFFF4D4D)

    // Light
    val Paper = Color(0xFFFAFAFA)
    val PaperVariant = Color(0xFFF1F1F3)
    val PaperElevated = Color(0xFFE8E8EC)
    val Ink = Color(0xFF09090B)

    /** 4.6:1 on Paper — passes AA for body text, unlike the dark-mode grey. */
    val InkMuted = Color(0xFF52525B)
    val InkDivider = Color(0xFFD4D4D8)

    /** Amber dark enough to read as text on white (4.7:1). */
    val AmberInk = Color(0xFF8A5B00)
    val AmberWash = Color(0xFFFFF1D0)

    /** Green dark enough to read as text on white; the neon is invisible there. */
    val NeonInk = Color(0xFF007A3D)
    val NeonWash = Color(0xFFD6F7E4)

    val ErrorInk = Color(0xFFB3261E)
}
