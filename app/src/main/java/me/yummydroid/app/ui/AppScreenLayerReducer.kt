package me.yummydroid.app.ui

import me.yummydroid.app.AppRoute
import me.yummydroid.app.YummyDroidUiState

internal fun List<AppScreenLayer>.syncedWith(state: YummyDroidUiState): List<AppScreenLayer> {
    return when (val route = state.route) {
        AppRoute.Home -> syncHomeLayer(state)
        is AppRoute.Details -> syncDetailsLayer(state, route.animeId)
        is AppRoute.Player -> syncPlayerLayer(state)
    }
}

private fun List<AppScreenLayer>.syncHomeLayer(
    state: YummyDroidUiState,
): List<AppScreenLayer> {
    val updatedLayer = AppScreenLayer(AppScreenKey.Home, state)
    val existingIndex = indexOfLast { it.key == AppScreenKey.Home }
    return if (existingIndex >= 0) {
        take(existingIndex) + updatedLayer
    } else {
        listOf(updatedLayer)
    }
}

private fun List<AppScreenLayer>.syncDetailsLayer(
    state: YummyDroidUiState,
    animeId: Long,
): List<AppScreenLayer> {
    val key = AppScreenKey.Details(animeId)
    val baseLayers = ensureHomeLayer(state)
    return baseLayers.replaceTailFrom(key, AppScreenLayer(key, state))
        .trimAppScreenLayers()
}

private fun List<AppScreenLayer>.ensureHomeLayer(
    state: YummyDroidUiState,
): List<AppScreenLayer> {
    if (any { it.key == AppScreenKey.Home }) return this
    return listOf(
        AppScreenLayer(
            key = AppScreenKey.Home,
            state = state.copy(route = AppRoute.Home),
        ),
    ) + this
}

private fun List<AppScreenLayer>.syncPlayerLayer(
    state: YummyDroidUiState,
): List<AppScreenLayer> {
    return replaceTailFrom(
        key = AppScreenKey.Player,
        updatedLayer = AppScreenLayer(AppScreenKey.Player, state),
    ).trimAppScreenLayers()
}

private fun List<AppScreenLayer>.replaceTailFrom(
    key: AppScreenKey,
    updatedLayer: AppScreenLayer,
): List<AppScreenLayer> {
    val existingIndex = indexOfLast { it.key == key }
    return if (existingIndex >= 0) {
        take(existingIndex) + updatedLayer
    } else {
        this + updatedLayer
    }
}

internal fun List<AppScreenLayer>.trimAppScreenLayers(): List<AppScreenLayer> {
    if (size <= APP_LAYER_STACK_LIMIT) return this
    val homeLayer = firstOrNull { it.key == AppScreenKey.Home }
    val tailLimit = APP_LAYER_STACK_LIMIT - if (homeLayer != null) 1 else 0
    val tail = filterNot { it.key == AppScreenKey.Home }
        .takeLast(tailLimit.coerceAtLeast(0))
    return if (homeLayer != null) listOf(homeLayer) + tail else tail
}
