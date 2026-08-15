package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdaptivePlayerEpisodeControlsTest {
    @Test
    fun compactViewportUsesMinimumTouchTarget() {
        val dimensions = resolvePlayerEpisodeControlDimensions(
            viewportWidth = 1_920,
            viewportHeight = 1_080,
            density = 3f,
        )

        assertEquals(144, dimensions.playPauseSize)
        assertTrue(dimensions.episodeButtonWidth < dimensions.playPauseSize)
    }

    @Test
    fun largeViewportCapsControlSize() {
        val dimensions = resolvePlayerEpisodeControlDimensions(
            viewportWidth = 1_280,
            viewportHeight = 720,
            density = 1f,
        )

        assertEquals(64, dimensions.playPauseSize)
        assertTrue(dimensions.controlsHeight > dimensions.playPauseSize)
    }

    @Test
    fun intermediateViewportScalesProportionally() {
        val dimensions = resolvePlayerEpisodeControlDimensions(
            viewportWidth = 1_000,
            viewportHeight = 600,
            density = 1f,
        )

        assertEquals(60, dimensions.playPauseSize)
    }
}
