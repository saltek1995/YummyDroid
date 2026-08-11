package me.yummydroid.app.ui.theme

import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals

class YummyBackgroundTest {
    @Test
    fun `keeps textures within the original output size`() {
        assertEquals(
            YummyBackgroundTextureSpec(IntSize(640, 360), 1f),
            yummyBackgroundTextureSpec(widthPx = 640, heightPx = 360),
        )
    }

    @Test
    fun `limits sixteen by nine textures to the cache bounds`() {
        assertEquals(
            YummyBackgroundTextureSpec(IntSize(960, 540), 0.5f),
            yummyBackgroundTextureSpec(widthPx = 1920, heightPx = 1080),
        )
    }

    @Test
    fun `preserves aspect ratio when width is the limiting dimension`() {
        assertEquals(
            YummyBackgroundTextureSpec(IntSize(960, 240), 0.24f),
            yummyBackgroundTextureSpec(widthPx = 4000, heightPx = 1000),
        )
    }

    @Test
    fun `normalizes invalid output dimensions`() {
        assertEquals(
            YummyBackgroundTextureSpec(IntSize(1, 1), 1f),
            yummyBackgroundTextureSpec(widthPx = 0, heightPx = -1),
        )
    }
}
