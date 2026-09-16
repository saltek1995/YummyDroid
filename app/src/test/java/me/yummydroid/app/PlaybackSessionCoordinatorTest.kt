package me.yummydroid.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.ResolvedPlayback
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.VideoVariant

class PlaybackSessionCoordinatorTest {
    @Test
    fun playPreservesStateTransitionOrderAndEnrichesAcceptedStream() {
        val video = video(id = 1, animeId = 10, player = "CVH")
        val initialStream = stream("https://stream.test/master.m3u8")
        val enrichedStream = initialStream.copy(maxVideoHeight = 1080)
        val events = mutableListOf<String>()
        val harness = harness(
            initialState = YummyDroidUiState(
                route = AppRoute.Details(video.animeId),
                videos = LoadState.Ready(listOf(video)),
            ),
            resolveBestPlayback = { candidates, _, _, waitForRuntimeSubtitles ->
                events += "resolve:$waitForRuntimeSubtitles"
                ResolvedPlayback(candidates.single(), initialStream)
            },
            resolvePlaybackMetadata = { playback, candidates, quality ->
                events += "metadata:${candidates.size}:$quality"
                playback.copy(stream = enrichedStream)
            },
            cachedSiteBaseUrl = { "https://site.test" },
        )

        harness.coordinator.play(
            request(
                video = video,
                startPositionMs = -1L,
                resumeChoicePositionMs = -2L,
                preferredQuality = PreferredQuality.P1080,
            ),
        )

        val route = assertIs<AppRoute.Player>(harness.state.route)
        assertEquals(0L, route.startPositionMs)
        assertNull(route.resumeChoicePositionMs)
        assertEquals(video, route.video)
        assertEquals("https://site.test", harness.state.siteBaseUrl)
        assertEquals(video.groupKey, harness.state.selectedVideoGroup)
        assertEquals(enrichedStream.copy(playbackGeneration = 1L), harness.state.playerStream.readyDataOrNull())
        assertFalse(harness.state.playbackMetadataLoading)
        assertEquals(listOf(AppRoute.Details(video.animeId)), harness.state.navigationBackStack.map { it.route })
        assertEquals(listOf("resolve:false", "metadata:1:P1080"), events)

        val playerStates = harness.states.filter { it.route is AppRoute.Player }
        assertTrue(playerStates.first().playerStream is LoadState.Loading)
        assertTrue(playerStates.any { it.playerStream.readyDataOrNull() == initialStream.copy(playbackGeneration = 1L) })
        assertTrue(playerStates.any { it.playbackMetadataLoading })
        assertEquals(enrichedStream.copy(playbackGeneration = 1L), playerStates.last().playerStream.readyDataOrNull())
        harness.close()
    }

    @Test
    fun castLoadCanOpenPlayerWithoutStartingPlayback() {
        val video = video(id = 1, animeId = 10, player = "CVH")
        val harness = harness(
            initialState = YummyDroidUiState(
                route = AppRoute.Details(video.animeId),
                videos = LoadState.Ready(listOf(video)),
            ),
        )

        harness.coordinator.play(request(video = video, playWhenReady = false))

        val route = assertIs<AppRoute.Player>(harness.state.route)
        assertFalse(route.playWhenReady)
        assertEquals(video, route.video)
        harness.close()
    }

    @Test
    fun forcedOfflineModeReplacesOnlineRouteWithDownloadedVariant() {
        val online = video(id = 1, animeId = 10, player = "CVH")
        val offline = online.copy(id = 2, localPlaybackUrl = "file:///episode-1.mp4")
        var providerCalls = 0
        val harness = harness(
            initialState = YummyDroidUiState(
                forcedOfflineMode = true,
                videos = LoadState.Ready(listOf(online, offline)),
            ),
            resolveLocalStream = { stream(it.localPlaybackUrl) },
            resolveBestPlayback = { _, _, _, _ ->
                providerCalls += 1
                error("Provider resolution must not run in this test")
            },
        )

        harness.coordinator.play(request(video = online))

        assertEquals(offline, assertIs<AppRoute.Player>(harness.state.route).video)
        assertEquals("file:///episode-1.mp4", harness.state.playerStream.readyDataOrNull()?.url)
        assertEquals(0, providerCalls)
        harness.close()
    }

    @Test
    fun forcedOfflineModeReportsUnavailableEpisodeWithoutResolvingProvider() {
        val online = video(id = 1, animeId = 10, player = "CVH")
        var resolverCalled = false
        val harness = harness(
            initialState = YummyDroidUiState(
                forcedOfflineMode = true,
                videos = LoadState.Ready(listOf(online)),
            ),
            resolveBestPlayback = { _, _, _, _ ->
                resolverCalled = true
                error("Provider resolution must not run")
            },
            offlineUnavailableMessage = { "Unavailable offline" },
        )

        harness.coordinator.play(request(video = online))

        assertFalse(resolverCalled)
        assertEquals("Unavailable offline", harness.state.offlineDownload.message)
        assertEquals("Unavailable offline", assertIs<LoadState.Error>(harness.state.playerStream).message)
        harness.close()
    }

