package me.yummydroid.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import java.text.Collator
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.yummydroid.app.InputAction
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.data.ageRatingFilterOptions
import me.yummydroid.app.data.AnimeSort
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.FilterCatalog
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.data.OfflineAnimeEntry
import me.yummydroid.app.data.seasonFilterOptions
import me.yummydroid.app.data.statusFilterOptions
import me.yummydroid.app.data.translateFilterOptions
import me.yummydroid.app.data.userMarkFilterOptions
import me.yummydroid.app.LoadState
import me.yummydroid.app.R
import me.yummydroid.app.readyDataOrNull
import me.yummydroid.app.ui.components.clearFocusAfterTouch
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.components.focusRing
import me.yummydroid.app.ui.components.liquidGlassBackdrop
import me.yummydroid.app.ui.theme.YummyAlpha
import me.yummydroid.app.ui.theme.YummyColors
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySizes
import me.yummydroid.app.ui.theme.YummySpacing
import me.yummydroid.app.ui.theme.yummyActionBorder
import me.yummydroid.app.ui.theme.yummyActionContentColor
import me.yummydroid.app.ui.theme.yummyActionSurfaceColor
import me.yummydroid.app.ui.theme.yummySurfaceBorder
import me.yummydroid.app.ui.theme.yummySurfaceColor
import me.yummydroid.app.ui.theme.yummySurfaceContentColor
import me.yummydroid.app.ui.theme.YummySurfaceRole

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
    modifier: Modifier = Modifier,
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
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val stackActions = !isWide && screenWidthDp < 360

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
private const val BrowseSearchActionIndex = 0
private const val BrowseFiltersActionIndex = 1
private const val BrowseDownloadsActionIndex = 2
private const val BrowseSettingsActionIndex = 3
private const val BrowseProfileActionIndex = 4
private const val BrowseActionButtonCount = 5
private val BrowseBottomBarGlassTopFadeHeight = 32.dp
internal val BrowseBottomChromeInteractiveTopPadding = BrowseBottomBarGlassTopFadeHeight + 10.dp
internal val BrowseChromeItemGap = 8.dp
private val BrowseBottomChromeItemGap = BrowseChromeItemGap
private val BrowseBottomCalendarToTabsGap = BrowseChromeItemGap
private val BrowseBottomBaseControlsFallbackHeight = 96.dp
private val BrowseSectionTabsHeight = 32.dp
private val BrowseTvSectionIndicatorHorizontalPadding = 24.dp

private data class BrowseActionFocusLinks(
    val leftFocusRequester: FocusRequester? = null,
    val rightFocusRequester: FocusRequester? = null,
    val upFocusRequester: FocusRequester? = null,
    val downFocusRequester: FocusRequester? = null,
    val consumeDownKey: Boolean = false,
    val consumeHorizontalEdgeKey: Boolean = false,
) {
    val hasCustomKeyHandling: Boolean
        get() = leftFocusRequester != null ||
            rightFocusRequester != null ||
            upFocusRequester != null ||
            downFocusRequester != null ||
            consumeDownKey ||
            consumeHorizontalEdgeKey
}

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
    modifier: Modifier = Modifier,
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
    modifier: Modifier = Modifier,
) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val stackActions = screenWidthDp < 360
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
    activeSectionPosition: Float? = null,
    onSectionSelected: (BrowseSection) -> Unit,
    sectionFocusRequesters: Map<BrowseSection, FocusRequester> = emptyMap(),
    onExitUp: (() -> Boolean)? = null,
    onExitDown: (() -> Boolean)? = null,
    squareTopCorners: Boolean = false,
    focusEnabled: Boolean = true,
    modifier: Modifier = Modifier,
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

