package me.yummydroid.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.ResolvedPlayback
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.SourceQuality
import me.yummydroid.app.data.VideoVariant

class PlaybackSourceCoordinatorTest {
    @Test
    fun localPlaybackBypassesProviderResolution() = runBlocking {
        val localVideo = sourceVideo(id = 1, player = "Offline")
            .copy(localPlaybackUrl = "file:///episode-5.mp4")
        var localCalls = 0
        var providerCalls = 0
        val coordinator = coordinator(
            resolveLocalStream = { video ->
                localCalls += 1
                stream(video.localPlaybackUrl)
            },
            resolveBestPlayback = { _, _, _, _ ->
                providerCalls += 1
                error("Provider resolver must not be called")
            },
        )

        val result = coordinator.resolve(
            requested = localVideo,
            candidates = listOf(localVideo),
            preferredQuality = PreferredQuality.Auto,
        )

        assertSame(localVideo, result.playback.video)
        assertEquals("file:///episode-5.mp4", result.playback.stream.url)
        assertEquals(1, localCalls)
        assertEquals(0, providerCalls)
    }

    @Test
    fun candidatePoolKeepsEpisodeAndVoiceAndHonorsManualSource() {
        val cvh = sourceVideo(id = 1, player = "CVH")
        val kodik = sourceVideo(id = 2, player = "Kodik", url = "https://kodik.test/720p")
        val otherEpisode = sourceVideo(id = 3, player = "Alloha").copy(episode = "6")
        val otherVoice = sourceVideo(id = 4, player = "Alloha").copy(dubbing = "AniDUB")
        val coordinator = coordinator()
        coordinator.rememberManualSource(kodik)

        val candidates = coordinator.candidates(
            requested = cvh,
            allVideos = listOf(cvh, otherEpisode, otherVoice, kodik),
            excludedSourceKeys = emptySet(),
        )

        assertEquals(listOf(kodik, cvh), candidates)
        assertEquals(
            listOf(kodik),
            coordinator.candidates(cvh, listOf(cvh, kodik), setOf(cvh.playbackSourceKey)),
        )
    }

    @Test
    fun manualFailureDoesNotFallbackToAutomaticSource() = runBlocking {
        val cvh = sourceVideo(id = 1, player = "CVH")
        val kodik = sourceVideo(id = 2, player = "Kodik", url = "https://kodik.test/720p")
        val calls = mutableListOf<List<String>>()
        val coordinator = coordinator(
            resolveBestPlayback = { candidates, _, _, _ ->
                calls += candidates.map(VideoVariant::player)
                error("manual failed")
            },
        )
        coordinator.rememberManualSource(kodik)

        val failure = assertFailsWith<IllegalStateException> {
            coordinator.resolve(
                requested = cvh,
                candidates = listOf(cvh, kodik),
                preferredQuality = PreferredQuality.P1080,
            )
        }

        assertEquals(listOf(listOf("Kodik")), calls)
        assertEquals("manual failed", failure.message)
    }

    @Test
    fun manualSourceRuntimeFailureDoesNotFallbackToOtherSource() {
        val cvh = sourceVideo(id = 1, player = "CVH")
        val kodik = sourceVideo(id = 2, player = "Kodik", url = "https://kodik.test/720p")
        val coordinator = coordinator()
        coordinator.rememberManualSource(kodik)

        assertNull(
            coordinator.fallbackPlan(
                currentVideo = kodik,
                failedVideo = kodik,
                failure = PlaybackFailure(PlaybackFailureKind.BufferingTimeout),
                reason = "timeout",
                allVideos = listOf(cvh, kodik),
                preferredQuality = PreferredQuality.P480,
                currentStream = stream("https://kodik.test/480.m3u8", selectedVideoHeight = 480),
            ),
        )
    }

    @Test
    fun manualSuccessDoesNotResolveAutomaticSources() = runBlocking {
        val cvh = sourceVideo(id = 1, player = "CVH")
        val kodik = sourceVideo(id = 2, player = "Kodik", url = "https://kodik.test/720p")
        val calls = mutableListOf<List<VideoVariant>>()
        val coordinator = coordinator(
            resolveBestPlayback = { candidates, _, _, _ ->
                calls += candidates
                ResolvedPlayback(candidates.first(), stream("https://stream.test/master.m3u8"))
            },
        )
        coordinator.rememberManualSource(kodik)

        val result = coordinator.resolve(
            requested = cvh,
            candidates = listOf(cvh, kodik),
            preferredQuality = PreferredQuality.Auto,
        )

        assertEquals(listOf(listOf(kodik)), calls)
        assertEquals(kodik, result.playback.video)
        assertNull(result.manualFallbackNotice)
    }

