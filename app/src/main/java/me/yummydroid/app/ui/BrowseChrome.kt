package me.yummydroid.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import kotlin.math.abs
import kotlin.math.roundToInt
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.R
import me.yummydroid.app.ui.components.clearFocusAfterTouch
import me.yummydroid.app.ui.components.liquidGlassBackdrop
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySizes
import me.yummydroid.app.ui.theme.YummySpacing
import me.yummydroid.app.ui.theme.yummyActionContentColor
import me.yummydroid.app.ui.theme.yummyActionSurfaceColor

@Composable
internal fun BrowseTopBarModern(
    onOpenSearch: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDownloads: () -> Unit,
    auth: AuthUiState,
    activeFilters: Int,
    activeSearch: Boolean,
    activeFiltersPanel: Boolean,
    activeSettings: Boolean,
    activeDownloads: Boolean,
    activeProfile: Boolean,
    activeDownloadCount: Int,
    forcedOfflineMode: Boolean,
    modifier: Modifier = Modifier,
    searchEnabled: Boolean = true,
    filtersEnabled: Boolean = true,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    isWide: Boolean,
    activeSection: BrowseSection,
    visibleSections: List<BrowseSection>,
    activeSectionPosition: Float? = null,
    onSectionSelected: (BrowseSection) -> Unit,
    onExitDown: (() -> Unit)? = null,
    actionsFocusRequester: FocusRequester? = null,
    sectionTabsFocusRequester: FocusRequester? = null,
    sectionTabFocusRequesters: Map<BrowseSection, FocusRequester> = emptyMap(),
    sectionTabsFocusEnabled: Boolean = true,
    showCompactControls: Boolean = true,
    collapseWhenHidden: Boolean = true,
    visible: Boolean = true,
    visibilityProgress: Float? = null,
    visibilityProgressProvider: (() -> Float)? = null,
) {
    val horizontalPadding = if (isWide) {
        BrowseChromeWideHorizontalPadding
    } else {
        BrowseChromePhoneHorizontalPadding
    }
    val stackActions = !isWide && currentWindowSizeDp().width < 360.dp

    if (isWide) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .browseTopBarVisibility(visible, collapseWhenHidden, visibilityProgress, visibilityProgressProvider)
                .browseTopBarExitDown(onExitDown)
                .statusBarsPadding()
                .padding(horizontal = horizontalPadding, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppWordmark(
                    modifier = Modifier.weight(1f),
                    height = 52.dp,
                )

                if (forcedOfflineMode) {
                    OfflineModeChip()
                }

                BrowseTopBarActions(
                    onOpenSearch = onOpenSearch,
                    onOpenFilters = onOpenFilters,
                    onOpenSettings = onOpenSettings,
                    onOpenDownloads = onOpenDownloads,
                    auth = auth,
                    activeFilters = activeFilters,
                    activeSearch = activeSearch,
                    activeFiltersPanel = activeFiltersPanel,
                    activeSettings = activeSettings,
                    activeDownloads = activeDownloads,
                    activeProfile = activeProfile,
                    activeDownloadCount = activeDownloadCount,
                    searchEnabled = searchEnabled,
                    filtersEnabled = filtersEnabled,
                    onOpenLogin = onOpenLogin,
                    onOpenProfile = onOpenProfile,
                    entryFocusRequester = actionsFocusRequester,
                    downFocusRequester = sectionTabsFocusRequester,
                    consumeUpWhenNoRequester = true,
                    consumeHorizontalEdgesWhenNoRequester = true,
                )
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .browseTopBarVisibility(visible, collapseWhenHidden, visibilityProgress, visibilityProgressProvider)
                .browseTopBarExitDown(onExitDown)
                .padding(horizontal = horizontalPadding),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
            )

            if (forcedOfflineMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    OfflineModeChip()
                }
            }

            if (showCompactControls) {
                BrowseSectionTabs(
                    activeSection = activeSection,
                    visibleSections = visibleSections,
                    activeSectionPosition = activeSectionPosition,
                    onSectionSelected = onSectionSelected,
                    sectionFocusRequesters = sectionTabFocusRequesters,
                    focusEnabled = sectionTabsFocusEnabled,
                    modifier = Modifier.fillMaxWidth(),
                )

                BrowseTopBarActions(
                    onOpenSearch = onOpenSearch,
                    onOpenFilters = onOpenFilters,
                    onOpenSettings = onOpenSettings,
                    onOpenDownloads = onOpenDownloads,
                    auth = auth,
                    activeFilters = activeFilters,
                    activeSearch = activeSearch,
                    activeFiltersPanel = activeFiltersPanel,
                    activeSettings = activeSettings,
                    activeDownloads = activeDownloads,
                    activeProfile = activeProfile,
                    activeDownloadCount = activeDownloadCount,
                    searchEnabled = searchEnabled,
                    filtersEnabled = filtersEnabled,
                    onOpenLogin = onOpenLogin,
                    onOpenProfile = onOpenProfile,
                    entryFocusRequester = actionsFocusRequester,
                    modifier = Modifier.fillMaxWidth(),
                    spreadActions = !stackActions,
                    stackActions = stackActions,
                )
            }
        }
    }
}