@Composable
internal fun BrowseTopBarActions(
    onOpenSearch: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDownloads: () -> Unit,
    auth: AuthUiState,
    activeFilters: Int,
    activeSearch: Boolean,
    activeFiltersPanel: Boolean = false,
    activeSettings: Boolean = false,
    activeDownloads: Boolean = false,
    activeProfile: Boolean = false,
    activeDownloadCount: Int,
    searchEnabled: Boolean = true,
    filtersEnabled: Boolean = true,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
    spreadActions: Boolean = false,
    stackActions: Boolean = false,
    entryFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    consumeUpWhenNoRequester: Boolean = false,
    consumeDownWhenNoRequester: Boolean = false,
    consumeHorizontalEdgesWhenNoRequester: Boolean = false,
) {
    val actionFocusRequesters = remember { List(BrowseActionButtonCount) { FocusRequester() } }
    var focusedActionIndex by remember { mutableIntStateOf(-1) }
    val visibleActiveFilters = if (filtersEnabled) activeFilters else 0
    val visibleActiveSearch = searchEnabled && activeSearch
    val visibleFiltersPanel = filtersEnabled && activeFiltersPanel
    val enabledActionIndexes = remember(searchEnabled, filtersEnabled) {
        buildList {
            if (searchEnabled) add(BrowseSearchActionIndex)
            if (filtersEnabled) add(BrowseFiltersActionIndex)
            add(BrowseDownloadsActionIndex)
            add(BrowseSettingsActionIndex)
            add(BrowseProfileActionIndex)
        }
    }
    val entryActionIndex = enabledActionIndexes.firstOrNull() ?: BrowseDownloadsActionIndex
    fun actionRequester(actionIndex: Int): FocusRequester {
        return if (entryActionIndex == actionIndex && entryFocusRequester != null) {
            entryFocusRequester
        } else {
            actionFocusRequesters[actionIndex]
        }
    }
    fun adjacentActionRequester(actionIndex: Int, delta: Int): FocusRequester? {
        val currentPosition = enabledActionIndexes.indexOf(actionIndex)
        if (currentPosition < 0) return null
        return enabledActionIndexes
            .getOrNull(currentPosition + delta)
            ?.let(::actionRequester)
    }
    fun Modifier.exitDownFocus(): Modifier {
        val requester = downFocusRequester ?: return this
        return focusProperties { down = requester }
    }
    fun Modifier.actionEdgeGuards(): Modifier {
        val firstActionIndex = enabledActionIndexes.firstOrNull()
        val lastActionIndex = enabledActionIndexes.lastOrNull()
        val consumeActionUp = upFocusRequester == null && consumeUpWhenNoRequester
        if (!consumeActionUp && !consumeHorizontalEdgesWhenNoRequester) return this
        return onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionUp -> consumeActionUp && focusedActionIndex in enabledActionIndexes
                Key.DirectionLeft -> consumeHorizontalEdgesWhenNoRequester && focusedActionIndex == firstActionIndex
                Key.DirectionRight -> consumeHorizontalEdgesWhenNoRequester && focusedActionIndex == lastActionIndex
                else -> false
            }
        }
    }
    val consumeActionDown = downFocusRequester == null && consumeDownWhenNoRequester
    val consumeActionHorizontalEdge = consumeHorizontalEdgesWhenNoRequester

    fun actionModifier(actionIndex: Int): Modifier {
        return Modifier
            .onFocusChanged { focusState ->
                if (focusState.isFocused || focusState.hasFocus) {
                    focusedActionIndex = actionIndex
                } else if (focusedActionIndex == actionIndex) {
                    focusedActionIndex = -1
                }
            }
            .focusRequester(actionRequester(actionIndex))
            .exitDownFocus()
    }

    fun actionFocusLinks(actionIndex: Int): BrowseActionFocusLinks {
        return BrowseActionFocusLinks(
            leftFocusRequester = adjacentActionRequester(actionIndex, -1),
            rightFocusRequester = adjacentActionRequester(actionIndex, 1),
            upFocusRequester = upFocusRequester,
            downFocusRequester = downFocusRequester,
            consumeDownKey = consumeActionDown,
            consumeHorizontalEdgeKey = consumeActionHorizontalEdge,
        )
    }

    if (stackActions) {
        Column(
            modifier = modifier.actionEdgeGuards(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                BrowseSearchActionButton(
                    visibleActiveSearch,
                    searchEnabled,
                    onOpenSearch,
                    actionModifier(BrowseSearchActionIndex),
                    focusLinks = actionFocusLinks(BrowseSearchActionIndex),
                )
                BrowseFiltersActionButton(
                    visibleActiveFilters,
                    visibleFiltersPanel,
                    filtersEnabled,
                    onOpenFilters,
                    actionModifier(BrowseFiltersActionIndex),
                    focusLinks = actionFocusLinks(BrowseFiltersActionIndex),
                )
                BrowseDownloadsActionButton(
                    activeDownloadCount,
                    activeDownloads,
                    onOpenDownloads,
                    actionModifier(BrowseDownloadsActionIndex),
                    focusLinks = actionFocusLinks(BrowseDownloadsActionIndex),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                BrowseSettingsActionButton(
                    activeSettings,
                    onOpenSettings,
                    actionModifier(BrowseSettingsActionIndex),
                    focusLinks = actionFocusLinks(BrowseSettingsActionIndex),
                )
                BrowseProfileActionButton(
                    auth,
                    activeProfile,
                    onOpenLogin,
                    onOpenProfile,
                    actionModifier(BrowseProfileActionIndex),
                    focusLinks = actionFocusLinks(BrowseProfileActionIndex),
                )
            }
        }
        return
    }

    Row(
        modifier = modifier.actionEdgeGuards(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (spreadActions) Arrangement.SpaceBetween else Arrangement.spacedBy(10.dp),
    ) {
        BrowseSearchActionButton(
            visibleActiveSearch,
        searchEnabled,
        onOpenSearch,
        actionModifier(BrowseSearchActionIndex),
        focusLinks = actionFocusLinks(BrowseSearchActionIndex),
    )
    BrowseFiltersActionButton(
        visibleActiveFilters,
        visibleFiltersPanel,
        filtersEnabled,
        onOpenFilters,
        actionModifier(BrowseFiltersActionIndex),
        focusLinks = actionFocusLinks(BrowseFiltersActionIndex),
    )
    BrowseDownloadsActionButton(
        activeDownloadCount,
        activeDownloads,
        onOpenDownloads,
        actionModifier(BrowseDownloadsActionIndex),
        focusLinks = actionFocusLinks(BrowseDownloadsActionIndex),
    )
    BrowseSettingsActionButton(
        activeSettings,
        onOpenSettings,
        actionModifier(BrowseSettingsActionIndex),
        focusLinks = actionFocusLinks(BrowseSettingsActionIndex),
    )
    BrowseProfileActionButton(
        auth,
        activeProfile,
        onOpenLogin,
        onOpenProfile,
        actionModifier(BrowseProfileActionIndex),
        focusLinks = actionFocusLinks(BrowseProfileActionIndex),
    )
}
}

@Composable
private fun BrowseActionIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    badgeText: String? = null,
    focusLinks: BrowseActionFocusLinks = BrowseActionFocusLinks(),
) {
    val shape = RoundedCornerShape(8.dp)
    var focused by remember { mutableStateOf(false) }
    val inputModeManager = LocalInputModeManager.current
    val focusVisible = focused && inputModeManager.inputMode != InputMode.Touch
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .size(48.dp)
            .then(
                if (enabled) {
                    Modifier
                        .onFocusChanged { focusState ->
                            focused = focusState.isFocused || focusState.hasFocus
                        }
                        .clearFocusAfterTouch()
                        .clip(shape)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick,
                        )
                        .then(
                            if (focusLinks.hasCustomKeyHandling) {
                                Modifier.onPreviewKeyEvent { event ->
                                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (event.key) {
                                        Key.DirectionLeft -> if (focusLinks.leftFocusRequester != null) {
                                            focusLinks.leftFocusRequester.requestFocusSafely()
                                        } else {
                                            focusLinks.consumeHorizontalEdgeKey
                                        }
                                        Key.DirectionRight -> if (focusLinks.rightFocusRequester != null) {
                                            focusLinks.rightFocusRequester.requestFocusSafely()
                                        } else {
                                            focusLinks.consumeHorizontalEdgeKey
                                        }
                                        Key.DirectionUp -> focusLinks.upFocusRequester?.requestFocusSafely() == true
                                        Key.DirectionDown -> if (focusLinks.downFocusRequester != null) {
                                            focusLinks.downFocusRequester.requestFocusSafely()
                                        } else {
                                            focusLinks.consumeDownKey
                                        }
                                        else -> false
                                    }
                                }
                            } else {
                                Modifier
                            },
                        )
                } else {
                    Modifier.clip(shape)
                },
            ),
        color = yummyActionSurfaceColor(enabled = enabled, selected = active, focused = focusVisible),
        contentColor = yummyActionContentColor(enabled = enabled, selected = active, focused = focusVisible),
        border = yummyActionBorder(enabled = enabled, selected = active, focused = focusVisible),
        shape = shape,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(27.dp))
            if (enabled && badgeText != null) {
                Surface(
                    color = YummyColors.offline,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp, end = 4.dp)
                        .widthIn(min = 16.dp)
                        .height(16.dp),
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowseSettingsActionButton(
    activeSettings: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    focusLinks: BrowseActionFocusLinks = BrowseActionFocusLinks(),
) {
    BrowseActionIconButton(
        icon = Icons.Default.Settings,
        contentDescription = uiText(UiStringKey.Settings),
        onClick = onOpenSettings,
        modifier = modifier,
        active = activeSettings,
        focusLinks = focusLinks,
    )
}

@Composable
private fun BrowseSearchActionButton(
    activeSearch: Boolean,
    enabled: Boolean,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
    focusLinks: BrowseActionFocusLinks = BrowseActionFocusLinks(),
) {
    BrowseActionIconButton(
        icon = Icons.Default.Search,
        contentDescription = uiText(UiStringKey.Search),
        onClick = onOpenSearch,
        modifier = modifier,
        active = activeSearch,
        enabled = enabled,
        focusLinks = focusLinks,
    )
}

@Composable
private fun BrowseFiltersActionButton(
    activeFilters: Int,
    activeFiltersPanel: Boolean,
    enabled: Boolean,
    onOpenFilters: () -> Unit,
    modifier: Modifier = Modifier,
    focusLinks: BrowseActionFocusLinks = BrowseActionFocusLinks(),
) {
    BrowseActionIconButton(
        icon = Icons.Default.FilterList,
        contentDescription = uiText(UiStringKey.Filters),
        onClick = onOpenFilters,
        modifier = modifier,
        active = activeFilters > 0 || activeFiltersPanel,
        enabled = enabled,
        badgeText = activeFilters.takeIf { it > 0 }?.coerceAtMost(9)?.toString(),
        focusLinks = focusLinks,
    )
}

@Composable
private fun BrowseDownloadsActionButton(
    activeDownloadCount: Int,
    activeDownloads: Boolean,
    onOpenDownloads: () -> Unit,
    modifier: Modifier = Modifier,
    focusLinks: BrowseActionFocusLinks = BrowseActionFocusLinks(),
) {
    BrowseActionIconButton(
        icon = Icons.Default.Download,
        contentDescription = uiText(UiStringKey.Downloads),
        onClick = onOpenDownloads,
        modifier = modifier,
        active = activeDownloadCount > 0 || activeDownloads,
        badgeText = activeDownloadCount.takeIf { it > 0 }?.let { count ->
            if (count > 9) "9+" else count.toString()
        },
        focusLinks = focusLinks,
    )
}

@Composable
private fun BrowseProfileActionButton(
    auth: AuthUiState,
    activeProfile: Boolean,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
    focusLinks: BrowseActionFocusLinks = BrowseActionFocusLinks(),
) {
    val unreadNotifications = auth.profile?.unreadNotifications ?: 0
    BrowseActionIconButton(
        icon = Icons.Default.AccountCircle,
        contentDescription = if (auth.profile == null) uiText(UiStringKey.SignIn) else uiText(UiStringKey.Profile),
        onClick = if (auth.profile == null) onOpenLogin else onOpenProfile,
        modifier = modifier,
        active = activeProfile,
        badgeText = unreadNotifications.notificationBadgeText(),
        focusLinks = focusLinks,
    )
}

private const val SEARCH_HISTORY_VISIBLE_LIMIT = 6

@Composable
internal fun SearchDialog(
    query: String,
    searchHistory: List<String> = emptyList(),
    keyboardDismissRequest: Long = 0L,
    remoteInputAction: InputAction? = null,
    remoteInputActionRequest: Long = 0L,
    onQueryChange: (String) -> Unit,
    onSubmitQuery: (String) -> Unit = {},
    onHistorySelected: (String) -> Unit = {},
    onDismiss: () -> Unit,
    onExitDown: () -> Unit = onDismiss,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val uiLanguage = LocalUiLanguage.current
    val voicePrompt = uiText(UiStringKey.WhatShouldIFind)
    val voiceUnavailable = uiText(UiStringKey.VoiceSearchIsNotAvailableOnThisDevice)
    val focusRequester = remember { FocusRequester() }
    val micFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var inputFocused by remember { mutableStateOf(false) }
    var micFocused by remember { mutableStateOf(false) }
    var focusedHistoryIndex by remember { mutableIntStateOf(-1) }
    val isTelevision = remember(configuration.uiMode) {
        val uiMode = configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK
        uiMode == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    }
    val visibleHistory = remember(query, searchHistory) {
        searchHistory
            .take(SEARCH_HISTORY_VISIBLE_LIMIT)
    }
    val historyFocusRequesters = remember(visibleHistory) {
        visibleHistory.map { FocusRequester() }
    }
    val firstHistoryFocusRequester = historyFocusRequesters.firstOrNull() ?: FocusRequester.Default
    fun submitCurrentQuery() {
        val submittedQuery = query.trim()
        if (submittedQuery.isNotBlank()) {
            onSubmitQuery(submittedQuery)
        }
    }
    fun dismissSearch() {
        submitCurrentQuery()
        keyboardController?.hide()
        onDismiss()
    }
    fun exitDownFromSearch() {
        submitCurrentQuery()
        keyboardController?.hide()
        onExitDown()
    }
    fun focusInput() {
        focusRequester.requestFocusSafely()
        if (!isTelevision) {
            keyboardController?.show()
        }
    }
    fun focusHistoryOrExit(): Boolean {
        val firstHistoryFocus = historyFocusRequesters.firstOrNull()
        if (firstHistoryFocus != null) {
            keyboardController?.hide()
            firstHistoryFocus.requestFocusSafely()
        } else {
            exitDownFromSearch()
        }
        return true
    }
    val voiceSearchLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val recognizedText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (recognizedText.isNotBlank()) {
                onQueryChange(recognizedText)
                onSubmitQuery(recognizedText)
            }
        }
    }
    val launchVoiceSearch = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, uiLanguage.voiceRecognizerTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, voicePrompt)
        }
        runCatching {
            keyboardController?.hide()
            voiceSearchLauncher.launch(intent)
        }.onFailure { throwable ->
            if (throwable is ActivityNotFoundException) {
                Toast.makeText(
                    context,
                    voiceUnavailable,
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                throw throwable
            }
        }
        Unit
    }

    LaunchedEffect(Unit) {
        delay(80)
        focusInput()
    }

    LaunchedEffect(keyboardDismissRequest) {
        if (keyboardDismissRequest > 0L) {
            keyboardController?.hide()
        }
    }

    LaunchedEffect(visibleHistory) {
        if (focusedHistoryIndex >= visibleHistory.size) {
            focusedHistoryIndex = -1
        }
    }

    LaunchedEffect(remoteInputActionRequest) {
        if (remoteInputActionRequest <= 0L) return@LaunchedEffect
        when (remoteInputAction) {
            InputAction.Up -> {
                when {
                    focusedHistoryIndex > 0 -> {
                        historyFocusRequesters.getOrNull(focusedHistoryIndex - 1)?.requestFocusSafely()
                    }
                    focusedHistoryIndex == 0 -> focusInput()
                    !inputFocused && !micFocused -> focusInput()
                }
            }
            InputAction.Down -> {
                if (focusedHistoryIndex >= 0) {
                    val nextHistoryFocus = historyFocusRequesters.getOrNull(focusedHistoryIndex + 1)
                    if (nextHistoryFocus == null) {
                        exitDownFromSearch()
                    } else {
                        keyboardController?.hide()
                        nextHistoryFocus.requestFocusSafely()
                    }
                } else {
                    focusHistoryOrExit()
                }
            }
            InputAction.Left -> {
                when {
                    inputFocused -> micFocusRequester.requestFocusSafely()
                    !micFocused && focusedHistoryIndex < 0 -> micFocusRequester.requestFocusSafely()
                }
            }
            InputAction.Right -> {
                if (micFocused) {
                    focusInput()
                }
            }
            InputAction.Confirm -> {
                when {
                    focusedHistoryIndex in visibleHistory.indices -> {
                        onHistorySelected(visibleHistory[focusedHistoryIndex])
                        focusInput()
                    }
                    micFocused -> launchVoiceSearch()
                    inputFocused -> {
                        submitCurrentQuery()
                        keyboardController?.hide()
                    }
                    else -> focusInput()
                }
            }
            InputAction.Play,
            InputAction.Pause,
            InputAction.PlayPause,
            InputAction.PreviousEpisode,
            InputAction.NextEpisode,
            InputAction.Back,
            null -> Unit
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { dismissSearch() }
                },
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(
                    start = if (isTelevision) 40.dp else 16.dp,
                    top = 0.dp,
                    end = if (isTelevision) 40.dp else 16.dp,
                    bottom = 10.dp,
                ),
            contentAlignment = Alignment.TopCenter,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .yummyDialogMotion(),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = YummyRadii.mediumShape,
                border = yummySurfaceBorder(YummySurfaceRole.Row),
                shadowElevation = 10.dp,
            ) {
                Column(
                    modifier = Modifier
                        .padding(YummySpacing.sm)
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when {
                                micFocused && event.key == Key.DirectionRight -> {
                                    focusInput()
                                    true
                                }
                                micFocused && event.key == Key.DirectionDown -> focusHistoryOrExit()
                                inputFocused && event.key == Key.DirectionLeft -> {
                                    micFocusRequester.requestFocusSafely()
                                    true
                                }
                                inputFocused && event.key == Key.DirectionDown -> focusHistoryOrExit()
                                else -> false
                            }
                        },
                    verticalArrangement = Arrangement.spacedBy(YummySpacing.xs),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = launchVoiceSearch,
                            modifier = Modifier
                                .size(56.dp)
                                .focusRequester(micFocusRequester)
                                .focusProperties {
                                    right = focusRequester
                                    down = firstHistoryFocusRequester
                                }
                                .onFocusChanged { focusState ->
                                    micFocused = focusState.hasFocus
                                    if (focusState.hasFocus) {
                                        focusedHistoryIndex = -1
                                    }
                                }
                                .onPreviewKeyEvent { event ->
                                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (event.key) {
                                        Key.DirectionRight -> {
                                            focusInput()
                                            true
                                        }
                                        Key.DirectionDown -> focusHistoryOrExit()
                                        else -> false
                                    }
                                }
                                .focusRing(RoundedCornerShape(8.dp)),
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = uiText(UiStringKey.VoiceSearch))
                        }
                        OutlinedTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            placeholder = { Text(uiText(UiStringKey.FindAnime)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Search,
                            ),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    submitCurrentQuery()
                                    keyboardController?.hide()
                                },
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(2.dp)
                                .focusRequester(focusRequester)
                                .focusProperties {
                                    left = micFocusRequester
                                    down = firstHistoryFocusRequester
                                }
                                .onFocusChanged { focusState ->
                                    inputFocused = focusState.hasFocus
                                    if (focusState.hasFocus) {
                                        focusedHistoryIndex = -1
                                    }
                                }
                                .onPreviewKeyEvent { event ->
                                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (event.key) {
                                        Key.DirectionLeft -> {
                                            micFocusRequester.requestFocusSafely()
                                            true
                                        }
                                        Key.DirectionDown -> focusHistoryOrExit()
                                        else -> false
                                    }
                                },
                        )
                    }
                    if (visibleHistory.isNotEmpty()) {
                        SearchHistoryDropdown(
                            history = visibleHistory,
                            focusRequesters = historyFocusRequesters,
                            inputFocusRequester = focusRequester,
                            onSelect = { historyQuery ->
                                onHistorySelected(historyQuery)
                                focusInput()
                            },
                            onFocusedIndexChange = { index, focused ->
                                if (focused) {
                                    focusedHistoryIndex = index
                                } else if (focusedHistoryIndex == index) {
                                    focusedHistoryIndex = -1
                                }
                            },
                            onFocusInput = ::focusInput,
                            onExitDown = ::exitDownFromSearch,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHistoryDropdown(
    history: List<String>,
    focusRequesters: List<FocusRequester>,
    inputFocusRequester: FocusRequester,
    onSelect: (String) -> Unit,
    onFocusedIndexChange: (Int, Boolean) -> Unit,
    onFocusInput: () -> Unit,
    onExitDown: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(YummyRadii.smallShape)
            .background(yummySurfaceColor(YummySurfaceRole.Panel))
            .border(yummySurfaceBorder(YummySurfaceRole.Panel), YummyRadii.smallShape),
    ) {
        history.forEachIndexed { index, historyQuery ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.14f)),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .focusRequester(focusRequesters.getOrElse(index) { FocusRequester.Default })
                    .focusProperties {
                        up = if (index == 0) {
                            inputFocusRequester
                        } else {
                            focusRequesters[index - 1]
                        }
                        down = focusRequesters.getOrElse(index + 1) { FocusRequester.Default }
                    }
                    .onFocusChanged { focusState ->
                        onFocusedIndexChange(index, focusState.isFocused || focusState.hasFocus)
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionUp -> {
                                if (index == 0) {
                                    onFocusInput()
                                } else {
                                    focusRequesters[index - 1].requestFocusSafely()
                                }
                                true
                            }
                            Key.DirectionDown -> {
                                val nextFocus = focusRequesters.getOrNull(index + 1)
                                if (nextFocus == null) {
                                    onExitDown()
                                } else {
                                    nextFocus.requestFocusSafely()
                                }
                                true
                            }
                            else -> false
                        }
                    }
                    .dpadClickable(YummyRadii.smallShape) { onSelect(historyQuery) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = historyQuery,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DialogActionRow(
    content: @Composable FlowRowScope.() -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(YummySpacing.sm),
        content = content,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DialogActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
    compact: Boolean = false,
    badgeText: String? = null,
) {
    val shape = YummyRadii.smallShape
    val buttonEnabled = enabled && !loading
    var focused by remember { mutableStateOf(false) }
    val inputModeManager = LocalInputModeManager.current
    val scope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val focusVisible = focused && inputModeManager.inputMode != InputMode.Touch
    val interactionSource = remember { MutableInteractionSource() }
    val contentPadding = if (compact) {
        PaddingValues(horizontal = 6.dp, vertical = YummySpacing.xs)
    } else {
        PaddingValues(horizontal = YummySpacing.md, vertical = YummySpacing.sm)
    }
    Surface(
        modifier = modifier
            .then(
                if (compact) {
                    Modifier
                } else {
                    Modifier.widthIn(
                        min = if (primary) {
                            YummySizes.primaryDialogButtonMinWidth
                        } else {
                            YummySizes.dialogButtonMinWidth
                        },
                    )
                },
            )
            .defaultMinSize(minWidth = 0.dp, minHeight = YummySizes.dialogButtonHeight)
            .then(
                if (buttonEnabled) {
                    Modifier
                        .bringIntoViewRequester(bringIntoViewRequester)
                        .onFocusChanged { focusState ->
                            val focusedNow = focusState.isFocused || focusState.hasFocus
                            focused = focusedNow
                            if (focusedNow && inputModeManager.inputMode != InputMode.Touch) {
                                scope.launch {
                                    withFrameNanos { }
                                    bringIntoViewRequester.bringIntoView()
                                }
                            }
                        }
                        .clearFocusAfterTouch()
                        .clip(shape)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick,
                        )
                } else {
                    Modifier.clip(shape)
                },
            ),
        color = yummyActionSurfaceColor(enabled = buttonEnabled, selected = primary, focused = focusVisible),
        contentColor = yummyActionContentColor(enabled = buttonEnabled, selected = primary, focused = focusVisible),
        border = yummyActionBorder(enabled = buttonEnabled, selected = primary, focused = focusVisible),
        shadowElevation = if (focusVisible) 0.dp else 2.dp,
        shape = shape,
    ) {
        Box(
            modifier = Modifier.defaultMinSize(minHeight = YummySizes.dialogButtonHeight),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier.padding(contentPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = if (focusVisible) YummyColors.onFocus else YummyColors.focus,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = text,
                    style = if (compact) {
                        MaterialTheme.typography.labelLarge
                    } else {
                        MaterialTheme.typography.titleSmall
                    },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = if (compact) TextOverflow.Ellipsis else TextOverflow.Clip,
                    textAlign = if (compact) TextAlign.Center else TextAlign.Unspecified,
                )
            }
            if (buttonEnabled && badgeText != null) {
                Surface(
                    color = YummyColors.offline,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 2.dp, end = 2.dp)
                        .widthIn(min = 16.dp)
                        .height(16.dp),
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun FiltersDialogAccordion(
    filters: BrowseFilters,
    auth: AuthUiState,
    catalogState: LoadState<FilterCatalog>,
    offlineEntries: List<OfflineAnimeEntry>,
    forcedOfflineMode: Boolean,
    onApply: (BrowseFilters) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isAuthorized = auth.profile != null && !forcedOfflineMode
    var draft by remember(filters, isAuthorized, forcedOfflineMode) {
        val baseFilters = if (isAuthorized) {
            filters
        } else {
            filters.copy(userMarks = emptySet(), excludedUserMarks = emptySet())
        }
        mutableStateOf(
            if (forcedOfflineMode) {
                baseFilters.copy(offlineOnly = true, userMarks = emptySet(), excludedUserMarks = emptySet())
            } else {
                baseFilters
            },
        )
    }
    var expandedSection by remember { mutableStateOf("") }
    var advancedVisible by remember(filters) { mutableStateOf(false) }
    val catalog = remember(catalogState, offlineEntries, forcedOfflineMode) {
        if (forcedOfflineMode) {
            offlineEntries.toOfflineFilterCatalog()
        } else {
            catalogState.readyDataOrNull() ?: FilterCatalog.Empty
        }
    }
    val studioOptions = remember(catalog.studios, draft.studios, draft.studioTitles) {
        mergedFilterOptions(catalog.studios, draft.studios, draft.studioTitles)
    }
    val creatorOptions = remember(catalog.creators, draft.creators, draft.creatorTitles) {
        mergedFilterOptions(catalog.creators, draft.creators, draft.creatorTitles)
    }
    val studioOptionTitles = remember(studioOptions) {
        studioOptions.associate { it.value to it.title }
    }
    val creatorOptionTitles = remember(creatorOptions) {
        creatorOptions.associate { it.value to it.title }
    }
    val hiddenActiveCount = remember(draft, isAuthorized) { draft.advancedFilterCount(isAuthorized) }
    val containerScrollState = rememberScrollState()
    val applyFocusRequester = remember { FocusRequester() }
    val moveFocusToActions: () -> Unit = remember {
        {
            applyFocusRequester.requestFocusSafely()
            Unit
        }
    }
    fun resetAndDismiss() {
        draft = if (forcedOfflineMode) BrowseFilters(offlineOnly = true) else BrowseFilters()
        onReset()
        onDismiss()
    }

    fun applyAndDismiss() {
        onApply(
            when {
                forcedOfflineMode -> draft.copy(
                    offlineOnly = true,
                    userMarks = emptySet(),
                    excludedUserMarks = emptySet(),
                )
                isAuthorized -> draft
                else -> draft.copy(userMarks = emptySet(), excludedUserMarks = emptySet())
            },
        )
        onDismiss()
    }

    AlertDialog(
        modifier = Modifier.yummyDialogMotion(),
        onDismissRequest = onDismiss,
        title = { Text(uiText(UiStringKey.Filters)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp)
                    .verticalScroll(state = containerScrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SortAccordionSection(
                    expanded = expandedSection == "sort",
                    selected = draft.sort,
                    onToggleExpanded = {
                        expandedSection = if (expandedSection == "sort") "" else "sort"
                    },
                    onSelected = { draft = draft.copy(sort = it) },
                    onSideExit = moveFocusToActions,
                )

                FilterAccordionSection(
                    id = "status",
                    title = uiText(UiStringKey.Status),
                    options = statusFilterOptions,
                    selected = draft.statuses,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value -> draft = draft.copy(statuses = draft.statuses.toggle(value)) },
                    onSideExit = moveFocusToActions,
                )

                FilterAccordionSection(
                    id = "genres",
                    title = uiText(UiStringKey.Genres),
                    options = catalog.genres,
                    selected = draft.genres,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value -> draft = draft.copy(genres = draft.genres.toggle(value)) },
                    onSideExit = moveFocusToActions,
                    searchable = true,
                )

                if (!advancedVisible) {
                    AdvancedFiltersButton(
                        activeCount = hiddenActiveCount,
                        onClick = { advancedVisible = true },
                    )
                }

                if (advancedVisible) {
                FilterAccordionSection(
                    id = "excluded_genres",
                    title = uiText(UiStringKey.ExcludeGenres),
                    options = catalog.genres,
                    selected = draft.excludedGenres,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value -> draft = draft.copy(excludedGenres = draft.excludedGenres.toggle(value)) },
                    onSideExit = moveFocusToActions,
                    searchable = true,
                )

                FilterAccordionSection(
                    id = "types",
                    title = uiText(UiStringKey.Type),
                    options = catalog.types,
                    selected = draft.types,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value -> draft = draft.copy(types = draft.types.toggle(value)) },
                    onSideExit = moveFocusToActions,
                )

                FilterAccordionSection(
                    id = "studios",
                    title = uiText(UiStringKey.Studio),
                    options = studioOptions,
                    selected = draft.studios,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value ->
                        draft = draft.toggleStudioFilter(value, studioOptionTitles[value])
                    },
                    onSideExit = moveFocusToActions,
                    searchable = true,
                )

                FilterAccordionSection(
                    id = "creators",
                    title = uiText(UiStringKey.Director),
                    options = creatorOptions,
                    selected = draft.creators,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value ->
                        draft = draft.toggleCreatorFilter(value, creatorOptionTitles[value])
                    },
                    onSideExit = moveFocusToActions,
                    searchable = true,
                )

                RangeAccordionSection(
                    id = "years",
                    title = uiText(UiStringKey.Year),
                    summary = rangeSummary(draft.fromYear, draft.toYear),
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    startLabel = uiText(UiStringKey.From),
                    endLabel = uiText(UiStringKey.To),
                    startText = draft.fromYear?.toString().orEmpty(),
                    endText = draft.toYear?.toString().orEmpty(),
                    keyboardType = KeyboardType.Number,
                    sanitizeInput = ::integerInput,
                    onStartChange = { value -> draft = draft.copy(fromYear = value.yearFilterValue()) },
                    onEndChange = { value -> draft = draft.copy(toYear = value.yearFilterValue()) },
                    onSideExit = moveFocusToActions,
                )

                FilterAccordionSection(
                    id = "seasons",
                    title = uiText(UiStringKey.Season),
                    options = seasonFilterOptions,
                    selected = draft.seasons,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value -> draft = draft.copy(seasons = draft.seasons.toggle(value)) },
                    onSideExit = moveFocusToActions,
                )

                FilterAccordionSection(
                    id = "translates",
                    title = uiText(UiStringKey.Voice),
                    options = translateFilterOptions,
                    selected = draft.translates,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value -> draft = draft.copy(translates = draft.translates.toggle(value)) },
                    onSideExit = moveFocusToActions,
                )

                FilterAccordionSection(
                    id = "age",
                    title = uiText(UiStringKey.Age),
                    options = ageRatingFilterOptions,
                    selected = draft.ageRatings,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value -> draft = draft.copy(ageRatings = draft.ageRatings.toggle(value)) },
                    onSideExit = moveFocusToActions,
                )

                RangeAccordionSection(
                    id = "rating_range",
                    title = uiText(UiStringKey.Rating5709e2),
                    summary = rangeSummary(draft.minRating, draft.maxRating),
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    startLabel = uiText(UiStringKey.From),
                    endLabel = uiText(UiStringKey.To),
                    startText = draft.minRating.filterText(),
                    endText = draft.maxRating.filterText(),
                    keyboardType = KeyboardType.Decimal,
                    sanitizeInput = ::decimalInput,
                    onStartChange = { value -> draft = draft.copy(minRating = value.ratingFilterValue()) },
                    onEndChange = { value -> draft = draft.copy(maxRating = value.ratingFilterValue()) },
                    onSideExit = moveFocusToActions,
                )

                RangeAccordionSection(
                    id = "episodes",
                    title = uiText(UiStringKey.Episodes),
                    summary = rangeSummary(draft.episodeFrom, draft.episodeTo),
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    startLabel = uiText(UiStringKey.From),
                    endLabel = uiText(UiStringKey.To),
                    startText = draft.episodeFrom?.toString().orEmpty(),
                    endText = draft.episodeTo?.toString().orEmpty(),
                    keyboardType = KeyboardType.Number,
                    sanitizeInput = ::integerInput,
                    onStartChange = { value -> draft = draft.copy(episodeFrom = value.episodeFilterValue()) },
                    onEndChange = { value -> draft = draft.copy(episodeTo = value.episodeFilterValue()) },
                    onSideExit = moveFocusToActions,
                )

                if (isAuthorized) {
                    FilterAccordionSection(
                        id = "user_marks",
                        title = uiText(UiStringKey.Marks),
                        options = userMarkFilterOptions,
                        selected = draft.userMarks,
                        expandedSection = expandedSection,
                        onExpandedChange = { expandedSection = it },
                        onToggle = { value -> draft = draft.copy(userMarks = draft.userMarks.toggle(value)) },
                        onSideExit = moveFocusToActions,
                    )
                    FilterAccordionSection(
                        id = "excluded_user_marks",
                        title = uiText(UiStringKey.ExcludeMarks),
                        options = userMarkFilterOptions,
                        selected = draft.excludedUserMarks,
                        expandedSection = expandedSection,
                        onExpandedChange = { expandedSection = it },
                        onToggle = { value -> draft = draft.copy(excludedUserMarks = draft.excludedUserMarks.toggle(value)) },
                        onSideExit = moveFocusToActions,
                    )
                }

                if (forcedOfflineMode) {
                    OfflineFilterNotice()
                } else {
                    SettingsSwitchRow(
                        title = uiText(UiStringKey.AvailableOffline),
                        checked = draft.offlineOnly,
                        onCheckedChange = { checked -> draft = draft.copy(offlineOnly = checked) },
                    )
                }
                }

                if (!forcedOfflineMode && catalogState is LoadState.Error) {
                    InlineErrorMessage(
                        message = catalogState.message,
                        modifier = Modifier.padding(top = YummySpacing.xs),
                    )
                }
            }
        },
        confirmButton = {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (maxWidth < 300.dp) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(YummySpacing.sm),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DialogActionButton(
                                text = uiText(UiStringKey.Reset),
                                modifier = Modifier.weight(1f),
                                compact = true,
                                onClick = { resetAndDismiss() },
                            )
                            DialogActionButton(
                                text = uiText(UiStringKey.Cancel),
                                modifier = Modifier.weight(1f),
                                compact = true,
                                onClick = onDismiss,
                            )
                        }
                        DialogActionButton(
                            text = uiText(UiStringKey.Apply),
                            primary = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(applyFocusRequester),
                            onClick = { applyAndDismiss() },
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DialogActionButton(
                            text = uiText(UiStringKey.Reset),
                            modifier = Modifier.weight(1f),
                            compact = true,
                            onClick = { resetAndDismiss() },
                        )
                        DialogActionButton(
                            text = uiText(UiStringKey.Cancel),
                            modifier = Modifier.weight(1f),
                            compact = true,
                            onClick = onDismiss,
                        )
                        DialogActionButton(
                            text = uiText(UiStringKey.Apply),
                            primary = true,
                            compact = true,
                            modifier = Modifier
                                .weight(1.25f)
                                .focusRequester(applyFocusRequester),
                            onClick = { applyAndDismiss() },
                        )
                    }
                }
            }
        },
    )
}

