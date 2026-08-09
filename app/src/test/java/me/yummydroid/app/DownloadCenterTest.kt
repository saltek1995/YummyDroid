package me.yummydroid.app

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import me.yummydroid.app.data.PreferredQuality

class DownloadCenterTest {
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
