package me.yummydroid.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import kotlin.math.roundToInt
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.ui.components.liquidGlassBackdrop

// BrowseBottomChrome
internal data class BrowseBottomSectionNavigation(
    val onSectionSelected: (BrowseSection) -> Unit,
    val focusRequester: FocusRequester?,
    val focusRequesters: Map<BrowseSection, FocusRequester>,
    val onExitUp: (() -> Boolean)?,
    val focusEnabled: Boolean,
)

@Composable
internal fun BrowseBottomBarModern(
    state: BrowseHomeChromeState,
    actions: BrowseActionCallbacks,
    sectionNavigation: BrowseBottomSectionNavigation,
    hazeState: HazeState?,
    topProtectedContent: (@Composable (Modifier) -> Unit)?,
    topProtectedVisibilityProgress: Float?,
    modifier: Modifier = Modifier,
) {
    BrowseBottomChromeLayout(
        state = state,
        actions = actions,
        sectionNavigation = sectionNavigation,
        showSectionTabs = !state.forcedOfflineMode,
        hazeState = hazeState,
        topProtectedContent = topProtectedContent,
        topProtectedVisibilityProgress = topProtectedVisibilityProgress,
        modifier = modifier,
    )
}
// BrowseBottomChromeGeometry
internal class BrowseBottomChromeGeometryState {
    internal var barTopRootY by mutableFloatStateOf(0f)
    internal var barHeightPx by mutableIntStateOf(0)
    internal var baseControlsHeightPx by mutableIntStateOf(0)
    internal var pointerBlockStartY by mutableStateOf<Float?>(null)
}
private fun BrowseBottomChromeGeometryState.clearPointerBlockStart() {
    pointerBlockStartY = null
}

private fun BrowseBottomChromeGeometryState.pointerBlockHeight(
    density: Density,
    fallbackStart: Dp,
): Dp = with(density) {
    val start = pointerBlockStartY ?: fallbackStart.toPx()
    (barHeightPx - start).coerceAtLeast(0f).toDp()
}

private fun BrowseBottomChromeGeometryState.baseControlsContentHeight(
    density: Density,
    contentTopPadding: Dp,
): Dp {
    val controlsHeight = if (baseControlsHeightPx > 0) {
        with(density) { baseControlsHeightPx.toDp() }
    } else {
        contentTopPadding + BrowseBottomBaseControlsFallbackHeight
    }
    return (controlsHeight - contentTopPadding).coerceAtLeast(0.dp)
}

private fun Modifier.trackBrowseBottomBar(geometry: BrowseBottomChromeGeometryState): Modifier = this
    .onSizeChanged { size -> geometry.barHeightPx = size.height }
    .onGloballyPositioned { coordinates ->
        geometry.barTopRootY = coordinates.positionInRoot().y
    }

private fun Modifier.trackBrowseBaseControls(geometry: BrowseBottomChromeGeometryState): Modifier {
    return onSizeChanged { size -> geometry.baseControlsHeightPx = size.height }
}

private fun Modifier.browsePointerBlockStartAnchor(geometry: BrowseBottomChromeGeometryState): Modifier {
    return onGloballyPositioned { coordinates ->
        geometry.pointerBlockStartY = (coordinates.positionInRoot().y - geometry.barTopRootY).coerceAtLeast(0f)
    }
}
// BrowseBottomChromeLayout
private val BrowseBottomBarGlassTopFadeHeight = 32.dp
internal val BrowseBottomChromeInteractiveTopPadding = BrowseBottomBarGlassTopFadeHeight + 10.dp
internal val BrowseChromeItemGap = 8.dp
internal val BrowseBottomBaseControlsFallbackHeight = 96.dp
private val BrowseBottomCalendarToTabsGap = BrowseChromeItemGap

