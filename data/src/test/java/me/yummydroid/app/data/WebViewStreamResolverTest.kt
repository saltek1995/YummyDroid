package me.yummydroid.app.data

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebViewStreamResolverTest {
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
