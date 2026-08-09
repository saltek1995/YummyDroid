package me.yummydroid.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import kotlin.math.roundToInt
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.ui.components.liquidGlassBackdrop

private val BrowseBottomBarGlassTopFadeHeight = 32.dp
internal val BrowseBottomChromeInteractiveTopPadding = BrowseBottomBarGlassTopFadeHeight + 10.dp
internal val BrowseChromeItemGap = 8.dp
private val BrowseBottomChromeItemGap = BrowseChromeItemGap
private val BrowseBottomCalendarToTabsGap = BrowseChromeItemGap
private val BrowseBottomBaseControlsFallbackHeight = 96.dp

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