@Composable
internal fun BrowseBottomChromeLayout(
    state: BrowseHomeChromeState,
    actions: BrowseActionCallbacks,
    sectionNavigation: BrowseBottomSectionNavigation,
    showSectionTabs: Boolean,
    hazeState: HazeState?,
    topProtectedContent: (@Composable (Modifier) -> Unit)?,
    topProtectedVisibilityProgress: Float?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val geometry = remember { BrowseBottomChromeGeometryState() }
    val actionFocusRequester = remember { FocusRequester() }
    val protectedContent = rememberBrowseBottomProtectedContentState(
        content = topProtectedContent,
        visibilityProgress = topProtectedVisibilityProgress,
    )
    LaunchedEffect(protectedContent.active, showSectionTabs) {
        geometry.clearPointerBlockStart()
    }
    val pointerBlockHeight = geometry.pointerBlockHeight(
        density = density,
        fallbackStart = BrowseBottomChromeInteractiveTopPadding,
    )
    val baseContentHeight = geometry.baseControlsContentHeight(
        density = density,
        contentTopPadding = BrowseBottomChromeInteractiveTopPadding,
    )

    val trackedModifier = modifier.fillMaxWidth().trackBrowseBottomBar(geometry)
    Box(modifier = trackedModifier) {
        BrowseBottomChromeBackdrop(
            hazeState = hazeState,
            modifier = Modifier.matchParentSize(),
        )
        BrowseBottomPointerBlock(
            height = pointerBlockHeight,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        protectedContent.content?.let { content ->
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = baseContentHeight + BrowseBottomCalendarToTabsGap,
                    )
                    .browseBottomTopProtectedVisibility(protectedContent.progress)
                    .browsePointerBlockStartAnchor(geometry),
            ) {
                content(Modifier.fillMaxWidth())
            }
        }
        BrowseBottomControls(
            state = state,
            actions = actions,
            sectionNavigation = sectionNavigation,
            showSectionTabs = showSectionTabs,
            protectedSlotActive = protectedContent.active,
            actionFocusRequester = actionFocusRequester,
            geometry = geometry,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun BrowseBottomChromeBackdrop(
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.liquidGlassBackdrop(
                shape = RoundedCornerShape(0.dp),
                intensity = 1.12f,
                hazeState = hazeState,
                topFadeFraction = 0.36f,
            ),
    )
}

@Composable
private fun BrowseBottomPointerBlock(
    height: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    if (height <= 0.dp) return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .consumeUnhandledPointerInput(height),
    )
}
// BrowseBottomControls
private val BrowseBottomChromeItemGap = BrowseChromeItemGap

@Composable
internal fun BrowseBottomControls(
    state: BrowseHomeChromeState,
    actions: BrowseActionCallbacks,
    sectionNavigation: BrowseBottomSectionNavigation,
    showSectionTabs: Boolean,
    protectedSlotActive: Boolean,
    actionFocusRequester: FocusRequester,
    geometry: BrowseBottomChromeGeometryState,
    modifier: Modifier = Modifier,
) {
    val trackedModifier = modifier.fillMaxWidth().trackBrowseBaseControls(geometry)
    Column(
        modifier = trackedModifier
            .padding(
                start = 16.dp,
                top = BrowseBottomChromeInteractiveTopPadding,
                end = 16.dp,
                bottom = BrowseBottomChromeItemGap,
            ),
    ) {
        if (showSectionTabs) {
            BrowseBottomSectionTabs(
                state = state,
                navigation = sectionNavigation,
                protectedSlotActive = protectedSlotActive,
                actionFocusRequester = actionFocusRequester,
                geometry = geometry,
            )
            Spacer(modifier = Modifier.height(BrowseBottomChromeItemGap))
        }
        BrowseBottomActions(
            state = state,
            actions = actions,
            sectionNavigation = sectionNavigation,
            showSectionTabs = showSectionTabs,
            protectedSlotActive = protectedSlotActive,
            actionFocusRequester = actionFocusRequester,
            geometry = geometry,
        )
    }
}

