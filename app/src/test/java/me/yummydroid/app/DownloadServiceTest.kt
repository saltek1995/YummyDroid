package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import me.yummydroid.app.data.OfflineVideoFile
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.VideoVariant

class DownloadServiceTest {
    @Test
    fun cancelWinsWhenTaskHasCancelAndPauseRequests() {
        val result = resolveDownloadTaskInterruption(
            taskCancelRequested = true,
            parentCancelRequested = false,
            taskPauseRequested = true,
            parentPauseRequested = false,
        )

        assertEquals(DownloadTaskInterruption.Cancelled, result)
    }

    @Test
    fun parentCancelWinsOverTaskPause() {
        val result = resolveDownloadTaskInterruption(
            taskCancelRequested = false,
            parentCancelRequested = true,
            taskPauseRequested = true,
            parentPauseRequested = false,
        )

        assertEquals(DownloadTaskInterruption.Cancelled, result)
    }

    @Test
    fun taskPausePausesWhenCancelIsNotRequested() {
        val result = resolveDownloadTaskInterruption(
            taskCancelRequested = false,
            parentCancelRequested = false,
            taskPauseRequested = true,
            parentPauseRequested = false,
        )

        assertEquals(DownloadTaskInterruption.Paused, result)
    }

    @Test
    fun parentPausePausesWhenCancelIsNotRequested() {
        val result = resolveDownloadTaskInterruption(
            taskCancelRequested = false,
            parentCancelRequested = false,
            taskPauseRequested = false,
            parentPauseRequested = true,
        )

        assertEquals(DownloadTaskInterruption.Paused, result)
    }

    @Test
    fun noStopRequestDoesNotInterruptTask() {
        val result = resolveDownloadTaskInterruption(
            taskCancelRequested = false,
            parentCancelRequested = false,
            taskPauseRequested = false,
            parentPauseRequested = false,
        )

        assertNull(result)
    }

    @Test
    fun preRunningPauseDoesNotClearStopRequest() {
        val result = resolveDownloadTaskInterruptionHandling(
            taskCancelRequested = false,
            parentCancelRequested = false,
            taskPauseRequested = true,
            parentPauseRequested = false,
            clearStopRequestOnCancel = true,
            clearStopRequestOnPause = false,
        )

        assertEquals(
            DownloadTaskInterruptionHandling(
                interruption = DownloadTaskInterruption.Paused,
                clearStopRequest = false,
                waitingForUnmetered = null,
            ),
            result,
        )
    }

    @Test
    fun retryCheckpointPauseClearsStopRequestAndResetsNetworkWait() {
        val result = resolveDownloadTaskInterruptionHandling(
            taskCancelRequested = false,
            parentCancelRequested = false,
            taskPauseRequested = true,
            parentPauseRequested = false,
            clearStopRequestOnCancel = true,
            clearStopRequestOnPause = true,
            waitingForUnmetered = false,
        )

        assertEquals(
            DownloadTaskInterruptionHandling(
                interruption = DownloadTaskInterruption.Paused,
                clearStopRequest = true,
                waitingForUnmetered = false,
            ),
            result,
        )
    }

    @Test
    fun cancelCheckpointClearsStopRequest() {
        val result = resolveDownloadTaskInterruptionHandling(
            taskCancelRequested = true,
            parentCancelRequested = false,
            taskPauseRequested = false,
            parentPauseRequested = false,
            clearStopRequestOnCancel = true,
            clearStopRequestOnPause = false,
        )

        assertEquals(
            DownloadTaskInterruptionHandling(
                interruption = DownloadTaskInterruption.Cancelled,
                clearStopRequest = true,
                waitingForUnmetered = null,
            ),
            result,
        )
    }

    @Test
    fun downloadAllTargetsPreferRequestedVoiceAcrossEpisodes() {
        val videos = listOf(
            downloadVideo(id = 3, player = "Kodik", dubbing = "MiraiDUB", episode = "2", index = 3),
            downloadVideo(id = 1, player = "CVH", dubbing = "AniDUB", episode = "1", index = 1),
            downloadVideo(id = 2, player = "Alloha", dubbing = "MiraiDUB", episode = "1", index = 2),
        )

        val targets = videos.selectDownloadAllTargets(preferredGroupKey = videos.first().groupKey)

        assertEquals(listOf(2L, 3L), targets.map { it.id })
    }

