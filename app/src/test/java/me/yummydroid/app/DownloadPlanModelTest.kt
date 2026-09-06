package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import java.nio.file.Files
import me.yummydroid.app.data.PreferredQuality

class DownloadPlanModelTest {
    @Test
    fun summarySubtitleIncludesVoiceSourceAndQualityWithoutRepeatingEveryEpisode() {
        val item = DownloadPlanItem("1", "Episode 1", 10L, "voice", "AnimeVost", "Player Kodik|AnimeVost")
        val selected = plan(PreferredQuality.P720.name).copy(items = listOf(item, item.copy(videoId = 20, episodeKey = "2")))
        assertEquals("AnimeVost \u2022 Kodik \u2022 720p", selected.downloadTaskSubtitle())
        assertEquals("720p", selected.copy(items = emptyList()).downloadTaskSubtitle())
    }

    @Test
    fun unavailablePlanEpisodesRemainFailuresInsteadOfDisappearingAsAlreadyDownloaded() {
        val first = DownloadPlanItem("1", "Episode 1", 10L, "voice", "Voice", "group")
        val second = first.copy(episodeKey = "2", videoId = 20L)
        val selected = plan(PreferredQuality.P720.name).copy(items = listOf(first, second))
        val video = downloadPlanTestVideo(10L, "CVH", "Voice", "1", 720)

        val partial = selected.resolveExecution(listOf(video))
        assertEquals(2, partial.total)
        assertEquals(listOf(10L), partial.targets.map { it.video.id })
        assertEquals(listOf(second), partial.unavailable)
        assertEquals(2, selected.resolveExecution(emptyList()).unavailable.size)
    }

    @Test
    fun planOutcomeSeparatesCancelledFailedAndPausedEpisodes() {
        val progress = DownloadPlanProgress(total = 2)
        progress.record(DownloadTaskState.Completed)
        progress.record(DownloadTaskState.Cancelled)
        assertEquals(0, progress.failed.get())
        assertEquals(1, progress.cancelled.get())
        assertEquals(DownloadTaskState.Completed, progress.result(null))

        val failed = DownloadPlanProgress(total = 3)
        failed.record(DownloadTaskState.Completed)
        failed.record(DownloadTaskState.Cancelled)
        failed.record(DownloadTaskState.Failed)
        assertEquals(DownloadTaskState.Failed, failed.result(null))
        assertEquals(DownloadTaskState.Cancelled, progress.result(DownloadTaskInterruption.Cancelled))
        assertEquals(DownloadTaskState.Paused, progress.result(DownloadTaskInterruption.Paused))

        val paused = DownloadPlanProgress(total = 1)
        paused.record(DownloadTaskState.Paused)
        assertEquals(DownloadTaskState.Paused, paused.result(null))
        val unavailable = DownloadPlanProgress(total = 1, initialErrors = 1)
        assertEquals(DownloadTaskState.Failed, unavailable.result(null))
    }

    @Test
    fun removingAnEpisodeUpdatesPersistedPlansAndLeavesOtherAnimeIntact() {
        val directory = Files.createTempDirectory("download-plans").toFile()
        try {
            val storage = DownloadPlanStorage(directory)
            val first = DownloadPlanItem("1", "Episode 1", 10L, "voice", "Voice", "group")
            val second = first.copy(episodeKey = "2", episodeTitle = "Episode 2", videoId = 20L)
            val selected = plan(PreferredQuality.P720.name).copy(items = listOf(first, second))
            val other = selected.copy(id = "other", animeId = 2L)
            storage.save(selected)
            storage.save(other)

            assertEquals(emptySet(), storage.removeTargets(DownloadRemoval(1L, setOf(10L))))
            assertEquals(listOf(second), DownloadPlanStorage(directory).read(selected.id)?.items)
            assertEquals(other, storage.read(other.id))

            storage.removeEpisode(other.id, "1")
            assertEquals(listOf(second), DownloadPlanStorage(directory).read(other.id)?.items)

            assertEquals(setOf(selected.id), storage.removeTargets(DownloadRemoval(1L)))
            assertNull(storage.read(selected.id))
            assertEquals(listOf(second), storage.read(other.id)?.items)
            storage.clear()
            assertNull(storage.read(other.id))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun explicitQualitiesAreValidatedDeduplicatedAndSorted() {
        val plan = plan(
            preferredQualityName = PreferredQuality.P480.name,
            qualityNames = listOf("invalid", PreferredQuality.P720.name, PreferredQuality.P1080.name, PreferredQuality.P720.name),
        )

        assertEquals(listOf(PreferredQuality.P1080, PreferredQuality.P720), plan.acceptableQualities)
        assertEquals(PreferredQuality.P1080, plan.preferredQuality)
        assertEquals("1080p, 720p", plan.qualityTitle)
    }

    @Test
    fun legacyPreferredQualityIsUsedWhenExplicitListIsEmpty() {
        val plan = plan(preferredQualityName = PreferredQuality.P480.name)

        assertEquals(listOf(PreferredQuality.P480), plan.acceptableQualities)
        assertEquals(PreferredQuality.P480, plan.preferredQuality)
        assertEquals("480p", plan.qualityTitle)
    }

    @Test
    fun invalidLegacyQualityFallsBackToAuto() {
        val plan = plan(preferredQualityName = "invalid")

        assertEquals(listOf(PreferredQuality.Auto), plan.acceptableQualities)
        assertEquals(PreferredQuality.Auto, plan.preferredQuality)
        assertEquals("Auto", plan.qualityTitle)
    }

    private fun plan(
        preferredQualityName: String,
        qualityNames: List<String> = emptyList(),
    ) = DownloadPlan(
        id = "plan",
        animeId = 1,
        animeTitle = "Anime",
        preferredQualityName = preferredQualityName,
        qualityNames = qualityNames,
        onlyMissing = false,
        items = emptyList(),
        createdAtMs = 1,
    )
}
