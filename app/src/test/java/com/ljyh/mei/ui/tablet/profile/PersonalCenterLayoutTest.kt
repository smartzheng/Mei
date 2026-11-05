package com.ljyh.mei.ui.tablet.profile

import org.junit.Assert.*
import org.junit.Test

class PersonalCenterLayoutTest {
    @Test fun ordinaryPortraitTabletsUseTwoPanes() {
        for (width in listOf(600f, 768f, 800f, 1024f)) {
            val side = requireNotNull(personalCenterSideWidth(width, 1f))
            assertTrue(side >= 224f)
            assertTrue(width - side - 24f >= 320f)
        }
    }

    @Test fun narrowOrLargeTextWindowsFallBackToOnePane() {
        assertNull(personalCenterSideWidth(599f, 1f))
        assertNull(personalCenterSideWidth(800f, 2f))
        assertNull(personalCenterSideWidth(Float.NaN, 1f))
    }

    @Test fun wideWindowKeepsProfileColumnReadable() {
        assertEquals(360f, personalCenterSideWidth(1600f, 1f)!!, 0.01f)
        val side = requireNotNull(personalCenterSideWidth(1400f, 2f))
        assertTrue(side >= 448f)
        assertTrue(1400f - side - 24f >= 640f)
    }
}
