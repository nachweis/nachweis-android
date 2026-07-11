package com.quellkern.nachweis.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Motion tokens (design-tokens.md §4.4). Durations are milliseconds. The press-offset spring is
 * defined where it is used (the neo-brutal press); this holds the shared durations.
 */
object Motion {
    const val FAST = 120
    const val STANDARD = 220
    const val EMPHASIS = 320
}

/**
 * Whether the system "remove animations" setting is on. When true, every transition degrades to
 * an instant state change and the press-offset choreography is disabled (design-tokens.md §4.4,
 * a binding foundation requirement, not a C-late nicety). Android exposes this as an animator
 * duration scale of 0.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
internal fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}
