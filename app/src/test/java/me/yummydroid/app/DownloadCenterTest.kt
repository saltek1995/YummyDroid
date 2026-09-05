package me.yummydroid.app

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import me.yummydroid.app.data.PreferredQuality

class DownloadCenterTest {
    @Test
    fun systemInterruptionRetainsUnfinishedWorkAndPreservesManualPauseAndCompletedFiles() {
        val controller = DownloadCenterController()
        fun add(video: Long) = controller.addTask(DownloadTaskRequest(1, video, "Anime", "Episode $video"))
        val running = add(1)
        val paused = add(2)
        val completed = add(3)
        controller.updateTask(running, DownloadTaskUpdate(state = DownloadTaskState.Running, downloadedBytes = 123))
        controller.requestPause(paused)
        controller.updateTask(completed, DownloadTaskUpdate(state = DownloadTaskState.Completed))

        controller.interruptActiveTasks()

        assertEquals(DownloadTaskState.Interrupted, controller.task(running)?.state)
        assertEquals(123L, controller.task(running)?.downloadedBytes)
        assertEquals(DownloadTaskState.Paused, controller.task(paused)?.state)
        assertEquals(DownloadTaskState.Completed, controller.task(completed)?.state)
        assertEquals(listOf(running), controller.state.value.tasks.automaticResumeTargets(true).map { it.id })
    }

    @Test
    fun resumedPlanEpisodeKeepsItsRowAcrossProviderAndVoiceChanges() {
        val controller = DownloadCenterController()
        val original = DownloadTaskRequest(
            animeId = 10, videoId = 11, title = "Anime", episodeTitle = "Episode 0",
            groupKey = "CVH voice", planId = "plan", batchKey = "plan", episodeKey = "0",
        )
        val id = controller.addTask(original)
        controller.updateTask(id, DownloadTaskUpdate(state = DownloadTaskState.Interrupted, downloadedBytes = 123))
        val resumed = original.copy(videoId = 22, groupKey = "Alloha voice", qualityTitle = "Alloha 720p")

        assertEquals(id, controller.addTask(resumed))
        val task = controller.state.value.tasks.single()
        assertEquals(22L, task.videoId)
        assertEquals("0", task.episodeKey)
        assertEquals("Alloha voice", task.groupKey)
        assertEquals("Alloha 720p", task.qualityTitle)
        assertEquals(DownloadTaskState.Queued, task.state)
        assertEquals(123L, task.downloadedBytes)
        controller.addTask(resumed.copy(existingTaskId = id, episodeKey = ""))
        assertEquals("0", controller.task(id)?.episodeKey)
        controller.addTask(resumed.copy(planId = "other-plan", batchKey = "other-plan"))
        assertEquals(2, controller.state.value.tasks.size)
    }

    @Test
    fun legacyDuplicatePlanRowsMergeByEpisodeWithoutRemovingOtherEpisodes() {
        val controller = DownloadCenterController()
        val original = DownloadTaskRequest(
            animeId = 10, videoId = 11, title = "Anime", episodeTitle = "Episode 1",
            groupKey = "CVH", planId = "plan", batchKey = "plan",
        )
        val first = controller.addTask(original)
        val duplicate = controller.addTask(original.copy(videoId = 22, groupKey = "Alloha"))
        val other = controller.addTask(original.copy(videoId = 33, episodeTitle = "Episode 2"))
        listOf(first, duplicate, other).forEach { controller.updateTask(it, DownloadTaskUpdate(state = DownloadTaskState.Interrupted)) }

        val resumed = controller.addTask(original.copy(videoId = 22, groupKey = "Alloha", episodeKey = "1", compatibleVideoIds = setOf(11, 22)))

        assertEquals(duplicate, resumed)
        assertEquals(setOf(resumed, other), controller.state.value.tasks.map { it.id }.toSet())
        assertEquals("1", controller.task(resumed)?.episodeKey)
        assertEquals(DownloadTaskState.Interrupted, controller.task(other)?.state)
    }

