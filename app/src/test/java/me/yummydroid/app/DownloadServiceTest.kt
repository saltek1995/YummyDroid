package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.cancelAndJoin
import me.yummydroid.app.data.DownloadSourceCoolingDown
import me.yummydroid.app.data.OfflineVideoFile
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.VideoVariant

class DownloadServiceTest {
    @Test
    fun sourceCooldownReleasesTheWriterSlotAndAutomaticallyRetriesAtItsDeadline() = runBlocking {
        val limits = DownloadExecutionLimits(AppSettings(downloadParallelism = 1))
        val waiting = CompletableDeferred<Unit>()
        val releaseWait = CompletableDeferred<Unit>()
        var now = 0L
        var attempts = 0
        val blocked = async {
            limits.withSourceCooldown(
                shouldStop = { false },
                onWaiting = { minutes -> assertEquals(5L, minutes); waiting.complete(Unit) },
                nowMs = { now },
                wait = { releaseWait.await() },
            ) {
                attempts += 1
                if (attempts == 1) Result.failure(DownloadSourceCoolingDown(300_000L)) else Result.success("resumed")
            }
        }
        waiting.await()
        assertEquals("other source", withTimeout(1_000) { limits.withPermit { "other source" } })
        assertEquals(1, attempts)
        now = 300_000L
        releaseWait.complete(Unit)
        assertEquals("resumed", blocked.await()?.getOrThrow())
        assertEquals(2, attempts)
    }

    @Test
    fun manualPauseAndCancellationStopSourceWaitingWithoutAnotherRequest() = runBlocking {
        val limits = DownloadExecutionLimits(AppSettings(downloadParallelism = 1))
        var stop = false
        var attempts = 0
        val paused = limits.withSourceCooldown<String>(
            shouldStop = { stop }, onWaiting = {}, nowMs = { 0L }, wait = { stop = true },
        ) {
            attempts += 1
            Result.failure(DownloadSourceCoolingDown(300_000L))
        }
        assertNull(paused)
        assertEquals(1, attempts)
        val waiting = CompletableDeferred<Unit>()
        val cancelled = launch {
            limits.withSourceCooldown<String>(
                shouldStop = { false }, onWaiting = {}, nowMs = { 0L },
                wait = { waiting.complete(Unit); awaitCancellation() },
            ) { Result.failure(DownloadSourceCoolingDown(300_000L)) }
        }
        waiting.await()
        cancelled.cancelAndJoin()
        assertEquals("available", limits.withPermit { "available" })
    }

    @Test
    fun replacingAnUpdateDrainsItsPredecessorAndDiscardsUndeliveredOldRequests() = runBlocking {
        val commands = DownloadCommandCoordinator()
        val writing = CompletableDeferred<Unit>()
        val stopping = CompletableDeferred<Unit>()
        val releaseWriter = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        commands.launch(this) {
            writing.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                stopping.complete(Unit)
                withContext(NonCancellable) { releaseWriter.await() }
                events += "old writer stopped"
            }
        }
        writing.await()
        val undelivered = commands.reserve(0L, null)
        val replacement = commands.reserve(0L, null, replacePending = true)
        val next = commands.launch(this, id = replacement) { events += "new update" }
        commands.launch(this, id = undelivered) { events += "obsolete update" }.join()
        stopping.await()
        assertTrue(events.isEmpty())
        releaseWriter.complete(Unit)
        next.join()

