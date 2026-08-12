package me.yummydroid.app.ui

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import me.yummydroid.app.data.PosterCardSize

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

    @Test
    fun catalogColumnsFollowEveryResponsiveWidthBoundary() {
        val cases = listOf(
            1_200 to listOf(7, 5, 3),
            1_199 to listOf(6, 4, 2),
            900 to listOf(6, 4, 2),
            899 to listOf(5, 3, 2),
            600 to listOf(5, 3, 2),
            599 to listOf(4, 2, 1),
            430 to listOf(4, 2, 1),
            429 to listOf(3, 2, 1),
        )

        cases.forEach { (width, expectedColumns) ->
            PosterCardSize.entries.forEachIndexed { index, cardSize ->
                assertEquals(
                    expected = expectedColumns[index],
                    actual = cardSize.resolveCatalogColumns(width),
                    message = "$cardSize at ${width}dp",
                )
            }
        }
    }
}