@Composable
private fun Modifier.browseTopBarVisibility(
    visible: Boolean,
    collapseWhenHidden: Boolean,
    visibilityProgress: Float? = null,
    visibilityProgressProvider: (() -> Float)? = null,
): Modifier {
    val animatedProgress = if (visibilityProgress == null && visibilityProgressProvider == null) {
        val animatedProgress by animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
            label = "browseTopBarVisibility",
        )
        animatedProgress
    } else {
        null
    }
    fun progress(): Float {
        return (visibilityProgressProvider?.invoke() ?: visibilityProgress ?: animatedProgress ?: 0f)
            .coerceIn(0f, 1f)
    }
    return this
        .then(if (visible) Modifier else Modifier.focusProperties { canFocus = false })
        .layout { measurable, constraints ->
            val progress = progress()
            val placeable = measurable.measure(constraints)
            val height = if (collapseWhenHidden) {
                (placeable.height * progress).roundToInt()
            } else {
                placeable.height
            }
            val offsetY = if (collapseWhenHidden) {
                height - placeable.height
            } else {
                ((progress - 1f) * placeable.height).roundToInt()
            }
            layout(width = placeable.width, height = height) {
                placeable.placeRelative(x = 0, y = offsetY)
            }
        }
        .clipToBounds()
        .graphicsLayer { alpha = if (collapseWhenHidden) progress() else 1f }
}
private fun Modifier.browseBottomTopProtectedVisibility(progress: Float): Modifier {
    val resolvedProgress = progress.coerceIn(0f, 1f)
    return this
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val height = (placeable.height * resolvedProgress).roundToInt()
            val offsetY = height - placeable.height
            layout(width = placeable.width, height = height) {
                placeable.placeRelative(x = 0, y = offsetY)
            }
        }
        .clipToBounds()
        .graphicsLayer { alpha = resolvedProgress }
}

internal val BrowseTvSectionIndicatorHeight = 56.dp
private val BrowseTvSectionIndicatorGlassExtraHeight = 96.dp
private const val BrowseTvSectionIndicatorGlassIntensity = 1.85f
internal val BrowseChromePhoneHorizontalPadding = 16.dp
internal val BrowseChromeWideHorizontalPadding = 24.dp
private val BrowseBottomBarGlassTopFadeHeight = 32.dp
internal val BrowseBottomChromeInteractiveTopPadding = BrowseBottomBarGlassTopFadeHeight + 10.dp
internal val BrowseChromeItemGap = 8.dp
private val BrowseBottomChromeItemGap = BrowseChromeItemGap
private val BrowseBottomCalendarToTabsGap = BrowseChromeItemGap
private val BrowseBottomBaseControlsFallbackHeight = 96.dp
private val BrowseSectionTabsHeight = 32.dp
private val BrowseTvSectionIndicatorHorizontalPadding = 24.dp

