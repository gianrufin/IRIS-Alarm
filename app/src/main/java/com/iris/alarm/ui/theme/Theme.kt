package com.iris.alarm.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

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
 * IRIS is dark-first by design; the light scheme exists only so the app does not
 * look broken if the system forces it, and it keeps the same accent language.
 */
private val IrisLightScheme = lightColorScheme(
    primary = IrisColors.Amber,
    onPrimary = IrisColors.Black,
    secondary = IrisColors.Neon,
    onSecondary = IrisColors.Black,
    background = IrisColors.White,
    onBackground = IrisColors.Black,
    surface = IrisColors.White,
    onSurface = IrisColors.Black,
    onSurfaceVariant = IrisColors.Muted,
    outline = IrisColors.Divider,
)

@Composable
fun IrisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /**
     * Material You wallpaper extraction. Off by default: the identity of IRIS is
     * pitch black + one accent, and dynamic schemes lift the background off black.
     */
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && darkTheme && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            // Keep the pitch-black canvas; borrow only the accents from the wallpaper.
            dynamicDarkColorScheme(LocalContext.current).copy(
                background = IrisColors.Black,
                onBackground = IrisColors.White,
                surface = IrisColors.Black,
                onSurface = IrisColors.White,
            )
        }

        darkTheme -> IrisDarkScheme
        else -> IrisLightScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // Edge-to-edge with transparent bars is enabled per-Activity; here we only
            // keep the status/navigation icon contrast in sync with the scheme.
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
