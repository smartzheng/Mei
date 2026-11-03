package com.ljyh.mei.ui.component.layout

import androidx.compose.runtime.staticCompositionLocalOf

/** Application chrome capability after applying the phone/tablet boundary. */
internal enum class AppWindowLayout { Compact, Wide }

/**
 * A phone must keep the phone shell even if the system presents a wide window.
 * smallestWidthDp comes from Configuration.smallestScreenWidthDp, which does not
 * become the landscape width on rotation. Large windows still need 600dp of usable
 * width for the wide dock (584dp including gutters).
 */
internal fun appWindowLayout(availableWidthDp: Float, smallestWidthDp: Int): AppWindowLayout =
    if (smallestWidthDp >= 600 && availableWidthDp.isFinite() && availableWidthDp >= 600f) {
        AppWindowLayout.Wide
    } else {
        AppWindowLayout.Compact
    }

internal val LocalAppWindowLayout = staticCompositionLocalOf<AppWindowLayout?> { null }
