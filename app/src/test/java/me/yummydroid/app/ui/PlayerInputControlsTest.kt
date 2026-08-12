package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerInputControlsTest {
    @Test
    fun displayModeVideoRequiresBothPositiveDimensions() {
        assertTrue(VideoDisplayInfo(width = 1920, height = 1080, frameRate = 24f).hasValidDimensions())
        assertFalse(VideoDisplayInfo(width = 0, height = 1080, frameRate = 24f).hasValidDimensions())
        assertFalse(VideoDisplayInfo(width = 1920, height = -1, frameRate = 24f).hasValidDimensions())
    }

    @Test
    fun timelineStepAcceleratesAtStableRepeatThresholds() {
        assertEquals(5_000L, timelineStep(repeatCount = 1))
        assertEquals(5_000L, timelineStep(repeatCount = 3))
        assertEquals(10_000L, timelineStep(repeatCount = 4))
        assertEquals(10_000L, timelineStep(repeatCount = 7))
        assertEquals(30_000L, timelineStep(repeatCount = 8))
        assertEquals(30_000L, timelineStep(repeatCount = 13))
        assertEquals(60_000L, timelineStep(repeatCount = 14))
    }

    @Test
    fun timelineStepNeverExceedsFivePercentOfDuration() {
        val state = TimelineScrubState(
            pendingPositionMs = 0L,
            repeatedInputCount = 14,
        )

        assertEquals(6_000L, state.stepMs(durationMs = 120_000L))
        assertEquals(1_000L, state.stepMs(durationMs = 10_000L))
    }

    private fun timelineStep(repeatCount: Int): Long {
        return TimelineScrubState(
            pendingPositionMs = 0L,
            repeatedInputCount = repeatCount,
        ).stepMs(durationMs = 3_600_000L)
    }
}
