package com.quellkern.nachweis.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.quellkern.nachweis.R

/**
 * Typography (design-tokens.md §3). Three bundled OFL faces, no runtime font fetch (a wallet must
 * not phone home for fonts): Bricolage Grotesque (display/title), Familjen Grotesk (body/label),
 * Space Mono (machine values — claim paths, hashes, DCQL). Bricolage and Familjen are variable
 * fonts; the `wght` axis is driven by the default `FontVariation.Settings(weight, style)` that
 * `Font(resId, weight)` applies, so one TTF serves every weight. Every size is `sp` so system
 * font scaling applies (§3), with explicit line heights so large scales stay legible.
 */

/** One variable TTF serves every weight; the `wght` axis is set explicitly per entry. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun variableFont(resId: Int, weight: FontWeight) = Font(
    resId = resId,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Bricolage = FontFamily(
    variableFont(R.font.bricolage_grotesque, FontWeight.SemiBold),
    variableFont(R.font.bricolage_grotesque, FontWeight.Bold),
)

val Familjen = FontFamily(
    variableFont(R.font.familjen_grotesk, FontWeight.Normal),
    variableFont(R.font.familjen_grotesk, FontWeight.Medium),
    variableFont(R.font.familjen_grotesk, FontWeight.SemiBold),
    variableFont(R.font.familjen_grotesk, FontWeight.Bold),
)

val SpaceMono = FontFamily(
    Font(R.font.space_mono_regular, FontWeight.Normal),
    Font(R.font.space_mono_bold, FontWeight.Bold),
)

/**
 * Mono style for machine values (claim paths, hashes, DCQL). Not a Material `Typography` slot, so
 * it is exposed here and read directly where a value must read as machine text, not prose (§3).
 */
val MonoTextStyle = TextStyle(
    fontFamily = SpaceMono,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 22.sp,
)

val NachweisTypography = Typography(
    // display → Bricolage 32/38 700 (design-tokens.md §3)
    displaySmall = TextStyle(
        fontFamily = Bricolage,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Bricolage,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Bricolage,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
    ),
    // title → Bricolage 22/28 600
    titleLarge = TextStyle(
        fontFamily = Bricolage,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Bricolage,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Familjen,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    // body → Familjen 16/24 400
    bodyLarge = TextStyle(
        fontFamily = Familjen,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Familjen,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Familjen,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    // label → Familjen 14/20 600
    labelLarge = TextStyle(
        fontFamily = Familjen,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Familjen,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)