    @Test
    fun offlinePlaybackUsesDownloadedVoiceWithoutAnyResolverOrMetadataRequests() {
        val online = video(1, 10, "CVH")
        val downloaded = online.copy(id = 2, player = "Kodik", dubbing = "AnimeVost", localPlaybackUrl = "file:///one.mp4")
        val harness = harness(
            initialState = YummyDroidUiState(forcedOfflineMode = true, videos = LoadState.Ready(listOf(online, downloaded))),
            resolveLocalStream = { error("Offline playback must construct a local stream directly") },
            resolveBestPlayback = { _, _, _, _ -> error("Must not contact a provider") },
            resolvePlaybackMetadata = { _, _, _ -> error("Must not probe quality or subtitles") },
        )
        harness.coordinator.play(request(online))
        assertEquals(downloaded, assertIs<AppRoute.Player>(harness.state.route).video)
        assertEquals("file:///one.mp4", harness.state.playerStream.readyDataOrNull()?.url)
        assertFalse(harness.state.playbackMetadataLoading)
        val failure = harness.coordinator.handlePlaybackFailure(downloaded, 1000,
            PlaybackFailure(PlaybackFailureKind.PlayerError), "Local file is missing")
        assertEquals(PlaybackFailureOutcome.Failed, failure)
        assertIs<LoadState.Error>(harness.state.playerStream)
        harness.close()
    }

    @Test
    fun offlineSourceLockAndMissingEpisodeNeverReintroduceOnlineCandidates() {
        val online = video(1, 10, "CVH")
        val otherSource = online.copy(id = 2, player = "Kodik", localPlaybackUrl = "file:///one.mp4")
        val otherEpisode = online.copy(id = 3, episode = "2", localPlaybackUrl = "file:///two.mp4")
        val harness = harness(
            initialState = YummyDroidUiState(forcedOfflineMode = true, videos = LoadState.Ready(listOf(online, otherSource, otherEpisode))),
            resolveBestPlayback = { _, _, _, _ -> error("Must not contact a provider") },
        )
        harness.coordinator.play(request(online, lockPlaybackSource = true))
        assertIs<LoadState.Error>(harness.state.playerStream)
        harness.coordinator.play(request(online.copy(episode = "3")))
        assertIs<LoadState.Error>(harness.state.playerStream)
        harness.close()
    }

    @Test
    fun disconnectCancelsPendingOnlineResolutionAndStartsLocalPlayback() = runBlocking {
        val online = video(1, 10, "CVH")
        val local = online.copy(id = 2, localPlaybackUrl = "file:///one.mp4")
        val started = CompletableDeferred<Unit>()
        val pending = CompletableDeferred<ResolvedPlayback>()
        val harness = harness(
            initialState = YummyDroidUiState(videos = LoadState.Ready(listOf(online))),
            resolveBestPlayback = { _, _, _, _ -> started.complete(Unit); pending.await() },
            resolvePlaybackMetadata = { _, _, _ -> error("Must not probe metadata offline") },
        )
        harness.coordinator.play(request(online))
        started.await()
        harness.update { it.copy(forcedOfflineMode = true, videos = LoadState.Ready(listOf(online, local))) }
        harness.coordinator.enterOfflineMode()
        pending.complete(ResolvedPlayback(online, stream("https://online.test/one.mp4")))
        yield()
        assertEquals("file:///one.mp4", harness.state.playerStream.readyDataOrNull()?.url)
        assertFalse(harness.state.playbackMetadataLoading)
        harness.close()
    }

    @Test
    fun lockedPlaybackSourceDoesNotFallbackToOtherSourceDuringManualQualityChange() = runBlocking {
        val cvh = video(id = 1, animeId = 10, player = "CVH")
        val kodik = video(id = 2, animeId = 10, player = "Kodik")
        val calls = mutableListOf<List<VideoVariant>>()
        val harness = harness(
            initialState = YummyDroidUiState(videos = LoadState.Ready(listOf(cvh, kodik))),
            resolveBestPlayback = { candidates, quality, _, _ ->
                calls += candidates
                assertEquals(PreferredQuality.P480, quality)
                error("CVH 480 failed")
            },
        )

        harness.coordinator.play(
            request(
                video = cvh,
                preferredQuality = PreferredQuality.P480,
                lockPlaybackSource = true,
            ),
        )
        harness.awaitPlayerStreamSettled()

        assertEquals(listOf(listOf(cvh)), calls)
        assertEquals(cvh, assertIs<AppRoute.Player>(harness.state.route).video)
        assertEquals("CVH 480 failed", assertIs<LoadState.Error>(harness.state.playerStream).message)
        harness.close()
    }

    @Test
    fun sourceResolutionTimeoutReportsPlaybackError() = runBlocking {
        val video = video(id = 1, animeId = 10, player = "Kodik")
        val neverResolved = CompletableDeferred<ResolvedPlayback>()
        val harness = harness(
            initialState = YummyDroidUiState(videos = LoadState.Ready(listOf(video))),
            resolveBestPlayback = { _, _, _, _ -> neverResolved.await() },
            sourceResolveTimeoutMessage = { "Source did not respond" },
            sourceResolveTimeoutMs = 1L,
        )

        harness.coordinator.play(request(video = video))
        harness.awaitPlayerStreamSettled()

        assertEquals(video, assertIs<AppRoute.Player>(harness.state.route).video)
        assertEquals("Source did not respond", assertIs<LoadState.Error>(harness.state.playerStream).message)
        harness.close()
    }

