package me.yummydroid.app.ui

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WindowSizeTest {
    @Test
    fun responsiveSizeUsesUnscaledDeviceDensity() {
        val responsiveSize = responsiveWindowSizeDp(
            widthPixels = 1080,
            heightPixels = 2400,
            densityDpi = 420,
        )
        val scaledSize = responsiveWindowSizeDp(
            widthPixels = 1080,
            heightPixels = 2400,
            densityDpi = 210,
        )

        assertEquals(411.42856f, responsiveSize.width.value, absoluteTolerance = 0.0001f)
        assertEquals(914.2857f, responsiveSize.height.value, absoluteTolerance = 0.0001f)
        assertTrue(responsiveSize.width < 720.dp)
        assertTrue(scaledSize.width >= 720.dp)
    }

    @Test
    fun invalidWindowReturnsZeroSize() {
        assertEquals(DpSize.Zero, responsiveWindowSizeDp(0, 2400, 420))
    }
}
