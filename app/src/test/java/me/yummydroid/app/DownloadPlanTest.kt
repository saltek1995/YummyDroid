package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import me.yummydroid.app.data.OfflineVideoFile
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.SourceQuality
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.matchingVoiceKey

class DownloadPlanTest {
    @Test
    fun emptyQualitySelectionReportsEveryEpisodeAsMissingQuality() {
        val first = video(id = 1, player = "CVH", dubbing = "Voice A", episode = "1", quality = 1080)
        val second = video(id = 2, player = "CVH", dubbing = "Voice A", episode = "2", quality = 1080)

        val result = buildDownloadPlan(
            animeId = 100,
            animeTitle = "Anime",
            videos = listOf(first, second),
            acceptableQualities = emptyList(),
            selectedVoiceKeys = setOf(first.matchingVoiceKey),
            voiceOrder = listOf(first.matchingVoiceKey),
            onlyMissing = false,
        )

        assertEquals(2, result.totalEpisodes)
        assertEquals(0, result.selectedVoiceCount)
        assertEquals(2, result.missingSelectedQuality)
        assertEquals(0, result.scheduledCount)
        assertTrue(result.plan.qualityNames.isEmpty())
    }

    @Test
    fun onlyMissingSkipsEpisodeDownloadedInSelectedQuality() {
        val downloaded = video(id = 1, player = "CVH", dubbing = "Voice A", episode = "1", quality = 1080)
            .copy(
                localFiles = listOf(
                    OfflineVideoFile(
                        playbackUrl = "file:///episode-1.m3u8",
                        bytes = 1L,
                        qualityTitle = "1080p",
                    ),
                ),
            )
        val pending = video(id = 2, player = "CVH", dubbing = "Voice A", episode = "2", quality = 1080)

        val result = buildDownloadPlan(
            animeId = 100,
            animeTitle = "Anime",
            videos = listOf(downloaded, pending),
            acceptableQualities = listOf(PreferredQuality.P1080),
            selectedVoiceKeys = setOf(downloaded.matchingVoiceKey),
            voiceOrder = listOf(downloaded.matchingVoiceKey),
            onlyMissing = true,
        )

        assertEquals(1, result.alreadyDownloaded)
        assertEquals(listOf(pending.id), result.plan.items.map { it.videoId })
    }

    @Test
    fun missingVoiceAndMissingQualityAreCountedSeparately() {
        val selectedVoiceLowQuality = video(
            id = 1,
            player = "CVH",
            dubbing = "Voice A",
            episode = "1",
            quality = 720,
        )
        val otherVoice = video(
            id = 2,
            player = "Kodik",
            dubbing = "Voice B",
            episode = "2",
            quality = 1080,
        )

        val result = buildDownloadPlan(
            animeId = 100,
            animeTitle = "Anime",
            videos = listOf(selectedVoiceLowQuality, otherVoice),
            acceptableQualities = listOf(PreferredQuality.P1080),
            selectedVoiceKeys = setOf(selectedVoiceLowQuality.matchingVoiceKey),
            voiceOrder = listOf(selectedVoiceLowQuality.matchingVoiceKey),
            onlyMissing = false,
        )

        assertEquals(1, result.missingSelectedQuality)
        assertEquals(1, result.missingInSelectedVoices)
        assertEquals(0, result.scheduledCount)
    }

