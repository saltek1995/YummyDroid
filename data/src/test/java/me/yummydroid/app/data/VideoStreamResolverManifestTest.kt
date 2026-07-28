package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VideoStreamResolverManifestTest {
    @Test
    fun extensionlessHlsManifestResponseIsCapturedAsPlayback() {
        val url = "https://alloha.yani.tv/playlist/6a8d259b6bd5b6b609329cf9e0f0c3"
        val body = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-STREAM-INF:BANDWIDTH=2400000,RESOLUTION=1920x1080
            chunklist_1080.m3u8
        """.trimIndent()

        val capture = inspectMetadataBody(
            url = url,
            body = body,
            sourceUrl = "https://alloha.yani.tv/?translation=210&season=1&episode=14",
        )
        val playback = capture.fieldValue("playback")

        assertNotNull(playback)
        assertEquals(url, playback.fieldValue("url"))
        assertEquals("application/x-mpegURL", playback.fieldValue("mimeType"))
        assertEquals(1080, playback.fieldValue("maxVideoHeight"))
    }

    @Test
    fun allohaRuntimeQualityMapHonorsRequestedQuality() {
        val capture = inspectMetadataBody(
            url = "https://alloha.yani.tv/?translation=210&season=1&episode=14",
            body = """
                {
                  "hlsSource": [
                    {
                      "quality": {
                        "360": "39https://cdn.example.test/360/master.m3u8 or https://mirror.example.test/360/master.m3u8",
                        "720": "https://cdn.example.test/720/master.m3u8",
                        "1080": "https://cdn.example.test/1080/master.m3u8"
                      }
                    }
                  ]
                }
            """.trimIndent(),
            sourceUrl = "https://alloha.yani.tv/?translation=210&season=1&episode=14",
            preferredQuality = PreferredQuality.P360,
        )

        val playback = capture.fieldValue("playback")

        assertNotNull(playback)
        assertEquals("https://cdn.example.test/360/master.m3u8", playback.fieldValue("url"))
        assertEquals(
            listOf(
                "https://mirror.example.test/360/master.m3u8",
                "https://cdn.example.test/720/master.m3u8",
                "https://cdn.example.test/1080/master.m3u8",
            ),
            playback.fieldValue("fallbackUrls"),
        )
        assertFalse(playback.fieldValue("skipPlaybackProbe") as Boolean)
    }

    @Test
    fun capturedManifestUrlDropsAllohaNumericProtectionPrefix() {
        val capture = inspectMetadataBody(
            url = "39https://cdn.example.test/360/master.m3u8",
            body = """
                #EXTM3U
                #EXT-X-VERSION:3
                #EXT-X-STREAM-INF:BANDWIDTH=900000,RESOLUTION=640x360
                chunklist.m3u8
            """.trimIndent(),
            sourceUrl = "https://alloha.yani.tv/?translation=210&season=1&episode=14",
        )
        val playback = capture.fieldValue("playback")

        assertNotNull(playback)
        assertEquals("https://cdn.example.test/360/master.m3u8", playback.fieldValue("url"))
        assertEquals("application/x-mpegURL", playback.fieldValue("mimeType"))
    }

    @Test
    fun sourceResolveTimeoutsMatchProviderFlowCost() {
        assertEquals(
            SOURCE_RESOLVE_TIMEOUT_MS,
            timeoutVideo(player = "Kodik", url = "https://kodik.example/player").sourceResolveTimeoutMs(),
        )
        assertEquals(
            CVH_SOURCE_RESOLVE_TIMEOUT_MS,
            timeoutVideo(player = "CVH", url = "https://iframecvh.example/player").sourceResolveTimeoutMs(),
        )
        assertEquals(
            RUNTIME_SOURCE_RESOLVE_TIMEOUT_MS,
            timeoutVideo(player = "Alloha", url = "https://alloha.yani.tv/?translation=210").sourceResolveTimeoutMs(),
        )
        assertTrue(RUNTIME_SOURCE_RESOLVE_TIMEOUT_MS > VideoStreamResolver.WEBVIEW_RESOLVE_TIMEOUT_MS)
    }

    private fun inspectMetadataBody(
        url: String,
        body: String,
        sourceUrl: String,
        preferredQuality: PreferredQuality = PreferredQuality.Auto,
    ): Any {
        val method = VideoStreamResolver::class.java.getDeclaredMethod(
            "inspectPlayerMetadataBody",
            String::class.java,
            String::class.java,
            Map::class.java,
            String::class.java,
            String::class.java,
            PreferredQuality::class.java,
        )
        method.isAccessible = true
        return method.invoke(
            VideoStreamResolver(),
            url,
            body,
            emptyMap<String, String>(),
            sourceUrl,
            "https://ru.yummyani.me",
            preferredQuality,
        ) as Any
    }

    private fun Any.fieldValue(name: String): Any? {
        val field = javaClass.getDeclaredField(name)
        field.isAccessible = true
        return field.get(this)
    }

    private fun timeoutVideo(player: String, url: String): VideoVariant {
        return VideoVariant(
            id = 1,
            animeId = 5500,
            player = player,
            dubbing = "AniLibria",
            episode = "14",
            url = url,
            index = 14,
            durationSeconds = 1_400,
            views = 1,
        )
    }
}
