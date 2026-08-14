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
    fun remoteEntriesAreStoredBeforeOnlySupplementalLocalEntriesUpload() = runBlocking {
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
        assertTrue(sync.uploadSupplementalLocalProgress(local, remote))

        assertEquals(remote, stored)
        assertEquals(local, uploaded)
    }

    @Test
    fun uploadSupplementalLocalProgressReportsRejectedSiteWrites() = runBlocking {
        val uploaded = mutableListOf<PlaybackProgress>()
        val sync = WatchHistoryProgressSync(
            readProgress = { emptyList() },
            saveProgressIfNewer = {},
            fetchHistoryPage = { _, _ -> emptyList() },
            uploadProgress = { progress -> uploaded += progress; false },
            ioDispatcher = Dispatchers.Unconfined,
        )
        val local = listOf(watchHistoryProgress(2, 20, updatedAtMs = 200))

        assertFalse(sync.uploadSupplementalLocalProgress(local, emptyList()))
        assertEquals(local, uploaded)
    }

    @Test
    fun uploadSupplementalLocalProgressSkipsLowerLocalProgress() = runBlocking {
        val uploaded = mutableListOf<PlaybackProgress>()
        val sync = WatchHistoryProgressSync(
            readProgress = { emptyList() },
            saveProgressIfNewer = {},
            fetchHistoryPage = { _, _ -> emptyList() },
            uploadProgress = { progress -> uploaded += progress; true },
            ioDispatcher = Dispatchers.Unconfined,
        )
        val remote = listOf(
            watchHistoryProgress(1, 10, positionMs = 30_000, updatedAtMs = 100),
        )
        val local = listOf(
            watchHistoryProgress(1, 11, positionMs = 10_000, updatedAtMs = 300),
        )

        assertTrue(sync.uploadSupplementalLocalProgress(local, remote))
        assertEquals(emptyList(), uploaded)
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