    @Test
    fun fastStartResolvesOneHighQualitySourceAtATimeWithoutMetadata() = runBlocking {
        val kodik = sourceVideo(id = 1, player = "Kodik", url = "https://kodik.test/720p")
        val cvh = sourceVideo(id = 2, player = "CVH", url = "https://cvh.test/iframe")
        val alloha = sourceVideo(id = 3, player = "Alloha", url = "https://alloha.test/iframe")
        val calls = mutableListOf<ResolveCall>()
        val coordinator = coordinator(
            resolveBestPlayback = { candidates, _, metadataCandidates, waitForRuntimeSubtitles ->
                calls += ResolveCall(candidates, metadataCandidates, waitForRuntimeSubtitles)
                if (candidates.single() == cvh) error("CVH unavailable")
                ResolvedPlayback(candidates.single(), stream("https://stream.test/master.m3u8"))
            },
        )

        val result = coordinator.resolve(
            requested = kodik,
            candidates = listOf(kodik, cvh, alloha),
            preferredQuality = PreferredQuality.Auto,
            fastStart = true,
        )

        assertEquals(listOf(cvh, alloha), calls.flatMap(ResolveCall::candidates))
        assertTrue(calls.all { it.candidates.size == 1 })
        assertTrue(calls.all { it.metadataCandidates.isEmpty() })
        assertTrue(calls.none(ResolveCall::waitForRuntimeSubtitles))
        assertEquals(alloha, result.playback.video)
    }

    @Test
    fun confirmedProviderIsCachedUntilRuntimeCacheReset() = runBlocking {
        val cvh = sourceVideo(id = 1, player = "CVH", index = 1)
        val alloha = sourceVideo(id = 2, player = "Alloha", index = 2)
        val firstCandidates = mutableListOf<VideoVariant>()
        val coordinator = coordinator(
            resolveBestPlayback = { candidates, _, _, _ ->
                firstCandidates += candidates.first()
                ResolvedPlayback(candidates.first(), stream("https://stream.test/master.m3u8"))
            },
        )
        assertTrue(coordinator.confirm(alloha, alloha))

        coordinator.resolve(cvh, listOf(cvh, alloha), PreferredQuality.Auto)
        coordinator.resetRuntime(clearSourceCache = true)
        coordinator.resolve(cvh, listOf(cvh, alloha), PreferredQuality.Auto)

        assertEquals(listOf(alloha, cvh), firstCandidates)
    }

    @Test
    fun playerErrorDoesNotFallbackToSameQualitySameVoiceSource() {
        val current = sourceVideo(id = 1, player = "Kodik", quality = 480)
        val sameQuality = sourceVideo(id = 2, player = "Alloha", quality = 480)

        assertNull(
            coordinator().fallbackPlan(
                currentVideo = current,
                failedVideo = current,
                failure = PlaybackFailure(PlaybackFailureKind.PlayerError, "decoder failed"),
                reason = "decoder failed",
                allVideos = listOf(current, sameQuality),
                preferredQuality = PreferredQuality.Auto,
                currentStream = stream("https://stream.test/480.m3u8", selectedVideoHeight = 480),
            ),
        )
    }

    @Test
    fun playerErrorCanFallbackToHigherQualitySameVoiceSource() {
        val current = sourceVideo(id = 1, player = "Kodik", quality = 480)
        val higher = sourceVideo(id = 2, player = "Alloha", quality = 1080)

        val plan = coordinator().fallbackPlan(
            currentVideo = current,
            failedVideo = current,
            failure = PlaybackFailure(PlaybackFailureKind.PlayerError, "decoder failed"),
            reason = "decoder failed",
            allVideos = listOf(current, higher),
            preferredQuality = PreferredQuality.Auto,
            currentStream = stream("https://stream.test/480.m3u8", selectedVideoHeight = 480),
        )

        assertEquals(higher, plan?.targetVideo)
        assertNull(plan?.voiceFallbackFromVideo)
        assertTrue(current.playbackSourceKey in plan.orEmptyExcludedKeys())
    }

    @Test
    fun unavailableSourceCanFallbackToSameQualitySameVoiceSource() {
        val current = sourceVideo(id = 1, player = "Alloha", quality = 1080)
        val sameQuality = sourceVideo(id = 2, player = "CVH", quality = 1080)

        val plan = coordinator().fallbackPlan(
            currentVideo = current,
            failedVideo = current,
            failure = PlaybackFailure(PlaybackFailureKind.SourceUnavailable, "HTTP 403"),
            reason = "HTTP 403",
            allVideos = listOf(current, sameQuality),
            preferredQuality = PreferredQuality.Auto,
            currentStream = stream("https://stream.test/1080.m3u8", selectedVideoHeight = 1080),
        )

        assertEquals(sameQuality, plan?.targetVideo)
        assertEquals(current, plan?.notice?.selectedVideo)
        assertEquals("HTTP 403", plan?.notice?.reason)
        assertNull(plan?.voiceFallbackFromVideo)
        assertTrue(current.playbackSourceKey in plan.orEmptyExcludedKeys())
    }

