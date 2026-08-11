package me.yummydroid.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import kotlin.math.abs
import kotlin.math.roundToInt
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.ui.components.clearFocusAfterTouch
import me.yummydroid.app.ui.components.liquidGlassBackdrop
import me.yummydroid.app.ui.theme.YummySpacing
import me.yummydroid.app.ui.theme.yummyActionContentColor
import me.yummydroid.app.ui.theme.yummyActionSurfaceColor

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
    private var barTopRootY by mutableFloatStateOf(0f)
    private var barHeightPx by mutableIntStateOf(0)
    private var baseControlsHeightPx by mutableIntStateOf(0)
    private var measuredPointerBlockStartY by mutableStateOf<Float?>(null)

    fun clearPointerBlockStart() {
        measuredPointerBlockStartY = null
    }

    fun pointerBlockHeight(density: Density, fallbackStart: Dp): Dp = with(density) {
        val start = measuredPointerBlockStartY ?: fallbackStart.toPx()
        (barHeightPx - start).coerceAtLeast(0f).toDp()
    }

    fun baseControlsContentHeight(density: Density, contentTopPadding: Dp): Dp {
        val baseControlsHeight = if (baseControlsHeightPx > 0) {
            with(density) { baseControlsHeightPx.toDp() }
        } else {
            contentTopPadding + BrowseBottomBaseControlsFallbackHeight
        }
        return (baseControlsHeight - contentTopPadding).coerceAtLeast(0.dp)
    }

    fun Modifier.trackBar(): Modifier = this
        .onSizeChanged { size -> barHeightPx = size.height }
        .onGloballyPositioned { coordinates ->
            barTopRootY = coordinates.positionInRoot().y
        }

    fun Modifier.trackBaseControls(): Modifier = onSizeChanged { size ->
        baseControlsHeightPx = size.height
    }

    fun Modifier.pointerBlockStartAnchor(): Modifier = onGloballyPositioned { coordinates ->
        measuredPointerBlockStartY = (coordinates.positionInRoot().y - barTopRootY).coerceAtLeast(0f)
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

    val trackedModifier = with(geometry) { modifier.fillMaxWidth().trackBar() }
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
                    .run { geometry.run { pointerBlockStartAnchor() } },
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
    val trackedModifier = with(geometry) { modifier.fillMaxWidth().trackBaseControls() }
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
                    Modifier.pointerBlockStartAnchor()
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
                    Modifier.pointerBlockStartAnchor()
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

// BrowsePhoneTopChrome
@Composable
internal fun BrowsePhoneTopChrome(
    state: BrowseHomeChromeState,
    callbacks: BrowseActionCallbacks,
    navigation: BrowseTopSectionNavigation,
    showCompactControls: Boolean,
    visibility: BrowseTopChromeVisibility,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .browseTopBarVisibility(visibility)
            .browseTopBarExitDown(navigation.onExitDown)
            .padding(horizontal = BrowseChromePhoneHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Spacer(modifier = Modifier.fillMaxWidth().statusBarsPadding())
        if (state.forcedOfflineMode) BrowsePhoneOfflineIndicator()
        if (showCompactControls) {
            BrowseSectionTabs(
                activeSection = state.activeSection,
                visibleSections = state.visibleSections,
                activeSectionPosition = state.activeSectionPosition,
                onSectionSelected = navigation.onSectionSelected,
                sectionFocusRequesters = navigation.sectionTabFocusRequesters,
                focusEnabled = navigation.sectionTabsFocusEnabled,
                modifier = Modifier.fillMaxWidth(),
            )
            val stackActions = currentWindowSizeDp().width < 360.dp
            BrowseChromeActions(
                state = state,
                callbacks = callbacks,
                modifier = Modifier.fillMaxWidth(),
                spreadActions = !stackActions,
                stackActions = stackActions,
                entryFocusRequester = navigation.actionsFocusRequester,
            )
        }
    }
}

@Composable
private fun BrowsePhoneOfflineIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        OfflineModeChip()
    }
}

// BrowseSectionKeyNavigation
internal fun Modifier.browseSectionKeyNavigation(
    focusEnabled: Boolean,
    focusedSection: () -> BrowseSection?,
    visibleSections: List<BrowseSection>,
    onExitUp: (() -> Boolean)?,
    onExitDown: (() -> Boolean)?,
): Modifier {
    return onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.DirectionUp -> onExitUp.consumeSectionExit()
            Key.DirectionDown -> onExitDown.consumeSectionExit()
            Key.DirectionLeft -> isFocusedSectionEdge(
                focusEnabled,
                focusedSection(),
                visibleSections,
                edgeIndex = 0,
            )
            Key.DirectionRight -> isFocusedSectionEdge(
                focusEnabled,
                focusedSection(),
                visibleSections,
                edgeIndex = visibleSections.lastIndex,
            )
            else -> false
        }
    }
}

