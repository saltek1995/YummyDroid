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
            listOf(SourceQuality(height = 1080), SourceQuality(height = 720)),
            playlist.hlsSourceQualities(),
        )
    }

    @Test
    fun exactSelectionDoesNotFallbackToLowerQuality() {
        val playlist = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=900000,RESOLUTION=1280x720
            720/index.m3u8
        """.trimIndent()

        val selected = playlist.hlsVariants("https://cdn.example.test/master.m3u8")
            .selectExactQuality(PreferredQuality.P1080)

        assertEquals(null, selected)
    }

    @Test
    fun exactSelectionPicksHighestBitrateAtSameHeight() {
        val playlist = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=900000,RESOLUTION=1280x720
            low/720.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=1200000,RESOLUTION=1280x720
            high/720.m3u8
        """.trimIndent()

        val selected = playlist.hlsVariants("https://cdn.example.test/master.m3u8")
            .selectExactQuality(PreferredQuality.P720)

        assertEquals(720, selected?.height)
        assertEquals("https://cdn.example.test/high/720.m3u8", selected?.url)
    }
}