    @Test
    fun qualityFallbackDoesNotExceedManualQualitySelection() {
        val current = sourceVideo(id = 1, player = "Kodik", quality = 720)
        val higher = sourceVideo(id = 2, player = "Alloha", quality = 1080)

        val plan = coordinator().fallbackPlan(
            currentVideo = current,
            failedVideo = current,
            failure = PlaybackFailure(PlaybackFailureKind.PlayerError, "decoder failed"),
            reason = "decoder failed",
            allVideos = listOf(current, higher),
            preferredQuality = PreferredQuality.P720,
            currentStream = stream("https://stream.test/720.m3u8", selectedVideoHeight = 720),
        )

        assertNull(plan)
    }

    @Test
    fun bufferingTimeoutCanFallbackToSameQualitySameVoiceSource() {
        val current = sourceVideo(id = 1, player = "Kodik", quality = 480)
        val sameQuality = sourceVideo(id = 2, player = "Alloha", quality = 480)

        val plan = coordinator().fallbackPlan(
            currentVideo = current,
            failedVideo = current,
            failure = PlaybackFailure(PlaybackFailureKind.BufferingTimeout),
            reason = "timeout",
            allVideos = listOf(current, sameQuality),
            preferredQuality = PreferredQuality.Auto,
            currentStream = stream("https://stream.test/480.m3u8", selectedVideoHeight = 480),
        )

        assertEquals(sameQuality, plan?.targetVideo)
        assertNull(plan?.voiceFallbackFromVideo)
    }

    @Test
    fun fallbackChangesVoiceOnlyWhenCurrentVoiceHasNoRemainingSource() {
        val current = sourceVideo(id = 1, player = "Kodik", quality = 480)
        val otherVoice = sourceVideo(id = 2, player = "Alloha", quality = 1080, dubbing = "AniDUB")

        val plan = coordinator().fallbackPlan(
            currentVideo = current,
            failedVideo = current,
            failure = PlaybackFailure(PlaybackFailureKind.PlayerError, "decoder failed"),
            reason = "decoder failed",
            allVideos = listOf(current, otherVoice),
            preferredQuality = PreferredQuality.Auto,
            currentStream = stream("https://stream.test/480.m3u8", selectedVideoHeight = 480),
        )

        assertEquals(otherVoice, plan?.targetVideo)
        assertEquals(current, plan?.voiceFallbackFromVideo)
    }

    @Test
    fun fallbackDoesNotChangeVoiceWhileSameVoiceSourceExists() {
        val current = sourceVideo(id = 1, player = "Kodik", quality = 480)
        val sameVoice = sourceVideo(id = 2, player = "Alloha", quality = 480)
        val otherVoice = sourceVideo(id = 3, player = "CVH", quality = 1080, dubbing = "AniDUB")

        val plan = coordinator().fallbackPlan(
            currentVideo = current,
            failedVideo = current,
            failure = PlaybackFailure(PlaybackFailureKind.PlayerError, "decoder failed"),
            reason = "decoder failed",
            allVideos = listOf(current, sameVoice, otherVoice),
            preferredQuality = PreferredQuality.Auto,
            currentStream = stream("https://stream.test/480.m3u8", selectedVideoHeight = 480),
        )

        assertNull(plan)
    }