    @Test
    fun missingStatePoolFallsBackToRequestedVideoWithoutExtraFetch() {
        val video = video(id = 1, animeId = 10, player = "Kodik")
        val resolvedCandidates = mutableListOf<List<VideoVariant>>()
        val harness = harness(
            initialState = YummyDroidUiState(),
            resolveBestPlayback = { candidates, _, _, _ ->
                resolvedCandidates += candidates
                ResolvedPlayback(candidates.single(), stream("https://stream.test/kodik.m3u8"))
            },
        )

        harness.coordinator.play(request(video = video))

        assertEquals(listOf(listOf(video)), resolvedCandidates)
        assertEquals("https://stream.test/kodik.m3u8", harness.state.playerStream.readyDataOrNull()?.url)
        harness.close()
    }

    @Test
    fun currentPlaybackFailureWithoutFallbackReplacesActiveStreamWithError() {
        val video = video(id = 1, animeId = 10, player = "CVH")
        val harness = harness(
            initialState = YummyDroidUiState(videos = LoadState.Ready(listOf(video))),
        )

        harness.coordinator.play(request(video = video))
        assertTrue(harness.state.playerStream is LoadState.Ready)

        harness.coordinator.handlePlaybackFailure(video, 5000L, PlaybackFailure(PlaybackFailureKind.BufferingTimeout), "Refresh")
        assertEquals(
            PlaybackFailureOutcome.Failed,
            harness.coordinator.handlePlaybackFailure(
                failedVideo = video,
                playbackPositionMs = 5_000L,
                failure = PlaybackFailure(PlaybackFailureKind.BufferingTimeout),
                reason = "Buffer is not filling",
            ),
        )

        assertEquals("Buffer is not filling", assertIs<LoadState.Error>(harness.state.playerStream).message)
        harness.close()
    }

    @Test
    fun obsoletePlayerFailuresCannotBreakTheActiveSession() {
        val currentVideo = video(id = 1, animeId = 10, player = "CVH")
        val staleVideos = listOf(
            video(id = 2, animeId = 10, player = "Kodik"),
            currentVideo.copy(animeId = 20),
            currentVideo.copy(episode = "2"),
            currentVideo.copy(dubbing = "Another voice"),
        )
        val harness = harness(YummyDroidUiState(videos = LoadState.Ready(listOf(currentVideo))))
        try {
            harness.coordinator.play(request(currentVideo))
            val activeState = harness.state
            staleVideos.forEach { stale ->
                assertEquals(
                    PlaybackFailureOutcome.Ignored,
                    harness.coordinator.handlePlaybackFailure(
                        stale, 5_000L, PlaybackFailure(PlaybackFailureKind.PlayerError), "Old failure",
                    ),
                )
                assertEquals(activeState, harness.state)
            }
        } finally {
            harness.close()
        }
    }

    @Test
    fun retainedSurfaceFailureDuringReplacementDoesNotCancelNewResolution() = runBlocking {
        val currentVideo = video(id = 1, animeId = 10, player = "CVH")
        val resolution = CompletableDeferred<ResolvedPlayback>()
        val harness = harness(
            initialState = YummyDroidUiState(videos = LoadState.Ready(listOf(currentVideo))),
            resolveBestPlayback = { _, _, _, _ -> resolution.await() },
        )
        try {
            harness.coordinator.play(request(currentVideo))
            assertIs<LoadState.Loading>(harness.state.playerStream)
            assertEquals(
                PlaybackFailureOutcome.Ignored,
                harness.coordinator.handlePlaybackFailure(
                    currentVideo, 5_000L, PlaybackFailure(PlaybackFailureKind.PlayerError), "Old surface",
                ),
            )
            resolution.complete(ResolvedPlayback(currentVideo, stream("https://stream.test/new.m3u8")))
            harness.awaitPlayerStreamSettled()
            assertEquals("https://stream.test/new.m3u8", harness.state.playerStream.readyDataOrNull()?.url)
        } finally {
            harness.close()
        }
    }

    @Test
    fun failureRecoveryUpdatesSourceAndPreservesPositionAndQuality() {
        val initialVideo = video(id = 1, animeId = 10, player = "CVH")
        val fallbackVideo = video(id = 2, animeId = 10, player = "Kodik")
        val harness = harness(YummyDroidUiState(videos = LoadState.Ready(listOf(initialVideo, fallbackVideo))))
        try {
            harness.coordinator.play(request(initialVideo, preferredQuality = PreferredQuality.P720))
            val playingVideo = assertIs<AppRoute.Player>(harness.state.route).video
        harness.coordinator.handlePlaybackFailure(playingVideo, 12345L, PlaybackFailure(PlaybackFailureKind.SourceUnavailable), "Refresh")
            val expectedFallback = if (playingVideo == initialVideo) fallbackVideo else initialVideo
            assertEquals(
                PlaybackFailureOutcome.Recovering,
                harness.coordinator.handlePlaybackFailure(
                    playingVideo, 12_345L, PlaybackFailure(PlaybackFailureKind.SourceUnavailable), "Unavailable",
                ),
            )
            val route = assertIs<AppRoute.Player>(harness.state.route)
            assertEquals(expectedFallback, route.video)
            assertEquals(12_345L, route.startPositionMs)
            assertEquals(PreferredQuality.P720, route.preferredQuality)
            assertIs<LoadState.Ready<ResolvedVideoStream>>(harness.state.playerStream)
        } finally {
            harness.close()
        }
    }