    @Test
    fun downloadAllTargetsFallBackToProviderRankWhenNoPreferredVoiceExists() {
        val videos = listOf(
            downloadVideo(id = 1, player = "Kodik", dubbing = "Voice", episode = "1", index = 2),
            downloadVideo(id = 2, player = "CVH", dubbing = "Voice", episode = "1", index = 3),
            downloadVideo(id = 3, player = "Alloha", dubbing = "Voice", episode = "2", index = 1),
        )

        val targets = videos.selectDownloadAllTargets(preferredGroupKey = "missing|voice")

        assertEquals(listOf(2L, 3L), targets.map { it.id })
    }

    @Test
    fun downloadedRequestedSlotMatchesEpisodeVoiceAndQuality() {
        val downloaded720 = downloadVideo(
            id = 1,
            player = "CVH",
            dubbing = "AniDUB",
            episode = "1",
            index = 1,
            offlineFiles = listOf(offlineFile("720p")),
        )
        val sameSlotAlloha = downloadVideo(id = 2, player = "Alloha", dubbing = "AniDUB", episode = "1", index = 2)
        val sameVoiceOtherEpisode = downloadVideo(id = 3, player = "CVH", dubbing = "AniDUB", episode = "2", index = 3)

        assertEquals(true, listOf(downloaded720).hasDownloadedRequestedSlot(sameSlotAlloha, PreferredQuality.P720))
        assertEquals(false, listOf(downloaded720).hasDownloadedRequestedSlot(sameSlotAlloha, PreferredQuality.P1080))
        assertEquals(false, listOf(downloaded720).hasDownloadedRequestedSlot(sameVoiceOtherEpisode, PreferredQuality.P720))
    }

    @Test
    fun notificationSummaryTextUsesStableAsciiSeparator() {
        assertEquals("2/5 - 1 MB/s", downloadNotificationSummaryText("2/5", "1 MB/s"))
        assertEquals("2/5", downloadNotificationSummaryText("2/5", null))
    }

    @Test
    fun downloadTaskSubtitleShowsVoiceSourceAndQuality() {
        val video = downloadVideo(id = 1, player = "Player Alloha", dubbing = "AniLibria", episode = "1", index = 1)

        assertEquals(
            "AniLibria \u2022 Alloha \u2022 1080p",
            video.downloadTaskSubtitle(quality = "1080p", voice = "AniLibria"),
        )
    }

    @Test
    fun downloadTaskActionPreservesVideoAnimeAndPlanRoutes() {
        val videoTask = downloadTask(videoId = 20L)
        val animeTask = downloadTask(videoId = null)
        val planTask = downloadTask(videoId = null, planId = "plan-1", isBatchSummary = true)

        assertEquals(DOWNLOAD_ACTION_VIDEO, downloadActionForTask(videoTask))
        assertEquals(DOWNLOAD_ACTION_ANIME, downloadActionForTask(animeTask))
        assertEquals(DOWNLOAD_ACTION_PLAN, downloadActionForTask(planTask))
    }

    private fun downloadVideo(
        id: Long,
        player: String,
        dubbing: String,
        episode: String,
        index: Int,
        offlineFiles: List<OfflineVideoFile> = emptyList(),
    ): VideoVariant {
        return VideoVariant(
            id = id,
            animeId = 10,
            player = player,
            dubbing = dubbing,
            episode = episode,
            url = "https://example.test/$id",
            index = index,
            durationSeconds = null,
            views = 0,
            localFiles = offlineFiles,
        )
    }

    private fun offlineFile(qualityTitle: String): OfflineVideoFile {
        return OfflineVideoFile(
            playbackUrl = "file:///episode.m3u8",
            bytes = 1024,
            qualityTitle = qualityTitle,
        )
    }

    private fun downloadTask(
        videoId: Long?,
        planId: String = "",
        isBatchSummary: Boolean = false,
    ): DownloadTaskUi {
        return DownloadTaskUi(
            id = 1L,
            animeId = 10L,
            videoId = videoId,
            title = "Anime",
            episodeTitle = "Episode",
            planId = planId,
            isBatchSummary = isBatchSummary,
        )
    }
}
