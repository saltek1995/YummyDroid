package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class DownloadQueuePolicyTest {
    @Test
    fun restorePausesInterruptedTasksWithNetworkSpecificMessages() {
        val running = task(id = 1L, state = DownloadTaskState.Running, waitingForUnmetered = true)
        val queued = task(id = 2L, state = DownloadTaskState.Queued)
        val completed = task(id = 3L, state = DownloadTaskState.Completed)

        val restored = listOf(running, queued, completed).restoreInterruptedTasks(
            waitingForNetworkMessage = "network",
            waitingToResumeMessage = "resume",
        )

        assertEquals(DownloadTaskState.Paused, restored[0].state)
        assertEquals("network", restored[0].message)
        assertEquals(0L, restored[0].bytesPerSecond)
        assertEquals(DownloadTaskState.Paused, restored[1].state)
        assertEquals("resume", restored[1].message)
        assertSame(completed, restored[2])
    }

    @Test
    fun historyCapKeepsProtectedTasksBeforeRecentFinishedHistory() {
        val paused = task(id = 1000L, state = DownloadTaskState.Paused)
        val failed = task(id = 1001L, state = DownloadTaskState.Failed)
        val completed = (1L..125L).map { id -> task(id, DownloadTaskState.Completed) }

        val capped = (completed + paused + failed).cappedDownloadTasks()

        assertEquals(120, capped.size)
        assertEquals(listOf(paused.id, failed.id), capped.take(2).map { it.id })
        assertEquals((1L..118L).toList(), capped.drop(2).map { it.id })
    }

    @Test
    fun stopTargetsIncludeUnfinishedBatchAndPreserveSingleTargetFallback() {
        val summary = task(id = 1L, state = DownloadTaskState.Paused, batchKey = "batch", isBatchSummary = true)
        val running = task(id = 2L, state = DownloadTaskState.Running, batchKey = "batch")
        val completed = task(id = 3L, state = DownloadTaskState.Completed, batchKey = "batch")

        assertEquals(setOf(1L, 2L), listOf(summary, running, completed).stopTargetIds(summary.id))
        assertEquals(setOf(1L), listOf(summary.copy(state = DownloadTaskState.Completed), completed).stopTargetIds(summary.id))
        assertEquals(setOf(99L), listOf(summary).stopTargetIds(99L))
    }

    @Test
    fun taskUpdateClampsProgressAndUsesSingleTimestamp() {
        val original = task(id = 1L, state = DownloadTaskState.Queued).copy(
            title = "Original",
            progress = 0.25f,
            updatedAtMs = 10L,
        )

        val updated = original.applyUpdate(
            DownloadTaskUpdate(
                progress = -1f,
                state = DownloadTaskState.Running,
                message = "running",
            ),
            updatedAtMs = 20L,
        )

        assertEquals("Original", updated.title)
        assertEquals(0f, updated.progress)
        assertEquals(DownloadTaskState.Running, updated.state)
        assertEquals("running", updated.message)
        assertEquals(20L, updated.updatedAtMs)
    }

    private fun task(
        id: Long,
        state: DownloadTaskState,
        waitingForUnmetered: Boolean = false,
        batchKey: String = "",
        isBatchSummary: Boolean = false,
    ): DownloadTaskUi {
        return DownloadTaskUi(
            id = id,
            animeId = 10L,
            videoId = id,
            title = "Anime $id",
            episodeTitle = "Episode $id",
            state = state,
            waitingForUnmetered = waitingForUnmetered,
            batchKey = batchKey,
            isBatchSummary = isBatchSummary,
            bytesPerSecond = 100L,
        )
    }
}
