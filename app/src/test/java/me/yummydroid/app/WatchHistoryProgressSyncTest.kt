package me.yummydroid.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import me.yummydroid.app.data.PlaybackProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchHistoryProgressSyncTest {
    @Test
    fun remoteEntriesAreStoredBeforeOnlyNewerLocalEntriesUpload() = runBlocking {
        val stored = mutableListOf<PlaybackProgress>()
        val uploaded = mutableListOf<PlaybackProgress>()
        val sync = WatchHistoryProgressSync(
            readProgress = { stored },
            saveProgressIfNewer = stored::add,
            fetchHistoryPage = { _, _ -> emptyList() },
            uploadProgress = { progress -> uploaded += progress; true },
            ioDispatcher = Dispatchers.Unconfined,
        )
        val remote = listOf(watchHistoryProgress(1, 10, updatedAtMs = 100))
        val local = listOf(watchHistoryProgress(2, 20, updatedAtMs = 200))

        sync.storeRemoteHistory(remote)
        assertTrue(sync.uploadNewerLocalProgress(local, remote))

        assertEquals(remote, stored)
        assertEquals(local, uploaded)
    }

    @Test
    fun uploadNewerLocalProgressReportsRejectedSiteWrites() = runBlocking {
        val uploaded = mutableListOf<PlaybackProgress>()
        val sync = WatchHistoryProgressSync(
            readProgress = { emptyList() },
            saveProgressIfNewer = {},
            fetchHistoryPage = { _, _ -> emptyList() },
            uploadProgress = { progress -> uploaded += progress; false },
            ioDispatcher = Dispatchers.Unconfined,
        )
        val local = listOf(watchHistoryProgress(2, 20, updatedAtMs = 200))

        assertFalse(sync.uploadNewerLocalProgress(local, emptyList()))
        assertEquals(local, uploaded)
    }

    @Test
    fun remoteFetchReturnsFailureWithoutThrowing() = runBlocking {
        val failure = IllegalStateException("offline")
        val sync = WatchHistoryProgressSync(
            readProgress = { emptyList() },
            saveProgressIfNewer = {},
            fetchHistoryPage = { _, _ -> throw failure },
            uploadProgress = { true },
            ioDispatcher = Dispatchers.Unconfined,
        )

        val result = sync.fetchRemoteHistory()

        assertTrue(result.isFailure)
        assertEquals(failure, result.exceptionOrNull())
    }
}
