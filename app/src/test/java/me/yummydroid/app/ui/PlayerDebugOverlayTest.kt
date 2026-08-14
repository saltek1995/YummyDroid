package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerDebugOverlayTest {
    @Test
    fun fifthPlayPauseHitWithinWindowTogglesOverlay() {
        var result = playerDebugToggleResult(emptyList(), nowMs = 1_000)
        result = playerDebugToggleResult(result.hits, nowMs = 2_000)
        result = playerDebugToggleResult(result.hits, nowMs = 3_000)
        result = playerDebugToggleResult(result.hits, nowMs = 4_000)

        assertFalse(result.shouldToggle)
        assertEquals(4, result.hits.size)

        result = playerDebugToggleResult(result.hits, nowMs = 5_000)

        assertTrue(result.shouldToggle)
        assertEquals(emptyList(), result.hits)
    }

    @Test
    fun oldPlayPauseHitsOutsideWindowAreIgnored() {
        val result = playerDebugToggleResult(
            previousHits = listOf(0, 1_000, 2_000, 3_000),
            nowMs = 6_001,
        )

        assertFalse(result.shouldToggle)
        assertEquals(listOf(2_000L, 3_000L, 6_001L), result.hits)
    }
}
