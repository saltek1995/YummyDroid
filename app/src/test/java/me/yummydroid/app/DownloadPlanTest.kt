package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.SourceQuality
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.matchingVoiceKey

class DownloadPlanTest {
    @Test
    fun voicePriorityWinsOverHigherQualityFromLowerPriorityVoice() {
        val firstVoice720 = video(
            id = 1,
            player = "CVH",
            dubbing = "Voice A",
            episode = "1",
            quality = 720,
        )
        val secondVoice1080 = video(
            id = 2,
            player = "Kodik",
            dubbing = "Voice B",
            episode = "1",
            quality = 1080,
        )

        val result = buildDownloadPlan(
            animeId = 100,
            animeTitle = "Anime",
            videos = listOf(firstVoice720, secondVoice1080),
            acceptableQualities = listOf(PreferredQuality.P1080, PreferredQuality.P720),
            selectedVoiceKeys = setOf(firstVoice720.matchingVoiceKey, secondVoice1080.matchingVoiceKey),
            voiceOrder = listOf(firstVoice720.matchingVoiceKey, secondVoice1080.matchingVoiceKey),
            onlyMissing = false,
        )

        assertEquals(1, result.scheduledCount)
        assertEquals(firstVoice720.id, result.plan.items.single().videoId)
        assertEquals(PreferredQuality.P720.name, result.plan.items.single().qualityName)
    }

    @Test
    fun highestSelectedQualityWinsInsideSameVoice() {
        val low = video(
            id = 1,
            player = "Kodik",
            dubbing = "Voice A",
            episode = "1",
            quality = 720,
        )
        val high = video(
            id = 2,
            player = "CVH",
            dubbing = "Voice A",
            episode = "1",
            quality = 1080,
        )

        val result = buildDownloadPlan(
            animeId = 100,
            animeTitle = "Anime",
            videos = listOf(low, high),
            acceptableQualities = listOf(PreferredQuality.P720, PreferredQuality.P1080),
            selectedVoiceKeys = setOf(high.matchingVoiceKey),
            voiceOrder = listOf(high.matchingVoiceKey),
            onlyMissing = false,
        )

        assertEquals(1, result.scheduledCount)
        assertEquals(high.id, result.plan.items.single().videoId)
        assertEquals(PreferredQuality.P1080.name, result.plan.items.single().qualityName)
    }

    private fun video(
        id: Long,
        player: String,
        dubbing: String,
        episode: String,
        quality: Int,
    ): VideoVariant {
        return VideoVariant(
            id = id,
            animeId = 100,
            player = player,
            playerId = id,
            dubbing = dubbing,
            episode = episode,
            url = "https://example.test/$id",
            index = id.toInt(),
            durationSeconds = 1_400,
            views = 0,
            sourceQualities = listOf(SourceQuality(height = quality)),
        )
    }
}
