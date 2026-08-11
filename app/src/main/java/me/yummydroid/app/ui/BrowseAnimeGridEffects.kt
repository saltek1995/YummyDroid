package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import me.yummydroid.app.data.Anime

@Composable
internal fun AnimeGridEffects(
    params: AnimeGridParams,
    animes: List<Anime>,
    layout: AnimeGridLayout,
    actions: AnimeGridActions,
    focusController: BrowseGridFocusController,
    lastLoadMoreRequestSize: IntArray,
    handledPersistentFocusResetNonce: Long,
    onHandledPersistentFocusResetNonceChange: (Long) -> Unit,
    handledTransientFocusResetNonce: Long,
    onHandledTransientFocusResetNonceChange: (Long) -> Unit,
    handledCurrentFocusRequestNonce: Long,
    onHandledCurrentFocusRequestNonceChange: (Long) -> Unit,
    retainedFocusedIndexOnOpen: Int,
    onRetainedFocusedIndexOnOpenChange: (Int) -> Unit,
) {
    AnimeGridBackToTopRegistrationEffect(params, animes, layout, actions)
    AnimeGridFirstFocusEffect(
        params = params,
        animes = animes,
        layout = layout,
        actions = actions,
        focusController = focusController,
        handledPersistentNonce = handledPersistentFocusResetNonce,
        onHandledPersistentNonceChange = onHandledPersistentFocusResetNonceChange,
        handledTransientNonce = handledTransientFocusResetNonce,
        onHandledTransientNonceChange = onHandledTransientFocusResetNonceChange,
    )
    AnimeGridCurrentFocusEffect(
        params = params,
        animes = animes,
        layout = layout,
        actions = actions,
        focusController = focusController,
        handledNonce = handledCurrentFocusRequestNonce,
        onHandledNonceChange = onHandledCurrentFocusRequestNonceChange,
        retainedIndexOnOpen = retainedFocusedIndexOnOpen,
        onRetainedIndexOnOpenChange = onRetainedFocusedIndexOnOpenChange,
    )
    AnimeGridIndexBoundsEffect(params, animes, actions)
    AnimeGridPagingEffect(params, animes, layout, actions, lastLoadMoreRequestSize)
}

@Composable
private fun AnimeGridBackToTopRegistrationEffect(
    params: AnimeGridParams,
    animes: List<Anime>,
    layout: AnimeGridLayout,
    actions: AnimeGridActions,
) {
    DisposableEffect(animes.size, layout.columnsCount, params.onRegisterBackToTopHandler) {
        val register = params.onRegisterBackToTopHandler
        if (register != null && animes.isNotEmpty() && layout.columnsCount > 0) {
            register(
                HomeBackToTopHandler(
                    section = params.backToTopSection,
                    canHandle = actions::canHandleBackToTop,
                    handle = actions::handleBackToTop,
                ),
            )
        } else {
            register?.invoke(null)
        }
        onDispose { register?.invoke(null) }
    }
}

@Composable
private fun AnimeGridFirstFocusEffect(
    params: AnimeGridParams,
    animes: List<Anime>,
    layout: AnimeGridLayout,
    actions: AnimeGridActions,
    focusController: BrowseGridFocusController,
    handledPersistentNonce: Long,
    onHandledPersistentNonceChange: (Long) -> Unit,
    handledTransientNonce: Long,
    onHandledTransientNonceChange: (Long) -> Unit,
) {
    LaunchedEffect(params.focusFirstRequest, animes.size, layout.columnsCount) {
        if (animes.isEmpty()) return@LaunchedEffect
        val persistentNonce = params.focusFirstRequest.persistentNonce
        val transientNonce = params.focusFirstRequest.transientNonce
        val shouldHandlePersistent = persistentNonce > 0L && persistentNonce != handledPersistentNonce
        val shouldHandleTransient = transientNonce > 0L && transientNonce != handledTransientNonce
        if (!shouldHandlePersistent && !shouldHandleTransient) return@LaunchedEffect

        focusController.cancelPendingRequest()
        actions.updateFocusedIndex(0)
        focusController.focusItemWhenVisible(0)
        if (shouldHandlePersistent) onHandledPersistentNonceChange(persistentNonce)
        if (shouldHandleTransient) onHandledTransientNonceChange(transientNonce)
    }
}

@Composable
private fun AnimeGridCurrentFocusEffect(
    params: AnimeGridParams,
    animes: List<Anime>,
    layout: AnimeGridLayout,
    actions: AnimeGridActions,
    focusController: BrowseGridFocusController,
    handledNonce: Long,
    onHandledNonceChange: (Long) -> Unit,
    retainedIndexOnOpen: Int,
    onRetainedIndexOnOpenChange: (Int) -> Unit,
) {
    LaunchedEffect(params.focusCurrentRequestNonce, animes.size, layout.columnsCount) {
        if (
            !shouldRequestBrowseCurrentFocus(
                contentFocusEnabled = params.contentFocusEnabled,
                requestNonce = params.focusCurrentRequestNonce,
                handledNonce = handledNonce,
                itemCount = animes.size,
            )
        ) {
            return@LaunchedEffect
        }
        val retainedIndex = preferredBrowseGridRestoreIndex(
            retainedIndexOnOpen = retainedIndexOnOpen,
            currentFocusedIndex = params.currentFocusedIndex(),
            itemCount = animes.size,
        )
        withFrameNanos { }
        val visibleIndexes = params.gridState.layoutInfo.visibleItemsInfo
            .asSequence()
            .map { item -> item.index }
            .filter { index -> index in animes.indices }
            .toList()
        val targetIndex = retainedIndex
            ?: visibleIndexes.minOrNull()
            ?: params.gridState.firstVisibleItemIndex.coerceIn(0, animes.lastIndex)
        actions.updateFocusedIndex(targetIndex)
        focusController.focusItemWhenVisible(targetIndex)
        onHandledNonceChange(params.focusCurrentRequestNonce)
        onRetainedIndexOnOpenChange(-1)
    }
}

@Composable
private fun AnimeGridIndexBoundsEffect(
    params: AnimeGridParams,
    animes: List<Anime>,
    actions: AnimeGridActions,
) {
    LaunchedEffect(animes.size) {
        boundedAnimeFocusedIndexUpdate(
            itemCount = animes.size,
            currentIndex = params.currentFocusedIndex(),
        )?.let(actions::updateFocusedIndex)
    }
}

@Composable
private fun AnimeGridPagingEffect(
    params: AnimeGridParams,
    animes: List<Anime>,
    layout: AnimeGridLayout,
    actions: AnimeGridActions,
    lastLoadMoreRequestSize: IntArray,
) {
    LaunchedEffect(
        animes.size,
        layout.columnsCount,
        params.pagingState.canLoadMore,
        params.pagingState.isLoadingMore,
        params.pagingState.error,
    ) {
        if (!params.pagingState.isLoadingMore) lastLoadMoreRequestSize[0] = -1
        actions.maybeLoadMoreNear(params.currentFocusedIndex())
    }
}
