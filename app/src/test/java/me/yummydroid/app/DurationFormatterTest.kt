package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DurationFormatterTest {
    @Test
    fun optionalDurationRejectsMissingAndNonPositiveValues() {
        assertNull(formatDuration(null))
        assertNull(formatDuration(0))
        assertNull(formatDuration(-1))
    }

    @Test
    fun optionalDurationKeepsMinutesBeyondOneHour() {
        assertEquals("1:05", formatDuration(65))
        assertEquals("61:05", formatDuration(3_665))
    }

    @Test
    fun playbackTimeSwitchesToHoursAndClampsNegativeValues() {
        assertEquals("00:00", formatPlaybackTime(-1_000))
        assertEquals("01:05", formatPlaybackTime(65_000))
        assertEquals("1:01:05", formatPlaybackTime(3_665_000))
    }
}