@Composable
internal fun OfflineFilterNotice() {
    Surface(
        color = yummySurfaceColor(YummySurfaceRole.Row),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = uiText(UiStringKey.OfflineOnlyDownloadedAnimeAreShown),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun AdvancedFiltersButton(
    activeCount: Int,
    onClick: () -> Unit,
) {
    val title = if (activeCount > 0) {
        "${uiText(UiStringKey.AdvancedMode)} • $activeCount"
    } else {
        uiText(UiStringKey.AdvancedMode)
    }
    val shape = RoundedCornerShape(8.dp)
    val selected = activeCount > 0
    val contentColor = yummyActionContentColor(selected = selected)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .then(
                if (selected) {
                    Modifier
                        .background(yummyActionSurfaceColor(selected = true), shape)
                        .border(yummyActionBorder(selected = true), shape)
                } else {
                    Modifier
                },
            )
            .dpadClickable(shape, onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = contentColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = contentColor)
    }
}

@Composable
internal fun SortAccordionSection(
    expanded: Boolean,
    selected: AnimeSort,
    onToggleExpanded: () -> Unit,
    onSelected: (AnimeSort) -> Unit,
    onSideExit: () -> Unit,
) {
    AccordionHeader(
        title = uiText(UiStringKey.Sorting),
        summary = selected.localizedTitle(),
        expanded = expanded,
        active = selected != AnimeSort.Rating,
        onClick = onToggleExpanded,
    )

    if (expanded) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AnimeSort.entries.forEach { sort ->
                SelectableFilterRow(
                    title = sort.localizedTitle(),
                    selected = selected == sort,
                    onClick = { onSelected(sort) },
                    onSideExit = onSideExit,
                )
            }
        }
    }
}

