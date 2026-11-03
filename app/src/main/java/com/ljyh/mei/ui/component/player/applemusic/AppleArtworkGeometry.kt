package com.ljyh.mei.ui.component.player.applemusic

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp

/**
 * Blend complete mini-to-player paths. The normal path already includes sheet progress and
 * playback scale; the header path must connect mini directly to header, not through that path.
 */
internal fun resolveAppleArtworkBounds(
    miniBounds: Rect,
    normalPathBounds: Rect,
    headerBounds: Rect,
    sheetProgress: Float,
    headerProgress: Float,
): Rect {
    val sheet = sheetProgress.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
    val header = headerProgress.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
    val headerPathBounds = lerp(miniBounds, headerBounds, sheet)
    return lerp(normalPathBounds, headerPathBounds, header)
}
