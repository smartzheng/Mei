package com.ljyh.mei.ui.tablet.player

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import org.junit.Assert.*
import org.junit.Test

class TabletPlayerExpansionStateTest {
    private val window = Size(1280f, 800f)
    private val surface = Rect(588f, 700f, 928f, 764f)
    private val artwork = Rect(596f, 708f, 644f, 756f)

    @Test fun geometryKeepsTheRealOffCenterMiniAndUsesTheSameHostCoordinates() {
        val state = TabletPlayerExpansionState()
        state.recordOrigin(surface, artwork, window, canRecord = true)
        assertEquals(TabletMiniPlayerOrigin(surface, artwork), state.originInHost(Offset.Zero, window))
        assertEquals(TabletMiniPlayerOrigin(surface.translate(Offset(-10f, -20f)), artwork.translate(Offset(-10f, -20f))),
            state.originInHost(Offset(10f, 20f), window))
    }

    @Test fun navigationExitCannotMoveTheFrozenStartingPoint() {
        val state = TabletPlayerExpansionState()
        state.recordOrigin(surface, artwork, window, canRecord = true)
        state.recordOrigin(surface.translate(Offset(0f, 60f)), artwork.translate(Offset(0f, 60f)), window, canRecord = false)
        assertEquals(TabletMiniPlayerOrigin(surface, artwork), state.originInHost(Offset.Zero, window))
        val next = surface.translate(Offset(-20f, 0f))
        state.recordOrigin(next, artwork.translate(Offset(-20f, 0f)), window, canRecord = true)
        assertEquals(next, state.originInHost(Offset.Zero, window)!!.surface)
    }

    @Test fun resizingOrAnInvalidArtworkMeasurementUsesTheLegacyFallback() {
        val state = TabletPlayerExpansionState()
        assertNull(state.originInHost(Offset.Zero, window))
        state.recordOrigin(surface, artwork, window, canRecord = true)
        assertNull(state.originInHost(Offset.Zero, Size(1000f, 800f)))
        state.recordOrigin(surface, Rect.Zero, window, canRecord = true)
        assertNull(state.originInHost(Offset.Zero, window))
        state.recordOrigin(surface, Rect(0f, 0f, 48f, 48f), window, canRecord = true)
        assertNull(state.originInHost(Offset.Zero, window))
    }

    @Test fun thumbnailCannotBeReusedForAnotherSongCover() {
        val state = TabletPlayerExpansionState()
        val painter = ColorPainter(Color.Red)
        state.recordThumbnail("cover-a", painter)
        assertSame(painter, state.thumbnailFor("cover-a"))
        assertNull(state.thumbnailFor("cover-b"))
        assertNull(state.thumbnailFor(null))
    }

    @Test fun pauseScalingChangesTheTargetOnceAndKeepsTheMiniEndpointAt48Pixels() {
        val target = Rect(100f, 100f, 560f, 560f)
        assertEquals(artwork, tabletSharedArtworkBounds(artwork, target, .9f, 0f))
        val end = tabletSharedArtworkBounds(artwork, target, .9f, 1f)
        assertEquals(414f, end.width, .001f)
        assertEquals(123f, end.left, .001f)
        assertEquals(231f, tabletSharedArtworkBounds(artwork, target, .9f, .5f).width, .001f)
    }

    @Test fun aCompactHeaderCoverDoesNotVisitTheLargeArtworkSizeWhenCollapsing() {
        val target = Rect(100f, 60f, 156f, 116f)
        var previous = 50.4f
        for (step in 100 downTo 0) {
            val bounds = tabletSharedArtworkBounds(artwork, target, .9f, step / 100f)
            assertTrue(bounds.width <= previous + .001f)
            assertTrue(bounds.width >= 48f - .001f)
            previous = bounds.width
        }
    }
}
