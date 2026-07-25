package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import me.yummydroid.app.data.VideoVariant

class PlaybackSourceSelectionTest {
    @Test
    fun higherEstimatedSourceQualityWinsOverSiteOrderWithoutManualChoice() {
        val kodik = sourceVideo(
            id = 593472,
            player = "Kodik",
            index = 30,
            url = "https://kodikplayer.com/season/95032/hash/720p?episode=5",
        )
        val cvh = sourceVideo(
            id = 843499,
            player = "CVH",
            index = 511,
            url = "https://ru.yummyani.me/iframeCVH.html?dubbing_code=AniLibria&anime_id=51215&episode=5",
        )

        val ordered = listOf(kodik, cvh).sortedForPlaybackSource(
            requested = kodik,
            manualSourceKey = null,
        )

        assertEquals(cvh.id, ordered.first().id)
    }

    @Test
    fun manualSourceWinsOverHigherEstimatedSourceQuality() {
        val kodik = sourceVideo(
            id = 593472,
            player = "Kodik",
            index = 30,
            url = "https://kodikplayer.com/season/95032/hash/720p?episode=5",
        )
        val cvh = sourceVideo(
            id = 843499,
            player = "CVH",
            index = 511,
            url = "https://ru.yummyani.me/iframeCVH.html?dubbing_code=AniLibria&anime_id=51215&episode=5",
        )

        val ordered = listOf(cvh, kodik).sortedForPlaybackSource(
            requested = cvh,
            manualSourceKey = kodik.sourceSelectionKey,
            cachedSourceKey = cvh.sourceSelectionKey,
        )

        assertEquals(kodik.id, ordered.first().id)
    }

    private fun sourceVideo(
        id: Long,
        player: String,
        index: Int,
        url: String,
    ): VideoVariant {
        return VideoVariant(
            id = id,
            animeId = 10669,
            player = player,
            playerId = 0L,
            dubbing = "AniLibria",
            episode = "5",
            url = url,
            index = index,
            durationSeconds = 1_421,
            views = 0L,
        )
    }
}
