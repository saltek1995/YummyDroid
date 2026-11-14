package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.AnimeRatingSummary
import me.yummydroid.app.data.UserProfile

class AnimeCardRatingsTest {
    @Test
    fun restoreKnownRatingProjectsIntoCachedProfileLoadingAndErrorStates() = runBlocking {
        val coordinator = coordinator(readRatings = { mapOf(10L to 7) })
        val state = MutableStateFlow(
            YummyDroidUiState(
                auth = AuthUiState(profile = profile()),
                featured = LoadState.Loading,
                searchResults = LoadState.Error("cached search failed"),
            ),
        )
        val observer = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            observeAnimeCardRatings(state, coordinator.cardRatings) { transform -> state.value = transform(state.value) }
        }
        try {
            coordinator.restore(1)
            state.awaitRatings(mapOf(10L to 7))

            assertEquals(mapOf(10L to 7), state.value.cardAnimeRatings)
            assertTrue(state.value.featured is LoadState.Loading)
            assertTrue(state.value.searchResults is LoadState.Error)
        } finally {
            observer.cancelAndJoin()
        }
    }

    @Test
    fun contentSessionTransitionRepublishesCurrentRatingsForTheSameProfile() = runBlocking {
        val coordinator = coordinator(readRatings = { mapOf(10L to 7) })
        coordinator.restore(1)
        val state = MutableStateFlow(YummyDroidUiState(auth = AuthUiState(profile = profile())))
        val observer = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            observeAnimeCardRatings(state, coordinator.cardRatings) { transform -> state.value = transform(state.value) }
        }
        try {
            state.awaitRatings(mapOf(10L to 7))
            assertEquals(mapOf(10L to 7), state.value.cardAnimeRatings)
            val previous = state.value
            state.value = previous.copy(contentSessionRevision = 1).withContentContextTransition(previous)
            state.awaitRatings(mapOf(10L to 7))
            assertEquals(mapOf(10L to 7), state.value.cardAnimeRatings)
        } finally {
            observer.cancelAndJoin()
        }
    }

    @Test
    fun failedSubmissionAfterHomeNavigationRollsBackGlobalCardsWithoutChangingDetails() = runBlocking {
        val allowFailure = CompletableDeferred<Unit>()
        val coordinator = coordinator(
            readRatings = { mapOf(10L to 4) },
            setRating = { _, _ -> allowFailure.await(); error("rejected") },
        )
        coordinator.restore(1)
        val details = LoadState.Loading
        val state = MutableStateFlow(
            YummyDroidUiState(
                route = AppRoute.Details(10),
                auth = AuthUiState(profile = profile()),
                details = details,
            ),
        )
        val observer = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            observeAnimeCardRatings(state, coordinator.cardRatings) { transform -> state.value = transform(state.value) }
        }
        try {
            val staged = coordinator.stage(10, 9)
            state.awaitRatings(mapOf(10L to 9))
            assertEquals(9, state.value.cardAnimeRatings[10])
            state.value = state.value.copy(route = AppRoute.Home)
            allowFailure.complete(Unit)

            assertFailsWith<IllegalStateException> { coordinator.submit(staged) }
            state.awaitRatings(mapOf(10L to 4))
            assertEquals(mapOf(10L to 4), state.value.cardAnimeRatings)
            assertEquals(details, state.value.details)
        } finally {
            observer.cancelAndJoin()
        }
    }

    @Test
    fun confirmedRemovalPersistsAndRestoresTombstoneAndUnchangedRemoteNullPublishesIt() = runBlocking {
        val saves = mutableListOf<Map<Long, Int?>>()
        val coordinator = coordinator(
            readRatings = { mapOf(10L to 8) },
            saveRatings = { _, ratings -> saves += ratings },
        )
        coordinator.restore(1)

        coordinator.submit(coordinator.stage(10, null))
        assertEquals(mapOf(10L to null), coordinator.cardRatings.value.ratings)
        assertEquals(mapOf(10L to null), saves.single())

        val restored = coordinator(readRatings = { saves.single() })
        restored.restore(1)
        restored.effectiveRating(10, remoteRating = null, trustRemote = true)
        assertEquals(mapOf(10L to null), restored.cardRatings.value.ratings)

        val authoritativeSaves = mutableListOf<Map<Long, Int?>>()
        val unknown = coordinator(
            saveRatings = { _, ratings -> authoritativeSaves += ratings },
        )
        unknown.restore(1)
        unknown.effectiveRating(30, remoteRating = null, trustRemote = true)
        assertEquals(mapOf(30L to null), authoritativeSaves.single())
        val unknownRestored = coordinator(readRatings = { authoritativeSaves.single() })
        unknownRestored.restore(1)
        assertEquals(mapOf(30L to null), unknownRestored.cardRatings.value.ratings)
    }

    @Test
    fun accountChangeAndClearDoNotLeakOldRatingsAndContentTransitionOnlyClearsHistoryMarkers() = runBlocking {
        val coordinator = coordinator(readRatings = { userId -> if (userId == 1L) mapOf(10L to 8) else mapOf(20L to 6) })
        coordinator.restore(1)
        val stale = coordinator.stage(10, 9)
        coordinator.restore(2)
        assertEquals(false, coordinator.submit(stale).accepted)
        assertEquals(AnimeCardRatingsSnapshot(2, mapOf(20L to 6)), coordinator.cardRatings.value)
        coordinator.clear()
        assertEquals(AnimeCardRatingsSnapshot(), coordinator.cardRatings.value)

        val history = anime(10, 8)
        val featured = anime(20, 7)
        val previous = YummyDroidUiState(
            auth = AuthUiState(profile = profile()),
            historyAnime = LoadState.Ready(listOf(history)),
            featured = LoadState.Ready(listOf(featured)),
        )
        val transitioned = previous.copy(contentSessionRevision = 1).withContentContextTransition(previous)
        assertEquals(null, transitioned.historyAnime.readyDataOrNull()!!.single().userRating)
        assertEquals(history.rating, transitioned.historyAnime.readyDataOrNull()!!.single().rating)
        assertTrue(transitioned.featured is LoadState.Loading)
        assertTrue(previous.copy(cardAnimeRatings = mapOf(10L to 8))
            .withCardAnimeRatings(AnimeCardRatingsSnapshot(2, mapOf(20L to 6))).cardAnimeRatings.isEmpty())
    }

    private suspend fun MutableStateFlow<YummyDroidUiState>.awaitRatings(expected: Map<Long, Int?>) {
        withTimeout(5_000) { first { it.cardAnimeRatings == expected } }
    }

    private fun coordinator(
        readRatings: (Long) -> Map<Long, Int?> = { emptyMap() },
        saveRatings: (Long, Map<Long, Int?>) -> Unit = { _, _ -> },
        setRating: suspend (Long, Int) -> AnimeRatingSummary = { _, _ -> AnimeRatingSummary() },
    ) = AnimeRatingCoordinator(
        readRatings = readRatings,
        saveRatings = saveRatings,
        setRating = setRating,
        deleteRating = { AnimeRatingSummary() },
        fetchUserRating = { null },
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun profile() = UserProfile(1, "User", "")

    private fun anime(id: Long, userRating: Int?) = Anime(
        id = id, title = "Anime $id", description = "", posterUrl = "", animeUrl = "", year = 2026,
        rating = 8.0, userRating = userRating, views = 0, status = "ongoing", type = "TV",
        genres = emptyList(), blockedIn = emptyList(),
    )
}
