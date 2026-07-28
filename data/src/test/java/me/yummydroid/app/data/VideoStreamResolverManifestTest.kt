package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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

    private fun inspectMetadataBody(
        url: String,
        body: String,
        sourceUrl: String,
    ): Any {
        val method = VideoStreamResolver::class.java.getDeclaredMethod(
            "inspectPlayerMetadataBody",
            String::class.java,
            String::class.java,
            Map::class.java,
            String::class.java,
            String::class.java,
        )
        method.isAccessible = true
        return method.invoke(
            VideoStreamResolver(),
            url,
            body,
            emptyMap<String, String>(),
            sourceUrl,
            "https://ru.yummyani.me",
        )
    }

    private fun Any.fieldValue(name: String): Any? {
        val field = javaClass.getDeclaredField(name)
        field.isAccessible = true
        return field.get(this)
    }
}
