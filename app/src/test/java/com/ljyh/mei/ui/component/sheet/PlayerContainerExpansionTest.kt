package com.ljyh.mei.ui.component.sheet

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.*
import org.junit.Test

class PlayerContainerExpansionTest {
    private val source = Rect(20f, 700f, 380f, 748f)
    private val target = Size(400f, 800f)

    @Test fun endpointsExactlyMatchTheMiniAndFullHost() {
        assertEquals(PlayerContainerFrame(source, 24f), playerContainerFrame(source, target, 0f, 24f))
        assertEquals(PlayerContainerFrame(Rect(0f, 0f, 400f, 800f), 0f), playerContainerFrame(source, target, 1f, 24f))
    }
    @Test fun anOffCenterTabletSourceKeepsItsActualHorizontalOrigin() {
        val source = Rect(588f, 700f, 928f, 764f)
        val target = Size(1280f, 800f)
        assertEquals(588f, playerContainerFrame(source, target, 0f, 24f)!!.bounds.left)
        val middle = playerContainerFrame(source, target, .5f, 24f)!!
        assertEquals(Rect(294f, 350f, 1104f, 782f), middle.bounds)
        assertEquals(12f, middle.radius)
    }
    @Test fun reversingUsesTheSameContinuousGeometryWithoutAnEndpointRestart() {
        val opening = playerContainerFrame(source, target, .37f, 24f)
        val closing = listOf(.9f, .7f, .37f).map { playerContainerFrame(source, target, it, 24f) }.last()
        assertEquals(opening, closing)
        assertEquals(Rect(10f, 350f, 390f, 774f), playerContainerFrame(source, target, .5f, 24f)!!.bounds)
    }
    @Test fun missingStaleAndNonFiniteMeasurementsSelectTheLegacyFallback() {
        assertNull(playerContainerFrame(null, target, .5f, 24f))
        assertNull(playerContainerFrame(Rect.Zero, target, .5f, 24f))
        assertNull(playerContainerFrame(source, Size(300f, 600f), .5f, 24f))
        assertNull(playerContainerFrame(Rect(Float.NaN, 0f, 40f, 40f), target, .5f, 24f))
        assertNull(playerContainerFrame(source, Size(Float.POSITIVE_INFINITY, 800f), .5f, 24f))
    }
    @Test fun springOvershootCannotGrowTheOutlineBeyondTheHostOrCreateNegativeRadii() {
        assertEquals(playerContainerFrame(source, target, 0f, 24f), playerContainerFrame(source, target, -1f, 24f))
        assertEquals(playerContainerFrame(source, target, 1f, 24f), playerContainerFrame(source, target, 1.1f, 24f))
        assertEquals(24f, playerContainerFrame(source, target, 0f, 400f)!!.radius)
    }
}