    @Test
    fun episodeSelectionCountsExcludedEpisodes() {
        val first = video(id = 1, player = "CVH", dubbing = "Voice A", episode = "1", quality = 1080)
        val second = video(id = 2, player = "CVH", dubbing = "Voice A", episode = "2", quality = 1080)

        val result = buildDownloadPlan(
            animeId = 100,
            animeTitle = "Anime",
            videos = listOf(first, second),
            acceptableQualities = listOf(PreferredQuality.P1080),
            selectedVoiceKeys = setOf(first.matchingVoiceKey),
            voiceOrder = listOf(first.matchingVoiceKey),
            onlyMissing = false,
            episodeSelectionsByVoice = mapOf(
                first.matchingVoiceKey to DownloadEpisodeSelection(listOf(1..1)),
            ),
        )

        assertEquals(1, result.excludedByEpisodeSelection)
        assertEquals(listOf(first.id), result.plan.items.map { it.videoId })
    }

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
    fun episodeRangeValidationRejectsEpisodesMissingFromVoice() {
        val parsed = validateDownloadEpisodeSelection(
            input = "1-4, 8",
            availableRanges = listOf(1..2, 4..4),
        )

        assertEquals(DownloadEpisodeSelectionError.MissingEpisodes("3, 8"), parsed.error)
        assertTrue(parsed.selection.allows(1.0))
        assertTrue(parsed.selection.allows(8.0))
    }

    @Test
    fun episodeRangeParserReturnsStructuredErrors() {
        assertEquals(
            DownloadEpisodeSelectionError.InvalidEpisodeNumber("0"),
            parseDownloadEpisodeSelection("0").error,
        )
        assertEquals(
            DownloadEpisodeSelectionError.InvalidEpisodeRange("4-2"),
            parseDownloadEpisodeSelection("4-2").error,
        )
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

    @Test
    fun downloadVoiceCoveragesKeepSiteVoiceOrder() {
        val siteFirstShort = video(id = 1, player = "Alloha", dubbing = "Voice B", episode = "1", quality = 1080)
        val siteSecondLong1 = video(id = 2, player = "CVH", dubbing = "Voice A", episode = "1", quality = 1080)
        val siteSecondLong2 = video(id = 3, player = "CVH", dubbing = "Voice A", episode = "2", quality = 1080)

        val coverages = buildDownloadVoiceCoverages(
            videos = listOf(siteFirstShort, siteSecondLong1, siteSecondLong2),
            acceptableQualities = listOf(PreferredQuality.P1080),
        )

        assertEquals(
            listOf(siteFirstShort.matchingVoiceKey, siteSecondLong1.matchingVoiceKey),
            coverages.map { it.voiceKey },
        )
    }

    @Test
    fun downloadRetryCandidatesRotateSourcesForSameEpisodeVoiceAndQuality() {
        val requested = video(id = 1, player = "CVH", dubbing = "Voice A", episode = "1", quality = 1080)
        val alternate = video(id = 2, player = "Kodik", dubbing = "Voice A", episode = "1", quality = 1080)
        val wrongQuality = video(id = 3, player = "Aksor", dubbing = "Voice A", episode = "1", quality = 720)
        val otherVoice = video(id = 4, player = "Alloha", dubbing = "Voice B", episode = "1", quality = 1080)
        val otherEpisode = video(id = 5, player = "Sibnet", dubbing = "Voice A", episode = "2", quality = 1080)

        val candidates = listOf(wrongQuality, otherVoice, alternate, otherEpisode, requested)
            .downloadRetryCandidatesFor(requested, PreferredQuality.P1080)

        assertEquals(listOf(requested.id, alternate.id), candidates.map { it.id })
        assertEquals(requested.id, candidates.downloadRetryCandidateForAttempt(1)?.id)
        assertEquals(alternate.id, candidates.downloadRetryCandidateForAttempt(2)?.id)
        assertEquals(requested.id, candidates.downloadRetryCandidateForAttempt(3)?.id)
    }

    @Test
    fun downloadRetryCandidatesKeepUnknownQualitySourcesForRuntimeCheck() {
        val requested = video(id = 1, player = "CVH", dubbing = "Voice A", episode = "1", quality = 1080)
        val unknownQuality = video(id = 2, player = "Kodik", dubbing = "Voice A", episode = "1", quality = 1080)
            .copy(sourceQualities = emptyList())

        val candidates = listOf(unknownQuality, requested)
            .downloadRetryCandidatesFor(requested, PreferredQuality.P1080)

        assertEquals(listOf(requested.id, unknownQuality.id), candidates.map { it.id })
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
