package me.yummydroid.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import me.yummydroid.app.data.AnimeRatingSummary

class AnimeRatingCoordinatorTest {
    @Test
    fun restoreCompletesBeforeCachedRatingCanBeRead() = runBlocking {
        val events = mutableListOf<String>()
        val coordinator = coordinator(
            readRatings = {
                events += "read"
                mapOf(10L to 8)
            },
        )

        coordinator.restore(userId = 42)
        events += "resolve"

        assertEquals(8, coordinator.effectiveRating(10, remoteRating = null, trustRemote = false))
        assertEquals(listOf("read", "resolve"), events)
    }

    @Test
    fun trustedRemoteRatingReplacesCachedValueAndPersistsIt() = runBlocking {
        val saved = mutableListOf<Map<Long, Int?>>()
        val coordinator = coordinator(
            readRatings = { mapOf(10L to 4) },
            saveRatings = { _, ratings -> saved += ratings },
        )
        coordinator.restore(userId = 42)

        val rating = coordinator.effectiveRating(10, remoteRating = 9, trustRemote = true)

        assertEquals(9, rating)
        assertEquals(mapOf(10L to 9), coordinator.snapshot())
        assertEquals(mapOf(10L to 9), saved.single())
    }

    @Test
    fun untrustedInvalidRemoteRatingFallsBackToCache() = runBlocking {
        val coordinator = coordinator(readRatings = { mapOf(10L to 7) })
        coordinator.restore(userId = 42)

        assertEquals(7, coordinator.effectiveRating(10, remoteRating = 0, trustRemote = false))
    }

    @Test
    fun submittedRatingUsesConfirmedServerValue() = runBlocking {
        val saved = mutableListOf<Map<Long, Int?>>()
        val requests = mutableListOf<Pair<Long, Int>>()
        val coordinator = coordinator(
            saveRatings = { _, ratings -> saved += ratings },
            setRating = { animeId, rating ->
                requests += animeId to rating
                AnimeRatingSummary()
            },
            fetchUserRating = { 9 },
        )
        coordinator.restore(userId = 42)
        val staged = coordinator.stage(animeId = 10, rating = 8)

        val update = coordinator.submit(staged)

        assertEquals(listOf(10L to 8), requests)
        assertEquals(9, update.userRating)
        assertEquals(9, update.summary.userRating)
        assertEquals(mapOf(10L to 9), saved.single())
    }

    @Test
    fun unavailableConfirmationFallsBackToRequestedRating() = runBlocking {
        val coordinator = coordinator(
            fetchUserRating = { error("confirmation unavailable") },
        )
        coordinator.restore(userId = 42)

        val update = coordinator.submit(coordinator.stage(animeId = 10, rating = 8))

        assertEquals(8, update.userRating)
        assertEquals(mapOf(10L to 8), coordinator.snapshot())
    }

    @Test
    fun deleteRemovesRatingWithoutConfirmationRequest() = runBlocking {
        var confirmationRequests = 0
        val saved = mutableListOf<Map<Long, Int?>>()
        val coordinator = coordinator(
            readRatings = { mapOf(10L to 8) },
            saveRatings = { _, ratings -> saved += ratings },
            fetchUserRating = {
                confirmationRequests += 1
                8
            },
        )
        coordinator.restore(userId = 42)

        val update = coordinator.submit(coordinator.stage(animeId = 10, rating = null))

        assertEquals(null, update.userRating)
        assertEquals(0, confirmationRequests)
        assertEquals(mapOf(10L to null), coordinator.snapshot())
        assertEquals(mapOf(10L to null), saved.single())
    }

    @Test
    fun failedMutationRestoresPreviousCachedRating() = runBlocking {
        val coordinator = coordinator(
            readRatings = { mapOf(10L to 6) },
            setRating = { _, _ -> error("mutation failed") },
        )
        coordinator.restore(userId = 42)
        val staged = coordinator.stage(animeId = 10, rating = 9)
        assertEquals(mapOf(10L to 9), coordinator.snapshot())

        assertFailsWith<IllegalStateException> { coordinator.submit(staged) }

        assertEquals(mapOf(10L to 6), coordinator.snapshot())
    }