        assertEquals(listOf("old writer stopped", "new update"), events)
    }

    @Test
    fun liveParallelismGrowsImmediatelyAndShrinksWithoutInterruptingActiveFiles() = runBlocking {
        val settings = AppSettings(downloadParallelism = 1)
        val limits = DownloadExecutionLimits(settings)
        val started = List(3) { CompletableDeferred<Unit>() }
        val finished = List(3) { CompletableDeferred<Unit>() }
        val jobs = (0..2).map { index ->
            launch { limits.withPermit { started[index].complete(Unit); finished[index].await() } }
        }
        started[0].await()
        yield()
        assertFalse(started[1].isCompleted)

        limits.update(settings.copy(downloadParallelism = 2, downloadSpeedLimitMegabytesPerSecond = 3))
        started[1].await()
        assertEquals(3L * 1024L * 1024L, limits.speedBytesPerSecond)
        limits.update(settings.copy(downloadParallelism = 1))
        finished[0].complete(Unit)
        jobs[0].join()
        yield()
        assertTrue(jobs[1].isActive)
        assertFalse(started[2].isCompleted)

        finished[1].complete(Unit)
        started[2].await()
        finished[2].complete(Unit)
        jobs.forEach { it.join() }
    }

    @Test
    fun cancellingAnActiveOrWaitingDownloadCannotLeakALimitSlot() = runBlocking {
        val limits = DownloadExecutionLimits(AppSettings(downloadParallelism = 1))
        val started = CompletableDeferred<Unit>()
        val active = launch { limits.withPermit { started.complete(Unit); awaitCancellation() } }
        started.await()
        val waiting = launch { limits.withPermit { error("Cancelled waiter must not start") } }
        yield()
        waiting.cancel()
        waiting.join()
        active.cancel()
        active.join()

        assertEquals("downloaded", limits.withPermit { "downloaded" })
    }

    @Test
    fun deletingOneEpisodeStopsItsWriterAndSkipsItsLaterAttemptsWhileTheBatchContinues() = runBlocking {
        val commands = DownloadCommandCoordinator()
        val writing = CompletableDeferred<Unit>()
        val stopping = CompletableDeferred<Unit>()
        val releaseWriter = CompletableDeferred<Unit>()
        val otherWriting = CompletableDeferred<Unit>()
        val releaseOther = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val batch = commands.launch(this, animeId = 1L) {
            coroutineScope {
                launch {
                    val downloaded = commands.runVideo(10L) {
                        writing.complete(Unit)
                        try {
                            awaitCancellation()
                        } finally {
                            stopping.complete(Unit)
                            withContext(NonCancellable) { releaseWriter.await() }
                            events += "target stopped"
                        }
                    }
                    assertFalse(downloaded)
                    assertFalse(commands.runVideo(10L) { events += "target redownloaded" })
                    assertFalse(commands.runVideo(99L, "1") { events += "target redownloaded" })
                    assertTrue(commands.runVideo(12L) { events += "next episode" })
                }
                launch {
                    commands.runVideo(11L) {
                        otherWriting.complete(Unit)
                        releaseOther.await()
                        events += "other episode completed"
                    }
                }
            }
        }
        writing.await()
        otherWriting.await()
        val deletion = launch { commands.withMaintenance(DownloadRemoval(1L, setOf(10L), setOf("1"))) { events += "deleted" } }
        stopping.await()
        assertTrue(events.isEmpty())
        releaseWriter.complete(Unit)
        deletion.join()
        assertTrue(batch.isActive)
        assertTrue(events.indexOf("target stopped") < events.indexOf("deleted"))
        releaseOther.complete(Unit)
        batch.join()

        assertTrue("next episode" in events)
        assertTrue("other episode completed" in events)
        assertFalse("target redownloaded" in events)
    }

    @Test
    fun cleanupAlsoCancelsRequestsWhoseAndroidIntentsHaveNotReachedTheService() = runBlocking {
        val commands = DownloadCommandCoordinator()
        val removed = commands.reserve(1L, 10L)
        val retained = commands.reserve(2L, 20L)
        val events = mutableListOf<Long>()

        commands.withMaintenance(DownloadRemoval(1L)) {}
        commands.launch(this, animeId = 1L, videoId = 10L, id = removed) { events += 1L }.join()
        commands.launch(this, animeId = 2L, videoId = 20L, id = retained) { events += 2L }.join()
        commands.launch(this, animeId = 1L, videoId = 10L) { events += 3L }.join()

        assertEquals(listOf(2L, 3L), events)
    }

    @Test
    fun cacheResetDrainsTheWriterAndCancelsCommandsWaitingBehindIt() = runBlocking {
        val commands = DownloadCommandCoordinator()
        val writing = CompletableDeferred<Unit>()
        val stopping = CompletableDeferred<Unit>()
        val releaseWriter = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        commands.launch(this) {
            writing.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                stopping.complete(Unit)
                withContext(NonCancellable) { releaseWriter.await() }
                events += "writer stopped"
            }
        }
        writing.await()
        commands.launch(this) { events += "old queued command" }

        val reset = async { commands.withMaintenance { events += "cache cleared" } }
        stopping.await()
        assertTrue(events.isEmpty())
        releaseWriter.complete(Unit)
        reset.await()

        assertEquals(listOf("writer stopped", "cache cleared"), events)
        assertTrue(commands.isIdle())
    }

    @Test
    fun commandsAddedDuringCacheResetWaitUntilDeletionFinishes() = runBlocking {
        val commands = DownloadCommandCoordinator()
        val clearing = CompletableDeferred<Unit>()
        val releaseClear = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val reset = launch {
            commands.withMaintenance {
                clearing.complete(Unit)
                releaseClear.await()
                events += "cache cleared"
            }
        }
        clearing.await()
        val next = commands.launch(this) { events += "new download" }
        yield()
        assertTrue(events.isEmpty())
        releaseClear.complete(Unit)
        reset.join()
        next.join()

        assertEquals(listOf("cache cleared", "new download"), events)
    }

    @Test
    fun closingTheInitiatingScreenStillCompletesResetBeforeReleasingFutureCommands() = runBlocking {
        val commands = DownloadCommandCoordinator()
        val writing = CompletableDeferred<Unit>()
        val stopping = CompletableDeferred<Unit>()
        val releaseWriter = CompletableDeferred<Unit>()
        var cleared = false
        var nextStarted = false
        commands.launch(this) {
            writing.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                stopping.complete(Unit)
                withContext(NonCancellable) { releaseWriter.await() }
            }
        }
        writing.await()
        val reset = launch { commands.withMaintenance { cleared = true } }
        stopping.await()
        reset.cancel()
        val next = commands.launch(this) { nextStarted = true }
        yield()
        assertFalse(reset.isCompleted)
        assertFalse(nextStarted)
        releaseWriter.complete(Unit)
        reset.join()
        next.join()

        assertTrue(cleared)
        assertTrue(nextStarted)
    }

    @Test
    fun unrelatedEpisodesCannotMakeAMissingRequestedEpisodeLookDownloaded() {
        val videos = listOf(downloadVideo(id = 5, player = "CVH", dubbing = "Voice", episode = "5", index = 0))

        assertFalse(videos.containsDownloadTarget(99))
        assertTrue(videos.containsDownloadTarget(5))
        assertTrue(videos.containsDownloadTarget(null))
        assertFalse(emptyList<VideoVariant>().containsDownloadTarget(null))
    }

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
