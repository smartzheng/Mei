package com.ljyh.mei.ui.app

import com.ljyh.mei.ui.component.layout.AppWindowLayout
import com.ljyh.mei.ui.component.layout.appWindowLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class AppWindowLayoutTest {
    @Test fun phoneKeepsThePhoneShellWhenRotatedIntoAWideLandscapeWindow() {
        // Rotation changes current width, but not the smallest-width qualifier.
        for (smallestWidth in listOf(360, 412, 480, 599)) {
            for (width in listOf(smallestWidth.toFloat(), 800f, 960f, 1200f)) {
                assertEquals(AppWindowLayout.Compact, appWindowLayout(width, smallestWidth))
            }
        }
    }

    @Test fun tabletUsesTheWideShellInBothOrientationsWhenThereIsEnoughRoom() {
        for (width in listOf(600f, 720f, 840f, 1366f)) {
            assertEquals(AppWindowLayout.Wide, appWindowLayout(width, smallestWidthDp = 600))
        }
    }

    @Test fun tabletFallsBackToCompactWhenTheWindowOrSafeAreasAreTooNarrow() {
        for (width in listOf(0f, 320f, 480f, 599f)) {
            assertEquals(AppWindowLayout.Compact, appWindowLayout(width, smallestWidthDp = 720))
        }
        assertEquals(AppWindowLayout.Wide, appWindowLayout(620f, smallestWidthDp = 600))
        assertEquals(AppWindowLayout.Compact, appWindowLayout(620f - 32f - 16f, smallestWidthDp = 600))
    }

    @Test fun foldingOrWindowConfigurationChangesCanReevaluateTheShell() {
        assertEquals(AppWindowLayout.Compact, appWindowLayout(900f, smallestWidthDp = 412))
        assertEquals(AppWindowLayout.Wide, appWindowLayout(900f, smallestWidthDp = 720))
        assertEquals(AppWindowLayout.Compact, appWindowLayout(412f, smallestWidthDp = 412))
    }

    @Test fun undefinedOrInvalidDimensionsDoNotEnableTheTabletShell() {
        for (width in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(AppWindowLayout.Compact, appWindowLayout(width, smallestWidthDp = 600))
        }
        assertEquals(AppWindowLayout.Compact, appWindowLayout(900f, smallestWidthDp = 0))
    }
}
