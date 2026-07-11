package com.quellkern.nachweis.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Light color scheme (design-tokens.md §1.3). Surfaces are all `paper` — separation comes from a
 * 4dp ink border, never a tonal tint — so the `surfaceContainer*` slots stay `paper`. `error` is
 * the text-safe deep red; `errorContainer` is the bright red used behind ink text.
 */
private val LightColors = lightColorScheme(
    primary = Blue,
    onPrimary = Paper,
    primaryContainer = Blue,
    onPrimaryContainer = Paper,
    secondary = Ink,
    onSecondary = Paper,
    secondaryContainer = Paper,
    onSecondaryContainer = Ink,
    tertiary = Ink,
    onTertiary = Paper,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Paper,
    onSurfaceVariant = Ink,
    surfaceContainer = Paper,
    surfaceContainerLow = Paper,
    surfaceContainerLowest = Paper,
    surfaceContainerHigh = Paper,
    surfaceContainerHighest = Paper,
    outline = Ink,
    outlineVariant = Ink,
    error = OverDeep,
    onError = Paper,
    errorContainer = Over,
    onErrorContainer = Ink,
    scrim = Ink,
)

/** Dark scheme (§1.4): inverted paper/ink, lightened chromatics that clear AA on the dark surface. */
private val DarkColors = darkColorScheme(
    primary = BlueLight,
    onPrimary = DarkBg,
    primaryContainer = BlueLight,
    onPrimaryContainer = DarkBg,
    secondary = Paper,
    onSecondary = DarkBg,
    secondaryContainer = DarkSurface,
    onSecondaryContainer = Paper,
    tertiary = Paper,
    onTertiary = DarkBg,
    background = DarkBg,
    onBackground = Paper,
    surface = DarkSurface,
    onSurface = Paper,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = Paper,
    surfaceContainer = DarkSurface,
    surfaceContainerLow = DarkSurface,
    surfaceContainerLowest = DarkBg,
    surfaceContainerHigh = DarkSurface,
    surfaceContainerHighest = DarkSurface,
    outline = Paper,
    outlineVariant = Paper,
    error = OverLight,
    onError = DarkBg,
    errorContainer = OverDeep,
    onErrorContainer = Paper,
    scrim = Ink,
)

/**
 * The nachweis theme. Wraps Material 3, overriding color/typography/shape with the neo-brutal
 * tokens and providing [NachweisColors] and [LocalReducedMotion] as composition locals. There is
 * deliberately **no dynamic color**: the brand identity must not be repainted by Material You —
 * carrying the Augenmass look is the point (design-system.md).
 */
@Composable
fun NachweisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val nachweisColors = if (darkTheme) DarkNachweisColors else LightNachweisColors
    val reducedMotion = rememberReducedMotion()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // Status-bar icons are dark when the background is light, and vice versa.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                colorScheme.background.luminance() > 0.5f
        }
    }

    CompositionLocalProvider(
        LocalNachweisColors provides nachweisColors,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = NachweisTypography,
            shapes = NachweisShapes,
            content = content,
        )
    }
}

/** Convenience accessor for the [NachweisColors] extension, mirroring `MaterialTheme.colorScheme`. */
val nachweisColors: NachweisColors
    @Composable get() = LocalNachweisColors.current
