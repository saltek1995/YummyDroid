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
                requiresRuntimePlayerDiscovery = true,
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
                requiresRuntimePlayerDiscovery = true,
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
                requiresRuntimePlayerDiscovery = true,
            ),
        )
        assertEquals(
            1_200L,
            webViewDiscoveryIdleMs(
                waitForRuntimeSubtitles = true,
                hasCapturedSubtitles = false,
                requiresRuntimePlayerDiscovery = false,
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
        assertTrue("sources" in STREAM_PLAYER_DISCOVERY_BRIDGE_SCRIPT)
        assertTrue("textTracks" in STREAM_PLAYER_DISCOVERY_BRIDGE_SCRIPT)
        assertTrue("captions" in STREAM_PLAYER_DISCOVERY_BRIDGE_SCRIPT)
        assertTrue("lastCapturedBody" in STREAM_PLAYER_DISCOVERY_BRIDGE_SCRIPT)
    }

    @Test
    fun capturedPlaybackMergeKeepsRuntimeQualitiesAndEnablesManifestProbe() {
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
        assertFalse(merged.skipPlaybackProbe)
    }
}
