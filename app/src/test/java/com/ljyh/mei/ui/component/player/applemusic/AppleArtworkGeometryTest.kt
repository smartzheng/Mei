package com.ljyh.mei.ui.component.player.applemusic

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test

class AppleArtworkGeometryTest {
    private val mini = Rect(36f, 700f, 68f, 732f)
    private val normal = Rect(40f, 160f, 360f, 480f)
    private val header = Rect(24f, 64f, 76f, 116f)

    @Test fun collapsingFromHeaderShrinksDirectlyToMiniWithoutPassingThroughALargeCover() {
        var previousSize = header.width
        for (step in 100 downTo 0) {
            val p = step / 100f
            val bounds = resolveAppleArtworkBounds(mini, normal, header, p, 1f)
            assertTrue("Header cover grew while collapsing at $p: ${bounds.width}", bounds.width <= previousSize + .001f)
            assertTrue(bounds.width >= mini.width - .001f)
            previousSize = bounds.width
        }
        assertEquals(Rect(30f, 382f, 72f, 424f), resolveAppleArtworkBounds(mini, normal, header, .5f, 1f))
        assertEquals(mini, resolveAppleArtworkBounds(mini, normal, header, 0f, 1f))
        assertEquals(header, resolveAppleArtworkBounds(mini, normal, header, 1f, 1f))
    }

    @Test fun expandingInHeaderModeNeverOvershootsTheHeaderSize() {
        var previousSize = mini.width
        for (step in 0..100) {
            val p = step / 100f
            val bounds = resolveAppleArtworkBounds(mini, normal, header, p, 1f)
            assertTrue(bounds.width >= previousSize - .001f)
            assertTrue(bounds.width <= header.width + .001f)
            previousSize = bounds.width
        }
    }

    @Test fun switchingHeaderModeWhileFullyExpandedRemainsASingleShrinkOrGrow() {
        var previousSize = normal.width
        for (step in 0..100) {
            val q = step / 100f
            val bounds = resolveAppleArtworkBounds(mini, normal, header, 1f, q)
            assertTrue(bounds.width <= previousSize + .001f)
            assertTrue(bounds.width >= header.width - .001f)
            previousSize = bounds.width
        }
        assertEquals(normal, resolveAppleArtworkBounds(mini, normal, header, 1f, 0f))
        assertEquals(header, resolveAppleArtworkBounds(mini, normal, header, 1f, 1f))
    }

    @Test fun ordinaryCoverModePreservesTheExistingPlaybackScaleAndSheetPath() {
        val existingNormalPath = Rect(55.5f, 321.25f, 237.25f, 503f)
        for (p in listOf(0f, .2f, .5f, .8f, 1f)) {
            assertEquals(existingNormalPath, resolveAppleArtworkBounds(mini, existingNormalPath, header, p, 0f))
        }
    }

    @Test fun resettingHeaderModeAtTheCollapsedEndpointCannotMoveTheCover() {
        for (q in listOf(0f, .2f, .5f, .8f, 1f)) {
            val bounds = resolveAppleArtworkBounds(mini, mini, header, 0f, q)
            assertEquals(mini.left, bounds.left, .001f)
            assertEquals(mini.top, bounds.top, .001f)
            assertEquals(mini.width, bounds.width, .001f)
            assertEquals(mini.height, bounds.height, .001f)
        }
    }
}