private fun (() -> Boolean)?.consumeSectionExit(): Boolean {
    if (this == null) return false
    invoke()
    return true
}

private fun isFocusedSectionEdge(
    focusEnabled: Boolean,
    focusedSection: BrowseSection?,
    visibleSections: List<BrowseSection>,
    edgeIndex: Int,
): Boolean {
    if (!focusEnabled) return false
    val focusedIndex = focusedSection?.let(visibleSections::indexOf) ?: -1
    return focusedIndex == edgeIndex
}

// BrowseSectionTab
private data class BrowseSectionTabStyle(
    val shape: Shape,
    val surfaceColor: Color,
    val contentColor: Color,
)

@Composable
internal fun BrowseSectionTab(
    section: BrowseSection,
    selectedFraction: Float,
    focusEnabled: Boolean,
    squareTopCorners: Boolean,
    focusRequester: FocusRequester?,
    focusedSection: BrowseSection?,
    onFocusedSectionChanged: (BrowseSection?) -> Unit,
    onSectionSelected: (BrowseSection) -> Unit,
    modifier: Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focusEnabled) {
        if (!focusEnabled) {
            focused = false
            if (focusedSection == section) onFocusedSectionChanged(null)
        }
    }
    val focusVisible = focusEnabled &&
        focused &&
        LocalInputModeManager.current.inputMode != InputMode.Touch
    val style = resolveBrowseSectionTabStyle(focusVisible, squareTopCorners)
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .onFocusChanged { focusState ->
                val hasFocus = focusState.isFocused || focusState.hasFocus
                focused = hasFocus
                when {
                    hasFocus -> onFocusedSectionChanged(section)
                    focusedSection == section -> onFocusedSectionChanged(null)
                }
            }
            .focusProperties { canFocus = focusEnabled }
            .clearFocusAfterTouch()
            .clip(style.shape)
            .background(style.surfaceColor, style.shape)
            .clickable(interactionSource = interactionSource, indication = null) {
                onSectionSelected(section)
            },
    ) {
        BrowseSectionTabContent(section, selectedFraction, style.contentColor)
    }
}

@Composable
private fun resolveBrowseSectionTabStyle(
    focusVisible: Boolean,
    squareTopCorners: Boolean,
): BrowseSectionTabStyle {
    val shape = if (squareTopCorners) {
        RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomEnd = 7.dp, bottomStart = 7.dp)
    } else {
        RoundedCornerShape(7.dp)
    }
    return BrowseSectionTabStyle(
        shape = shape,
        surfaceColor = if (focusVisible) yummyActionSurfaceColor(focused = true) else yummyActionSurfaceColor(),
        contentColor = if (focusVisible) {
            yummyActionContentColor(focused = true)
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.96f)
        },
    )
}

