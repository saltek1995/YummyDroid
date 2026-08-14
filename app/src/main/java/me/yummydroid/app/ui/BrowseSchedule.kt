package me.yummydroid.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.LoadState
import me.yummydroid.app.data.PosterCardSize
import me.yummydroid.app.data.ScheduleAnime

// BrowseScheduleFilter
internal fun upcomingScheduleItems(
    items: List<ScheduleAnime>,
    nowSeconds: Long = System.currentTimeMillis() / 1000L,
): List<ScheduleAnime> = items.filter { item -> item.nextEpisodeAtSeconds > nowSeconds }

// BrowseScheduleGrid
@Composable
internal fun ScheduleSection(
    state: LoadState<List<ScheduleAnime>>,
    precomputedDayGroups: List<ScheduleDayGroup>? = null,
    gridState: LazyGridState,
    cardSize: PosterCardSize,
    locale: Locale,
    focusFirstRequest: FocusFirstRequest,
    focusCurrentRequestNonce: Long,
    calendarFocusRequestNonce: Long = 0L,
    contentFocusEnabled: Boolean = true,
    showCalendarInGrid: Boolean = true,
    selectedEpochDay: Long,
    onSelectedEpochDayChange: (Long) -> Unit,
    currentFocusedIndex: () -> Int,
    onFocusedIndexChange: (Int) -> Unit,
    pinnedTopPadding: Dp = 0.dp,
    contentBottomPadding: Dp = 0.dp,
    onRegisterBackToTopHandler: ((HomeBackToTopHandler?) -> Unit)? = null,
    onRetry: () -> Unit,
    onExitHorizontalDirection: (VisualGridDirection) -> Boolean = { true },
    onExitUp: () -> Boolean = { false },
    onExitDown: () -> Boolean = { false },
    onOpenAnime: (Long) -> Unit,
) {
    when (state) {
        LoadState.Loading -> LoadingPane(Modifier.fillMaxSize())
        is LoadState.Error -> ErrorPane(
            message = state.message,
            onRetry = onRetry,
            modifier = Modifier.fillMaxSize(),
        )
        is LoadState.Ready -> ScheduleReadySection(
            schedule = state.data,
            precomputedDayGroups = precomputedDayGroups,
            gridState = gridState,
            cardSize = cardSize,
            locale = locale,
            focusFirstRequest = focusFirstRequest,
            focusCurrentRequestNonce = focusCurrentRequestNonce,
            calendarFocusRequestNonce = calendarFocusRequestNonce,
            contentFocusEnabled = contentFocusEnabled,
            showCalendarInGrid = showCalendarInGrid,
            selectedEpochDay = selectedEpochDay,
            onSelectedEpochDayChange = onSelectedEpochDayChange,
            currentFocusedIndex = currentFocusedIndex,
            onFocusedIndexChange = onFocusedIndexChange,
            pinnedTopPadding = pinnedTopPadding,
            contentBottomPadding = contentBottomPadding,
            onRegisterBackToTopHandler = onRegisterBackToTopHandler,
            onExitHorizontalDirection = onExitHorizontalDirection,
            onExitUp = onExitUp,
            onExitDown = onExitDown,
            onOpenAnime = onOpenAnime,
        )
    }
}

// BrowseScheduleReadyContent
@Composable
internal fun ScheduleReadyContent(
    params: ScheduleReadyParams,
    data: ScheduleReadyData,
    layout: ScheduleReadyLayout,
    actions: ScheduleReadyActions,
    internalCalendarFocusRequestNonce: Long,
    suppressCalendarFocusAfterBackToTop: Boolean,
) {
    if (params.schedule.isEmpty()) {
        EmptyPane(message = uiText(UiStringKey.ScheduleIsEmpty), modifier = Modifier.fillMaxSize())
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (data.dayGroups.isEmpty() || data.visibleItems.isEmpty()) {
            ScheduleNoUpcomingReleases(params)
        } else {
            ScheduleReadyGrid(
                params = params,
                data = data,
                layout = layout,
                actions = actions,
                internalCalendarFocusRequestNonce = internalCalendarFocusRequestNonce,
                suppressCalendarFocusAfterBackToTop = suppressCalendarFocusAfterBackToTop,
            )
        }
    }
}

