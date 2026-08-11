package me.yummydroid.app.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.LoadState
import me.yummydroid.app.PagingUiState
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.PosterCardSize

@Composable
internal fun AnimeGridSection(
    contentState: LoadState<List<Anime>>,
    pagingState: PagingUiState,
    gridState: LazyGridState,
    cardSize: PosterCardSize,
    contentTopPadding: Dp = 0.dp,
    contentBottomPadding: Dp = 0.dp,
    focusFirstRequest: FocusFirstRequest,
    focusCurrentRequestNonce: Long,
    contentFocusEnabled: Boolean = true,
    currentFocusedIndex: () -> Int,
    onFocusedIndexChange: (Int) -> Unit,
    backToTopSection: BrowseSection,
    onRegisterBackToTopHandler: ((HomeBackToTopHandler?) -> Unit)? = null,
    emptyMessage: String,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onExitHorizontalDirection: (VisualGridDirection) -> Boolean = { true },
    onExitUp: () -> Boolean = { false },
    exitUpFocusRequester: FocusRequester? = null,
    onExitDown: () -> Boolean = { false },
    onOpenAnime: (Long) -> Unit,
) {
    val params = AnimeGridParams(
        contentState = contentState,
        pagingState = pagingState,
        gridState = gridState,
        cardSize = cardSize,
        contentTopPadding = contentTopPadding,
        contentBottomPadding = contentBottomPadding,
        focusFirstRequest = focusFirstRequest,
        focusCurrentRequestNonce = focusCurrentRequestNonce,
        contentFocusEnabled = contentFocusEnabled,
        currentFocusedIndex = currentFocusedIndex,
        onFocusedIndexChange = onFocusedIndexChange,
        backToTopSection = backToTopSection,
        onRegisterBackToTopHandler = onRegisterBackToTopHandler,
        emptyMessage = emptyMessage,
        onRetry = onRetry,
        onLoadMore = onLoadMore,
        onExitHorizontalDirection = onExitHorizontalDirection,
        onExitUp = onExitUp,
        exitUpFocusRequester = exitUpFocusRequester,
        onExitDown = onExitDown,
        onOpenAnime = onOpenAnime,
    )
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

private class AnimeGridRuntimeState {
    val lastLoadMoreRequestSize = intArrayOf(-1)
    var handledPersistentFocusResetNonce by mutableLongStateOf(0L)
    var handledTransientFocusResetNonce by mutableLongStateOf(0L)
    var handledCurrentFocusRequestNonce by mutableLongStateOf(0L)
    var retainedFocusedIndexOnOpen by mutableIntStateOf(-1)
}

private data class AnimeGridRuntime(
    val state: AnimeGridRuntimeState,
    val focusController: BrowseGridFocusController,
    val actions: AnimeGridActions,
)

@Composable
private fun AnimeGridCoordinator(
    params: AnimeGridParams,
    animes: List<Anime>,
    layout: AnimeGridLayout,
) {
    val runtime = createAnimeGridRuntime(params, animes, layout)
    val state = runtime.state

    AnimeGridEffects(
        params = params,
        animes = animes,
        layout = layout,
        actions = runtime.actions,
        focusController = runtime.focusController,
        lastLoadMoreRequestSize = state.lastLoadMoreRequestSize,
        handledPersistentFocusResetNonce = state.handledPersistentFocusResetNonce,
        onHandledPersistentFocusResetNonceChange = { state.handledPersistentFocusResetNonce = it },
        handledTransientFocusResetNonce = state.handledTransientFocusResetNonce,
        onHandledTransientFocusResetNonceChange = { state.handledTransientFocusResetNonce = it },
        handledCurrentFocusRequestNonce = state.handledCurrentFocusRequestNonce,
        onHandledCurrentFocusRequestNonceChange = { state.handledCurrentFocusRequestNonce = it },
        retainedFocusedIndexOnOpen = state.retainedFocusedIndexOnOpen,
        onRetainedFocusedIndexOnOpenChange = { state.retainedFocusedIndexOnOpen = it },
    )
    AnimeGridContent(
        params = params,
        animes = animes,
        layout = layout,
        actions = runtime.actions,
        focusUpdateBlocked = browseGridFocusUpdateBlocked(
            retainedIndexOnOpen = state.retainedFocusedIndexOnOpen,
            contentFocusEnabled = params.contentFocusEnabled,
            requestNonce = params.focusCurrentRequestNonce,
            handledRequestNonce = state.handledCurrentFocusRequestNonce,
        ),
        onRetainedFocusedIndexChange = { state.retainedFocusedIndexOnOpen = it },
    )
}

@Composable
private fun createAnimeGridRuntime(
    params: AnimeGridParams,
    animes: List<Anime>,
    layout: AnimeGridLayout,
): AnimeGridRuntime {
    val focusScope = rememberCoroutineScope()
    val state = remember(params.backToTopSection) { AnimeGridRuntimeState() }
    val focusRequestJob = remember(params.backToTopSection, layout.columnsCount) { FocusRequestJobRef() }
    val maybeLoadMoreNear = { index: Int ->
        if (shouldRequestAnimePage(index, animes.size, layout.columnsCount, params, state)) {
            state.lastLoadMoreRequestSize[0] = animes.size
            params.onLoadMore()
        }
    }
    val updateFocusedIndex = { index: Int ->
        if (params.currentFocusedIndex() != index) params.onFocusedIndexChange(index)
        maybeLoadMoreNear(index)
    }
    val focusController = browseGridFocusController(
        gridState = params.gridState,
        itemFocusRequesters = layout.itemFocusRequesters,
        columns = layout.columnsCount,
        leadingGridItemCount = 0,
        currentFocusedIndex = params.currentFocusedIndex,
        updateFocusedIndex = updateFocusedIndex,
        protectedTopPx = layout.focusedTopInsetPx,
        protectedBottomPx = layout.focusedBottomInsetPx,
        focusedItemHeightPx = layout.focusedItemHeightPx,
        focusScope = focusScope,
        focusRequestJob = focusRequestJob,
    )
    return AnimeGridRuntime(
        state = state,
        focusController = focusController,
        actions = AnimeGridActions(
            params = params,
            animes = animes,
            layout = layout,
            focusController = focusController,
            focusScope = focusScope,
            maybeLoadMore = maybeLoadMoreNear,
            updateFocused = updateFocusedIndex,
        ),
    )
}

private fun shouldRequestAnimePage(
    index: Int,
    itemCount: Int,
    columnsCount: Int,
    params: AnimeGridParams,
    state: AnimeGridRuntimeState,
): Boolean {
    return shouldLoadMoreNearBrowseIndex(
        index = index,
        itemCount = itemCount,
        columnsCount = columnsCount,
        canLoadMore = params.pagingState.canLoadMore,
        isLoadingMore = params.pagingState.isLoadingMore,
        hasError = params.pagingState.error != null,
        lastRequestItemCount = state.lastLoadMoreRequestSize[0],
    )
}