@Composable
internal fun FilterAccordionSection(
    id: String,
    title: String,
    options: List<FilterOption>,
    selected: Set<String>,
    expandedSection: String,
    onExpandedChange: (String) -> Unit,
    onToggle: (String) -> Unit,
    onSideExit: () -> Unit,
    searchable: Boolean = false,
) {
    if (options.isEmpty()) return

    val uiLocale = LocalUiLanguage.current.uiLocale()
    val sortedOptions = remember(options, uiLocale) { options.sortedByTitle(uiLocale) }
    val expanded = expandedSection == id
    var query by remember(id, expanded) { mutableStateOf("") }
    val visibleOptions = remember(sortedOptions, query, searchable) {
        if (!searchable || query.isBlank()) {
            sortedOptions
        } else {
            sortedOptions.filter { option ->
                option.title.contains(query.trim(), ignoreCase = true) ||
                    option.value.contains(query.trim(), ignoreCase = true)
            }
        }
    }
    AccordionHeader(
        title = title,
        summary = selectedFilterSummary(sortedOptions, selected),
        expanded = expanded,
        active = selected.isNotEmpty(),
        onClick = { onExpandedChange(if (expanded) "" else id) },
    )

    if (expanded) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (searchable) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(uiText(UiStringKey.Search)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .onPreviewKeyEvent { event ->
                            if (event.isHorizontalFilterExit()) {
                                onSideExit()
                                true
                            } else {
                                false
                            }
                        },
                )
            }
            visibleOptions.forEach { option ->
                SelectableFilterRow(
                    title = option.localizedTitle(),
                    selected = option.value in selected,
                    onClick = { onToggle(option.value) },
                    onSideExit = onSideExit,
                )
            }
        }
    }
}

