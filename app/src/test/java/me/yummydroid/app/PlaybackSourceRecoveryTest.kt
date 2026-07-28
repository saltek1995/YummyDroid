package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackSourceRecoveryTest {
    @Test
    fun recoveryAcceptsSameOrBetterKnownQuality() {
        assertTrue(shouldAcceptPlaybackRecovery(currentHeight = 720, recoveredHeight = 720))
        assertTrue(shouldAcceptPlaybackRecovery(currentHeight = 720, recoveredHeight = 1080))
    }

    @Test
    fun recoveryRejectsWorseKnownQuality() {
        assertFalse(shouldAcceptPlaybackRecovery(currentHeight = 1080, recoveredHeight = 720))
    }

    @Test
    fun recoveryAcceptsUnknownQualityMetadata() {
        assertTrue(shouldAcceptPlaybackRecovery(currentHeight = 0, recoveredHeight = 720))
        assertTrue(shouldAcceptPlaybackRecovery(currentHeight = 720, recoveredHeight = 0))
    }
}
