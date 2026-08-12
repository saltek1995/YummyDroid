package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoStreamUrlParsingTest {
    @Test
    fun playerMetadataDetectionRecognizesSupportedMarkers() {
        listOf(
            "#EXTM3U\n#EXT-X-VERSION:3",
            "{\"file\":\"video.M3U8\"}",
            "{\"file\":\"video.mp4\"}",
            "{\"file\":\"video.mpd\"}",
            "{\"subtitle\":\"track.vtt\"}",
            "{\"captions\":[]}",
            "{\"textTracks\":[]}",
        ).forEach { body ->
            assertTrue(body.looksLikePlayerMetadataBody(), body)
        }
    }

    @Test
    fun playerMetadataDetectionIgnoresUnrelatedOrLateMarkers() {
        assertFalse("{\"status\":\"ok\"}".looksLikePlayerMetadataBody())
        assertFalse(("x".repeat(8192) + ".m3u8").looksLikePlayerMetadataBody())
    }

    @Test
    fun providerIframeDetectionUsesHostAndPathBoundaries() {
        assertTrue("https://kodikplayer.com/video/14".isKodikIframeUrl())
        assertTrue("https://edge.kodikplayer.com/video/14".isKodikIframeUrl())
        assertFalse("https://example.test/kodikplayer.com/video/14".isKodikIframeUrl())

        assertTrue("https://player.aksor.tv/video/14".isAksorIframeUrl())
        assertFalse("https://player.aksor.tv/embed/14".isAksorIframeUrl())

        assertTrue("https://video.sibnet.ru/shell.php?videoid=14".isSibnetIframeUrl())
        assertFalse("https://video.sibnet.ru/video/14".isSibnetIframeUrl())
    }

    @Test
    fun runtimeDiscoveryIsLimitedToAllohaHosts() {
        assertTrue("https://alloha.yani.tv/embed/14".requiresRuntimePlayerDiscovery())
        assertTrue("https://player.allohastream.example/embed/14".requiresRuntimePlayerDiscovery())
        assertFalse("https://cdn.example.test/alloha/video.m3u8".requiresRuntimePlayerDiscovery())
    }

    @Test
    fun siteHostRewriteKeepsEncodedPathQueryAndFragment() {
        assertEquals(
            "https://ru.yummyani.me/anime/title%2Fpart?episode=14#player",
            "https://old.yummyani.me/anime/title%2Fpart?episode=14#player"
                .rewriteKnownSiteHost("https://ru.yummyani.me/"),
        )
    }
}