@Composable
private fun BoxScope.BrowseSectionTabContent(
    section: BrowseSection,
    selectedFraction: Float,
    contentColor: Color,
) {
    Text(
        text = section.localizedTitle(),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = contentColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = YummySpacing.xs),
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

// BrowseSectionTabs
internal val BrowseSectionTabsHeight = 32.dp

internal fun browseSectionIndicatorFraction(activePosition: Float?, index: Int): Float {
    return activePosition
        ?.let { position -> (1f - abs(position - index)).coerceIn(0f, 1f) }
        ?: 0f
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
        ?: visibleSections.indexOf(activeSection).takeIf { index -> index >= 0 }?.toFloat()
    var focusedSection by remember(visibleSections) { mutableStateOf<BrowseSection?>(null) }
    Row(
        modifier = modifier
            .height(BrowseSectionTabsHeight)
            .browseSectionKeyNavigation(
                focusEnabled = focusEnabled,
                focusedSection = { focusedSection },
                visibleSections = visibleSections,
                onExitUp = onExitUp,
                onExitDown = onExitDown,
            ),
        horizontalArrangement = Arrangement.spacedBy(YummySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        visibleSections.forEachIndexed { index, section ->
            BrowseSectionTab(
                section = section,
                selectedFraction = browseSectionIndicatorFraction(activePosition, index),
                focusEnabled = focusEnabled,
                squareTopCorners = squareTopCorners,
                focusRequester = sectionFocusRequesters[section],
                focusedSection = focusedSection,
                onFocusedSectionChanged = { focused -> focusedSection = focused },
                onSectionSelected = onSectionSelected,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

// BrowseTopChrome
internal val BrowseChromePhoneHorizontalPadding = 16.dp
internal val BrowseChromeWideHorizontalPadding = 24.dp

internal data class BrowseTopSectionNavigation(
    val onSectionSelected: (BrowseSection) -> Unit,
    val onExitDown: (() -> Unit)?,
    val actionsFocusRequester: FocusRequester?,
    val sectionTabsFocusRequester: FocusRequester?,
    val sectionTabFocusRequesters: Map<BrowseSection, FocusRequester>,
    val sectionTabsFocusEnabled: Boolean,
)

internal data class BrowseTopChromeVisibility(
    val collapseWhenHidden: Boolean,
    val visible: Boolean,
    val progress: Float?,
    val progressProvider: (() -> Float)?,
)

@Composable
internal fun BrowseTopBarModern(
    state: BrowseHomeChromeState,
    callbacks: BrowseActionCallbacks,
    navigation: BrowseTopSectionNavigation,
    showCompactControls: Boolean,
    visibility: BrowseTopChromeVisibility,
    modifier: Modifier = Modifier,
) {
    if (state.isWide) {
        BrowseWideTopChrome(state, callbacks, navigation, visibility, modifier)
    } else {
        BrowsePhoneTopChrome(
            state = state,
            callbacks = callbacks,
            navigation = navigation,
            showCompactControls = showCompactControls,
            visibility = visibility,
            modifier = modifier,
        )
    }
}

// BrowseTopChromeVisibility
@Composable
internal fun Modifier.browseTopBarVisibility(visibility: BrowseTopChromeVisibility): Modifier {
    val animatedProgress = if (visibility.progress == null && visibility.progressProvider == null) {
        val progress by animateFloatAsState(
            targetValue = if (visibility.visible) 1f else 0f,
            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
            label = "browseTopBarVisibility",
        )
        progress
    } else {
        null
    }
    fun progress(): Float = (
        visibility.progressProvider?.invoke() ?: visibility.progress ?: animatedProgress ?: 0f
    ).coerceIn(0f, 1f)

    return this
        .then(if (visibility.visible) Modifier else Modifier.focusProperties { canFocus = false })
        .layout { measurable, constraints ->
            val resolvedProgress = progress()
            val placeable = measurable.measure(constraints)
            val height = if (visibility.collapseWhenHidden) {
                (placeable.height * resolvedProgress).roundToInt()
            } else {
                placeable.height
            }
            val offsetY = if (visibility.collapseWhenHidden) {
                height - placeable.height
            } else {
                ((resolvedProgress - 1f) * placeable.height).roundToInt()
            }
            layout(width = placeable.width, height = height) {
                placeable.placeRelative(x = 0, y = offsetY)
            }
        }
        .clipToBounds()
        .graphicsLayer { alpha = if (visibility.collapseWhenHidden) progress() else 1f }
}

internal fun Modifier.browseTopBarExitDown(onExitDown: (() -> Unit)?): Modifier {
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

// BrowseTvChrome
internal val BrowseTvSectionIndicatorHeight = 56.dp
private val BrowseTvSectionIndicatorGlassExtraHeight = 96.dp
private const val BrowseTvSectionIndicatorGlassIntensity = 1.85f
private val BrowseTvSectionIndicatorHorizontalPadding = 24.dp

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
                modifier = Modifier.fillMaxSize(),
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

// BrowseWideTopChrome
@Composable
internal fun BrowseWideTopChrome(
    state: BrowseHomeChromeState,
    callbacks: BrowseActionCallbacks,
    navigation: BrowseTopSectionNavigation,
    visibility: BrowseTopChromeVisibility,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .browseTopBarVisibility(visibility)
            .browseTopBarExitDown(navigation.onExitDown)
            .statusBarsPadding()
            .padding(horizontal = BrowseChromeWideHorizontalPadding, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppWordmark(modifier = Modifier.weight(1f), height = 52.dp)
            if (state.forcedOfflineMode) OfflineModeChip()
            BrowseChromeActions(
                state = state,
                callbacks = callbacks,
                entryFocusRequester = navigation.actionsFocusRequester,
                downFocusRequester = navigation.sectionTabsFocusRequester,
                consumeUpWhenNoRequester = true,
                consumeHorizontalEdgesWhenNoRequester = true,
            )
        }
    }
}
