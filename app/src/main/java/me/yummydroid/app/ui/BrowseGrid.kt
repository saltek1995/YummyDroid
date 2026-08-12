package me.yummydroid.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.LoadState
import me.yummydroid.app.PagingUiState
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.PosterCardSize

// BrowseAnimeGridContent
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun AnimeGridContent(
    params: AnimeGridParams,
    animes: List<Anime>,
    layout: AnimeGridLayout,
    actions: AnimeGridActions,
    focusUpdateBlocked: Boolean,
    onRetainedFocusedIndexChange: (Int) -> Unit,
) {
    BrowseGridScrollLocalProvider(touchOverscrollEnabled = layout.touchOverscrollEnabled) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(layout.columnsCount),
            state = params.gridState,
            contentPadding = PaddingValues(
                start = layout.horizontalPadding,
                top = layout.topContentPadding,
                end = layout.horizontalPadding,
                bottom = layout.bottomContentPadding,
            ),
            horizontalArrangement = Arrangement.spacedBy(BrowseGridHorizontalGap),
            verticalArrangement = Arrangement.spacedBy(BrowseGridVerticalGap),
            modifier = Modifier
                .fillMaxSize()
                .browseTouchBounceOverscroll(
                    enabled = layout.touchOverscrollEnabled,
                    gridState = params.gridState,
                )
                .focusGroup(),
        ) {
            animeGridCards(
                params = params,
                animes = animes,
                layout = layout,
                actions = actions,
                focusUpdateBlocked = focusUpdateBlocked,
                onRetainedFocusedIndexChange = onRetainedFocusedIndexChange,
            )
            animeGridFooter(params, layout, actions)
        }
    }
}

private fun LazyGridScope.animeGridCards(
    params: AnimeGridParams,
    animes: List<Anime>,
    layout: AnimeGridLayout,
    actions: AnimeGridActions,
    focusUpdateBlocked: Boolean,
    onRetainedFocusedIndexChange: (Int) -> Unit,
) {
    itemsIndexed(
        items = animes,
        key = { _, anime -> anime.id },
        contentType = { _, _ -> "anime-card" },
    ) { index, anime ->
        AnimeCard(
            anime = anime,
            onClick = {
                actions.updateFocusedIndex(index)
                onRetainedFocusedIndexChange(index)
                params.onOpenAnime(anime.id)
            },
            modifier = Modifier
                .focusProperties { canFocus = params.contentFocusEnabled }
                .focusRequester(layout.itemFocusRequesters[index])
                .onPreviewKeyEvent { event ->
                    event.type == KeyEventType.KeyDown && actions.handleGridDirection(index, event.key)
                }
                .onFocusChanged { focusState ->
                    if (focusState.hasFocus && !focusUpdateBlocked) actions.updateFocusedIndex(index)
                },
        )
    }
}

private fun LazyGridScope.animeGridFooter(
    params: AnimeGridParams,
    layout: AnimeGridLayout,
    actions: AnimeGridActions,
) {
    val pagingState = params.pagingState
    if (!pagingState.isLoadingMore && !pagingState.canLoadMore && pagingState.error == null) return
    item(span = { GridItemSpan(maxLineSpan) }) {
        PagingGridFooter(
            paging = pagingState,
            onLoadMore = params.onLoadMore,
            onRetry = actions::retryPaging,
            retryModifier = Modifier
                .focusRequester(layout.pagingRetryFocusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionUp -> actions.returnFromPagingRetry()
                        Key.DirectionDown -> actions.exitPagingRetryDown()
                        else -> false
                    }
                },
        )
    }
}

