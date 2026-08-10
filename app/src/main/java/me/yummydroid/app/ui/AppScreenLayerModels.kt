package me.yummydroid.app.ui

import me.yummydroid.app.YummyDroidUiState

internal data class AppScreenLayer(
    val key: AppScreenKey,
    val state: YummyDroidUiState,
)

internal sealed interface AppScreenKey {
    data object Home : AppScreenKey
    data class Details(val animeId: Long) : AppScreenKey
    data object Player : AppScreenKey
}

internal const val APP_LAYER_STACK_LIMIT = 40
