package com.quellkern.nachweis.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Roles Material 3 has no slot for (design-tokens.md §1.3): success/warning containers and the
 * selection highlight, plus the two neo-brutal structural constants (border weight, hard-shadow
 * offset). Provided as a [staticCompositionLocalOf] alongside `MaterialTheme` rather than by
 * abusing `tertiary`. `on*` colors are always `ink` (light) or the dark on-color, per the
 * contrast law: the hue is the fill, ink is the text on top.
 */
@Immutable
data class NachweisColors(
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val selection: Color,
    val onSelection: Color,
    /** 4dp ink hairline that separates surfaces in place of Material elevation (§4.3). */
    val borderWeight: Dp = 4.dp,
    /** The neo-brutal hard-shadow offset (design-system.md `--off`), scaled down for mobile. */
    val shadowOffset: Dp = 6.dp,
    val borderColor: Color,
)

val LightNachweisColors = NachweisColors(
    successContainer = Teal,
    onSuccessContainer = Ink,
    warningContainer = Amber,
    onWarningContainer = Ink,
    selection = Yellow,
    onSelection = Ink,
    borderColor = Ink,
)

val DarkNachweisColors = NachweisColors(
    successContainer = TealDeep,
    onSuccessContainer = Paper,
    warningContainer = Amber,
    onWarningContainer = Ink,
    selection = Yellow,
    onSelection = Ink,
    borderColor = Paper,
)

val LocalNachweisColors = staticCompositionLocalOf { LightNachweisColors }
