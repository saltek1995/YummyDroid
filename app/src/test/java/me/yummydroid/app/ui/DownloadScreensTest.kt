package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import me.yummydroid.app.DownloadTaskState
import me.yummydroid.app.DownloadTaskUi

class DownloadScreensTest {
    @Test
    fun modelPreservesSectionOrderAndFiltersQueueStates() {
        val plan = task(1, DownloadTaskState.Completed, isBatchSummary = true)
        val running = task(2, DownloadTaskState.Running)
        val paused = task(3, DownloadTaskState.Paused)
        val failed = task(4, DownloadTaskState.Failed)
        val completed = task(5, DownloadTaskState.Completed)
        val cancelled = task(6, DownloadTaskState.Cancelled)

        val model = buildDownloadScreenModel(
            tasks = listOf(running, completed, plan, paused, cancelled, failed),
            offlineAnimeIds = listOf(10L),
        )

        assertEquals(listOf(plan), model.planTasks)
        assertEquals(listOf(running, paused, failed), model.queueTasks)
        assertEquals(listOf(plan, running, paused, failed), model.visibleTasks)
        assertFalse(model.isEmpty)
    }

    @Test
    fun focusIndexesIncludeEveryVisibleSectionHeader() {
        val model = buildDownloadScreenModel(
            tasks = listOf(
                task(1, DownloadTaskState.Completed, isBatchSummary = true),
                task(2, DownloadTaskState.Paused, isBatchSummary = true),
                task(3, DownloadTaskState.Running),
            ),
            offlineAnimeIds = listOf(10L, 11L),
        )

        assertEquals(
            mapOf(
                "task:1" to 1,
                "task:2" to 2,
                "task:3" to 4,
                "offline:10" to 6,
                "offline:11" to 7,
            ),
            model.listIndexesByFocusKey,
        )
        assertEquals("task:3", model.focusKeysByListIndex[4])
        assertEquals("offline:10", model.focusKeysByListIndex[6])
    }

    @Test
    fun focusFallsBackOnlyWhenPreviousTargetDisappears() {
        val model = buildDownloadScreenModel(
            tasks = listOf(task(3, DownloadTaskState.Running)),
            offlineAnimeIds = listOf(10L),
        )

        assertEquals("offline:10", model.activeFocusKey("offline:10"))
        assertEquals("task:3", model.activeFocusKey("task:99"))
        assertEquals("task:3", model.activeFocusKey(null))
        assertNull(buildDownloadScreenModel(emptyList(), emptyList()).activeFocusKey(null))
    }

    @Test
    fun clearHistoryPolicyMatchesExistingTaskRules() {
        assertFalse(buildDownloadScreenModel(emptyList(), emptyList()).canClearHistory)
        assertFalse(
            buildDownloadScreenModel(
                listOf(task(1, DownloadTaskState.Running), task(2, DownloadTaskState.Paused)),
                emptyList(),
            ).canClearHistory,
        )
        assertTrue(
            buildDownloadScreenModel(
                listOf(task(1, DownloadTaskState.Completed)),
                emptyList(),
            ).canClearHistory,
        )
        assertTrue(
            buildDownloadScreenModel(
                listOf(task(1, DownloadTaskState.Failed)),
                emptyList(),
            ).canClearHistory,
        )
    }

    @Test
    fun taskActionsMatchEveryDownloadState() {
        assertEquals(DownloadTaskActions(true, false, true), task(1, DownloadTaskState.Queued).downloadTaskActions())
        assertEquals(DownloadTaskActions(true, false, true), task(1, DownloadTaskState.Running).downloadTaskActions())
        assertEquals(DownloadTaskActions(false, true, true), task(1, DownloadTaskState.Paused).downloadTaskActions())
        assertEquals(DownloadTaskActions(false, true, true), task(1, DownloadTaskState.Failed).downloadTaskActions())
        assertEquals(DownloadTaskActions(false, false, false), task(1, DownloadTaskState.Added).downloadTaskActions())
        assertEquals(DownloadTaskActions(false, false, false), task(1, DownloadTaskState.Completed).downloadTaskActions())
        assertEquals(DownloadTaskActions(false, false, false), task(1, DownloadTaskState.Cancelled).downloadTaskActions())
    }

    @Test
    fun transferStatusKeepsExistingVisibilityAndBounds() {
        assertNull(
            task(
                id = 1,
                state = DownloadTaskState.Added,
                progress = 0.5f,
                downloadedBytes = 200L,
            ).downloadTransferStatus(),
        )
        assertEquals(
            DownloadTransferStatus(
                percent = 100,
                downloadedBytes = 200L,
                totalBytes = 1_000L,
                bytesPerSecond = 50L,
            ),
            task(
                id = 1,
                state = DownloadTaskState.Running,
                progress = 1.5f,
                downloadedBytes = 200L,
                totalBytes = 1_000L,
                bytesPerSecond = 50L,
            ).downloadTransferStatus(),
        )
        assertEquals(
            DownloadTransferStatus(
                percent = 33,
                downloadedBytes = 200L,
                totalBytes = null,
                bytesPerSecond = null,
            ),
            task(
                id = 1,
                state = DownloadTaskState.Paused,
                progress = 0.333f,
                downloadedBytes = 200L,
                totalBytes = -1L,
                bytesPerSecond = 50L,
            ).downloadTransferStatus(),
        )
    }

    private fun task(
        id: Long,
        state: DownloadTaskState,
        isBatchSummary: Boolean = false,
        progress: Float = 0f,
        downloadedBytes: Long = 0L,
        totalBytes: Long = -1L,
        bytesPerSecond: Long = 0L,
    ): DownloadTaskUi {
        return DownloadTaskUi(
            id = id,
            animeId = id * 10,
            videoId = id,
            title = "Task $id",
            episodeTitle = "Episode $id",
            state = state,
            isBatchSummary = isBatchSummary,
            progress = progress,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            bytesPerSecond = bytesPerSecond,
            createdAtMs = 0L,
            updatedAtMs = 0L,
        )
    }
}
