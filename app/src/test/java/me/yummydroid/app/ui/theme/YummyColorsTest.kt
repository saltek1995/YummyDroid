package me.yummydroid.app.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class YummyColorsTest {
    @Test
    fun semanticColorsKeepTheirArgbContracts() {
        assertEquals(Color(0xFFFFB454), YummyColors.focus)
        assertEquals(Color(0xFF211200), YummyColors.onFocus)
        assertEquals(YummyColors.focus, YummyColors.focusOverlay)
        assertEquals(YummyColors.focus, YummyColors.rating)
        assertEquals(Color(0xFFB8FF2D), YummyColors.offline)
        assertEquals(Color(0xFF3DFF9D), YummyColors.watched)
        assertEquals(Color(0xFF142238), YummyColors.actionSurface)
        assertEquals(Color(0xFF1A304B), YummyColors.actionSurfaceSelected)
        assertEquals(Color(0xFF10192A), YummyColors.actionSurfaceDisabled)
        assertEquals(Color(0xFF42658A), YummyColors.actionBorder)
    }
}
