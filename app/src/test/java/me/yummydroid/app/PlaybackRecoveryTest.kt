package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import me.yummydroid.app.data.VideoVariant

class PlaybackRecoveryTest {
    private val unavailable = PlaybackFailure(PlaybackFailureKind.SourceUnavailable)

    @Test
    fun healthyMinuteOfAdvancingPlaybackReplenishesAttempt() {
        var now = 1_000L
        val recovery = PlaybackRecovery { now }
        val video = video()
        assertEquals(2_000L, recovery.consumeAttempt(video, unavailable))
        recovery.observeProgress(video, 0L)
        repeat(4) { index ->
            now += 15_000L
            recovery.observeProgress(video, (index + 1) * 15_000L)
        }
        assertEquals(2_000L, recovery.consumeAttempt(video, unavailable))
        assertNull(recovery.consumeAttempt(video, unavailable))
    }

    @Test
    fun pausedPlaybackShortHealthyPeriodAndSeekCannotReplenishAttempt() {
        var now = 1_000L
        val recovery = PlaybackRecovery { now }
        val video = video()
        recovery.consumeAttempt(video, unavailable)
        recovery.observeProgress(video, 0L)
        repeat(4) {
            now += 15_000L
            recovery.observeProgress(video, 0L)
        }
        assertNull(recovery.consumeAttempt(video, unavailable))
        recovery.observeProgress(video, 0L)
        now += 15_000L
        recovery.observeProgress(video, 15_000L)
        now += 15_000L
        recovery.observeProgress(video, 615_000L)
        repeat(3) { index ->
            now += 15_000L
            recovery.observeProgress(video, 615_000L + (index + 1) * 15_000L)
        }
        assertNull(recovery.consumeAttempt(video, unavailable))
    }

    @Test
    fun rateLimitAppliesAcrossEpisodesButNotOtherProviders() {
        val recovery = PlaybackRecovery { 1_000L }
        val video = video()
        assertNull(recovery.consumeAttempt(video, PlaybackFailure(
            PlaybackFailureKind.SourceUnavailable, httpStatusCode = 429, retryAtEpochMs = 120_000L)))
        recovery.resetAttempts()
        assertEquals(119_000L, recovery.remainingCooldownMs(video.copy(episode = "2", animeId = 20)))
        assertEquals(0L, recovery.remainingCooldownMs(video(player = "Kodik")))
    }

    @Test
    fun providerAliasesAndMaximumDeadlineAreSupported() {
        val recovery = PlaybackRecovery { 0L }
        val alias = video(player = "CDNVideoHub")
        assertEquals(2_000L, recovery.consumeAttempt(alias, unavailable))
        recovery.consumeAttempt(alias, PlaybackFailure(
            PlaybackFailureKind.SourceUnavailable, httpStatusCode = 503, retryAtEpochMs = Long.MAX_VALUE))
        val message = assertNotNull(recovery.cooldownMessage(alias))
        assertTrue(message.contains("9223372036854776"))
    }

    @Test
    fun forbiddenResponseUsesCooldownOnlyWhenServerSuppliesDeadline() {
        val ordinary = PlaybackRecovery { 1_000L }
        assertEquals(2_000L, ordinary.consumeAttempt(video(), PlaybackFailure(
            PlaybackFailureKind.SourceUnavailable, httpStatusCode = 403)))
        val restricted = PlaybackRecovery { 1_000L }
        assertNull(restricted.consumeAttempt(video(), PlaybackFailure(
            PlaybackFailureKind.SourceUnavailable, httpStatusCode = 403, retryAtEpochMs = 120_000L)))
        assertEquals(119_000L, restricted.remainingCooldownMs(video()))
    }

    private fun video(player: String = "Alloha") = VideoVariant(
        id = 1, animeId = 10, player = player, dubbing = "Voice", episode = "1",
        url = "https://${player.lowercase()}.test/1", index = 1, durationSeconds = 1400, views = 0,
    )
}
