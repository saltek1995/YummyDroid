package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
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

    @Test
    fun episodeRangeParserAcceptsCommaSeparatedRanges() {
        val parsed = parseDownloadEpisodeSelection("1-3, 7, 10-11")

        assertNull(parsed.error)
        assertTrue(parsed.selection.allows(1.0))
        assertTrue(parsed.selection.allows(3.0))
        assertTrue(parsed.selection.allows(7.0))
        assertTrue(parsed.selection.allows(11.0))
        assertFalse(parsed.selection.allows(9.0))
    }

    @Test
    fun episodeRangesRestrictSelectedVoiceWithoutChangingVoicePriority() {
        val voiceA1 = video(id = 1, player = "CVH", dubbing = "Voice A", episode = "1", quality = 1080)
        val voiceA2 = video(id = 2, player = "CVH", dubbing = "Voice A", episode = "2", quality = 1080)
        val voiceA3 = video(id = 3, player = "CVH", dubbing = "Voice A", episode = "3", quality = 1080)
        val voiceB1 = video(id = 4, player = "Kodik", dubbing = "Voice B", episode = "1", quality = 1080)
        val voiceB2 = video(id = 5, player = "Kodik", dubbing = "Voice B", episode = "2", quality = 1080)
        val voiceB3 = video(id = 6, player = "Kodik", dubbing = "Voice B", episode = "3", quality = 1080)
        val voiceAKey = voiceA1.matchingVoiceKey
        val voiceBKey = voiceB1.matchingVoiceKey

        val result = buildDownloadPlan(
            animeId = 100,
            animeTitle = "Anime",
            videos = listOf(voiceA1, voiceA2, voiceA3, voiceB1, voiceB2, voiceB3),
            acceptableQualities = listOf(PreferredQuality.P1080),
            selectedVoiceKeys = setOf(voiceAKey, voiceBKey),
            voiceOrder = listOf(voiceAKey, voiceBKey),
            onlyMissing = false,
            episodeSelectionsByVoice = mapOf(
                voiceAKey to parseDownloadEpisodeSelection("1-2").selection,
                voiceBKey to parseDownloadEpisodeSelection("3").selection,
            ),
        )

        assertEquals(listOf(voiceA1.id, voiceA2.id, voiceB3.id), result.plan.items.map { it.videoId })
        assertEquals(0, result.excludedByEpisodeSelection)
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
