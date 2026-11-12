package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DownloadQueuePersistenceTest {
    @Test
    fun progressBurstCoalescesToLatestDelayedCheckpoint() {
        val scheduled = mutableListOf<Pair<Long, () -> Unit>>()
        val writes = mutableListOf<List<DownloadTaskUi>>()
        val persistence = DownloadQueuePersistence(
            schedule = { delay, task -> scheduled += delay to task },
            write = { snapshot -> writes += snapshot },
        )

        repeat(100) { progress ->
            persistence.submit(listOf(task(1).copy(progress = progress / 100f)), progressOnly = true)
        }

        assertEquals(listOf(1_000L), scheduled.map { it.first })
        scheduled.single().second()
        assertEquals(listOf(task(1).copy(progress = 0.99f)), writes.single())
    }

    @Test
    fun terminalCheckpointInvalidatesEarlierProgressTicket() {
        val scheduled = mutableListOf<Pair<Long, () -> Unit>>()
        val writes = mutableListOf<List<DownloadTaskUi>>()
        val persistence = DownloadQueuePersistence(
            schedule = { delay, task -> scheduled += delay to task },
            write = { snapshot -> writes += snapshot },
        )

        persistence.submit(listOf(task(1).copy(progress = 0.5f)), progressOnly = true)
        persistence.submit(listOf(task(1).copy(state = DownloadTaskState.Completed, progress = 1f)), progressOnly = false)

        assertEquals(listOf(1_000L, 0L), scheduled.map { it.first })
        scheduled.first().second()
        assertTrue(writes.isEmpty())
        scheduled.last().second()
        assertEquals(listOf(task(1).copy(state = DownloadTaskState.Completed, progress = 1f)), writes.single())
    }

    @Test
    fun structuralClearRetainsLatestOrderedSnapshotWhenProgressArrivesBeforeWrite() {
        val scheduled = mutableListOf<Pair<Long, () -> Unit>>()
        val writes = mutableListOf<List<DownloadTaskUi>>()
        val persistence = DownloadQueuePersistence(
            schedule = { delay, task -> scheduled += delay to task },
            write = { snapshot -> writes += snapshot },
        )
        val first = task(1)
        val second = task(2)

        persistence.submit(listOf(first, second), progressOnly = false)
        persistence.submit(emptyList(), progressOnly = false)
        persistence.submit(listOf(second.copy(progress = 0.75f), first), progressOnly = true)

        assertEquals(listOf(0L), scheduled.map { it.first })
        scheduled.single().second()
        assertEquals(listOf(second.copy(progress = 0.75f), first), writes.single())
    }

    private fun task(id: Long): DownloadTaskUi =
        DownloadTaskRequest(1L, id, "Anime", "Episode $id").createTask(id)
            .copy(createdAtMs = 1L, updatedAtMs = 1L)
}