@Composable
private fun ScheduleNoUpcomingReleases(params: ScheduleReadyParams) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = params.pinnedTopPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = uiText(UiStringKey.NoUpcomingReleasesYet),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ScheduleReadyGrid(
    params: ScheduleReadyParams,
    data: ScheduleReadyData,
    layout: ScheduleReadyLayout,
    actions: ScheduleReadyActions,
    internalCalendarFocusRequestNonce: Long,
    suppressCalendarFocusAfterBackToTop: Boolean,
) {
    BrowseGridScrollLocalProvider(touchOverscrollEnabled = layout.touchOverscrollEnabled) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(layout.columnsCount),
            state = params.gridState,
            modifier = Modifier
                .fillMaxSize()
                .browseTouchBounceOverscroll(
                    enabled = layout.touchOverscrollEnabled,
                    gridState = params.gridState,
                )
                .focusGroup(),
            contentPadding = PaddingValues(
                start = layout.gridHorizontalPadding,
                top = layout.gridTopContentPadding,
                end = layout.gridHorizontalPadding,
                bottom = layout.gridBottomContentPadding,
            ),
            horizontalArrangement = Arrangement.spacedBy(BrowseGridHorizontalGap),
            verticalArrangement = Arrangement.spacedBy(layout.gridVerticalGap),
        ) {
            scheduleCalendarItem(
                params = params,
                data = data,
                actions = actions,
                internalFocusRequestNonce = internalCalendarFocusRequestNonce,
                suppressFocusAfterBackToTop = suppressCalendarFocusAfterBackToTop,
            )
            scheduleCards(params, data, layout, actions)
        }
    }
}