private fun Modifier.consumeUnhandledPointerInput(key: Any = Unit): Modifier {
    return pointerInput(key) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                event.changes.forEach { change ->
                    if (!change.isConsumed) {
                        change.consume()
                    }
                }
            }
        }
    }
}

@Composable
internal fun BrowseTvSectionIndicatorBar(
    activeSection: BrowseSection,
    visibleSections: List<BrowseSection>,
    modifier: Modifier = Modifier,
    activeSectionPosition: Float? = null,
    onSectionSelected: (BrowseSection) -> Unit,
    sectionFocusRequesters: Map<BrowseSection, FocusRequester> = emptyMap(),
    onExitUp: (() -> Boolean)? = null,
    onExitDown: (() -> Boolean)? = null,
    drawBackdrop: Boolean = true,
    backdropVisible: Boolean = true,
    backdropProgress: Float? = null,
    backdropProgressProvider: (() -> Float)? = null,
    sectionTabsFocusEnabled: Boolean = true,
    squareTopCorners: Boolean = true,
    hazeState: HazeState? = null,
) {
    val animatedBackdropAlpha = if (drawBackdrop && backdropProgress == null && backdropProgressProvider == null) {
                val animatedBackdropAlpha by animateFloatAsState(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                    label = "browseTabsBackdropAlpha",
                )
                animatedBackdropAlpha
            } else {
        null
    }
    fun backdropAlpha(): Float {
        if (!drawBackdrop) return 0f
        return (backdropProgressProvider?.invoke()
            ?: backdropProgress
            ?: animatedBackdropAlpha
            ?: if (backdropVisible) 1f else 0f)
            .coerceIn(0f, 1f)
    }
    val barHeight = if (drawBackdrop) {
        BrowseTvSectionIndicatorHeight + BrowseTvSectionIndicatorGlassExtraHeight
    } else {
        BrowseSectionTabsHeight
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight),
    ) {
        if (drawBackdrop && (backdropVisible || backdropProgress != null || backdropProgressProvider != null)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = backdropAlpha() }
                    .liquidGlassBackdrop(
                        shape = RoundedCornerShape(0.dp),
                        intensity = BrowseTvSectionIndicatorGlassIntensity,
                        hazeState = hazeState,
                        topFadeFraction = 0f,
                        bottomFadeFraction = 0.56f,
                    ),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BrowseSectionTabsHeight),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize(),
                verticalAlignment = Alignment.Top,
            ) {
                Spacer(
                    modifier = Modifier
                        .width(BrowseTvSectionIndicatorHorizontalPadding)
                        .fillMaxHeight()
                        .consumeUnhandledPointerInput("tv-tabs-left-edge"),
                )
                visibleSections.forEachIndexed { index, _ ->
                    Spacer(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                    if (index < visibleSections.lastIndex) {
                        Spacer(
                            modifier = Modifier
                                .width(YummySpacing.md)
                                .fillMaxHeight()
                                .consumeUnhandledPointerInput("tv-tabs-gap-$index"),
                        )
                    }
                }
                Spacer(
                    modifier = Modifier
                        .width(BrowseTvSectionIndicatorHorizontalPadding)
                        .fillMaxHeight()
                        .consumeUnhandledPointerInput("tv-tabs-right-edge"),
                )
            }
        }
        BrowseSectionTabs(
            activeSection = activeSection,
            visibleSections = visibleSections,
            activeSectionPosition = activeSectionPosition,
            onSectionSelected = onSectionSelected,
            sectionFocusRequesters = sectionFocusRequesters,
            onExitUp = onExitUp,
            onExitDown = onExitDown,
            squareTopCorners = squareTopCorners,
            focusEnabled = sectionTabsFocusEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = BrowseTvSectionIndicatorHorizontalPadding,
                    end = BrowseTvSectionIndicatorHorizontalPadding,
                ),
        )
    }
}