@Composable
private fun BrowseBottomSectionTabs(
    state: BrowseHomeChromeState,
    navigation: BrowseBottomSectionNavigation,
    protectedSlotActive: Boolean,
    actionFocusRequester: FocusRequester,
    geometry: BrowseBottomChromeGeometryState,
) {
    val focusRequesters = navigation.focusRequesters.ifEmpty {
        navigation.focusRequester
            ?.let { requester -> mapOf(state.activeSection to requester) }
            .orEmpty()
    }
    BrowseSectionTabs(
        activeSection = state.activeSection,
        visibleSections = state.visibleSections,
        activeSectionPosition = state.activeSectionPosition,
        onSectionSelected = navigation.onSectionSelected,
        sectionFocusRequesters = focusRequesters,
        onExitUp = navigation.onExitUp,
        onExitDown = { actionFocusRequester.requestFocusSafely() },
        focusEnabled = navigation.focusEnabled,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (protectedSlotActive) Modifier else with(geometry) {
                    Modifier.browsePointerBlockStartAnchor(geometry)
                },
            ),
    )
}

@Composable
private fun BrowseBottomActions(
    state: BrowseHomeChromeState,
    actions: BrowseActionCallbacks,
    sectionNavigation: BrowseBottomSectionNavigation,
    showSectionTabs: Boolean,
    protectedSlotActive: Boolean,
    actionFocusRequester: FocusRequester,
    geometry: BrowseBottomChromeGeometryState,
) {
    val stackActions = currentWindowSizeDp().width < 360.dp
    BrowseChromeActions(
        state = state,
        callbacks = actions,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (showSectionTabs || protectedSlotActive) Modifier else with(geometry) {
                    Modifier.browsePointerBlockStartAnchor(geometry)
                },
            ),
        spreadActions = !stackActions,
        stackActions = stackActions,
        entryFocusRequester = actionFocusRequester,
        upFocusRequester = sectionNavigation.focusRequester,
        consumeDownWhenNoRequester = true,
        consumeHorizontalEdgesWhenNoRequester = true,
    )
}
// BrowseBottomProtectedContent
internal data class BrowseBottomProtectedContentState(
    val content: (@Composable (Modifier) -> Unit)?,
    val progress: Float,
) {
    val active: Boolean
        get() = content != null && progress > 0.001f
}

@Composable
internal fun rememberBrowseBottomProtectedContentState(
    content: (@Composable (Modifier) -> Unit)?,
    visibilityProgress: Float?,
): BrowseBottomProtectedContentState {
    var retainedContent by remember {
        mutableStateOf<(@Composable (Modifier) -> Unit)?>(null)
    }
    val animatedProgress = if (visibilityProgress == null) {
        val progress by animateFloatAsState(
            targetValue = if (content != null) 1f else 0f,
            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
            label = "browseBottomTopProtectedProgress",
            finishedListener = { value ->
                if (value <= 0.001f) retainedContent = null
            },
        )
        progress
    } else {
        0f
    }
    val resolvedProgress = visibilityProgress?.coerceIn(0f, 1f) ?: animatedProgress

    SideEffect {
        if (content != null) retainedContent = content
    }
    LaunchedEffect(content, resolvedProgress) {
        if (content == null && resolvedProgress <= 0.001f) retainedContent = null
    }
    return BrowseBottomProtectedContentState(
        content = content ?: retainedContent,
        progress = resolvedProgress,
    )
}

internal fun Modifier.browseBottomTopProtectedVisibility(progress: Float): Modifier {
    val resolvedProgress = progress.coerceIn(0f, 1f)
    return this
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val height = (placeable.height * resolvedProgress).roundToInt()
            layout(width = placeable.width, height = height) {
                placeable.placeRelative(x = 0, y = height - placeable.height)
            }
        }
        .clipToBounds()
        .graphicsLayer { alpha = resolvedProgress }
}