    @Test
    fun terminalFailureKeepsPositionForErrorShellRetryAndSourceSelection() {
        val current = video(id = 1, animeId = 10, player = "Alloha")
        val harness = harness(YummyDroidUiState(videos = LoadState.Ready(listOf(current))))
        try {
            harness.coordinator.play(request(current, startPositionMs = 1_000L))
        harness.coordinator.handlePlaybackFailure(current, 615000L, PlaybackFailure(PlaybackFailureKind.SourceUnavailable), "Refresh")
            assertEquals(
                PlaybackFailureOutcome.Failed,
                harness.coordinator.handlePlaybackFailure(
                    current, 615_000L, PlaybackFailure(PlaybackFailureKind.SourceUnavailable), "HTTP 403",
                ),
            )
            assertIs<LoadState.Error>(harness.state.playerStream)
            val route = assertIs<AppRoute.Player>(harness.state.route)
            assertEquals(615_000L, route.startPositionMs)
            // The shell passes this route position when the user chooses another source.
            val other = video(id = 2, animeId = 10, player = "Kodik")
            harness.coordinator.play(request(other, startPositionMs = route.startPositionMs, lockPlaybackSource = true))
            assertEquals(615_000L, assertIs<AppRoute.Player>(harness.state.route).startPositionMs)
        } finally { harness.close() }
    }

    @Test
    fun manualSelectionAfterResolveFailureLeavesErrorAndLoadsTheNewSource() {
        val failed = video(id = 1, animeId = 10, player = "Alloha")
        val working = video(id = 2, animeId = 10, player = "Kodik")
        val calls = mutableListOf<VideoVariant>()
        val harness = harness(
            YummyDroidUiState(videos = LoadState.Ready(listOf(failed, working))),
            resolveBestPlayback = { candidates, _, _, _ ->
                val selected = candidates.single()
                calls += selected
                if (selected == failed) throw java.io.IOException("HTTP 403")
                ResolvedPlayback(selected, stream("https://stream.test/working.mp4"))
            },
        )
        try {
            harness.coordinator.rememberManualSource(failed)
            harness.coordinator.play(request(failed, startPositionMs = 615_000L, lockPlaybackSource = true))
            assertEquals("HTTP 403", assertIs<LoadState.Error>(harness.state.playerStream).message)
            harness.coordinator.rememberManualSource(working)
            harness.coordinator.resetRuntime(clearSourceCache = true)
            harness.coordinator.play(request(working, startPositionMs = 615_000L, lockPlaybackSource = true))
            assertEquals(listOf(failed, working), calls)
            assertEquals(working, assertIs<AppRoute.Player>(harness.state.route).video)
            assertEquals(615_000L, assertIs<AppRoute.Player>(harness.state.route).startPositionMs)
            assertEquals("https://stream.test/working.mp4", harness.state.playerStream.readyDataOrNull()?.url)
        } finally { harness.close() }
    }

    @Test
    fun acceptedVoiceFallbackReportsNotice() {
        val previousVoice = video(id = 1, animeId = 10, player = "Kodik")
        val fallbackVoice = video(id = 2, animeId = 10, player = "Alloha")
            .copy(dubbing = "AniDUB")
        var notice: Pair<VideoVariant, VideoVariant>? = null
        val harness = harness(
            initialState = YummyDroidUiState(
                videos = LoadState.Ready(listOf(fallbackVoice)),
            ),
            onVoiceFallbackNotice = { previous, fallback -> notice = previous to fallback },
        )

        harness.coordinator.play(
            request(
                video = fallbackVoice,
                voiceFallbackFromVideo = previousVoice,
            ),
        )

        assertEquals(previousVoice to fallbackVoice, notice)
        harness.close()
    }

    @Test
    fun newerSessionCancelsOldResolutionAndKeepsItsRouteAndStream() = runBlocking {
        val first = video(id = 1, animeId = 10, player = "CVH")
        val second = video(id = 2, animeId = 20, player = "Kodik")
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val harness = harness(
            initialState = YummyDroidUiState(videos = LoadState.Ready(listOf(first, second))),
            resolveBestPlayback = { candidates, _, _, _ ->
                val selected = candidates.single()
                if (selected == first) {
                    firstStarted.complete(Unit)
                    releaseFirst.await()
                }
                ResolvedPlayback(selected, stream("https://stream.test/${selected.id}.m3u8"))
            },
        )

        harness.coordinator.play(request(video = first))
        firstStarted.await()
        harness.coordinator.play(request(video = second))
        releaseFirst.complete(Unit)
        yield()

        assertEquals(second, assertIs<AppRoute.Player>(harness.state.route).video)
        assertEquals("https://stream.test/2.m3u8", harness.state.playerStream.readyDataOrNull()?.url)
        harness.close()
    }

    @Test
    fun newerSessionCancelsOldMetadataAndKeepsItsEnrichedStream() = runBlocking {
        val first = video(id = 1, animeId = 10, player = "CVH")
        val second = video(id = 2, animeId = 20, player = "Kodik")
        val firstMetadataStarted = CompletableDeferred<Unit>()
        val releaseFirstMetadata = CompletableDeferred<Unit>()
        val harness = harness(
            initialState = YummyDroidUiState(videos = LoadState.Ready(listOf(first, second))),
            resolveBestPlayback = { candidates, _, _, _ ->
                val selected = candidates.single()
                ResolvedPlayback(selected, stream("https://stream.test/${selected.id}.m3u8"))
            },
            resolvePlaybackMetadata = { playback, _, _ ->
                if (playback.video == first) {
                    firstMetadataStarted.complete(Unit)
                    releaseFirstMetadata.await()
                }
                playback.copy(stream = playback.stream.copy(maxVideoHeight = playback.video.id.toInt() * 360))
            },
        )

        harness.coordinator.play(request(video = first))
        firstMetadataStarted.await()
        harness.coordinator.play(request(video = second))
        releaseFirstMetadata.complete(Unit)
        yield()

        assertEquals(second, assertIs<AppRoute.Player>(harness.state.route).video)
        assertEquals("https://stream.test/2.m3u8", harness.state.playerStream.readyDataOrNull()?.url)
        assertEquals(720, harness.state.playerStream.readyDataOrNull()?.maxVideoHeight)
        assertFalse(harness.state.playbackMetadataLoading)
        harness.close()
    }