@Composable
internal fun RangeAccordionSection(
    id: String,
    title: String,
    summary: String,
    expandedSection: String,
    onExpandedChange: (String) -> Unit,
    startLabel: String,
    endLabel: String,
    startText: String,
    endText: String,
    keyboardType: KeyboardType,
    sanitizeInput: (String) -> String,
    onStartChange: (String) -> Unit,
    onEndChange: (String) -> Unit,
    onSideExit: () -> Unit,
) {
    val expanded = expandedSection == id
    var localStart by remember(id, startText) { mutableStateOf(startText) }
    var localEnd by remember(id, endText) { mutableStateOf(endText) }

    AccordionHeader(
        title = title,
        summary = summary,
        expanded = expanded,
        active = startText.isNotBlank() || endText.isNotBlank(),
        onClick = { onExpandedChange(if (expanded) "" else id) },
    )

    if (expanded) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = localStart,
                onValueChange = { value ->
                    val sanitized = sanitizeInput(value)
                    localStart = sanitized
                    onStartChange(sanitized)
                },
                label = { Text(startLabel) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier
                    .weight(1f)
                    .padding(2.dp)
                    .defaultMinSize(minWidth = 0.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.isHorizontalFilterExit()) {
                            onSideExit()
                            true
                        } else {
                            false
                        }
                    },
            )
            OutlinedTextField(
                value = localEnd,
                onValueChange = { value ->
                    val sanitized = sanitizeInput(value)
                    localEnd = sanitized
                    onEndChange(sanitized)
                },
                label = { Text(endLabel) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier
                    .weight(1f)
                    .padding(2.dp)
                    .defaultMinSize(minWidth = 0.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.isHorizontalFilterExit()) {
                            onSideExit()
                            true
                        } else {
                            false
                        }
                    },
            )
        }
    }
}