    @Test
    fun episodeRemovalCancelsItsPausedTasksAndEmptiedPlansWithoutCancellingOtherDownloads() {
        val removed = DownloadCenter.addTask(1L, 10L, "Anime", "Episode 1")
        val retained = DownloadCenter.addTask(1L, 20L, "Anime", "Episode 2")
        val other = DownloadCenter.addTask(2L, 10L, "Other", "Episode 1")
        val summary = DownloadCenter.addTask(1L, null, "Anime", "Plan", planId = "empty", isBatchSummary = true)
        DownloadCenter.updateTask(removed, state = DownloadTaskState.Paused)

        DownloadCenter.cancelTargets(DownloadRemoval(1L, setOf(10L)), setOf("empty"))

        val tasks = DownloadCenter.state.value.tasks.associateBy { it.id }
        assertEquals(DownloadTaskState.Cancelled, tasks.getValue(removed).state)
        assertEquals(DownloadTaskState.Cancelled, tasks.getValue(summary).state)
        assertEquals(DownloadTaskState.Queued, tasks.getValue(retained).state)
        assertEquals(DownloadTaskState.Queued, tasks.getValue(other).state)
        assertFalse(tasks.getValue(removed).canResume)
    }

    @BeforeTest
    fun resetBeforeTest() {
        DownloadCenter.clearAll()
    }

    @AfterTest
    fun resetAfterTest() {
        DownloadCenter.clearAll()
    }

    @Test
    fun activeTaskIsReusedByStableDownloadIdentity() {
        val firstId = DownloadCenter.addTask(
            animeId = 10L,
            videoId = 20L,
            title = "Original",
            episodeTitle = "Episode 1",
            groupKey = "voice",
            preferredQuality = PreferredQuality.P720,
            planId = "plan",
        )
        val reusedId = DownloadCenter.addTask(
            animeId = 10L,
            videoId = 20L,
            title = "Replacement",
            episodeTitle = "Episode 1",
            groupKey = "voice",
            preferredQuality = PreferredQuality.P720,
            planId = "plan",
        )

        assertEquals(firstId, reusedId)
        assertEquals(1, DownloadCenter.state.value.tasks.size)
        assertEquals("Original", DownloadCenter.state.value.tasks.single().title)
    }

    @Test
    fun explicitTaskIdUpdatesExistingBatchSummaryMetadata() {
        val taskId = DownloadCenter.addTask(
            animeId = 10L,
            videoId = null,
            title = "Original",
            episodeTitle = "Plan",
            planId = "plan",
            batchKey = "plan",
            batchTotal = 2,
            isBatchSummary = true,
        )

        val updatedId = DownloadCenter.addTask(
            animeId = 10L,
            videoId = null,
            title = "Updated",
            episodeTitle = "Updated plan",
            qualityTitle = "1080p",
            preferredQuality = PreferredQuality.P1080,
            planId = "plan",
            batchKey = "plan",
            batchTotal = 5,
            batchCompleted = 2,
            isBatchSummary = true,
            existingTaskId = taskId,
        )

        val task = DownloadCenter.state.value.tasks.single()
        assertEquals(taskId, updatedId)
        assertEquals("Updated", task.title)
        assertEquals("Updated plan", task.episodeTitle)
        assertEquals("1080p", task.qualityTitle)
        assertEquals(5, task.batchTotal)
        assertEquals(2, task.batchCompleted)
    }

    @Test
    fun updateClampsProgressAndKeepsUnspecifiedFields() {
        val taskId = DownloadCenter.addTask(
            animeId = 10L,
            videoId = 20L,
            title = "Anime",
            episodeTitle = "Episode 1",
        )

        DownloadCenter.updateTask(
            id = taskId,
            progress = 2f,
            downloadedBytes = 500L,
            state = DownloadTaskState.Running,
        )

        val task = DownloadCenter.state.value.tasks.single()
        assertEquals(1f, task.progress)
        assertEquals(500L, task.downloadedBytes)
        assertEquals("Anime", task.title)
        assertEquals(DownloadTaskState.Running, task.state)
    }

