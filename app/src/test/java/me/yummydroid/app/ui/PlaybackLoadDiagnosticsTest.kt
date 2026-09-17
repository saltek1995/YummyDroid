package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackLoadDiagnosticsTest {
    private val point = PlaybackPoint(30_000, 12_000, true, 2, false, 123_000)

    @Test fun errorDoesNotClaimReloadAndLaterLoadKeepsOriginalTriggerSnapshot() {
        val trigger = PlaybackTrigger(PlaybackTriggerReason.PlayerError, point, 2004, 429)
        val failed = PlaybackAttemptDiagnostics().triggered(trigger)
        assertEquals(0, failed.loadCount)
        assertEquals(0, failed.loadsAfterTrigger)
        assertNull(failed.load)
        val loaded = failed.loaded(PlaybackLoadRequest(point.copy(bufferMs = 0), 7, 8, true))
        assertEquals(trigger, loaded.trigger)
        assertEquals(1, loaded.loadCount)
        assertEquals(1, loaded.loadsAfterTrigger)
        assertEquals(true, loaded.load?.sameUrl)
        assertTrue(loaded.overlayText().contains("http=429"))
        assertTrue(loaded.overlayText().contains("buf=12000"))
    }

    @Test fun ordinaryLoadsHaveNoInferredAutomaticTriggerAndCountsSaturate() {
        val request = PlaybackLoadRequest(point, null, 1, null)
        val loaded = PlaybackAttemptDiagnostics().loaded(request).loaded(request.copy(newGeneration = 2))
        assertNull(loaded.trigger)
        assertEquals(2, loaded.loadCount)
        assertEquals(0, loaded.loadsAfterTrigger)
        val saturated = loaded.copy(loadCount = Long.MAX_VALUE, loadsAfterTrigger = Long.MAX_VALUE,
            trigger = PlaybackTrigger(PlaybackTriggerReason.BufferingTimeout, point, timeoutMs = 60_000, inactivityMs = 61_000))
            .loaded(request)
        assertEquals(Long.MAX_VALUE, saturated.loadCount)
        assertEquals(Long.MAX_VALUE, saturated.loadsAfterTrigger)
        assertEquals(0, saturated.triggered(PlaybackTrigger(PlaybackTriggerReason.StartupTimeout, point)).loadsAfterTrigger)
    }
}