@Composable
internal fun AccordionHeader(
    title: String,
    summary: String = "",
    expanded: Boolean,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    centerTitle: Boolean = false,
) {
    val shape = RoundedCornerShape(8.dp)
    val backgroundColor = yummyActionSurfaceColor(selected = active)
    val contentColor = yummyActionContentColor(selected = active)
    val summaryColor = if (active) {
        YummyColors.focus.copy(alpha = 0.82f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .background(backgroundColor, shape)
            .border(yummyActionBorder(selected = active), shape)
            .dpadClickable(shape, onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        val textPadding = if (centerTitle) {
            Modifier.padding(horizontal = 34.dp)
        } else {
            Modifier.padding(end = 34.dp)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .then(textPadding),
            horizontalAlignment = if (centerTitle) Alignment.CenterHorizontally else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                textAlign = if (centerTitle) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
            if (summary.isNotBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = summaryColor,
                    textAlign = if (centerTitle) TextAlign.Center else TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
internal fun SelectableFilterRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    onSideExit: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .onPreviewKeyEvent { event ->
                if (event.isHorizontalFilterExit()) {
                    onSideExit?.invoke()
                    onSideExit != null
                } else {
                    false
                }
            }
            .dpadClickable(RoundedCornerShape(8.dp), onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = { onClick() },
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

internal fun androidx.compose.ui.input.key.KeyEvent.isHorizontalFilterExit(): Boolean {
    return type == KeyEventType.KeyDown && (key == Key.DirectionLeft || key == Key.DirectionRight)
}

internal fun Modifier.horizontalEdgeFocusHints(
    index: Int,
    total: Int,
    leftExit: FocusRequester? = null,
    rightExit: FocusRequester? = null,
): Modifier {
    if (total <= 0 || index < 0) return this
    val isFirst = index == 0
    val isLast = index >= total - 1
    return focusProperties {
        if (isFirst && leftExit != null) left = leftExit
        if (isLast && rightExit != null) right = rightExit
    }
}

@Composable
internal fun rangeSummary(from: Number?, to: Number?): String {
    val start = from.filterText()
    val end = to.filterText()
    return when {
        start.isBlank() && end.isBlank() -> uiText(UiStringKey.All)
        start.isNotBlank() && end.isNotBlank() -> "$start - $end"
        start.isNotBlank() -> "${uiText(UiStringKey.FromDba126)} $start"
        else -> "${uiText(UiStringKey.To7618b0)} $end"
    }
}

internal fun Number?.filterText(): String {
    return when (this) {
        null -> ""
        is Double -> if (this % 1.0 == 0.0) toInt().toString() else toString()
        else -> toString()
    }
}

internal fun BrowseFilters.advancedFilterCount(isAuthorized: Boolean): Int {
    return excludedGenres.size +
        seasons.size +
        types.size +
        studios.size +
        creators.size +
        translates.size +
        ageRatings.size +
        listOfNotNull(fromYear, toYear, minRating, maxRating, episodeFrom, episodeTo).size +
        (if (isAuthorized) userMarks.size + excludedUserMarks.size else 0) +
        if (offlineOnly) 1 else 0
}

internal fun integerInput(value: String): String {
    return value.filter { it.isDigit() }.take(5)
}

internal fun decimalInput(value: String): String {
    val normalized = value.replace(',', '.')
    val builder = StringBuilder()
    var dotSeen = false
    normalized.forEach { char ->
        when {
            char.isDigit() -> builder.append(char)
            char == '.' && !dotSeen -> {
                builder.append(char)
                dotSeen = true
            }
        }
    }
    return builder.toString().take(4)
}

internal fun String.yearFilterValue(): Int? {
    return toIntOrNull()?.takeIf { it in 1900..2100 }
}

internal fun String.episodeFilterValue(): Int? {
    return toIntOrNull()?.takeIf { it in 0..10000 }
}

internal fun String.ratingFilterValue(): Double? {
    return toDoubleOrNull()?.takeIf { it in 0.0..10.0 }
}

internal fun List<FilterOption>.sortedByTitle(locale: Locale = Locale.getDefault()): List<FilterOption> {
    val collator = Collator.getInstance(locale).apply {
        strength = Collator.PRIMARY
    }
    return sortedWith { first, second ->
        val titleCompare = collator.compare(first.title, second.title)
        if (titleCompare != 0) titleCompare else first.value.compareTo(second.value)
    }
}

@Composable
internal fun selectedFilterSummary(
    options: List<FilterOption>,
    selected: Set<String>,
): String {
    if (selected.isEmpty()) return uiText(UiStringKey.All)

    val titles = options
        .filter { it.value in selected }
        .map { it.localizedTitle() }

    return when {
        titles.isEmpty() -> "${selected.size} ${uiText(UiStringKey.Selected)}"
        titles.size <= 2 -> titles.joinToString(", ")
        else -> titles.take(2).joinToString(", ") + " +${titles.size - 2}"
    }
}

internal fun Set<String>.toggle(value: String): Set<String> {
    return if (value in this) this - value else this + value
}
