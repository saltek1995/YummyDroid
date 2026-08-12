package me.yummydroid.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
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
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.R
import me.yummydroid.app.ui.components.clearFocusAfterTouch
import me.yummydroid.app.ui.components.liquidGlassBackdrop
import me.yummydroid.app.ui.theme.YummySpacing
import me.yummydroid.app.ui.theme.yummyActionContentColor
import me.yummydroid.app.ui.theme.yummyActionSurfaceColor

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
internal fun AppWordmark(
    modifier: Modifier = Modifier,
    height: Dp,
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
            modifier = Modifier.fillMaxHeight().width(height * 5.45f),
        )
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
    return invoke()
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
    val onExitDown: (() -> Boolean)?,
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

internal fun Modifier.browseTopBarExitDown(onExitDown: (() -> Boolean)?): Modifier {
    if (onExitDown == null) return this
    return onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
            onExitDown()
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

internal data class BrowseTvBackdropPolicy(
    val enabled: Boolean,
    val visible: Boolean,
    val animateAlpha: Boolean,
    val fallbackAlpha: Float,
)

internal fun browseTvBackdropPolicy(
    drawBackdrop: Boolean,
    backdropVisible: Boolean,
    hasProgress: Boolean,
    hasProgressProvider: Boolean,
): BrowseTvBackdropPolicy {
    val hasDynamicProgress = hasProgress || hasProgressProvider
    val visible = drawBackdrop && (backdropVisible || hasDynamicProgress)
    return BrowseTvBackdropPolicy(
        enabled = drawBackdrop,
        visible = visible,
        animateAlpha = visible && !hasDynamicProgress,
        fallbackAlpha = if (drawBackdrop && backdropVisible) 1f else 0f,
    )
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
    val backdropPolicy = browseTvBackdropPolicy(
        drawBackdrop = drawBackdrop,
        backdropVisible = backdropVisible,
        hasProgress = backdropProgress != null,
        hasProgressProvider = backdropProgressProvider != null,
    )
    val backdropAlpha = rememberBrowseTvBackdropAlpha(
        policy = backdropPolicy,
        backdropProgress = backdropProgress,
        backdropProgressProvider = backdropProgressProvider,
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(browseTvSectionBarHeight(drawBackdrop)),
    ) {
        BrowseTvSectionBackdrop(
            visible = backdropPolicy.visible,
            alpha = backdropAlpha,
            hazeState = hazeState,
        )
        BrowseTvSectionPointerLayer(visibleSections)
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

@Composable
private fun rememberBrowseTvBackdropAlpha(
    policy: BrowseTvBackdropPolicy,
    backdropProgress: Float?,
    backdropProgressProvider: (() -> Float)?,
): () -> Float {
    val animatedAlpha = if (policy.animateAlpha) {
        val value by animateFloatAsState(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
            label = "browseTabsBackdropAlpha",
        )
        value
    } else {
        null
    }
    return {
        resolveBrowseTvBackdropAlpha(
            policy = policy,
            backdropProgress = backdropProgress,
            backdropProgressProvider = backdropProgressProvider,
            animatedAlpha = animatedAlpha,
        )
    }
}

internal fun resolveBrowseTvBackdropAlpha(
    policy: BrowseTvBackdropPolicy,
    backdropProgress: Float?,
    backdropProgressProvider: (() -> Float)?,
    animatedAlpha: Float?,
): Float {
    if (!policy.enabled) return 0f
    return (backdropProgressProvider?.invoke() ?: backdropProgress ?: animatedAlpha ?: policy.fallbackAlpha)
        .coerceIn(0f, 1f)
}

private fun browseTvSectionBarHeight(drawBackdrop: Boolean): Dp {
    return if (drawBackdrop) {
        BrowseTvSectionIndicatorHeight + BrowseTvSectionIndicatorGlassExtraHeight
    } else {
        BrowseSectionTabsHeight
    }
}

@Composable
private fun BrowseTvSectionBackdrop(
    visible: Boolean,
    alpha: () -> Float,
    hazeState: HazeState?,
) {
    if (!visible) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha() }
            .liquidGlassBackdrop(
                shape = RoundedCornerShape(0.dp),
                intensity = BrowseTvSectionIndicatorGlassIntensity,
                hazeState = hazeState,
                topFadeFraction = 0f,
                bottomFadeFraction = 0.56f,
            ),
    )
}

@Composable
private fun BrowseTvSectionPointerLayer(visibleSections: List<BrowseSection>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(BrowseSectionTabsHeight),
        verticalAlignment = Alignment.Top,
    ) {
        BrowseTvSectionPointerEdge("tv-tabs-left-edge")
        visibleSections.forEachIndexed { index, _ ->
            Spacer(modifier = Modifier.weight(1f).fillMaxHeight())
            if (index < visibleSections.lastIndex) {
                Spacer(
                    modifier = Modifier
                        .width(YummySpacing.md)
                        .fillMaxHeight()
                        .consumeUnhandledPointerInput("tv-tabs-gap-$index"),
                )
            }
        }
        BrowseTvSectionPointerEdge("tv-tabs-right-edge")
    }
}

@Composable
private fun BrowseTvSectionPointerEdge(key: String) {
    Spacer(
        modifier = Modifier
            .width(BrowseTvSectionIndicatorHorizontalPadding)
            .fillMaxHeight()
            .consumeUnhandledPointerInput(key),
    )
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