private fun LazyGridScope.scheduleCalendarItem(
    params: ScheduleReadyParams,
    data: ScheduleReadyData,
    actions: ScheduleReadyActions,
    internalFocusRequestNonce: Long,
    suppressFocusAfterBackToTop: Boolean,
) {
    if (!params.showCalendarInGrid) return
    item(
        key = "schedule-calendar",
        span = { GridItemSpan(maxLineSpan) },
        contentType = "schedule-calendar",
    ) {
        ScheduleCalendarBlock(
            dayGroups = data.dayGroups,
            selectedEpochDay = data.selectedGroup?.epochDay ?: Long.MIN_VALUE,
            locale = params.locale,
            focusRequestNonce = params.calendarFocusRequestNonce * 1_000_000L + internalFocusRequestNonce,
            focusEnabled = params.contentFocusEnabled && !suppressFocusAfterBackToTop,
            onExitUp = params.onExitUp,
            onExitDown = actions::requestContentFocus,
            onSelectDay = actions::selectDay,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun LazyGridScope.scheduleCards(
    params: ScheduleReadyParams,
    data: ScheduleReadyData,
    layout: ScheduleReadyLayout,
    actions: ScheduleReadyActions,
) {
    itemsIndexed(
        items = data.visibleItems,
        key = { _, item -> item.anime.id },
        contentType = { _, _ -> "schedule-card" },
    ) { index, item ->
        ScheduleRow(
            item = item,
            timeFormatter = data.timeFormatter,
            onOpenAnime = params.onOpenAnime,
            modifier = Modifier
                .focusProperties { canFocus = params.contentFocusEnabled }
                .focusRequester(layout.itemFocusRequesters[index])
                .onPreviewKeyEvent { event ->
                    event.type == KeyEventType.KeyDown && actions.handleGridDirection(index, event.key)
                }
                .onFocusChanged { focusState ->
                    if (focusState.hasFocus) actions.updateFocusedIndex(index)
                },
        )
    }
}

// BrowseScheduleReadyEffects
@Composable
internal fun ScheduleReadyEffects(
    params: ScheduleReadyParams,
    data: ScheduleReadyData,
    layout: ScheduleReadyLayout,
    actions: ScheduleReadyActions,
    focusController: BrowseGridFocusController,
    handledPersistentFocusResetNonce: Long,
    onHandledPersistentFocusResetNonceChange: (Long) -> Unit,
    handledTransientFocusResetNonce: Long,
    onHandledTransientFocusResetNonceChange: (Long) -> Unit,
    handledCurrentFocusRequestNonce: Long,
    onHandledCurrentFocusRequestNonceChange: (Long) -> Unit,
) {
    ScheduleDaySelectionEffect(params, data, actions)
    ScheduleBackToTopRegistrationEffect(params, data, actions)
    ScheduleFocusEffect(
        params = params,
        data = data,
        layout = layout,
        actions = actions,
        focusController = focusController,
        handledPersistentNonce = handledPersistentFocusResetNonce,
        onHandledPersistentNonceChange = onHandledPersistentFocusResetNonceChange,
        handledTransientNonce = handledTransientFocusResetNonce,
        onHandledTransientNonceChange = onHandledTransientFocusResetNonceChange,
        handledCurrentNonce = handledCurrentFocusRequestNonce,
        onHandledCurrentNonceChange = onHandledCurrentFocusRequestNonceChange,
    )
    ScheduleFocusedIndexEffect(params, data, actions)
}

@Composable
private fun ScheduleDaySelectionEffect(
    params: ScheduleReadyParams,
    data: ScheduleReadyData,
    actions: ScheduleReadyActions,
) {
    LaunchedEffect(data.dayGroupKeys) {
        if (data.dayGroups.isEmpty()) {
            params.onSelectedEpochDayChange(Long.MIN_VALUE)
            actions.updateFocusedIndex(-1)
            return@LaunchedEffect
        }
        if (data.dayGroups.none { group -> group.epochDay == params.selectedEpochDay }) {
            val fallbackDay = data.dayGroups.todayOrClosest()?.epochDay ?: data.dayGroups.first().epochDay
            params.onSelectedEpochDayChange(fallbackDay)
            actions.updateFocusedIndex(0)
        }
    }
}

@Composable
private fun ScheduleBackToTopRegistrationEffect(
    params: ScheduleReadyParams,
    data: ScheduleReadyData,
    actions: ScheduleReadyActions,
) {
    DisposableEffect(data.visibleItems.size, params.onRegisterBackToTopHandler) {
        val register = params.onRegisterBackToTopHandler
        if (register != null && data.visibleItems.isNotEmpty()) {
            register(
                HomeBackToTopHandler(
                    section = BrowseSection.Schedule,
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
private fun ScheduleFocusEffect(
    params: ScheduleReadyParams,
    data: ScheduleReadyData,
    layout: ScheduleReadyLayout,
    actions: ScheduleReadyActions,
    focusController: BrowseGridFocusController,
    handledPersistentNonce: Long,
    onHandledPersistentNonceChange: (Long) -> Unit,
    handledTransientNonce: Long,
    onHandledTransientNonceChange: (Long) -> Unit,
    handledCurrentNonce: Long,
    onHandledCurrentNonceChange: (Long) -> Unit,
) {
    val persistentNonce = params.focusFirstRequest.persistentNonce
    val transientNonce = params.focusFirstRequest.transientNonce
    val request = scheduleFocusRequest(
        visibleItemsCount = data.visibleItems.size,
        persistentNonce = persistentNonce,
        handledPersistentNonce = handledPersistentNonce,
        transientNonce = transientNonce,
        handledTransientNonce = handledTransientNonce,
    )
    val shouldFocusCurrent = shouldRequestBrowseCurrentFocus(
        contentFocusEnabled = params.contentFocusEnabled,
        requestNonce = params.focusCurrentRequestNonce,
        handledNonce = handledCurrentNonce,
        itemCount = data.visibleItems.size,
    )
    UiControlEffect(
        params.focusFirstRequest,
        params.focusCurrentRequestNonce,
        data.visibleItems.size,
        layout.columnsCount,
        enabled = request.shouldFocusFirst || shouldFocusCurrent,
    ) {
        focusController.cancelPendingRequest()
        if (request.shouldFocusFirst) {
            focusFirstScheduleItem(
                request = request,
                actions = actions,
                focusController = focusController,
                focusCurrentRequestNonce = params.focusCurrentRequestNonce,
                shouldFocusCurrent = shouldFocusCurrent,
                onHandledPersistentNonceChange = onHandledPersistentNonceChange,
                onHandledTransientNonceChange = onHandledTransientNonceChange,
                onHandledCurrentNonceChange = onHandledCurrentNonceChange,
            )
            return@UiControlEffect
        }
        focusCurrentScheduleItem(
            params = params,
            data = data,
            layout = layout,
            actions = actions,
            focusController = focusController,
            onHandledCurrentNonceChange = onHandledCurrentNonceChange,
        )
    }
}

private data class ScheduleFocusRequest(
    val persistentNonce: Long,
    val transientNonce: Long,
    val handlePersistent: Boolean,
    val handleTransient: Boolean,
) {
    val shouldFocusFirst: Boolean = handlePersistent || handleTransient
}

private fun scheduleFocusRequest(
    visibleItemsCount: Int,
    persistentNonce: Long,
    handledPersistentNonce: Long,
    transientNonce: Long,
    handledTransientNonce: Long,
): ScheduleFocusRequest {
    val hasVisibleItems = visibleItemsCount > 0
    return ScheduleFocusRequest(
        persistentNonce = persistentNonce,
        transientNonce = transientNonce,
        handlePersistent = hasVisibleItems && persistentNonce.isUnhandledFocusNonce(handledPersistentNonce),
        handleTransient = hasVisibleItems && transientNonce.isUnhandledFocusNonce(handledTransientNonce),
    )
}

private fun Long.isUnhandledFocusNonce(handledNonce: Long): Boolean {
    return this > 0L && this != handledNonce
}

private suspend fun focusFirstScheduleItem(
    request: ScheduleFocusRequest,
    actions: ScheduleReadyActions,
    focusController: BrowseGridFocusController,
    focusCurrentRequestNonce: Long,
    shouldFocusCurrent: Boolean,
    onHandledPersistentNonceChange: (Long) -> Unit,
    onHandledTransientNonceChange: (Long) -> Unit,
    onHandledCurrentNonceChange: (Long) -> Unit,
) {
    actions.updateFocusedIndex(0)
    focusController.focusItemWhenVisible(0)
    if (request.handlePersistent) onHandledPersistentNonceChange(request.persistentNonce)
    if (request.handleTransient) onHandledTransientNonceChange(request.transientNonce)
    if (shouldFocusCurrent) onHandledCurrentNonceChange(focusCurrentRequestNonce)
}

private suspend fun focusCurrentScheduleItem(
    params: ScheduleReadyParams,
    data: ScheduleReadyData,
    layout: ScheduleReadyLayout,
    actions: ScheduleReadyActions,
    focusController: BrowseGridFocusController,
    onHandledCurrentNonceChange: (Long) -> Unit,
) {
    withFrameNanos { }
    val targetIndex = scheduleCurrentFocusTargetIndex(
        currentFocusedIndex = params.currentFocusedIndex(),
        firstVisibleItemIndex = params.gridState.firstVisibleItemIndex,
        leadingGridItemCount = layout.leadingGridItemCount,
        lastIndex = data.visibleItems.lastIndex,
    )
    actions.updateFocusedIndex(targetIndex)
    focusController.focusItemWhenVisible(targetIndex)
    onHandledCurrentNonceChange(params.focusCurrentRequestNonce)
}

private fun scheduleCurrentFocusTargetIndex(
    currentFocusedIndex: Int,
    firstVisibleItemIndex: Int,
    leadingGridItemCount: Int,
    lastIndex: Int,
): Int {
    val focusedGridIndex = currentFocusedIndex.takeIf { index -> index in 0..lastIndex }
    return (
        focusedGridIndex ?: (firstVisibleItemIndex - leadingGridItemCount)
            .coerceIn(0, lastIndex)
        ).coerceIn(0, lastIndex)
}

@Composable
private fun ScheduleFocusedIndexEffect(
    params: ScheduleReadyParams,
    data: ScheduleReadyData,
    actions: ScheduleReadyActions,
) {
    LaunchedEffect(data.visibleItems.size) {
        actions.updateFocusedIndex(
            normalizedScheduleFocusedIndex(
                itemCount = data.visibleItems.size,
                currentIndex = params.currentFocusedIndex(),
            ),
        )
    }
}

// BrowseScheduleReadyGrid
@Composable
internal fun ScheduleReadyGridRoot(params: ScheduleReadyParams) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val data = rememberScheduleReadyData(params)
        val layout = rememberScheduleReadyLayout(params, data, maxWidth, maxHeight)
        ScheduleReadyCoordinator(params, data, layout)
    }
}

// BrowseScheduleReadyRuntime
@Composable
internal fun ScheduleReadyCoordinator(
    params: ScheduleReadyParams,
    data: ScheduleReadyData,
    layout: ScheduleReadyLayout,
) {
    val focusScope = rememberCoroutineScope()
    val uiControls = LocalUiControlCoordinator.current
    var internalCalendarFocusRequestNonce by remember(data.scheduleDayKey) { mutableLongStateOf(0L) }
    var handledPersistentFocusResetNonce by remember { mutableLongStateOf(0L) }
    var handledTransientFocusResetNonce by remember { mutableLongStateOf(0L) }
    var handledCurrentFocusRequestNonce by remember { mutableLongStateOf(0L) }
    var suppressCalendarFocusAfterBackToTop by remember(data.scheduleDayKey) { mutableStateOf(false) }
    val focusRequestJob = remember(layout.columnsCount, uiControls) { FocusRequestJobRef(uiControls) }
    val updateFocusedIndex = { index: Int ->
        if (params.currentFocusedIndex() != index) params.onFocusedIndexChange(index)
    }
    val focusController = browseGridFocusController(
        gridState = params.gridState,
        itemFocusRequesters = layout.itemFocusRequesters,
        columns = layout.columnsCount,
        leadingGridItemCount = layout.leadingGridItemCount,
        currentFocusedIndex = params.currentFocusedIndex,
        updateFocusedIndex = updateFocusedIndex,
        protectedTopPx = layout.focusedGridTopInsetPx,
        protectedBottomPx = layout.focusedGridBottomInsetPx,
        focusedItemHeightPx = layout.focusedGridItemHeightPx,
        focusScope = focusScope,
        focusRequestJob = focusRequestJob,
    )
    val actions = ScheduleReadyActions(
        params = params,
        data = data,
        layout = layout,
        focusController = focusController,
        focusScope = focusScope,
        uiControls = uiControls,
        setSuppressCalendarFocusAfterBackToTop = { suppressCalendarFocusAfterBackToTop = it },
        incrementCalendarFocusNonce = { internalCalendarFocusRequestNonce += 1L },
    )

    ScheduleReadyEffects(
        params = params,
        data = data,
        layout = layout,
        actions = actions,
        focusController = focusController,
        handledPersistentFocusResetNonce = handledPersistentFocusResetNonce,
        onHandledPersistentFocusResetNonceChange = { handledPersistentFocusResetNonce = it },
        handledTransientFocusResetNonce = handledTransientFocusResetNonce,
        onHandledTransientFocusResetNonceChange = { handledTransientFocusResetNonce = it },
        handledCurrentFocusRequestNonce = handledCurrentFocusRequestNonce,
        onHandledCurrentFocusRequestNonceChange = { handledCurrentFocusRequestNonce = it },
    )
    ScheduleReadyContent(
        params = params,
        data = data,
        layout = layout,
        actions = actions,
        internalCalendarFocusRequestNonce = internalCalendarFocusRequestNonce,
        suppressCalendarFocusAfterBackToTop = suppressCalendarFocusAfterBackToTop,
    )
}

// BrowseScheduleReadySection
@Composable
internal fun ScheduleReadySection(
    schedule: List<ScheduleAnime>,
    precomputedDayGroups: List<ScheduleDayGroup>? = null,
    gridState: LazyGridState,
    cardSize: PosterCardSize,
    locale: Locale,
    focusFirstRequest: FocusFirstRequest,
    focusCurrentRequestNonce: Long,
    calendarFocusRequestNonce: Long = 0L,
    contentFocusEnabled: Boolean = true,
    showCalendarInGrid: Boolean = true,
    selectedEpochDay: Long,
    onSelectedEpochDayChange: (Long) -> Unit,
    currentFocusedIndex: () -> Int,
    onFocusedIndexChange: (Int) -> Unit,
    pinnedTopPadding: Dp = 0.dp,
    contentBottomPadding: Dp = 0.dp,
    onRegisterBackToTopHandler: ((HomeBackToTopHandler?) -> Unit)? = null,
    onExitHorizontalDirection: (VisualGridDirection) -> Boolean = { true },
    onExitUp: () -> Boolean = { false },
    onExitDown: () -> Boolean = { false },
    onOpenAnime: (Long) -> Unit,
) {
    ScheduleReadyGridRoot(
        ScheduleReadyParams(
            schedule = schedule,
            precomputedDayGroups = precomputedDayGroups,
            gridState = gridState,
            cardSize = cardSize,
            locale = locale,
            focusFirstRequest = focusFirstRequest,
            focusCurrentRequestNonce = focusCurrentRequestNonce,
            calendarFocusRequestNonce = calendarFocusRequestNonce,
            contentFocusEnabled = contentFocusEnabled,
            showCalendarInGrid = showCalendarInGrid,
            selectedEpochDay = selectedEpochDay,
            onSelectedEpochDayChange = onSelectedEpochDayChange,
            currentFocusedIndex = currentFocusedIndex,
            onFocusedIndexChange = onFocusedIndexChange,
            pinnedTopPadding = pinnedTopPadding,
            contentBottomPadding = contentBottomPadding,
            onRegisterBackToTopHandler = onRegisterBackToTopHandler,
            onExitHorizontalDirection = onExitHorizontalDirection,
            onExitUp = onExitUp,
            onExitDown = onExitDown,
            onOpenAnime = onOpenAnime,
        ),
    )
}

// BrowseScheduleReadyState
internal data class ScheduleReadyParams(
    val schedule: List<ScheduleAnime>,
    val precomputedDayGroups: List<ScheduleDayGroup>?,
    val gridState: LazyGridState,
    val cardSize: PosterCardSize,
    val locale: Locale,
    val focusFirstRequest: FocusFirstRequest,
    val focusCurrentRequestNonce: Long,
    val calendarFocusRequestNonce: Long,
    val contentFocusEnabled: Boolean,
    val showCalendarInGrid: Boolean,
    val selectedEpochDay: Long,
    val onSelectedEpochDayChange: (Long) -> Unit,
    val currentFocusedIndex: () -> Int,
    val onFocusedIndexChange: (Int) -> Unit,
    val pinnedTopPadding: Dp,
    val contentBottomPadding: Dp,
    val onRegisterBackToTopHandler: ((HomeBackToTopHandler?) -> Unit)?,
    val onExitHorizontalDirection: (VisualGridDirection) -> Boolean,
    val onExitUp: () -> Boolean,
    val onExitDown: () -> Boolean,
    val onOpenAnime: (Long) -> Unit,
)

internal data class ScheduleReadyData(
    val dayGroups: List<ScheduleDayGroup>,
    val dayGroupKeys: List<Long>,
    val selectedGroup: ScheduleDayGroup?,
    val visibleItems: List<ScheduleAnime>,
    val scheduleDayKey: Long,
    val timeFormatter: DateTimeFormatter,
)

internal data class ScheduleReadyLayout(
    val columnsCount: Int,
    val touchOverscrollEnabled: Boolean,
    val itemFocusRequesters: List<FocusRequester>,
    val focusedGridTopInsetPx: Float,
    val focusedGridBottomInsetPx: Float,
    val focusedGridItemHeightPx: Float,
    val leadingGridItemCount: Int,
    val gridTopContentPadding: Dp,
    val gridBottomContentPadding: Dp,
    val gridHorizontalPadding: Dp,
    val gridVerticalGap: Dp,
)

@Composable
internal fun rememberScheduleReadyData(params: ScheduleReadyParams): ScheduleReadyData {
    val zoneId = remember { ZoneId.systemDefault() }
    val timeFormatter = remember(params.locale) {
        DateTimeFormatter.ofPattern("HH:mm", params.locale)
    }
    val dayGroups = remember(params.schedule, zoneId, params.precomputedDayGroups) {
        params.precomputedDayGroups ?: params.schedule.toScheduleDayGroups(zoneId)
    }
    val dayGroupKeys = remember(dayGroups) { dayGroups.map { group -> group.epochDay } }
    val selectedGroup = remember(dayGroups, params.selectedEpochDay) {
        dayGroups.firstOrNull { group -> group.epochDay == params.selectedEpochDay }
            ?: dayGroups.todayOrClosest()
    }
    val visibleItems = selectedGroup?.items.orEmpty()
    return ScheduleReadyData(
        dayGroups = dayGroups,
        dayGroupKeys = dayGroupKeys,
        selectedGroup = selectedGroup,
        visibleItems = visibleItems,
        scheduleDayKey = selectedGroup?.epochDay ?: Long.MIN_VALUE,
        timeFormatter = timeFormatter,
    )
}

@Composable
internal fun rememberScheduleReadyLayout(
    params: ScheduleReadyParams,
    data: ScheduleReadyData,
    maxWidth: Dp,
    maxHeight: Dp,
): ScheduleReadyLayout {
    val responsiveWidth = currentResponsiveWindowSizeDp().width
    val columnsCount = remember(maxWidth, params.cardSize) {
        params.cardSize.resolveCatalogColumns(maxWidth.value.roundToInt())
    }
    val density = LocalDensity.current
    val focusedGridTopInset = browseGridFocusedCardTopInset(params.pinnedTopPadding, responsiveWidth)
    val focusedGridBottomInset = BrowseFocusedCardBottomGap + params.contentBottomPadding
    val horizontalPadding = browseGridHorizontalContentPadding(responsiveWidth)
    val baseBottomPadding = if (params.contentBottomPadding > 0.dp) {
        focusedGridBottomInset
    } else {
        24.dp + BrowseFocusedCardBottomGap
    }
    val itemFocusRequesters = remember(data.scheduleDayKey, data.visibleItems.size, columnsCount) {
        List(data.visibleItems.size) { FocusRequester() }
    }
    return ScheduleReadyLayout(
        columnsCount = columnsCount,
        touchOverscrollEnabled = LocalInputModeManager.current.inputMode == InputMode.Touch,
        itemFocusRequesters = itemFocusRequesters,
        focusedGridTopInsetPx = with(density) { focusedGridTopInset.toPx() },
        focusedGridBottomInsetPx = with(density) { focusedGridBottomInset.toPx() },
        focusedGridItemHeightPx = with(density) {
            browseGridItemHeight(
                maxWidth = maxWidth,
                columns = columnsCount,
                horizontalPadding = horizontalPadding,
            ).toPx()
        },
        leadingGridItemCount = if (params.showCalendarInGrid) 1 else 0,
        gridTopContentPadding = if (params.showCalendarInGrid) {
            params.pinnedTopPadding + ScheduleCalendarTopGap
        } else {
            params.pinnedTopPadding + BrowseGridTopContentPadding
        },
        gridBottomContentPadding = browseGridFocusedCardBottomPadding(
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            columns = columnsCount,
            horizontalPadding = horizontalPadding,
            topInset = focusedGridTopInset,
            bottomInset = focusedGridBottomInset,
            basePadding = baseBottomPadding,
        ),
        gridHorizontalPadding = horizontalPadding,
        gridVerticalGap = if (params.showCalendarInGrid) BrowseTvScheduleBlockGap else BrowseChromeItemGap,
    )
}

internal class ScheduleReadyActions(
    private val params: ScheduleReadyParams,
    private val data: ScheduleReadyData,
    private val layout: ScheduleReadyLayout,
    private val focusController: BrowseGridFocusController,
    private val focusScope: CoroutineScope,
    private val uiControls: UiControlCoordinator,
    private val setSuppressCalendarFocusAfterBackToTop: (Boolean) -> Unit,
    private val incrementCalendarFocusNonce: () -> Unit,
) {
    fun updateFocusedIndex(index: Int) {
        if (params.currentFocusedIndex() != index) {
            params.onFocusedIndexChange(index)
        }
    }

    fun requestCalendarFocus(): Boolean {
        if (!params.showCalendarInGrid) return params.onExitUp()
        setSuppressCalendarFocusAfterBackToTop(false)
        focusController.cancelPendingRequest()
        uiControls.launch(focusScope, this, UiControlOperation.NavigationLatest) {
            if (params.gridState.firstVisibleItemIndex != 0 || params.gridState.firstVisibleItemScrollOffset != 0) {
                params.gridState.animateScrollToItem(0, 0)
            }
            withFrameNanos { }
            incrementCalendarFocusNonce()
        }
        return true
    }

    fun requestContentFocus(): Boolean {
        setSuppressCalendarFocusAfterBackToTop(false)
        if (data.visibleItems.isEmpty()) return false
        uiControls.cancel(UiControlOperation.NavigationLatest)
        return focusController.moveFocusTo(0)
    }

    fun handleGridDirection(index: Int, key: Key): Boolean {
        return handleVisualGridNavigationKey(
            key = key,
            itemCount = data.visibleItems.size,
            columns = layout.columnsCount,
            sourceIndex = index,
            moveFocusTo = { target -> focusController.moveFocusTo(target) },
            onEdgeExit = { direction ->
                when (direction) {
                    VisualGridDirection.Left,
                    VisualGridDirection.Right -> params.onExitHorizontalDirection(direction)
                    VisualGridDirection.Up -> requestCalendarFocus()
                    VisualGridDirection.Down -> params.onExitDown()
                }
            },
        )
    }

    fun canHandleBackToTop(): Boolean {
        return params.gridState.canHandleBrowseRootBackToTop(BrowseSection.Schedule)
    }

    fun handleBackToTop(withFocus: Boolean): Boolean {
        if (!canHandleBackToTop()) return false
        focusController.cancelPendingRequest()
        if (!withFocus || data.visibleItems.isEmpty()) {
            uiControls.launch(focusScope, this, UiControlOperation.NavigationLatest) {
                params.gridState.animateScrollToItem(0, 0)
            }
            return true
        }
        updateFocusedIndex(0)
        setSuppressCalendarFocusAfterBackToTop(true)
        uiControls.launch(focusScope, this, UiControlOperation.NavigationLatest) {
            try {
                focusController.focusItemWhenVisible(0)
            } finally {
                setSuppressCalendarFocusAfterBackToTop(false)
            }
        }
        return true
    }

    fun selectDay(epochDay: Long) {
        params.onSelectedEpochDayChange(epochDay)
        updateFocusedIndex(0)
        focusController.cancelPendingRequest()
        uiControls.launch(focusScope, this, UiControlOperation.ContentScrollLatest) {
            params.gridState.animateScrollToItem(0, 0)
        }
    }
}

internal fun normalizedScheduleFocusedIndex(itemCount: Int, currentIndex: Int): Int {
    return when {
        itemCount <= 0 -> -1
        currentIndex < 0 -> 0
        currentIndex >= itemCount -> itemCount - 1
        else -> currentIndex
    }
}

internal fun shouldRequestBrowseCurrentFocus(
    contentFocusEnabled: Boolean,
    requestNonce: Long,
    handledNonce: Long,
    itemCount: Int,
): Boolean {
    if (!contentFocusEnabled || itemCount <= 0) return false
    return requestNonce > 0L && requestNonce != handledNonce
}