    @Test
    fun allProvidersRefreshOnceWithoutLosingManualSelectionPausedStatePositionOrQuality() {
        listOf("CVH", "Alloha", "Kodik", "Aksor", "Sibnet").forEach { provider ->
            val selected = video(1, 10, provider)
            val calls = mutableListOf<VideoVariant>()
            val harness = harness(
                YummyDroidUiState(videos = LoadState.Ready(listOf(selected, video(2, 10, "Other")))),
                resolveBestPlayback = { candidates, quality, _, _ ->
                    assertEquals(PreferredQuality.P720, quality)
                    calls += candidates.single()
                    ResolvedPlayback(candidates.single(), stream("https://stream.test/${calls.size}.m3u8"))
                },
            )
            try {
                harness.coordinator.rememberManualSource(selected)
                harness.coordinator.play(request(selected, preferredQuality = PreferredQuality.P720, playWhenReady = false))
                assertEquals(PlaybackFailureOutcome.Recovering, harness.coordinator.handlePlaybackFailure(
                    selected, 615_000L, PlaybackFailure(PlaybackFailureKind.SourceUnavailable), "Unavailable"))
                assertEquals(listOf(selected, selected), calls)
                val route = assertIs<AppRoute.Player>(harness.state.route)
                assertFalse(route.playWhenReady)
                assertEquals(615_000L, route.startPositionMs)
                assertEquals(PreferredQuality.P720, route.preferredQuality)
                harness.coordinator.confirm(selected, selected)
                assertEquals(PlaybackFailureOutcome.Failed, harness.coordinator.handlePlaybackFailure(
                    selected, 616_000L, PlaybackFailure(PlaybackFailureKind.SourceUnavailable), "Unavailable"))
                assertEquals(2, calls.size)
            } finally { harness.close() }
        }
    }

    @Test
    fun rateLimitsAndLongServerDeadlinesDoNotResolveEvenOnManualRetry() {
        listOf(429, 503).forEach { status ->
            val selected = video(1, 10, "Alloha")
            var calls = 0
            val harness = harness(YummyDroidUiState(videos = LoadState.Ready(listOf(selected))),
                resolveBestPlayback = { candidates, _, _, _ ->
                    calls++
                    ResolvedPlayback(candidates.single(), stream("https://stream.test/current.m3u8"))
                })
            try {
                harness.coordinator.play(request(selected))
                assertEquals(PlaybackFailureOutcome.Failed, harness.coordinator.handlePlaybackFailure(
                    selected, 5_000L, PlaybackFailure(PlaybackFailureKind.SourceUnavailable,
                        httpStatusCode = status, retryAtEpochMs = 120_000L), "Busy"))
                harness.coordinator.play(request(selected))
                assertEquals(1, calls)
                assertIs<LoadState.Error>(harness.state.playerStream)
            } finally { harness.close() }
        }
    }

    @Test
    fun newSelectionCancelsDelayedRefresh() = runBlocking {
        val selected = video(1, 10, "Alloha")
        val next = video(2, 20, "Kodik")
        val delayStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = mutableListOf<VideoVariant>()
        val harness = harness(YummyDroidUiState(videos = LoadState.Ready(listOf(selected, next))),
            resolveBestPlayback = { candidates, _, _, _ ->
                calls += candidates.single()
                ResolvedPlayback(candidates.single(), stream("https://stream.test/${calls.size}.m3u8"))
            }, recoveryDelay = { milliseconds ->
                assertEquals(2_000L, milliseconds)
                delayStarted.complete(Unit)
                release.await()
            })
        try {
            harness.coordinator.play(request(selected))
            harness.coordinator.handlePlaybackFailure(selected, 5000L,
                PlaybackFailure(PlaybackFailureKind.BufferingTimeout), "Timeout")
            delayStarted.await()
            harness.coordinator.play(request(next))
            release.complete(Unit)
            yield()
            assertEquals(listOf(selected, next), calls)
            assertEquals(next, assertIs<AppRoute.Player>(harness.state.route).video)
        } finally { harness.close() }
    }

    @Test
    fun failedRefreshContinuesExistingAutomaticFallback() {
        val selected = video(1, 10, "CVH")
        val other = video(2, 10, "Kodik")
        val calls = mutableListOf<VideoVariant>()
        val harness = harness(YummyDroidUiState(videos = LoadState.Ready(listOf(selected, other))),
            resolveBestPlayback = { candidates, _, _, _ ->
                val candidate = candidates.first()
                calls += candidate
                if (candidate == selected && calls.size > 1) error("Expired source")
                ResolvedPlayback(candidate, stream("https://stream.test/${candidate.id}.m3u8"))
            })
        try {
            harness.coordinator.play(request(selected, playWhenReady = false))
            harness.coordinator.handlePlaybackFailure(selected, 5_000L,
                PlaybackFailure(PlaybackFailureKind.SourceUnavailable), "Unavailable")
            assertEquals(listOf(selected, selected, other), calls)
            val route = assertIs<AppRoute.Player>(harness.state.route)
            assertEquals(other, route.video)
            assertEquals(5_000L, route.startPositionMs)
            assertFalse(route.playWhenReady)
        } finally { harness.close() }
    }

