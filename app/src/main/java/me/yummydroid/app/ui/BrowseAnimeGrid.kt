package me.yummydroid.app.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun AnimeGridRoot(params: AnimeGridParams) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        AnimeListStateContent(
            state = params.contentState,
            onRetry = params.onRetry,
            emptyMessage = params.emptyMessage,
        ) { animes ->
            val layout = rememberAnimeGridLayout(params, animes.size, maxWidth, maxHeight)
            AnimeGridCoordinator(params, animes, layout)
        }
    }
}
