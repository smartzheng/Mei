package com.ljyh.mei.ui.component.sheet

import kotlin.math.roundToInt
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

/** Pixel bounds in the sheet host; changing the outline never changes content constraints. */
internal data class PlayerContainerFrame(val bounds: Rect, val radius: Float)

internal fun playerContainerFrame(
    source: Rect?, targetSize: Size, progress: Float, sourceRadius: Float,
): PlayerContainerFrame? {
    if (source == null || !targetSize.width.isFinite() || !targetSize.height.isFinite() ||
        targetSize.width <= 0f || targetSize.height <= 0f ||
        listOf(source.left, source.top, source.right, source.bottom).any { !it.isFinite() } ||
        source.width <= 0f || source.height <= 0f || source.left < -.5f || source.top < -.5f ||
        source.right > targetSize.width + .5f || source.bottom > targetSize.height + .5f) return null
    val p = progress.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
    fun mix(a: Float, b: Float) = a + (b - a) * p
    val bounds = Rect(mix(source.left, 0f), mix(source.top, 0f),
        mix(source.right, targetSize.width), mix(source.bottom, targetSize.height))
    val radius = sourceRadius.takeIf(Float::isFinite)?.coerceAtLeast(0f) ?: 0f
    return PlayerContainerFrame(bounds, minOf(radius * (1f - p), bounds.width / 2, bounds.height / 2))
}

/** Fix the inner viewport without scaling glyphs or exposing it to intermediate constraints. */
internal fun Modifier.playerContainerViewport(frame: PlayerContainerFrame?, hostSize: IntSize): Modifier =
    if (frame == null) this else layout { measurable, constraints ->
        val child = measurable.measure(Constraints.fixed(hostSize.width, hostSize.height))
        layout(constraints.constrainWidth(child.width), constraints.constrainHeight(child.height)) {
            child.place(-frame.bounds.left.roundToInt(), -frame.bounds.top.roundToInt())
        }
    }