    @Test
    fun fallbackPolicyIgnoresStaleSourceFailuresAndExpiresBlockedSources() {
        var nowMs = 1_000L
        val cvh = sourceVideo(id = 1, player = "CVH")
        val kodik = sourceVideo(id = 2, player = "Kodik", url = "https://kodik.test/720p")
        val coordinator = coordinator(clockMs = { nowMs }, failedSourceCooldownMs = 100L)

        assertNull(
            coordinator.fallbackPlan(
                currentVideo = cvh,
                failedVideo = cvh,
                failure = PlaybackFailure(PlaybackFailureKind.BufferingTimeout),
                reason = "timeout",
                allVideos = listOf(cvh),
                preferredQuality = PreferredQuality.Auto,
                currentStream = stream("https://stream.test/master.m3u8"),
            ),
        )
        assertNull(
            coordinator.fallbackPlan(
                currentVideo = cvh,
                failedVideo = kodik,
                failure = PlaybackFailure(PlaybackFailureKind.PlayerError),
                reason = "stale",
                allVideos = listOf(cvh, kodik),
                preferredQuality = PreferredQuality.Auto,
                currentStream = stream("https://stream.test/master.m3u8"),
            ),
        )

        val plan = coordinator.fallbackPlan(
            currentVideo = cvh,
            failedVideo = cvh,
            failure = PlaybackFailure(PlaybackFailureKind.BufferingTimeout),
            reason = "timeout",
            allVideos = listOf(cvh, kodik),
            preferredQuality = PreferredQuality.Auto,
            currentStream = stream("https://stream.test/master.m3u8"),
        )
        assertEquals(setOf(cvh.playbackSourceKey), plan?.excludedSourceKeys)

        nowMs += 101L
        val expiredPlan = coordinator.fallbackPlan(
            currentVideo = kodik,
            failedVideo = kodik,
            failure = PlaybackFailure(PlaybackFailureKind.BufferingTimeout),
            reason = "timeout",
            allVideos = listOf(cvh, kodik),
            preferredQuality = PreferredQuality.Auto,
            currentStream = stream("https://stream.test/master.m3u8"),
        )
        assertFalse(cvh.playbackSourceKey in expiredPlan.orEmptyExcludedKeys())
        assertTrue(kodik.playbackSourceKey in expiredPlan.orEmptyExcludedKeys())
    }

    @Test
    fun confirmRejectsAStaleProvider() {
        val cvh = sourceVideo(id = 1, player = "CVH")
        val kodik = sourceVideo(id = 2, player = "Kodik", url = "https://kodik.test/720p")

        assertFalse(coordinator().confirm(cvh, kodik))
    }

    @Test
    fun cancellationIsNotConvertedIntoPlaybackFailure() {
        val cvh = sourceVideo(id = 1, player = "CVH")
        val coordinator = coordinator(
            resolveBestPlayback = { _, _, _, _ -> throw CancellationException("cancelled") },
        )

        assertFailsWith<CancellationException> {
            runBlocking {
                coordinator.resolve(cvh, listOf(cvh), PreferredQuality.Auto)
            }
        }
    }

    private fun coordinator(
        resolveLocalStream: suspend (VideoVariant) -> ResolvedVideoStream = { stream(it.localPlaybackUrl) },
        resolveBestPlayback: suspend (
            List<VideoVariant>,
            PreferredQuality,
            List<VideoVariant>,
            Boolean,
        ) -> ResolvedPlayback = { candidates, _, _, _ ->
            ResolvedPlayback(candidates.first(), stream("https://stream.test/master.m3u8"))
        },
        clockMs: () -> Long = { 0L },
        failedSourceCooldownMs: Long = 5L * 60L * 1_000L,
    ): PlaybackSourceCoordinator {
        return PlaybackSourceCoordinator(
            resolveLocalStream = resolveLocalStream,
            resolveBestPlayback = resolveBestPlayback,
            couldNotSelectSourceMessage = { "Could not select source" },
            noFallbackAfterManualMessage = { "No fallback after manual source" },
            clockMs = clockMs,
            failedSourceCooldownMs = failedSourceCooldownMs,
        )
    }

    private fun sourceVideo(
        id: Long,
        player: String,
        url: String = "https://${player.lowercase()}.test/iframe",
        index: Int = id.toInt(),
        quality: Int? = null,
        dubbing: String = "AniLibria",
    ): VideoVariant {
        return VideoVariant(
            id = id,
            animeId = 10_669,
            player = player,
            dubbing = dubbing,
            episode = "5",
            url = url,
            index = index,
            durationSeconds = 1_421,
            views = 0L,
            sourceQualities = quality?.let { listOf(SourceQuality(height = it)) }.orEmpty(),
        )
    }

    private fun stream(
        url: String,
        selectedVideoHeight: Int? = null,
        maxVideoHeight: Int? = null,
    ): ResolvedVideoStream {
        return ResolvedVideoStream(
            url = url,
            mimeType = null,
            headers = emptyMap(),
            selectedVideoHeight = selectedVideoHeight,
            maxVideoHeight = maxVideoHeight,
        )
    }

    private fun PlaybackSourceFallbackPlan?.orEmptyExcludedKeys(): Set<String> {
        return this?.excludedSourceKeys.orEmpty()
    }

    private data class ResolveCall(
        val candidates: List<VideoVariant>,
        val metadataCandidates: List<VideoVariant>,
        val waitForRuntimeSubtitles: Boolean,
    )
}