    @Test
    fun shortServiceCooldownWaitsUntilServerDeadlineBeforeResolving() {
        val selected = video(1, 10, "Sibnet")
        var now = 1_000L
        val requestTimes = mutableListOf<Long>()
        val waits = mutableListOf<Long>()
        val harness = harness(YummyDroidUiState(videos = LoadState.Ready(listOf(selected))),
            resolveBestPlayback = { candidates, _, _, _ ->
                requestTimes += now
                ResolvedPlayback(candidates.single(), stream("https://stream.test/current.m3u8"))
            }, recoveryClockMs = { now }, recoveryDelay = { milliseconds ->
                waits += milliseconds
                now += milliseconds
            })
        try {
            harness.coordinator.play(request(selected))
            harness.coordinator.handlePlaybackFailure(selected, 5_000L,
                PlaybackFailure(PlaybackFailureKind.SourceUnavailable,
                    httpStatusCode = 503, retryAtEpochMs = 12_000L), "Busy")
            assertEquals(listOf(11_000L), waits)
            assertEquals(listOf(1_000L, 12_000L), requestTimes)
            assertIs<LoadState.Ready<ResolvedVideoStream>>(harness.state.playerStream)
        } finally { harness.close() }
    }

    @Test
    fun resolverRateLimitPersistsAcrossExplicitRetryWithoutMoreNetwork() {
        val selected = video(1, 10, "Alloha")
        var calls = 0
        val harness = harness(YummyDroidUiState(videos = LoadState.Ready(listOf(selected))),
            resolveBestPlayback = { _, _, _, _ ->
                calls++
                throw me.yummydroid.app.data.PlaybackHttpException(429, 120_000L)
            })
        try {
            harness.coordinator.play(request(selected))
            assertIs<LoadState.Error>(harness.state.playerStream)
            harness.coordinator.resetRuntime(clearSourceCache = false)
            harness.coordinator.play(request(selected))
            assertIs<LoadState.Error>(harness.state.playerStream)
            assertEquals(1, calls)
        } finally { harness.close() }
    }

    @Test
    fun livePauseSnapshotOverridesInitiallyPlayingRouteAcrossRefreshAndTerminalFailure() {
        val selected = video(1, 10, "Alloha")
        val harness = harness(YummyDroidUiState(videos = LoadState.Ready(listOf(selected))))
        try {
            harness.coordinator.play(request(selected, startPositionMs = 50_000L, playWhenReady = true))
            val generation = harness.state.playerStream.readyDataOrNull()!!.playbackGeneration
            harness.coordinator.handlePlaybackFailure(selected, 0L,
                PlaybackFailure(PlaybackFailureKind.SourceUnavailable, playWhenReady = false), "Unavailable")
            assertFalse(assertIs<AppRoute.Player>(harness.state.route).playWhenReady)
            assertEquals(0L, assertIs<AppRoute.Player>(harness.state.route).startPositionMs)
            assertTrue(harness.state.playerStream.readyDataOrNull()!!.playbackGeneration > generation)
            harness.coordinator.handlePlaybackFailure(selected, 6000L,
                PlaybackFailure(PlaybackFailureKind.SourceUnavailable, playWhenReady = false), "Unavailable")
            assertIs<LoadState.Error>(harness.state.playerStream)
            val route = assertIs<AppRoute.Player>(harness.state.route)
            assertFalse(route.playWhenReady)
            harness.coordinator.play(request(selected, startPositionMs = route.startPositionMs,
                playWhenReady = route.playWhenReady))
            assertFalse(assertIs<AppRoute.Player>(harness.state.route).playWhenReady)
        } finally { harness.close() }
    }

    @Test
    fun differentRequestedProviderCannotResolveOrEnrichWithCooledProvider() {
        val cooled = video(1, 10, "Alloha")
        val available = video(2, 10, "CVH")
        val calls = mutableListOf<VideoVariant>()
        val metadataCalls = mutableListOf<List<VideoVariant>>()
        var failAvailable = false
        val harness = harness(YummyDroidUiState(videos = LoadState.Ready(listOf(cooled, available))),
            resolveBestPlayback = { candidates, _, _, _ ->
                val candidate = candidates.single()
                calls += candidate
                if (failAvailable) error("CVH unavailable")
                ResolvedPlayback(candidate, stream("https://stream.test/current.m3u8"))
            }, resolvePlaybackMetadata = { playback, candidates, _ ->
                metadataCalls += candidates
                playback
            })
        try {
            harness.coordinator.play(request(cooled, lockPlaybackSource = true))
            harness.coordinator.handlePlaybackFailure(cooled, 5000L, PlaybackFailure(
                PlaybackFailureKind.SourceUnavailable, httpStatusCode = 429, retryAtEpochMs = 120_000L), "Busy")
            calls.clear()
            metadataCalls.clear()
            harness.coordinator.play(request(available))
            assertEquals(listOf(available), calls)
            assertTrue(metadataCalls.isNotEmpty())
            assertTrue(metadataCalls.flatten().none { it == cooled })
            calls.clear()
            failAvailable = true
            harness.coordinator.play(request(available))
            assertEquals(listOf(available), calls)
            assertIs<LoadState.Error>(harness.state.playerStream)
        } finally { harness.close() }
    }

