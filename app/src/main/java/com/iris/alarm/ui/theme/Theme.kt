package com.iris.alarm.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.iris.alarm.domain.model.ThemeMode

/**
 * Structural rounding. IRIS uses one radius everywhere something is a container
 * (camera window, alarm row, sheet) and a pill for anything tappable.
 */
val IrisShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(20.dp),
    medium = RoundedCornerShape(28.dp),
    large = RoundedCornerShape(32.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val IrisDarkScheme = darkColorScheme(
    primary = IrisColors.Amber,
    onPrimary = IrisColors.Black,
    primaryContainer = IrisColors.Elevated,
    onPrimaryContainer = IrisColors.Amber,
    secondary = IrisColors.Neon,
    onSecondary = IrisColors.Black,
    secondaryContainer = IrisColors.Elevated,
    onSecondaryContainer = IrisColors.Neon,
    tertiary = IrisColors.White,
    onTertiary = IrisColors.Black,
    background = IrisColors.Black,
    onBackground = IrisColors.White,
    surface = IrisColors.Black,
    onSurface = IrisColors.White,
    surfaceVariant = IrisColors.OffBlack,
    onSurfaceVariant = IrisColors.Muted,
    surfaceContainer = IrisColors.OffBlack,
    surfaceContainerHigh = IrisColors.Elevated,
    outline = IrisColors.Divider,
    outlineVariant = IrisColors.Divider,
    error = IrisColors.Error,
    onError = IrisColors.Black,
)

/**
 * The light scheme is a real design, not a fallback.
 *
 * Two colours could not simply be inverted. Amber on white fails contrast for
 * text, so in light mode it is only ever a *fill* with black on top, and the
 * darker [IrisColors.AmberInk] carries any amber-coloured text. The neon green
 * is invisible on white, so light mode uses [IrisColors.NeonInk] instead.
 */
private val IrisLightScheme = lightColorScheme(
    primary = IrisColors.Amber,
    onPrimary = IrisColors.Black,
    primaryContainer = IrisColors.AmberWash,
    onPrimaryContainer = IrisColors.AmberInk,
    secondary = IrisColors.NeonInk,
    onSecondary = IrisColors.White,
    secondaryContainer = IrisColors.NeonWash,
    onSecondaryContainer = IrisColors.NeonInk,
    tertiary = IrisColors.Ink,
    onTertiary = IrisColors.White,
    background = IrisColors.Paper,
    onBackground = IrisColors.Ink,
    surface = IrisColors.White,
    onSurface = IrisColors.Ink,
    surfaceVariant = IrisColors.PaperVariant,
    onSurfaceVariant = IrisColors.InkMuted,
    surfaceContainer = IrisColors.PaperVariant,
    surfaceContainerHigh = IrisColors.PaperElevated,
    outline = IrisColors.InkDivider,
    outlineVariant = IrisColors.InkDivider,
    error = IrisColors.ErrorInk,
    onError = IrisColors.White,
)

@Composable
fun IrisTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Forces dark regardless of the setting, for the ringing screen at night. */
    forceDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when {
        forceDark -> true
        themeMode == ThemeMode.DARK -> true
        themeMode == ThemeMode.LIGHT -> false
        else -> isSystemInDarkTheme()
    }

    IrisTheme(darkTheme = darkTheme, content = content)
}

@Composable
fun IrisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) IrisDarkScheme else IrisLightScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // Edge-to-edge with transparent bars is enabled per-Activity; here we
            // only keep the status/navigation icon contrast in sync with the scheme.
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = IrisTypography,
        shapes = IrisShapes,
        content = content,
    )
}
