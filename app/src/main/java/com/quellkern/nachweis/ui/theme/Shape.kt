package com.quellkern.nachweis.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes

/**
 * Hard edges everywhere (design-tokens.md §4.2): every Material shape slot is 0dp. Rounded
 * corners are not part of the identity. Kept as `RoundedCornerShape(0.dp)` (equivalent to
 * `RectangleShape`) so Material components that expect a `CornerBasedShape` still resolve.
 */
val NachweisShapes = Shapes(
    extraSmall = RoundedCornerShape(0),
    small = RoundedCornerShape(0),
    medium = RoundedCornerShape(0),
    large = RoundedCornerShape(0),
    extraLarge = RoundedCornerShape(0),
)
