package me.yummydroid.app.data

import java.io.IOException
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebViewStreamResolverTest {
    @Test
    fun sessionReadinessChecksDoNotRestartOptionalDiscovery() {
        val window = WebViewDiscoveryWindow()
        assertFalse(window.isElapsed(10_000L)) // No stream discovered yet.
        assertEquals(1_200L, window.onMetadata(100L, true, true, true))
        for (now in listOf(200L, 800L, 1_299L)) assertFalse(window.isElapsed(now))
        assertTrue(window.isElapsed(1_300L))
        // Credentials arriving late can finish immediately, without another idle window.
        assertTrue(window.isElapsed(3_000L))
    }

    @Test
    fun newSubtitleMetadataStillGetsIdleTimeWithinOriginalGrace() {
        val window = WebViewDiscoveryWindow()
        assertEquals(4_000L, window.onMetadata(100L, true, false, true))
        assertEquals(1_200L, window.onMetadata(500L, true, true, true))
        assertFalse(window.isElapsed(1_699L))
        assertTrue(window.isElapsed(1_700L))
        assertEquals(200L, window.onMetadata(3_900L, true, true, true))
        assertTrue(window.isElapsed(4_100L))
    }

    @Test
    fun subtitleHintsDoNotClassifyVideoAsOptional() {
        val source = "https://alloha.example/player"
        val media = "https://alloha.example/tracks/video.m3u8?track=1"
        val subtitles = "https://alloha.example/video/subtitles.m3u8"
        assertFalse(isKnownOptionalSubtitleRequest(media, source, emptySet(), emptySet()))
        assertTrue(isKnownOptionalSubtitleRequest(subtitles, source, setOf(subtitles), emptySet()))
        assertFalse(isKnownOptionalSubtitleRequest(media, source, setOf(media), setOf(media)))
        assertFalse(isKnownOptionalSubtitleRequest(source, source, setOf(source), emptySet()))
        assertTrue(isKnownOptionalSubtitleRequest("https://cdn.example/sub.vtt", source, emptySet(), emptySet()))
    }

    @Test
    fun terminatingCaptureClosesItsBlockingHttpInterception() = runBlocking {
        ServerSocket(0).use { server ->
            val ready = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val serverJob = launch(Dispatchers.IO) {
                server.accept().use { socket ->
                    val input = socket.getInputStream().bufferedReader()
                    while (!input.readLine().isNullOrEmpty()) Unit
                    ready.complete(Unit)
                    release.await()
                }
            }
            val client = OkHttpClient.Builder().readTimeout(10, TimeUnit.SECONDS).build()
            val termination = WebViewSessionTermination()
            val callback = launch(Dispatchers.IO) {
                assertFailsWith<CancellationException> {
                    termination.runRequest {
                        client.withCancellableResponse(Request.Builder().url("http://127.0.0.1:${server.localPort}/").build()) { it.code }
                    }
                }
            }
            try {
                withTimeout(3_000) {
                    ready.await()
                    assertTrue(termination.tryTerminate())
                    callback.join()
                }
                assertFailsWith<CancellationException> { termination.runRequest { error("Late request admitted") } }
            } finally {
                termination.tryTerminate()
                release.complete(Unit)
                callback.cancelAndJoin()
                serverJob.join()
                client.connectionPool.evictAll()
            }
        }
        Unit
    }

    @Test
    fun sessionAllowsExactlyOneTerminalTransition() {
        val termination = WebViewSessionTermination()

        assertFalse(termination.isTerminated)
        assertTrue(termination.tryTerminate())
        assertTrue(termination.isTerminated)
        assertFalse(termination.tryTerminate())
    }

    @Test
    fun playbackOnlyResolutionUsesShortIdleWindow() {
        assertEquals(
            250L,
            webViewDiscoveryIdleMs(
                waitForRuntimeSubtitles = false,
                hasCapturedSubtitles = false,
                isAllohaIframe = true,
            ),
        )
    }

    @Test
    fun runtimeProviderWaitsForLateSubtitleDiscovery() {
        assertEquals(
            4_000L,
            webViewDiscoveryIdleMs(
                waitForRuntimeSubtitles = true,
                hasCapturedSubtitles = false,
                isAllohaIframe = true,
            ),
        )
    }

    @Test
    fun capturedOrStaticSubtitlesUseNormalIdleWindow() {
        assertEquals(
            1_200L,
            webViewDiscoveryIdleMs(
                waitForRuntimeSubtitles = true,
                hasCapturedSubtitles = true,
                isAllohaIframe = true,
            ),
        )
        assertEquals(
            1_200L,
            webViewDiscoveryIdleMs(
                waitForRuntimeSubtitles = true,
                hasCapturedSubtitles = false,
                isAllohaIframe = false,
            ),
        )
    }

    @Test
    fun sessionUpdatesCannotKeepExtendingAllohaSubtitleDiscovery() {
        for (elapsed in listOf(0L, 1_000L, 3_900L, 4_000L, 12_000L, Long.MAX_VALUE)) {
            val remaining = (4_000L - elapsed.coerceAtMost(4_000L)).coerceAtLeast(0L)
            assertEquals(remaining, webViewDiscoveryIdleMs(true, false, true, elapsed))
            assertEquals(minOf(1_200L, remaining), webViewDiscoveryIdleMs(true, true, true, elapsed))
        }
        // A late session descriptor is checked immediately, rather than waiting four more seconds.
        assertEquals(0L, webViewDiscoveryIdleMs(true, false, true, 8_000L))
        assertEquals(250L, webViewDiscoveryIdleMs(false, false, true, 8_000L))
        assertEquals(1_200L, webViewDiscoveryIdleMs(true, false, false, 8_000L))
    }

    @Test
    fun documentStartScriptUsesExactRuntimePlayerOrigin() {
        assertEquals(
            "https://player.allohastream.example:8443",
            runtimeDocumentStartOriginRule("https://player.allohastream.example:8443/embed/14?episode=2"),
        )
        assertFailsWith<IOException> {
            runtimeDocumentStartOriginRule("not a URL")
        }
    }

    @Test
    fun documentStartScriptCapturesFullPlayerState() {
        assertTrue("currentSource" in STREAM_PLAYER_DISCOVERY_BRIDGE_SCRIPT)
        assertTrue("player && player.source" in STREAM_PLAYER_DISCOVERY_BRIDGE_SCRIPT)
        assertTrue("getSources" in STREAM_PLAYER_DISCOVERY_BRIDGE_SCRIPT)
        assertTrue("getQualityOptions" in STREAM_PLAYER_DISCOVERY_BRIDGE_SCRIPT)
        assertTrue("function callPlayerGetter" in STREAM_PLAYER_DISCOVERY_BRIDGE_SCRIPT)
        assertFalse("player.hls.url" in STREAM_PLAYER_DISCOVERY_BRIDGE_SCRIPT)
        assertTrue("textTracks" in STREAM_PLAYER_DISCOVERY_BRIDGE_SCRIPT)
        assertTrue("captions" in STREAM_PLAYER_DISCOVERY_BRIDGE_SCRIPT)
        assertTrue("lastCapturedBody" in STREAM_PLAYER_DISCOVERY_BRIDGE_SCRIPT)
    }

    @Test
    fun allohaPreferredQualityScriptRequestsPlayerQuality() {
        val script = allohaPreferredQualityScript(720)

        assertTrue("window.__yummyPreferredQualityHeight = 720" in script)
        assertTrue("player.setQuality(720)" in script)
        assertTrue("player.quality = 720" in script)
        assertTrue("if (applyPreferredQuality())" in script)
    }

    @Test
    fun capturedPlaybackMergeKeepsRuntimeQualitiesAndSkipProbeFlag() {
        val runtimePlayback = CapturedPlayback(
            url = "https://cdn.example.test/480/master.m3u8",
            mimeType = "application/x-mpegURL",
            headers = mapOf("Referer" to "https://alloha.yani.tv/"),
            maxVideoHeight = 1080,
            availableQualities = listOf(
                SourceQuality(height = 360),
                SourceQuality(height = 480),
                SourceQuality(height = 720),
                SourceQuality(height = 1080),
            ),
            selectedVideoHeight = 480,
            fallbackUrls = listOf("https://cdn.example.test/720/master.m3u8"),
            fallbackUrlHeights = mapOf("https://cdn.example.test/720/master.m3u8" to 720),
            skipPlaybackProbe = true,
        )
        val networkPlayback = CapturedPlayback(
            url = "https://cdn.example.test/480/master.m3u8",
            mimeType = "application/x-mpegURL",
            headers = mapOf("Origin" to "https://alloha.yani.tv"),
            maxVideoHeight = 480,
            skipPlaybackProbe = false,
        )

        val merged = runtimePlayback.mergeWith(networkPlayback)

        assertEquals(listOf(1080, 720, 480, 360), merged.availableQualities.mapNotNull(SourceQuality::height))
        assertEquals(480, merged.selectedVideoHeight)
        assertEquals(1080, merged.maxVideoHeight)
        assertEquals(listOf("https://cdn.example.test/720/master.m3u8"), merged.fallbackUrls)
        assertEquals(mapOf("https://cdn.example.test/720/master.m3u8" to 720), merged.fallbackUrlHeights)
        assertTrue(merged.skipPlaybackProbe)
    }

    @Test
    fun runtimePlaybackIsNotReplacedByLaterRawNetworkRequest() {
        val runtimePlayback = CapturedPlayback(
            url = "https://cdn.example.test/1080/master.m3u8",
            mimeType = "application/x-mpegURL",
            headers = mapOf("Referer" to "https://alloha.yani.tv/"),
            maxVideoHeight = 1080,
            selectedVideoHeight = 1080,
            skipPlaybackProbe = true,
        )
        val rawNetworkPlayback = CapturedPlayback(
            url = "https://cdn.example.test/protected/master.m3u8",
            mimeType = "application/x-mpegURL",
            headers = mapOf("Origin" to "https://alloha.yani.tv"),
            maxVideoHeight = null,
            skipPlaybackProbe = false,
        )

        val merged = runtimePlayback.mergeWith(rawNetworkPlayback)

        assertEquals(runtimePlayback.url, merged.url)
        assertEquals(1080, merged.selectedVideoHeight)
        assertTrue(merged.skipPlaybackProbe)
    }

    @Test
    fun allohaFallbackHeadersPromoteActualRequestedUrl() {
        val selectedUrl = "https://cdn.example.test/1080/master.m3u8"
        val fallbackUrl = "https://cdn.example.test/480/master.m3u8"
        val playback = CapturedPlayback(
            url = selectedUrl,
            mimeType = "application/x-mpegURL",
            headers = emptyMap(),
            maxVideoHeight = 1080,
            selectedVideoHeight = 1080,
            fallbackUrls = listOf(fallbackUrl),
            fallbackUrlHeights = mapOf(fallbackUrl to 480),
            skipPlaybackProbe = true,
        )
        val fallbackHeaders = mapOf("Authorization" to "captured", "Referer" to "https://alloha.yani.tv/")

        val updated = playback.withHeadersFor(
            playbackUrl = fallbackUrl,
            playbackHeaders = fallbackHeaders,
        )

        assertEquals(fallbackUrl, updated.url)
        assertEquals(fallbackHeaders, updated.headers)
        assertEquals(listOf(selectedUrl), updated.fallbackUrls)
        assertEquals(480, updated.selectedVideoHeight)
        assertEquals(mapOf(selectedUrl to 1080), updated.fallbackUrlHeights)
    }

    @Test
    fun fallbackHeadersCanStillPromoteFallbackWhenRequested() {
        val selectedUrl = "https://cdn.example.test/1080/master.m3u8"
        val fallbackUrl = "https://cdn.example.test/480/master.m3u8"
        val playback = CapturedPlayback(
            url = selectedUrl,
            mimeType = "application/x-mpegURL",
            headers = emptyMap(),
            maxVideoHeight = 1080,
            fallbackUrls = listOf(fallbackUrl),
        )

        val updated = playback.withHeadersFor(
            playbackUrl = fallbackUrl,
            playbackHeaders = mapOf("Referer" to "https://player.example.test/"),
        )

        assertEquals(fallbackUrl, updated.url)
        assertEquals(listOf(selectedUrl), updated.fallbackUrls)
    }
}
