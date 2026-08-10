package me.yummydroid.app.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YummyAlphaTest {
    @Test
    fun surfaceAlphaTokensRemainValidAndDistinct() {
        val values = listOf(
            YummyAlpha.subtleSurface,
            YummyAlpha.rowSurface,
            YummyAlpha.disabledSurface,
            YummyAlpha.badgeSurface,
        )

        assertEquals(listOf(0.42f, 0.92f, 0.58f, 0.82f), values)
        assertTrue(values.all { alpha -> alpha in 0f..1f })
    }
}