// BrowseAnimeGridEffects
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
    AnimeGridFocusEffect(
        params = params,
        animes = animes,
        layout = layout,
        actions = actions,
        focusController = focusController,
        handledPersistentNonce = handledPersistentFocusResetNonce,
        onHandledPersistentNonceChange = onHandledPersistentFocusResetNonceChange,
        handledTransientNonce = handledTransientFocusResetNonce,
        onHandledTransientNonceChange = onHandledTransientFocusResetNonceChange,
        handledCurrentNonce = handledCurrentFocusRequestNonce,
        onHandledCurrentNonceChange = onHandledCurrentFocusRequestNonceChange,
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
private fun AnimeGridFocusEffect(
    params: AnimeGridParams,
    animes: List<Anime>,
    layout: AnimeGridLayout,
    actions: AnimeGridActions,
    focusController: BrowseGridFocusController,
    handledPersistentNonce: Long,
    onHandledPersistentNonceChange: (Long) -> Unit,
    handledTransientNonce: Long,
    onHandledTransientNonceChange: (Long) -> Unit,
    handledCurrentNonce: Long,
    onHandledCurrentNonceChange: (Long) -> Unit,
    retainedIndexOnOpen: Int,
    onRetainedIndexOnOpenChange: (Int) -> Unit,
) {
    val request = animeGridFocusRequest(
        params = params,
        itemCount = animes.size,
        handledPersistentNonce = handledPersistentNonce,
        handledTransientNonce = handledTransientNonce,
        handledCurrentNonce = handledCurrentNonce,
    )
    UiControlEffect(
        params.focusFirstRequest,
        params.focusCurrentRequestNonce,
        animes.size,
        layout.columnsCount,
        retainedIndexOnOpen,
        enabled = request.enabled,
    ) {
        focusController.cancelPendingRequest()
        if (request.focusFirst) {
            focusFirstAnimeGridItem(
                request = request,
                actions = actions,
                focusController = focusController,
                onHandledPersistentNonceChange = onHandledPersistentNonceChange,
                onHandledTransientNonceChange = onHandledTransientNonceChange,
                onHandledCurrentNonceChange = onHandledCurrentNonceChange,
            )
        } else {
            restoreAnimeGridFocus(
                params = params,
                animes = animes,
                actions = actions,
                focusController = focusController,
                retainedIndexOnOpen = retainedIndexOnOpen,
                onHandledCurrentNonceChange = onHandledCurrentNonceChange,
                onRetainedIndexOnOpenChange = onRetainedIndexOnOpenChange,
            )
        }
    }
}

private data class AnimeGridFocusRequest(
    val persistentNonce: Long,
    val transientNonce: Long,
    val currentNonce: Long,
    val handlePersistent: Boolean,
    val handleTransient: Boolean,
    val focusFirst: Boolean,
    val focusCurrent: Boolean,
) {
    val enabled: Boolean get() = focusFirst || focusCurrent
}

private fun animeGridFocusRequest(
    params: AnimeGridParams,
    itemCount: Int,
    handledPersistentNonce: Long,
    handledTransientNonce: Long,
    handledCurrentNonce: Long,
): AnimeGridFocusRequest {
    val persistentNonce = params.focusFirstRequest.persistentNonce
    val transientNonce = params.focusFirstRequest.transientNonce
    val handlePersistent = persistentNonce > 0L && persistentNonce != handledPersistentNonce
    val handleTransient = transientNonce > 0L && transientNonce != handledTransientNonce
    val focusCurrent = shouldRequestBrowseCurrentFocus(
        contentFocusEnabled = params.contentFocusEnabled,
        requestNonce = params.focusCurrentRequestNonce,
        handledNonce = handledCurrentNonce,
        itemCount = itemCount,
    )
    return AnimeGridFocusRequest(
        persistentNonce = persistentNonce,
        transientNonce = transientNonce,
        currentNonce = params.focusCurrentRequestNonce,
        handlePersistent = handlePersistent,
        handleTransient = handleTransient,
        focusFirst = itemCount > 0 && (handlePersistent || handleTransient),
        focusCurrent = focusCurrent,
    )
}

private suspend fun focusFirstAnimeGridItem(
    request: AnimeGridFocusRequest,
    actions: AnimeGridActions,
    focusController: BrowseGridFocusController,
    onHandledPersistentNonceChange: (Long) -> Unit,
    onHandledTransientNonceChange: (Long) -> Unit,
    onHandledCurrentNonceChange: (Long) -> Unit,
) {
    actions.updateFocusedIndex(0)
    focusController.focusItemWhenVisible(0)
    if (request.handlePersistent) onHandledPersistentNonceChange(request.persistentNonce)
    if (request.handleTransient) onHandledTransientNonceChange(request.transientNonce)
    if (request.focusCurrent) onHandledCurrentNonceChange(request.currentNonce)
}

private suspend fun restoreAnimeGridFocus(
    params: AnimeGridParams,
    animes: List<Anime>,
    actions: AnimeGridActions,
    focusController: BrowseGridFocusController,
    retainedIndexOnOpen: Int,
    onHandledCurrentNonceChange: (Long) -> Unit,
    onRetainedIndexOnOpenChange: (Int) -> Unit,
) {
    val retainedIndex = preferredBrowseGridRestoreIndex(
        retainedIndexOnOpen = retainedIndexOnOpen,
        currentFocusedIndex = params.currentFocusedIndex(),
        itemCount = animes.size,
    )
    withFrameNanos { }
    val firstVisibleIndex = params.gridState.layoutInfo.visibleItemsInfo
        .asSequence()
        .map { item -> item.index }
        .filter { index -> index in animes.indices }
        .minOrNull()
    val targetIndex = retainedIndex
        ?: firstVisibleIndex
        ?: params.gridState.firstVisibleItemIndex.coerceIn(0, animes.lastIndex)
    actions.updateFocusedIndex(targetIndex)
    focusController.focusItemWhenVisible(targetIndex)
    onHandledCurrentNonceChange(params.focusCurrentRequestNonce)
    onRetainedIndexOnOpenChange(-1)
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

// BrowseAnimeGridRuntime
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
internal fun AnimeGridCoordinator(
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
    val uiControls = LocalUiControlCoordinator.current
    val state = remember(params.backToTopSection) { AnimeGridRuntimeState() }
    val focusRequestJob = remember(params.backToTopSection, layout.columnsCount, uiControls) {
        FocusRequestJobRef(uiControls)
    }
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
            uiControls = uiControls,
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

// BrowseAnimeGridSection
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
    onExitDown: () -> Boolean = { false },
    onOpenAnime: (Long) -> Unit,
) {
    AnimeGridRoot(
        AnimeGridParams(
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
            onExitDown = onExitDown,
            onOpenAnime = onOpenAnime,
        ),
    )
}

@Composable
private fun AnimeGridRoot(params: AnimeGridParams) {
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

// BrowseAnimeGridState
internal data class AnimeGridParams(
    val contentState: LoadState<List<Anime>>,
    val pagingState: PagingUiState,
    val gridState: LazyGridState,
    val cardSize: PosterCardSize,
    val contentTopPadding: Dp,
    val contentBottomPadding: Dp,
    val focusFirstRequest: FocusFirstRequest,
    val focusCurrentRequestNonce: Long,
    val contentFocusEnabled: Boolean,
    val currentFocusedIndex: () -> Int,
    val onFocusedIndexChange: (Int) -> Unit,
    val backToTopSection: BrowseSection,
    val onRegisterBackToTopHandler: ((HomeBackToTopHandler?) -> Unit)?,
    val emptyMessage: String,
    val onRetry: () -> Unit,
    val onLoadMore: () -> Unit,
    val onExitHorizontalDirection: (VisualGridDirection) -> Boolean,
    val onExitUp: () -> Boolean,
    val onExitDown: () -> Boolean,
    val onOpenAnime: (Long) -> Unit,
)

internal data class AnimeGridLayout(
    val columnsCount: Int,
    val touchOverscrollEnabled: Boolean,
    val horizontalPadding: Dp,
    val topContentPadding: Dp,
    val bottomContentPadding: Dp,
    val itemFocusRequesters: List<FocusRequester>,
    val pagingRetryFocusRequester: FocusRequester,
    val focusedTopInsetPx: Float,
    val focusedBottomInsetPx: Float,
    val focusedItemHeightPx: Float,
)

@Composable
internal fun rememberAnimeGridLayout(
    params: AnimeGridParams,
    itemCount: Int,
    maxWidth: Dp,
    maxHeight: Dp,
): AnimeGridLayout {
    val responsiveWidth = currentResponsiveWindowSizeDp().width
    val columnsCount = remember(maxWidth, params.cardSize) {
        params.cardSize.resolveCatalogColumns(maxWidth.value.roundToInt())
    }
    val density = LocalDensity.current
    val horizontalPadding = browseGridHorizontalContentPadding(responsiveWidth)
    val focusedTopInset = browseGridFocusedCardTopInset(params.contentTopPadding, responsiveWidth)
    val focusedBottomInset = BrowseFocusedCardBottomGap + params.contentBottomPadding
    val baseBottomPadding = if (params.contentBottomPadding > 0.dp) {
        focusedBottomInset
    } else {
        24.dp + BrowseFocusedCardBottomGap
    }
    val itemFocusRequesters = remember(params.backToTopSection, itemCount, columnsCount) {
        List(itemCount) { FocusRequester() }
    }
    val pagingRetryFocusRequester = remember(params.backToTopSection) { FocusRequester() }
    return AnimeGridLayout(
        columnsCount = columnsCount,
        touchOverscrollEnabled = LocalInputModeManager.current.inputMode == InputMode.Touch,
        horizontalPadding = horizontalPadding,
        topContentPadding = BrowseGridTopContentPadding + params.contentTopPadding,
        bottomContentPadding = browseGridFocusedCardBottomPadding(
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            columns = columnsCount,
            horizontalPadding = horizontalPadding,
            topInset = focusedTopInset,
            bottomInset = focusedBottomInset,
            basePadding = baseBottomPadding,
        ),
        itemFocusRequesters = itemFocusRequesters,
        pagingRetryFocusRequester = pagingRetryFocusRequester,
        focusedTopInsetPx = with(density) { focusedTopInset.toPx() },
        focusedBottomInsetPx = with(density) { focusedBottomInset.toPx() },
        focusedItemHeightPx = with(density) {
            browseGridItemHeight(
                maxWidth = maxWidth,
                columns = columnsCount,
                horizontalPadding = horizontalPadding,
            ).toPx()
        },
    )
}

internal class AnimeGridActions(
    private val params: AnimeGridParams,
    private val animes: List<Anime>,
    private val layout: AnimeGridLayout,
    private val focusController: BrowseGridFocusController,
    private val focusScope: CoroutineScope,
    private val uiControls: UiControlCoordinator,
    private val maybeLoadMore: (Int) -> Unit,
    private val updateFocused: (Int) -> Unit,
) {
    fun maybeLoadMoreNear(index: Int) {
        maybeLoadMore(index)
    }

    fun updateFocusedIndex(index: Int) {
        updateFocused(index)
    }

    fun handleGridDirection(index: Int, key: Key): Boolean {
        return handleVisualGridNavigationKey(
            key = key,
            itemCount = animes.size,
            columns = layout.columnsCount,
            sourceIndex = index,
            moveFocusTo = { target -> focusController.moveFocusTo(target) },
            onEdgeExit = { direction -> handleGridEdgeExit(direction) },
        )
    }

    private fun handleGridEdgeExit(direction: VisualGridDirection): Boolean {
        when (browsePagingEdgeAction(direction, params.pagingState)) {
            BrowsePagingEdgeAction.FocusRetry -> return layout.pagingRetryFocusRequester.requestFocusSafely()
            BrowsePagingEdgeAction.RequestMore -> {
                params.onLoadMore()
                return true
            }
            BrowsePagingEdgeAction.Consume -> return true
            BrowsePagingEdgeAction.Exit -> Unit
        }
        return when (direction) {
            VisualGridDirection.Left,
            VisualGridDirection.Right -> params.onExitHorizontalDirection(direction)
            VisualGridDirection.Down -> params.onExitDown()
            VisualGridDirection.Up -> params.onExitUp()
        }
    }

    fun retryPaging() {
        returnFromPagingRetry()
        params.onLoadMore()
    }

    fun returnFromPagingRetry(): Boolean {
        return focusController.moveFocusTo(animes.lastIndex)
    }

    fun exitPagingRetryDown(): Boolean = params.onExitDown()

    fun canHandleBackToTop(): Boolean {
        return params.gridState.canHandleBrowseRootBackToTop(params.backToTopSection)
    }

    fun handleBackToTop(withFocus: Boolean): Boolean {
        if (!canHandleBackToTop()) return false
        focusController.cancelPendingRequest()
        if (withFocus && animes.isNotEmpty()) {
            uiControls.cancel(UiControlOperation.NavigationLatest)
            return focusController.moveFocusTo(0)
        }
        uiControls.launch(focusScope, this, UiControlOperation.NavigationLatest) {
            params.gridState.animateScrollToItem(0, 0)
        }
        return true
    }
}

internal enum class BrowsePagingEdgeAction { FocusRetry, RequestMore, Consume, Exit }

internal fun browsePagingEdgeAction(
    direction: VisualGridDirection,
    paging: PagingUiState,
): BrowsePagingEdgeAction {
    if (direction != VisualGridDirection.Down) {
        return BrowsePagingEdgeAction.Exit
    }
    return when {
        paging.error != null -> BrowsePagingEdgeAction.FocusRetry
        !paging.canLoadMore -> BrowsePagingEdgeAction.Exit
        paging.isLoadingMore -> BrowsePagingEdgeAction.Consume
        else -> BrowsePagingEdgeAction.RequestMore
    }
}

internal fun shouldLoadMoreNearBrowseIndex(
    index: Int,
    itemCount: Int,
    columnsCount: Int,
    canLoadMore: Boolean,
    isLoadingMore: Boolean,
    hasError: Boolean,
    lastRequestItemCount: Int,
): Boolean {
    if (index < 0 || columnsCount <= 0 || itemCount <= 0) return false
    if (!canLoadMore) return false
    if (isLoadingMore) return false
    if (hasError) return false
    if (lastRequestItemCount == itemCount) return false
    val focusedRow = index / columnsCount
    val lastLoadedRow = (itemCount - 1) / columnsCount
    return lastLoadedRow - focusedRow < 2
}

internal fun boundedAnimeFocusedIndexUpdate(itemCount: Int, currentIndex: Int): Int? {
    return when {
        itemCount <= 0 -> -1
        currentIndex >= itemCount -> itemCount - 1
        else -> null
    }
}

internal fun browseGridFocusUpdateBlocked(
    retainedIndexOnOpen: Int,
    contentFocusEnabled: Boolean,
    requestNonce: Long,
    handledRequestNonce: Long,
): Boolean {
    return retainedIndexOnOpen >= 0 ||
        (contentFocusEnabled && requestNonce > 0L && requestNonce != handledRequestNonce)
}

internal fun preferredBrowseGridRestoreIndex(
    retainedIndexOnOpen: Int,
    currentFocusedIndex: Int,
    itemCount: Int,
): Int? {
    return retainedIndexOnOpen.takeIf { it in 0 until itemCount }
        ?: currentFocusedIndex.takeIf { it in 0 until itemCount }
}

// BrowseGridFocusController
internal class BrowseGridFocusController(
    private val gridState: LazyGridState,
    private val itemCount: Int,
    private val columns: Int,
    private val leadingGridItemCount: Int,
    private val currentFocusedIndex: () -> Int,
    private val updateFocusedIndex: (Int) -> Unit,
    private val requestItemFocus: (Int) -> Boolean,
    private val protectedTopPx: Float,
    private val protectedBottomPx: Float,
    private val focusedItemHeightPx: Float? = null,
    private val focusScope: CoroutineScope,
    private val focusRequestJob: FocusRequestJobRef,
) {
    fun rowStartIndex(index: Int): Int {
        return if (columns > 0) (index / columns) * columns else index
    }

    fun gridIndex(index: Int): Int = index + leadingGridItemCount

    fun cancelPendingRequest() {
        focusRequestJob.cancel()
    }

    suspend fun focusItemAfterLayout(index: Int) {
        repeat(8) {
            withFrameNanos { }
            if (requestItemFocus(index)) return
        }
    }

    suspend fun focusItemWhenVisible(index: Int) {
        if (index !in 0 until itemCount) return
        val focusedImmediately = requestItemFocus(index)
        gridState.requestGridItemIntoFocusPosition(index)
        if (!focusedImmediately) {
            focusItemAfterLayout(index)
        }
    }

    fun moveFocusTo(index: Int): Boolean {
        if (index !in 0 until itemCount) return false
        cancelPendingRequest()
        val sourceIndex = currentFocusedIndex().takeIf { it in 0 until itemCount }
        val verticalMove = sourceIndex == null || rowStartIndex(index) != rowStartIndex(sourceIndex)
        updateFocusedIndex(index)
        val focusedImmediately = requestItemFocus(index)

        if (!verticalMove && focusedImmediately) {
            return true
        }

        if (verticalMove) {
            gridState.requestGridItemIntoFocusPosition(index)
        } else {
            gridState.scrollToItemIfNeeded(scrollIndexForRowStart(rowStartIndex(index)), 0)
        }
        if (!focusedImmediately) {
            focusRequestJob.requestFocusWhenReady(
                index = index,
                focusScope = focusScope,
                requestItemFocus = requestItemFocus,
            )
        }
        return true
    }

    private fun scrollIndexForRowStart(rowStart: Int): Int {
        return if (rowStart <= 0) 0 else gridIndex(rowStart)
    }

    private fun LazyGridState.requestGridItemIntoFocusPosition(index: Int) {
        val targetRowStart = rowStartIndex(index)
        if (targetRowStart == 0) {
            scrollToItemIfNeeded(0, 0)
            return
        }

        val scrollOffset = focusedGridScrollOffsetPx(
            itemHeight = focusedItemHeightPx ?: 0f,
            containerHeight = layoutInfo.viewportSize.height.toFloat(),
            protectedTopPx = protectedTopPx,
            protectedBottomPx = protectedBottomPx,
        )
        scrollToItemIfNeeded(scrollIndexForRowStart(targetRowStart), scrollOffset)
    }
}

internal fun browseGridFocusController(
    gridState: LazyGridState,
    itemFocusRequesters: List<FocusRequester>,
    columns: Int,
    leadingGridItemCount: Int,
    currentFocusedIndex: () -> Int,
    updateFocusedIndex: (Int) -> Unit,
    protectedTopPx: Float,
    protectedBottomPx: Float,
    focusedItemHeightPx: Float,
    focusScope: CoroutineScope,
    focusRequestJob: FocusRequestJobRef,
): BrowseGridFocusController {
    return BrowseGridFocusController(
        gridState = gridState,
        itemCount = itemFocusRequesters.size,
        columns = columns,
        leadingGridItemCount = leadingGridItemCount,
        currentFocusedIndex = currentFocusedIndex,
        updateFocusedIndex = updateFocusedIndex,
        requestItemFocus = itemFocusRequesters::requestBrowseGridItemFocus,
        protectedTopPx = protectedTopPx,
        protectedBottomPx = protectedBottomPx,
        focusedItemHeightPx = focusedItemHeightPx,
        focusScope = focusScope,
        focusRequestJob = focusRequestJob,
    )
}

private fun List<FocusRequester>.requestBrowseGridItemFocus(index: Int): Boolean {
    val requester = getOrNull(index) ?: return false
    return requester.requestFocusSafely()
}

private fun LazyGridState.scrollToItemIfNeeded(index: Int, scrollOffset: Int) {
    if (firstVisibleItemIndex == index && firstVisibleItemScrollOffset == scrollOffset) return
    requestScrollToItem(index, scrollOffset)
}

private fun focusedGridScrollOffsetPx(
    itemHeight: Float,
    containerHeight: Float,
    protectedTopPx: Float,
    protectedBottomPx: Float,
): Int {
    if (containerHeight <= 0f || itemHeight <= 0f) return 0
    val safeTop = protectedTopPx.coerceIn(0f, containerHeight)
    val safeBottom = (containerHeight - protectedBottomPx.coerceAtLeast(0f)).coerceIn(safeTop, containerHeight)
    val safeHeight = safeBottom - safeTop
    if (safeHeight <= 0f) return 0

    val targetItemTop = safeTop + (safeHeight - itemHeight) / 2f
    return (-targetItemTop).roundToInt()
}

// BrowseGridLayout
internal val BrowseTvScheduleBlockGap = 10.dp
internal val BrowseGridTopContentPadding = 12.dp
internal val BrowseFocusedCardBottomGap = 20.dp
private const val BrowseTouchBounceOverscrollResistance = 0.48f

internal val BrowseGridHorizontalGap = 18.dp
internal val BrowseGridVerticalGap = 22.dp

internal fun browseGridHorizontalContentPadding(maxWidth: Dp): Dp {
    return if (maxWidth >= 720.dp) {
        BrowseChromeWideHorizontalPadding
    } else {
        BrowseChromePhoneHorizontalPadding
    }
}

internal fun browseGridItemHeight(
    maxWidth: Dp,
    columns: Int,
    horizontalPadding: Dp,
): Dp {
    if (columns <= 0 || maxWidth <= 0.dp) return 0.dp
    val horizontalGaps = BrowseGridHorizontalGap * (columns - 1).coerceAtLeast(0).toFloat()
    val itemWidth = ((maxWidth - horizontalPadding * 2f - horizontalGaps) / columns.toFloat())
        .coerceAtLeast(0.dp)
    return itemWidth / AnimeCardPosterAspectRatio
}

internal fun browseGridFocusedCardTopInset(
    contentTopPadding: Dp,
    maxWidth: Dp,
): Dp {
    if (contentTopPadding <= 0.dp) return 0.dp
    return if (maxWidth >= 720.dp) {
        BrowseTvSectionIndicatorHeight + BrowseFocusedCardBottomGap
    } else {
        contentTopPadding
    }
}

internal fun browseGridFocusedCardBottomPadding(
    maxWidth: Dp,
    maxHeight: Dp,
    columns: Int,
    horizontalPadding: Dp,
    topInset: Dp,
    bottomInset: Dp,
    basePadding: Dp,
): Dp {
    if (columns <= 0 || maxWidth <= 0.dp || maxHeight <= 0.dp) return basePadding
    val itemHeight = browseGridItemHeight(
        maxWidth = maxWidth,
        columns = columns,
        horizontalPadding = horizontalPadding,
    )
    val safeHeight = (maxHeight - topInset - bottomInset).coerceAtLeast(0.dp)
    if (itemHeight <= 0.dp || safeHeight <= 0.dp) return basePadding

    val targetCenter = topInset + safeHeight / 2f
    val requiredPadding = maxHeight - targetCenter - itemHeight / 2f
    return maxOf(basePadding, requiredPadding.coerceAtLeast(0.dp))
}

@Composable
internal fun Modifier.browseTouchBounceOverscroll(
    enabled: Boolean,
    gridState: LazyGridState,
): Modifier {
    if (!enabled) return this

    val scope = rememberCoroutineScope()
    val offsetPx = remember { mutableFloatStateOf(0f) }
    val reboundJobRef = remember { arrayOfNulls<Job>(1) }
    val reboundSpec = remember {
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        )
    }

    fun cancelRebound() {
        reboundJobRef[0]?.cancel()
        reboundJobRef[0] = null
    }

    fun startRebound() {
        val start = offsetPx.floatValue
        if (abs(start) <= 0.5f) {
            offsetPx.floatValue = 0f
            return
        }
        cancelRebound()
        reboundJobRef[0] = scope.launch {
            val animatable = Animatable(start)
            animatable.animateTo(0f, reboundSpec) {
                offsetPx.floatValue = value
            }
            offsetPx.floatValue = 0f
        }
    }

    fun consumePull(deltaY: Float): Float {
        if (deltaY == 0f) return 0f
        val current = offsetPx.floatValue
        val pullingPastTop = deltaY > 0f && !gridState.canScrollBackward
        val pullingPastBottom = deltaY < 0f && !gridState.canScrollForward
        if (!pullingPastTop && !pullingPastBottom) return 0f

        cancelRebound()
        offsetPx.floatValue = current + deltaY * BrowseTouchBounceOverscrollResistance
        return deltaY
    }

    fun consumeReturn(deltaY: Float): Float {
        val current = offsetPx.floatValue
        if (current == 0f || deltaY == 0f) return 0f
        val returnsFromTop = current > 0f && deltaY < 0f
        val returnsFromBottom = current < 0f && deltaY > 0f
        if (!returnsFromTop && !returnsFromBottom) return 0f

        cancelRebound()
        val proposed = current + deltaY
        val consumed = when {
            current > 0f && proposed < 0f -> -current
            current < 0f && proposed > 0f -> -current
            else -> deltaY
        }
        offsetPx.floatValue = current + consumed
        return consumed
    }

    val connection = remember(gridState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val consumedY = consumeReturn(available.y)
                return if (consumedY != 0f) Offset(x = 0f, y = consumedY) else Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val consumedY = consumePull(available.y)
                return if (consumedY != 0f) Offset(x = 0f, y = consumedY) else Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offsetPx.floatValue == 0f) return Velocity.Zero
                startRebound()
                return Velocity(x = 0f, y = available.y)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (offsetPx.floatValue == 0f) return Velocity.Zero
                startRebound()
                return Velocity(x = 0f, y = available.y)
            }
        }
    }

    return this
        .nestedScroll(connection)
        .graphicsLayer {
            translationY = offsetPx.floatValue
        }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun BrowseGridScrollLocalProvider(
    touchOverscrollEnabled: Boolean,
    content: @Composable () -> Unit,
) {
    if (touchOverscrollEnabled) {
        CompositionLocalProvider(
            LocalBringIntoViewSpec provides BrowseGridNoopBringIntoViewSpec,
            content = content,
        )
    } else {
        CompositionLocalProvider(
            LocalBringIntoViewSpec provides BrowseGridNoopBringIntoViewSpec,
            LocalOverscrollFactory provides null,
            content = content,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
private val BrowseGridNoopBringIntoViewSpec = object : BringIntoViewSpec {
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override val scrollAnimationSpec: AnimationSpec<Float> = tween(durationMillis = 0)

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

// BrowseLazyGridState
@Composable
internal fun rememberBrowseRootLazyGridState(): LazyGridState {
    return rememberSaveable(
        saver = listSaver(
            save = { state ->
                listOf(
                    state.firstVisibleItemIndex,
                    state.firstVisibleItemScrollOffset,
                )
            },
            restore = { values ->
                LazyGridState(
                    firstVisibleItemIndex = values.getOrElse(0) { 0 },
                    firstVisibleItemScrollOffset = values.getOrElse(1) { 0 },
                )
            },
        ),
    ) {
        LazyGridState()
    }
}
