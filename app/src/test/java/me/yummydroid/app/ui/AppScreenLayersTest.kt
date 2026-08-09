package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import me.yummydroid.app.AppRoute
import me.yummydroid.app.YummyDroidUiState
import me.yummydroid.app.data.VideoVariant

class AppScreenLayersTest {
    @Test
    fun homeSynchronizationReplacesTheWholeNavigationTail() {
        val oldHome = state(AppRoute.Home, query = "old")
        val details = state(AppRoute.Details(10))
        val updatedHome = state(AppRoute.Home, query = "new")

        val result = listOf(
            AppScreenLayer(AppScreenKey.Home, oldHome),
            AppScreenLayer(AppScreenKey.Details(10), details),
        ).syncedWith(updatedHome)

        assertEquals(listOf(AppScreenLayer(AppScreenKey.Home, updatedHome)), result)
    }

    @Test
    fun detailsSynchronizationCreatesMissingHomeLayer() {
        val details = state(AppRoute.Details(42))

        val result = emptyList<AppScreenLayer>().syncedWith(details)

        assertEquals(listOf(AppScreenKey.Home, AppScreenKey.Details(42)), result.map { it.key })
        assertEquals(AppRoute.Home, result.first().state.route)
        assertEquals(details, result.last().state)
    }

    @Test
    fun reopeningDetailsReplacesThatLayerAndDropsLaterLayers() {
        val home = state(AppRoute.Home)
        val details10 = state(AppRoute.Details(10))
        val details20 = state(AppRoute.Details(20))
        val updatedDetails10 = state(AppRoute.Details(10), query = "updated")

        val result = listOf(
            AppScreenLayer(AppScreenKey.Home, home),
            AppScreenLayer(AppScreenKey.Details(10), details10),
            AppScreenLayer(AppScreenKey.Details(20), details20),
        ).syncedWith(updatedDetails10)

        assertEquals(listOf(AppScreenKey.Home, AppScreenKey.Details(10)), result.map { it.key })
        assertEquals(updatedDetails10, result.last().state)
    }

    @Test
    fun playerSynchronizationReplacesExistingPlayerAndDropsLaterLayers() {
        val home = state(AppRoute.Home)
        val oldPlayer = state(playerRoute(videoId = 1))
        val details = state(AppRoute.Details(20))
        val updatedPlayer = state(playerRoute(videoId = 2), query = "updated")

        val result = listOf(
            AppScreenLayer(AppScreenKey.Home, home),
            AppScreenLayer(AppScreenKey.Player, oldPlayer),
            AppScreenLayer(AppScreenKey.Details(20), details),
        ).syncedWith(updatedPlayer)

        assertEquals(listOf(AppScreenKey.Home, AppScreenKey.Player), result.map { it.key })
        assertEquals(updatedPlayer, result.last().state)
    }

    @Test
    fun trimmingPreservesHomeAndMostRecentLayers() {
        val home = AppScreenLayer(AppScreenKey.Home, state(AppRoute.Home))
        val details = (1L..50L).map { animeId ->
            AppScreenLayer(AppScreenKey.Details(animeId), state(AppRoute.Details(animeId)))
        }

        val result = (listOf(home) + details).trimAppScreenLayers()

        assertEquals(APP_LAYER_STACK_LIMIT, result.size)
        assertEquals(AppScreenKey.Home, result.first().key)
        assertEquals(AppScreenKey.Details(12), result[1].key)
        assertEquals(AppScreenKey.Details(50), result.last().key)
    }

    private fun state(route: AppRoute, query: String = ""): YummyDroidUiState {
        return YummyDroidUiState(route = route, searchQuery = query)
    }

    private fun playerRoute(videoId: Long): AppRoute.Player {
        return AppRoute.Player(
            video = video(videoId),
            animeTitle = "Anime",
        )
    }

    private fun video(id: Long): VideoVariant {
        return VideoVariant(
            id = id,
            animeId = 100,
            player = "Player",
            dubbing = "Voice",
            episode = id.toString(),
            url = "https://example.test/$id",
            index = id.toInt(),
            durationSeconds = 1_400,
            views = 0,
        )
    }
}