    @Test
    fun cancellationDuringConfirmationPropagatesAndRollsBack() = runBlocking {
        val coordinator = coordinator(
            readRatings = { mapOf(10L to 6) },
            fetchUserRating = { throw CancellationException("cancelled") },
        )
        coordinator.restore(userId = 42)

        assertFailsWith<CancellationException> {
            coordinator.submit(coordinator.stage(animeId = 10, rating = 9))
        }

        assertEquals(mapOf(10L to 6), coordinator.snapshot())
    }

    @Test
    fun olderCompletionCannotOverwriteNewerRating() = runBlocking {
        val firstStarted = CompletableDeferred<Unit>()
        val finishFirst = CompletableDeferred<Unit>()
        val coordinator = coordinator(
            setRating = { _, rating ->
                if (rating == 5) {
                    firstStarted.complete(Unit)
                    finishFirst.await()
                }
                AnimeRatingSummary()
            },
        )
        coordinator.restore(userId = 42)

        val first = coordinator.stage(animeId = 10, rating = 5)
        val firstResult = async { coordinator.submit(first) }
        firstStarted.await()
        val secondResult = coordinator.submit(coordinator.stage(animeId = 10, rating = 9))
        finishFirst.complete(Unit)

        assertEquals(true, secondResult.accepted)
        assertEquals(false, firstResult.await().accepted)
        assertEquals(mapOf(10L to 9), coordinator.snapshot())
    }

    @Test
    fun olderFailureCannotRollbackNewerRating() = runBlocking {
        val firstStarted = CompletableDeferred<Unit>()
        val failFirst = CompletableDeferred<Unit>()
        val coordinator = coordinator(
            setRating = { _, rating ->
                if (rating == 5) {
                    firstStarted.complete(Unit)
                    failFirst.await()
                    error("older mutation failed")
                }
                AnimeRatingSummary()
            },
        )
        coordinator.restore(userId = 42)

        val first = coordinator.stage(animeId = 10, rating = 5)
        val firstResult = async { runCatching { coordinator.submit(first) } }
        firstStarted.await()
        coordinator.submit(coordinator.stage(animeId = 10, rating = 9))
        failFirst.complete(Unit)

        assertEquals(true, firstResult.await().isFailure)
        assertEquals(mapOf(10L to 9), coordinator.snapshot())
    }

    @Test
    fun clearDropsAccountState() = runBlocking {
        val coordinator = coordinator(readRatings = { mapOf(10L to 8) })
        coordinator.restore(userId = 42)

        coordinator.clear()

        assertEquals(emptyMap(), coordinator.snapshot())
        assertEquals(null, coordinator.effectiveRating(10, remoteRating = null, trustRemote = false))
    }

    @Test
    fun completedRestoreCannotRepopulateStateAfterClear() = runBlocking {
        val readStarted = CountDownLatch(1)
        val allowReadToFinish = CountDownLatch(1)
        val coordinator = coordinator(
            readRatings = {
                readStarted.countDown()
                allowReadToFinish.await()
                mapOf(10L to 8)
            },
            ioDispatcher = Dispatchers.Default,
        )

        val restoreJob = launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.restore(userId = 42)
        }
        readStarted.await()
        coordinator.clear()
        allowReadToFinish.countDown()
        restoreJob.join()

        assertEquals(emptyMap(), coordinator.snapshot())
    }

    private fun coordinator(
        readRatings: (Long) -> Map<Long, Int> = { emptyMap() },
        saveRatings: (Long, Map<Long, Int?>) -> Unit = { _, _ -> },
        setRating: suspend (Long, Int) -> AnimeRatingSummary = { _, _ -> AnimeRatingSummary() },
        deleteRating: suspend (Long) -> AnimeRatingSummary = { AnimeRatingSummary() },
        fetchUserRating: suspend (Long) -> Int? = { null },
        ioDispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    ): AnimeRatingCoordinator {
        return AnimeRatingCoordinator(
            readRatings = readRatings,
            saveRatings = saveRatings,
            setRating = setRating,
            deleteRating = deleteRating,
            fetchUserRating = fetchUserRating,
            ioDispatcher = ioDispatcher,
        )
    }
}
