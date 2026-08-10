package me.yummydroid.app.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FocusBorderGeometryTest {
    @Test
    fun geometryUsesWidestStrokeAsInsetAndBoundsCornerRadius() {
        val geometry = focusBorderGeometry(
            widthPx = 100f,
            heightPx = 40f,
            strokeWidthPx = 4f,
            highlightStrokeWidthPx = 6f,
            cornerRadiusPx = 50f,
        )

        assertEquals(3f, geometry.inset)
        assertEquals(97f, geometry.right)
        assertEquals(37f, geometry.bottom)
        assertEquals(17f, geometry.radius)
        assertEquals(FocusBorderFrameKey(100, 40, 4, 6, 17), geometry.frameKey)
        assertTrue(geometry.hasArea)
    }

    @Test
    fun nonPositiveCanvasDimensionsProduceStableKeyWithoutArea() {
        val geometry = focusBorderGeometry(0f, -10f, 2f, 3f, 8f)

        assertEquals(FocusBorderFrameKey(1, 1, 2, 3, 0), geometry.frameKey)
        assertFalse(geometry.hasArea)
    }

    @Test
    fun layerAlphaIsClampedAndRounded() {
        assertEquals(0, focusBorderLayerAlpha(-1f))
        assertEquals(128, focusBorderLayerAlpha(0.5f))
        assertEquals(255, focusBorderLayerAlpha(2f))
    }
}