    @Test
    fun navigatingAwayWhileRefreshWaitsDoesNotResolveOrRestorePlayer() = runBlocking {
        val selected = video(1, 10, "Alloha")
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val harness = harness(YummyDroidUiState(videos = LoadState.Ready(listOf(selected))),
            resolveBestPlayback = { candidates, _, _, _ ->
                calls++
                ResolvedPlayback(candidates.single(), stream("https://stream.test/current.m3u8"))
            }, recoveryDelay = { release.await() })
        try {
            harness.coordinator.play(request(selected))
            harness.coordinator.handlePlaybackFailure(selected, 5000L,
                PlaybackFailure(PlaybackFailureKind.SourceUnavailable), "Unavailable")
            harness.update { it.copy(route = AppRoute.Details(10)) }
            release.complete(Unit)
            yield()
            assertEquals(1, calls)
            assertEquals(AppRoute.Details(10), harness.state.route)
        } finally { harness.close() }
    }

    @Test
    fun pendingSeekSurvivesRefreshFailureAndAutomaticFallback() = runBlocking {
        val selected = video(1, 10, "CVH")
        val fallback = video(2, 10, "Kodik")
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val harness = harness(YummyDroidUiState(videos = LoadState.Ready(listOf(selected, fallback))),
            resolveBestPlayback = { candidates, _, _, _ ->
                val candidate = candidates.single()
                calls++
                if (calls > 1 && candidate == selected) error("Expired")
                ResolvedPlayback(candidate, stream("https://stream.test/current.m3u8"))
            }, recoveryDelay = { release.await() })
        try {
            harness.coordinator.play(request(selected))
            harness.coordinator.handlePlaybackFailure(selected, 5000L,
                PlaybackFailure(PlaybackFailureKind.SourceUnavailable), "Unavailable")
            harness.coordinator.updatePendingRecoveryPosition(selected, 123_000L)
            harness.coordinator.updatePendingRecoveryIntent(selected, false)
            release.complete(Unit)
            yield()
            val route = assertIs<AppRoute.Player>(harness.state.route)
            assertEquals(fallback, route.video)
            assertEquals(123_000L, route.startPositionMs)
            assertFalse(route.playWhenReady)
        } finally { harness.close() }
    }

    @Test
    fun navigatingAwayDuringRefreshResolutionCannotTriggerFallbackNavigation() = runBlocking {
        val selected = video(1, 10, "CVH")
        val fallback = video(2, 10, "Kodik")
        val release = CompletableDeferred<Unit>()
        val calls = mutableListOf<VideoVariant>()
        val harness = harness(YummyDroidUiState(videos = LoadState.Ready(listOf(selected, fallback))),
            resolveBestPlayback = { candidates, _, _, _ ->
                val candidate = candidates.single()
                calls += candidate
                if (calls.size > 1) {
                    release.await()
                    error("Expired")
                }
                ResolvedPlayback(candidate, stream("https://stream.test/current.m3u8"))
            })
        try {
            harness.coordinator.play(request(selected))
            harness.coordinator.handlePlaybackFailure(selected, 5000L,
                PlaybackFailure(PlaybackFailureKind.SourceUnavailable), "Unavailable")
            harness.update { it.copy(route = AppRoute.Details(10)) }
            release.complete(Unit)
            yield()
            assertEquals(listOf(selected, selected), calls)
            assertEquals(AppRoute.Details(10), harness.state.route)
        } finally { harness.close() }
    }

    @Test
    fun metadataEnrichmentCannotReplaceRuntimeLoadIdentity() {
        val selected = video(1, 10, "Alloha")
        val runtimeStream = stream("https://stream.test/current.m3u8").copy(
            mimeType = "application/x-mpegURL", headers = mapOf("Referer" to "https://origin.test"))
        val harness = harness(YummyDroidUiState(videos = LoadState.Ready(listOf(selected))),
            resolveBestPlayback = { _, _, _, _ -> ResolvedPlayback(selected, runtimeStream) },
            resolvePlaybackMetadata = { playback, _, _ ->
                playback.copy(stream = playback.stream.copy(url = "https://stale.test/old.mp4",
                    mimeType = "video/mp4", headers = emptyMap(), playbackGeneration = 999L, maxVideoHeight = 1080))
            })
        try {
            harness.coordinator.play(request(selected))
            val actual = assertIs<LoadState.Ready<ResolvedVideoStream>>(harness.state.playerStream).data
            assertEquals(runtimeStream.url, actual.url)
            assertEquals(runtimeStream.mimeType, actual.mimeType)
            assertEquals(runtimeStream.headers, actual.headers)
            assertEquals(1L, actual.playbackGeneration)
            assertEquals(1080, actual.maxVideoHeight)
        } finally { harness.close() }
    }

