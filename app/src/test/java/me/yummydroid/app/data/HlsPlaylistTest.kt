package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

class HlsPlaylistTest {
    @Test
    fun selectsPreferredVariantWithoutExceedingWhenPossible() {
        val playlist = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=900000,RESOLUTION=1280x720
            720/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=1800000,RESOLUTION=1920x1080
            ../1080/index.m3u8
        """.trimIndent()

        val selected = playlist.selectBestHlsVariant(
            baseUrl = "https://cdn.example.test/anime/episode/master.m3u8",
            preferredQuality = PreferredQuality.P720,
        )

        assertEquals(720, selected?.height)
        assertEquals("https://cdn.example.test/anime/episode/720/index.m3u8", selected?.url)
    }

    @Test
    fun extractsHlsSourceQualities() {
        val playlist = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=1200000,RESOLUTION=1280x720
            720.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2400000,RESOLUTION=1920x1080
            1080.m3u8
        """.trimIndent()

        assertEquals(
            listOf(SourceQuality(height = 1080, bitrate = 2_400_000), SourceQuality(height = 720, bitrate = 1_200_000)),
            playlist.hlsSourceQualities(),
        )
    }
}