private fun Modifier.browseTopBarExitDown(onExitDown: (() -> Unit)?): Modifier {
    if (onExitDown == null) return this
    return onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
            onExitDown()
            true
        } else {
            false
        }
    }
}

@Composable
internal fun AppWordmark(
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier = modifier.height(height),
        contentAlignment = Alignment.CenterStart,
    ) {
        Image(
            painter = painterResource(R.drawable.app_wordmark),
            contentDescription = "YummyDroid",
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            modifier = Modifier
                .fillMaxHeight()
                .width(height * 5.45f),
        )
    }
}

@Composable
internal fun BrowseBottomBarModern(
    onOpenSearch: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDownloads: () -> Unit,
    auth: AuthUiState,
    activeFilters: Int,
    activeSearch: Boolean,
    activeFiltersPanel: Boolean,
    activeSettings: Boolean,
    activeDownloads: Boolean,
    activeProfile: Boolean,
    activeDownloadCount: Int,
    modifier: Modifier = Modifier,
    searchEnabled: Boolean = true,
    filtersEnabled: Boolean = true,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    activeSection: BrowseSection,
    visibleSections: List<BrowseSection>,
    activeSectionPosition: Float? = null,
    onSectionSelected: (BrowseSection) -> Unit,
    showSectionTabs: Boolean = true,
    sectionTabsFocusRequester: FocusRequester? = null,
    sectionTabFocusRequesters: Map<BrowseSection, FocusRequester> = emptyMap(),
    sectionTabsOnExitUp: (() -> Boolean)? = null,
    sectionTabsFocusEnabled: Boolean = true,
    hazeState: HazeState? = null,
    topProtectedContent: (@Composable (Modifier) -> Unit)? = null,
    topProtectedVisibilityProgress: Float? = null,
) {
    val stackActions = currentWindowSizeDp().width < 360.dp
    val bottomBarShape = RoundedCornerShape(0.dp)
    val contentTopPadding = BrowseBottomChromeInteractiveTopPadding
    val density = LocalDensity.current
    val bottomActionsFocusRequester = remember { FocusRequester() }
    var barTopRootY by remember { mutableFloatStateOf(0f) }
    var barHeightPx by remember { mutableIntStateOf(0) }
    var baseControlsHeightPx by remember { mutableIntStateOf(0) }
    var measuredPointerBlockStartY by remember { mutableStateOf<Float?>(null) }
    val hasTopProtectedContent = topProtectedContent != null
    var retainedTopProtectedContent by remember {
        mutableStateOf<(@Composable (Modifier) -> Unit)?>(null)
    }
    val animatedTopProtectedProgress = if (topProtectedVisibilityProgress == null) {
        val animatedProgress by animateFloatAsState(
            targetValue = if (hasTopProtectedContent) 1f else 0f,
            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
            label = "browseBottomTopProtectedProgress",
            finishedListener = { value ->
                if (value <= 0.001f) {
                    retainedTopProtectedContent = null
                }
            },
        )
        animatedProgress
    } else {
        0f
    }
    val topProtectedProgress = topProtectedVisibilityProgress?.coerceIn(0f, 1f)
        ?: animatedTopProtectedProgress
    val topProtectedContentForAnimation = topProtectedContent ?: retainedTopProtectedContent
    val topProtectedSlotActive =
        topProtectedContentForAnimation != null && topProtectedProgress > 0.001f
    SideEffect {
        if (topProtectedContent != null) {
            retainedTopProtectedContent = topProtectedContent
        }
    }
    LaunchedEffect(topProtectedContent, topProtectedProgress) {
        if (topProtectedContent == null && topProtectedProgress <= 0.001f) {
            retainedTopProtectedContent = null
        }
    }
    LaunchedEffect(topProtectedSlotActive, showSectionTabs) {
        measuredPointerBlockStartY = null
    }
    val pointerBlockStartY = measuredPointerBlockStartY ?: with(density) { contentTopPadding.toPx() }
    val pointerBlockHeight: Dp = with(density) {
        (barHeightPx - pointerBlockStartY).coerceAtLeast(0f).toDp()
    }
    val baseControlsHeight = if (baseControlsHeightPx > 0) {
        with(density) { baseControlsHeightPx.toDp() }
    } else {
        contentTopPadding + BrowseBottomBaseControlsFallbackHeight
    }
    val baseControlsContentHeight = (baseControlsHeight - contentTopPadding)
        .coerceAtLeast(0.dp)
    fun Modifier.pointerBlockStartAnchor(): Modifier {
        return onGloballyPositioned { coordinates ->
            measuredPointerBlockStartY = (coordinates.positionInRoot().y - barTopRootY).coerceAtLeast(0f)
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { size ->
                barHeightPx = size.height
            }
            .onGloballyPositioned { coordinates ->
                barTopRootY = coordinates.positionInRoot().y
            },
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .liquidGlassBackdrop(
                    shape = bottomBarShape,
                    intensity = 1.12f,
                    hazeState = hazeState,
                    topFadeFraction = 0.36f,
                ),
        )
        if (pointerBlockHeight > 0.dp) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(pointerBlockHeight)
                    .consumeUnhandledPointerInput(pointerBlockHeight),
            )
        }
        if (topProtectedContentForAnimation != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = baseControlsContentHeight + BrowseBottomCalendarToTabsGap,
                    )
                    .browseBottomTopProtectedVisibility(topProtectedProgress)
                    .pointerBlockStartAnchor(),
            ) {
                topProtectedContentForAnimation?.invoke(Modifier.fillMaxWidth())
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { size ->
                    baseControlsHeightPx = size.height
                }
                .padding(
                    start = 16.dp,
                    top = contentTopPadding,
                    end = 16.dp,
                    bottom = BrowseBottomChromeItemGap,
                ),
        ) {
            if (showSectionTabs) {
                BrowseSectionTabs(
                    activeSection = activeSection,
                    visibleSections = visibleSections,
                    activeSectionPosition = activeSectionPosition,
                    onSectionSelected = onSectionSelected,
                    sectionFocusRequesters = if (sectionTabFocusRequesters.isNotEmpty()) {
                        sectionTabFocusRequesters
                    } else {
                        sectionTabsFocusRequester?.let { requester -> mapOf(activeSection to requester) }.orEmpty()
                    },
                    onExitUp = sectionTabsOnExitUp,
                    onExitDown = {
                        bottomActionsFocusRequester.requestFocusSafely()
                    },
                    focusEnabled = sectionTabsFocusEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (topProtectedSlotActive) {
                                Modifier
                            } else {
                                Modifier.pointerBlockStartAnchor()
                            },
                        ),
                )
            }
            if (showSectionTabs) {
                Spacer(modifier = Modifier.height(BrowseBottomChromeItemGap))
            }
            BrowseTopBarActions(
                onOpenSearch = onOpenSearch,
                onOpenFilters = onOpenFilters,
                onOpenSettings = onOpenSettings,
                onOpenDownloads = onOpenDownloads,
                auth = auth,
                activeFilters = activeFilters,
                activeSearch = activeSearch,
                activeFiltersPanel = activeFiltersPanel,
                activeSettings = activeSettings,
                activeDownloads = activeDownloads,
                activeProfile = activeProfile,
                activeDownloadCount = activeDownloadCount,
                searchEnabled = searchEnabled,
                filtersEnabled = filtersEnabled,
                onOpenLogin = onOpenLogin,
                onOpenProfile = onOpenProfile,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (showSectionTabs || topProtectedSlotActive) {
                            Modifier
                        } else {
                            Modifier.pointerBlockStartAnchor()
                        },
                    ),
                spreadActions = !stackActions,
                stackActions = stackActions,
                entryFocusRequester = bottomActionsFocusRequester,
                upFocusRequester = sectionTabsFocusRequester,
                consumeDownWhenNoRequester = true,
                consumeHorizontalEdgesWhenNoRequester = true,
            )
        }
    }
}
@Composable
internal fun BrowseSectionTabs(
    activeSection: BrowseSection,
    visibleSections: List<BrowseSection>,
    modifier: Modifier = Modifier,
    activeSectionPosition: Float? = null,
    onSectionSelected: (BrowseSection) -> Unit,
    sectionFocusRequesters: Map<BrowseSection, FocusRequester> = emptyMap(),
    onExitUp: (() -> Boolean)? = null,
    onExitDown: (() -> Boolean)? = null,
    squareTopCorners: Boolean = false,
    focusEnabled: Boolean = true,
) {
    val activePosition = activeSectionPosition
        ?: visibleSections.indexOf(activeSection).takeIf { it >= 0 }?.toFloat()
    var focusedSection by remember(visibleSections) { mutableStateOf<BrowseSection?>(null) }
    Row(
        modifier = modifier
            .height(BrowseSectionTabsHeight)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> {
                        if (onExitUp == null) {
                            false
                        } else {
                            onExitUp()
                            true
                        }
                    }
                    Key.DirectionDown -> {
                        if (onExitDown == null) {
                            false
                        } else {
                            onExitDown()
                            true
                        }
                    }
                    Key.DirectionLeft -> {
                        val focusedIndex = focusedSection?.let { section ->
                            visibleSections.indexOf(section)
                        } ?: -1
                        focusEnabled &&
                            focusedIndex == 0
                    }
                    Key.DirectionRight -> {
                        val focusedIndex = focusedSection?.let { section ->
                            visibleSections.indexOf(section)
                        } ?: -1
                        focusEnabled &&
                            focusedIndex == visibleSections.lastIndex
                    }
                    else -> false
                }
            },
        horizontalArrangement = Arrangement.spacedBy(YummySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        visibleSections.forEachIndexed { index, section ->
            var focused by remember { mutableStateOf(false) }
            LaunchedEffect(focusEnabled) {
                if (!focusEnabled) {
                    focused = false
                    if (focusedSection == section) {
                        focusedSection = null
                    }
                }
            }
            val inputModeManager = LocalInputModeManager.current
            val focusVisible = focusEnabled && focused && inputModeManager.inputMode != InputMode.Touch
            val selectedFraction = activePosition
                ?.let { position -> (1f - abs(position - index)).coerceIn(0f, 1f) }
                ?: 0f
            val surfaceColor = if (focusVisible) {
                yummyActionSurfaceColor(focused = true)
            } else {
                yummyActionSurfaceColor()
            }
            val contentColor = if (focusVisible) {
                yummyActionContentColor(focused = true)
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.96f)
            }
            val shape = if (squareTopCorners) {
                RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomEnd = 7.dp, bottomStart = 7.dp)
            } else {
                RoundedCornerShape(7.dp)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(
                        sectionFocusRequesters[section]?.let { requester ->
                            Modifier.focusRequester(requester)
                        } ?: run {
                            Modifier
                        },
                    )
                    .onFocusChanged { focusState ->
                        val hasFocus = focusState.isFocused || focusState.hasFocus
                        focused = hasFocus
                        if (hasFocus) {
                            focusedSection = section
                        } else if (focusedSection == section) {
                            focusedSection = null
                        }
                    }
                    .focusProperties { canFocus = focusEnabled }
                    .clearFocusAfterTouch()
                    .clip(shape)
                    .background(
                        color = surfaceColor,
                        shape = shape,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSectionSelected(section) },
            ) {
                Text(
                    text = section.localizedTitle(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(horizontal = YummySpacing.xs),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = selectedFraction),
                            shape = RoundedCornerShape(1.dp),
                        ),
                )
            }
        }
    }
}

@Composable
internal fun OfflineModeChip() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = YummyRadii.pillShape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = YummySpacing.md, vertical = YummySpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YummySpacing.xs),
        ) {
            Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(YummySizes.badgeIcon))
            Text(
                text = uiText(UiStringKey.Offline),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}