    @Test
    fun pauseAndSeekDuringDelayedRecoverySurviveFreshResolution() = runBlocking {
        val selected = video(1, 10, "Alloha")
        val release = CompletableDeferred<Unit>()
        val harness = harness(YummyDroidUiState(videos = LoadState.Ready(listOf(selected))),
            recoveryDelay = { release.await() })
        try {
            harness.coordinator.play(request(selected, playWhenReady = true))
            harness.coordinator.handlePlaybackFailure(selected, 5000L,
                PlaybackFailure(PlaybackFailureKind.SourceUnavailable, playWhenReady = true), "Unavailable")
            harness.coordinator.updatePendingRecoveryPosition(selected, 123_000L)
            harness.coordinator.updatePendingRecoveryIntent(selected, false)
            assertEquals(123_000L, assertIs<AppRoute.Player>(harness.state.route).startPositionMs)
            release.complete(Unit)
            yield()
            val route = assertIs<AppRoute.Player>(harness.state.route)
            assertEquals(selected, route.video)
            assertEquals(123_000L, route.startPositionMs)
            assertFalse(route.playWhenReady)
            assertIs<LoadState.Ready<ResolvedVideoStream>>(harness.state.playerStream)
            // Once recovery ends, retained callbacks cannot mutate the active route.
            harness.coordinator.updatePendingRecoveryIntent(selected, true)
            assertFalse(assertIs<AppRoute.Player>(harness.state.route).playWhenReady)
        } finally { harness.close() }
    }

    private fun harness(
        initialState: YummyDroidUiState,
        resolveLocalStream: suspend (VideoVariant) -> ResolvedVideoStream = { stream(it.localPlaybackUrl) },
        resolveBestPlayback: suspend (
            List<VideoVariant>,
            PreferredQuality,
            List<VideoVariant>,
            Boolean,
        ) -> ResolvedPlayback = { candidates, _, _, _ ->
            ResolvedPlayback(candidates.first(), stream("https://stream.test/master.m3u8"))
        },
        resolvePlaybackMetadata: suspend (
            ResolvedPlayback,
            List<VideoVariant>,
            PreferredQuality,
        ) -> ResolvedPlayback = { playback, _, _ -> playback },
        cachedSiteBaseUrl: () -> String = { "https://site.test" },
        offlineUnavailableMessage: () -> String = { "Unavailable offline" },
        sourceResolveTimeoutMessage: () -> String = { "Source did not respond" },
        onVoiceFallbackNotice: (VideoVariant, VideoVariant) -> Unit = { _, _ -> Unit },
        sourceResolveTimeoutMs: Long = 30_000L,
        recoveryDelay: suspend (Long) -> Unit = {},
        recoveryClockMs: () -> Long = { 1_000L },
    ): Harness {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val harness = Harness(scope = scope, initialState = initialState)
        val sourceCoordinator = PlaybackSourceCoordinator(
            resolveLocalStream = resolveLocalStream,
            resolveBestPlayback = resolveBestPlayback,
            couldNotSelectSourceMessage = { "Could not select source" },
            noFallbackAfterManualMessage = { "No fallback source" },
        )
        harness.coordinator = PlaybackSessionCoordinator(
            scope = scope,
            sourceCoordinator = sourceCoordinator,
            currentState = { harness.state },
            updateState = harness::update,
            resolvePlaybackMetadata = resolvePlaybackMetadata,
            cachedSiteBaseUrl = cachedSiteBaseUrl,
            offlineUnavailableMessage = offlineUnavailableMessage,
            sourceResolveTimeoutMessage = sourceResolveTimeoutMessage,
            onFallbackNotice = { _, _ -> Unit },
            onVoiceFallbackNotice = onVoiceFallbackNotice,
            onMetadataFailure = { throw AssertionError("Unexpected metadata failure", it) },
            sourceResolveTimeoutMs = sourceResolveTimeoutMs,
            recoveryDelay = recoveryDelay,
            recoveryClockMs = recoveryClockMs,
        )
        return harness
    }

    private fun request(
        video: VideoVariant,
        startPositionMs: Long = 0L,
        resumeChoicePositionMs: Long? = null,
        preferredQuality: PreferredQuality = PreferredQuality.Auto,
        voiceFallbackFromVideo: VideoVariant? = null,
        lockPlaybackSource: Boolean = false,
        playWhenReady: Boolean = true,
    ): PlaybackSessionRequest {
        return PlaybackSessionRequest(
            video = video,
            title = "Anime ${video.animeId}",
            excludedSourceKeys = emptySet(),
            startPositionMs = startPositionMs,
            preferredQuality = preferredQuality,
            resumeChoicePositionMs = resumeChoicePositionMs,
            voiceFallbackFromVideo = voiceFallbackFromVideo,
            lockPlaybackSource = lockPlaybackSource,
            playWhenReady = playWhenReady,
        )
    }

    private fun video(id: Long, animeId: Long, player: String): VideoVariant {
        return VideoVariant(
            id = id,
            animeId = animeId,
            player = player,
            dubbing = "AniLibria",
            episode = "1",
            url = "https://${player.lowercase()}.test/$id",
            index = 1,
            durationSeconds = 1_400,
            views = 0,
        )
    }

    private class Harness(
        private val scope: CoroutineScope,
        initialState: YummyDroidUiState,
    ) {
        var state: YummyDroidUiState = initialState
        val states = mutableListOf<YummyDroidUiState>()
        lateinit var coordinator: PlaybackSessionCoordinator

        fun update(transform: (YummyDroidUiState) -> YummyDroidUiState) {
            val updated = transform(state)
            if (updated != state) states += updated
            state = updated
        }

        fun close() {
            scope.cancel()
        }

        suspend fun awaitPlayerStreamSettled() {
            withTimeout(1_000L) {
                while (state.playerStream is LoadState.Loading) yield()
            }
        }
    }

    private companion object {
        fun stream(url: String): ResolvedVideoStream {
            return ResolvedVideoStream(url = url, mimeType = null, headers = emptyMap())
        }
    }
}
