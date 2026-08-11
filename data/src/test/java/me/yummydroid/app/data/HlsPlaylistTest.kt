package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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

    @Test
    fun buildsSingleFilePlanWithInheritedEncryptionAndInitSegment() {
        val playlist = """
            #EXTM3U
            #EXT-X-MEDIA-SEQUENCE:42
            #EXT-X-MAP:URI="init.mp4"
            #EXT-X-KEY:METHOD=AES-128,URI="keys/episode.key",IV=0x0000000000000000000000000000002A
            #EXTINF:6.5,
            media/segment-42.m4s
            #EXT-X-KEY:METHOD=NONE
            #EXTINF:7.0,
            media/segment-43.m4s
        """.trimIndent()

        val plan = playlist.toHlsSingleFilePlan(
            baseUrl = "https://cdn.example.test/anime/episode/index.m3u8",
            variantBandwidth = 1_800_000,
        )

        assertEquals(42L, plan.mediaSequence)
        assertEquals("https://cdn.example.test/anime/episode/init.mp4", plan.initUrl)
        assertEquals("mp4", plan.outputExtension)
        assertEquals(1_800_000, plan.variantBandwidth)
        assertEquals(2, plan.segments.size)
        assertEquals("https://cdn.example.test/anime/episode/media/segment-42.m4s", plan.segments[0].url)
        assertEquals(6.5, plan.segments[0].durationSeconds)
        val encryption = assertNotNull(plan.segments[0].encryption)
        assertEquals("AES-128", encryption.method)
        assertEquals("https://cdn.example.test/anime/episode/keys/episode.key", encryption.keyUrl)
        assertContentEquals(42L.toAesIv(), encryption.iv)
        assertEquals(null, plan.segments[1].encryption)
        assertEquals(7.0, plan.segments[1].durationSeconds)
    }
}