    @Test
    fun batchStopAffectsOnlyUnfinishedMembersAndLatestRequestWins() {
        val summaryId = addBatchTask(videoId = null, isSummary = true)
        val activeId = addBatchTask(videoId = 1L)
        val completedId = addBatchTask(videoId = 2L)
        DownloadCenter.updateTask(completedId, state = DownloadTaskState.Completed)

        DownloadCenter.requestPause(summaryId)

        val paused = DownloadCenter.state.value.tasks.associateBy { it.id }
        assertEquals(DownloadTaskState.Paused, paused.getValue(summaryId).state)
        assertEquals(DownloadTaskState.Paused, paused.getValue(activeId).state)
        assertEquals(DownloadTaskState.Completed, paused.getValue(completedId).state)
        assertTrue(DownloadCenter.isPauseRequested(summaryId))
        assertTrue(DownloadCenter.isPauseRequested(activeId))
        assertFalse(DownloadCenter.isPauseRequested(completedId))

        DownloadCenter.requestCancel(summaryId)

        val cancelled = DownloadCenter.state.value.tasks.associateBy { it.id }
        assertEquals(DownloadTaskState.Cancelled, cancelled.getValue(summaryId).state)
        assertEquals(DownloadTaskState.Cancelled, cancelled.getValue(activeId).state)
        assertEquals(DownloadTaskState.Completed, cancelled.getValue(completedId).state)
        assertTrue(DownloadCenter.isCancelRequested(summaryId))
        assertTrue(DownloadCenter.isCancelRequested(activeId))
        assertFalse(DownloadCenter.isPauseRequested(summaryId))
        assertFalse(DownloadCenter.isPauseRequested(activeId))
    }

    @Test
    fun clearFinishedRetainsOnlyActiveAndPausedTasks() {
        val queuedId = addTask(videoId = 1L)
        val pausedId = addTask(videoId = 2L)
        val failedId = addTask(videoId = 3L)
        val completedId = addTask(videoId = 4L)
        DownloadCenter.updateTask(pausedId, state = DownloadTaskState.Paused)
        DownloadCenter.updateTask(failedId, state = DownloadTaskState.Failed)
        DownloadCenter.updateTask(completedId, state = DownloadTaskState.Completed)

        DownloadCenter.clearFinished()

        assertEquals(setOf(queuedId, pausedId), DownloadCenter.state.value.tasks.mapTo(mutableSetOf()) { it.id })
    }

    @Test
    fun simultaneousRequestsReuseOneTaskIdentity() {
        val workers = 16
        val executor = Executors.newFixedThreadPool(workers)
        try {
            repeat(10) {
                val controller = DownloadCenterController()
                val ready = CountDownLatch(workers)
                val start = CountDownLatch(1)
                val request = DownloadTaskRequest(10, 20, "Anime", "Episode 1")
                val results = (1..workers).map {
                    executor.submit<Long> {
                        ready.countDown()
                        check(start.await(5, TimeUnit.SECONDS))
                        controller.addTask(request)
                    }
                }
                assertTrue(ready.await(5, TimeUnit.SECONDS))
                start.countDown()
                val taskIds = results.map { it.get(5, TimeUnit.SECONDS) }.toSet()

                assertEquals(setOf(controller.state.value.tasks.single().id), taskIds)
            }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun addBatchTask(videoId: Long?, isSummary: Boolean = false): Long {
        return DownloadCenter.addTask(
            animeId = 10L,
            videoId = videoId,
            title = "Anime",
            episodeTitle = if (isSummary) "Plan" else "Episode $videoId",
            groupKey = if (isSummary) "" else "voice-$videoId",
            planId = "plan",
            batchKey = "batch",
            batchTotal = 2,
            isBatchSummary = isSummary,
        )
    }

    private fun addTask(videoId: Long): Long {
        return DownloadCenter.addTask(
            animeId = 10L,
            videoId = videoId,
            title = "Anime",
            episodeTitle = "Episode $videoId",
            groupKey = "voice-$videoId",
        )
    }
}
