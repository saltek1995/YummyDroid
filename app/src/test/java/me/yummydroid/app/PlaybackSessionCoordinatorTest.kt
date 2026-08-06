package me.yummydroid.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
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
        assertEquals(enrichedStream, harness.state.playerStream.readyDataOrNull())
        assertFalse(harness.state.playbackMetadataLoading)
        assertEquals(listOf(AppRoute.Details(video.animeId)), harness.state.navigationBackStack.map { it.route })
        assertEquals(listOf("resolve:false", "metadata:1:P1080"), events)

        val playerStates = harness.states.filter { it.route is AppRoute.Player }
        assertTrue(playerStates.first().playerStream is LoadState.Loading)
        assertTrue(playerStates.any { it.playerStream.readyDataOrNull() == initialStream })
        assertTrue(playerStates.any { it.playbackMetadataLoading })
        assertEquals(enrichedStream, playerStates.last().playerStream.readyDataOrNull())
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
        assertTrue(harness.state.playerStream is LoadState.Loading)
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
        fetchVideos: suspend (Long) -> List<VideoVariant> = { emptyList() },
        resolvePlaybackMetadata: suspend (
            ResolvedPlayback,
            List<VideoVariant>,
            PreferredQuality,
        ) -> ResolvedPlayback = { playback, _, _ -> playback },
        cachedSiteBaseUrl: () -> String = { "https://site.test" },
        offlineUnavailableMessage: () -> String = { "Unavailable offline" },
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
            fetchVideos = fetchVideos,
            resolvePlaybackMetadata = resolvePlaybackMetadata,
            cachedSiteBaseUrl = cachedSiteBaseUrl,
            offlineUnavailableMessage = offlineUnavailableMessage,
            onFallbackNotice = { _, _ -> Unit },
            onMetadataFailure = { throw AssertionError("Unexpected metadata failure", it) },
        )
        return harness
    }

    private fun request(
        video: VideoVariant,
        startPositionMs: Long = 0L,
        resumeChoicePositionMs: Long? = null,
        preferredQuality: PreferredQuality = PreferredQuality.Auto,
    ): PlaybackSessionRequest {
        return PlaybackSessionRequest(
            video = video,
            title = "Anime ${video.animeId}",
            excludedSourceKeys = emptySet(),
            startPositionMs = startPositionMs,
            preferredQuality = preferredQuality,
            resumeChoicePositionMs = resumeChoicePositionMs,
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
    }

    private companion object {
        fun stream(url: String): ResolvedVideoStream {
            return ResolvedVideoStream(url = url, mimeType = null, headers = emptyMap())
        }
    }
}
