package me.yummydroid.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged
import me.yummydroid.app.BrowseSection

// BrowsePagerBinding
internal data class BrowsePagerBinding(
    val runtime: BrowsePagerRuntime,
    val pagerPage: Int,
    val usePager: Boolean,
    val tabPosition: Float?,
    val topBarVisible: Boolean,
    val topBarVisibilityProgress: State<Float>,
    val pagerSettledAtTarget: Boolean,
    val focusRequestNonce: Long,
    val onSectionSelected: (BrowseSection) -> Unit,
    val onHorizontalExit: (Int, VisualGridDirection) -> Boolean,
)

@Composable
private fun BrowsePagerAlignmentEffects(
    active: Boolean,
    effectiveSection: BrowseSection,
    pagerSections: List<BrowseSection>,
    pagerPage: Int,
    usePager: Boolean,
    runtime: BrowsePagerRuntime,
    topBarProgressFor: (BrowseSection) -> Float,
    onBrowseSectionChange: (BrowseSection) -> Unit,
) {
    BrowsePagerTargetAlignmentEffect(
        active = active,
        effectiveSection = effectiveSection,
        pagerSections = pagerSections,
        pagerPage = pagerPage,
        usePager = usePager,
        runtime = runtime,
        topBarProgressFor = topBarProgressFor,
    )
    BrowsePagerSettlementEffect(
        active = active,
        effectiveSection = effectiveSection,
        pagerSections = pagerSections,
        pagerPage = pagerPage,
        usePager = usePager,
        runtime = runtime,
        onBrowseSectionChange = onBrowseSectionChange,
    )
}

@Composable
internal fun rememberBrowsePagerBinding(
    active: Boolean,
    effectiveSection: BrowseSection,
    pagerSections: List<BrowseSection>,
    usePager: Boolean,
    dpadFocusEnabled: Boolean,
    dpadLayerFocusRequestNonce: Long,
    isWide: Boolean,
    forcedOfflineMode: Boolean,
    browseCoordinator: BrowseRootUiCoordinator,
    topBarCollapseDistancePx: Float,
    runtime: BrowsePagerRuntime,
    onBrowseSectionChange: (BrowseSection) -> Unit,
    onHomeBrowseBackStateChange: (HomeBrowseBackState) -> Unit,
    onRequestSectionTabsFocus: (BrowseSection, Boolean) -> Boolean,
): BrowsePagerBinding {
    val pagerPage = pagerSections.indexOf(effectiveSection).takeIf { it >= 0 } ?: 0
    val topBarProgressFor: (BrowseSection) -> Float = { section ->
        if (isWide) {
            1f
        } else {
            browseCoordinator.topBarVisibilityProgress(section, topBarCollapseDistancePx)
        }
    }
    val presentation = rememberBrowsePagerPresentation(
        active = active,
        effectiveSection = effectiveSection,
        pagerSections = pagerSections,
        pagerPage = pagerPage,
        usePager = usePager,
        isWide = isWide,
        forcedOfflineMode = forcedOfflineMode,
        browseCoordinator = browseCoordinator,
        topBarCollapseDistancePx = topBarCollapseDistancePx,
        runtime = runtime,
        topBarProgressFor = topBarProgressFor,
    )

    BrowsePagerSectionFocusEffect(
        effectiveSection = effectiveSection,
        usePager = usePager,
        dpadFocusEnabled = dpadFocusEnabled,
        runtime = runtime,
        onRequestSectionTabsFocus = onRequestSectionTabsFocus,
    )
    BrowsePagerAlignmentEffects(
        active = active,
        effectiveSection = effectiveSection,
        pagerSections = pagerSections,
        pagerPage = pagerPage,
        usePager = usePager,
        runtime = runtime,
        topBarProgressFor = topBarProgressFor,
        onBrowseSectionChange = onBrowseSectionChange,
    )
    BrowseHomeBackStateEffect(
        active = active,
        effectiveSection = effectiveSection,
        pagerSections = pagerSections,
        pagerPage = pagerPage,
        usePager = usePager,
        runtime = runtime,
        pagerPosition = presentation.pagerPosition,
        onHomeBrowseBackStateChange = onHomeBrowseBackStateChange,
    )

    val latestOnBrowseSectionChange by rememberUpdatedState(onBrowseSectionChange)
    val selectSection: (BrowseSection, Boolean) -> Boolean = { section, keepTabsFocused ->
        runtime.selectSection(
            section = section,
            effectiveSection = effectiveSection,
            pagerSections = pagerSections,
            pagerPage = pagerPage,
            usePager = usePager,
            dpadFocusEnabled = dpadFocusEnabled,
            keepTabsFocused = keepTabsFocused,
            topBarProgressFor = topBarProgressFor,
            onRequestSectionTabsFocus = onRequestSectionTabsFocus,
            onBrowseSectionChange = latestOnBrowseSectionChange,
        )
    }
    return BrowsePagerBinding(
        runtime = runtime,
        pagerPage = pagerPage,
        usePager = usePager,
        tabPosition = presentation.tabPosition,
        topBarVisible = presentation.topBarVisible,
        topBarVisibilityProgress = presentation.topBarVisibilityProgress,
        pagerSettledAtTarget = presentation.pagerSettledAtTarget,
        focusRequestNonce = dpadLayerFocusRequestNonce + runtime.pageFocusRequestNonce,
        onSectionSelected = { section -> selectSection(section, true) },
        onHorizontalExit = { page, direction ->
            val targetPage = when (direction) {
                VisualGridDirection.Left -> page - 1
                VisualGridDirection.Right -> page + 1
                VisualGridDirection.Up,
                VisualGridDirection.Down -> -1
            }
            val targetSection = pagerSections.getOrNull(targetPage)
            targetSection != null && selectSection(targetSection, false)
        },
    )
}

private fun BrowsePagerRuntime.selectSection(
    section: BrowseSection,
    effectiveSection: BrowseSection,
    pagerSections: List<BrowseSection>,
    pagerPage: Int,
    usePager: Boolean,
    dpadFocusEnabled: Boolean,
    keepTabsFocused: Boolean,
    topBarProgressFor: (BrowseSection) -> Float,
    onRequestSectionTabsFocus: (BrowseSection, Boolean) -> Boolean,
    onBrowseSectionChange: (BrowseSection) -> Unit,
): Boolean {
    if (section !in pagerSections) return false
    if (section == effectiveSection) {
        if (keepTabsFocused && dpadFocusEnabled) {
            onRequestSectionTabsFocus(section, false)
        }
        return true
    }
    val keepTabsFocusedForKeyboard = keepTabsFocused && dpadFocusEnabled
    val requestContentFocusAfterTransition = !keepTabsFocused && dpadFocusEnabled
    keepTabsFocusedForSectionChange = keepTabsFocusedForKeyboard
    if (keepTabsFocusedForKeyboard) {
        pendingTabsFocusSection = section
        suppressedContentFocusSection = section
        onRequestSectionTabsFocus(section, false)
    } else {
        pendingTabsFocusSection = null
        suppressedContentFocusSection = null
    }
    if (usePager) {
        val targetPage = pagerSections.indexOf(section).takeIf { it >= 0 }
        programmaticScrollTarget = targetPage
        programmaticTabTargetPosition = targetPage?.toFloat()
        topBarProgrammaticTargetProgress = topBarProgressFor(section)
        transitionFocusSourcePage = if (requestContentFocusAfterTransition) pagerPage else null
        requestContentFocusOnFinish = requestContentFocusAfterTransition
    }
    onBrowseSectionChange(section)
    return true
}

// BrowsePagerPresentation
internal data class BrowsePagerPresentation(
    val pagerPosition: Float,
    val tabPosition: Float?,
    val topBarVisible: Boolean,
    val topBarVisibilityProgress: State<Float>,
    val pagerSettledAtTarget: Boolean,
)

@Composable
internal fun rememberBrowsePagerPresentation(
    active: Boolean,
    effectiveSection: BrowseSection,
    pagerSections: List<BrowseSection>,
    pagerPage: Int,
    usePager: Boolean,
    isWide: Boolean,
    forcedOfflineMode: Boolean,
    browseCoordinator: BrowseRootUiCoordinator,
    topBarCollapseDistancePx: Float,
    runtime: BrowsePagerRuntime,
    topBarProgressFor: (BrowseSection) -> Float,
): BrowsePagerPresentation {
    val pagerPositionState = remember(runtime.pagerState) {
        derivedStateOf { runtime.pagerState.currentPage + runtime.pagerState.currentPageOffsetFraction }
    }
    val topBarTargetProgressState = remember(
        effectiveSection,
        browseCoordinator,
        topBarCollapseDistancePx,
        isWide,
        forcedOfflineMode,
    ) {
        derivedStateOf { topBarProgressFor(effectiveSection) }
    }
    val topBarEffectiveTargetProgressState = remember(
        runtime.topBarProgrammaticTargetProgress,
        topBarTargetProgressState,
    ) {
        derivedStateOf { runtime.topBarProgrammaticTargetProgress ?: topBarTargetProgressState.value }
    }
    val pagerDriven = usePager &&
        (runtime.pagerState.isScrollInProgress ||
            runtime.programmaticScrollTarget != null ||
            runtime.transitionFocusSourcePage != null)
    val topBarVisibilityProgressState = remember(
        pagerSections,
        runtime.pagerState,
        browseCoordinator,
        topBarEffectiveTargetProgressState,
        topBarCollapseDistancePx,
        isWide,
        forcedOfflineMode,
        pagerDriven,
    ) {
        derivedStateOf {
            val effectiveTargetProgress = topBarEffectiveTargetProgressState.value
            if (!pagerDriven || pagerSections.isEmpty()) {
                effectiveTargetProgress
            } else {
                val maxPage = pagerSections.lastIndex
                val position = pagerPositionState.value.coerceIn(0f, maxPage.toFloat())
                val startPage = position.toInt().coerceIn(0, maxPage)
                val endPage = (startPage + 1).coerceAtMost(maxPage)
                val fraction = (position - startPage).coerceIn(0f, 1f)
                val startProgress = topBarProgressFor(pagerSections[startPage])
                val endProgress = topBarProgressFor(pagerSections[endPage])
                startProgress + (endProgress - startProgress) * fraction
            }
        }
    }
    val topBarVisibleState = remember(topBarEffectiveTargetProgressState, topBarVisibilityProgressState) {
        derivedStateOf {
            topBarEffectiveTargetProgressState.value > 0.001f ||
                topBarVisibilityProgressState.value > 0.001f
        }
    }
    val programmaticTabPosition by animateFloatAsState(
        targetValue = runtime.programmaticTabTargetPosition ?: pagerPage.toFloat(),
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "browseProgrammaticTabPosition",
    )
    val tabPosition = resolveBrowseTabPosition(
        active = active,
        useBrowsePager = usePager,
        pagerPage = pagerPage,
        pagerPosition = pagerPositionState.value,
        programmaticTabTargetPosition = runtime.programmaticTabTargetPosition,
        programmaticTabPosition = programmaticTabPosition,
        pagerDriven = runtime.pagerState.isScrollInProgress ||
            runtime.programmaticScrollTarget != null ||
            runtime.transitionFocusSourcePage != null,
        effectiveSectionVisible = effectiveSection in pagerSections,
        programmaticScrollPending = runtime.programmaticScrollTarget != null,
    )
    val pagerAwayFromTarget = usePager &&
        (runtime.pagerState.currentPage != pagerPage || abs(runtime.pagerState.currentPageOffsetFraction) > 0.001f)
    return BrowsePagerPresentation(
        pagerPosition = pagerPositionState.value,
        tabPosition = tabPosition,
        topBarVisible = topBarVisibleState.value,
        topBarVisibilityProgress = topBarVisibilityProgressState,
        pagerSettledAtTarget = effectiveSection in pagerSections &&
            (!usePager || (!runtime.pagerState.isScrollInProgress && !pagerAwayFromTarget)),
    )
}

// BrowsePagerRuntime
internal class BrowsePagerRuntime(
    val pagerState: PagerState,
    val pageStateHolder: SaveableStateHolder,
    initialSection: BrowseSection,
) {
    var suppressedContentFocusSection by mutableStateOf<BrowseSection?>(null)
    var transitionFocusSourcePage by mutableStateOf<Int?>(null)
    var requestContentFocusOnFinish by mutableStateOf(false)
    var pageFocusRequestNonce by mutableLongStateOf(0L)
    var pageFocusRequestSection by mutableStateOf(initialSection)
    var keepTabsFocusedForSectionChange by mutableStateOf(false)
    var pendingTabsFocusSection by mutableStateOf<BrowseSection?>(null)
    var programmaticScrollTarget by mutableStateOf<Int?>(null)
    var topBarProgrammaticTargetProgress by mutableStateOf<Float?>(null)
    var programmaticTabTargetPosition by mutableStateOf<Float?>(null)
    var wasAligned by mutableStateOf(false)

    val sectionTabsFocusEnabled: Boolean
        get() = transitionFocusSourcePage == null

    fun alignment(): PagerAlignmentState {
        return PagerAlignmentState(
            isScrollInProgress = pagerState.isScrollInProgress,
            settledPage = pagerState.settledPage,
            currentPage = pagerState.currentPage,
            offset = pagerState.currentPageOffsetFraction,
        )
    }

    fun isSettledAt(page: Int): Boolean = alignment().isSettledAt(page)

    fun releaseFocusTransition() {
        transitionFocusSourcePage = null
        requestContentFocusOnFinish = false
    }

    fun finishProgrammaticTarget(targetPage: Int, currentTargetPage: Int) {
        if (programmaticScrollTarget != targetPage) return
        if (currentTargetPage != targetPage) return
        if (!isSettledAt(targetPage)) return
        val shouldRequestContentFocus = transitionFocusSourcePage != null
        programmaticScrollTarget = null
        transitionFocusSourcePage = null
        val requestContentFocus = requestContentFocusOnFinish && shouldRequestContentFocus
        requestContentFocusOnFinish = false
        topBarProgrammaticTargetProgress = null
        programmaticTabTargetPosition = null
        if (requestContentFocus) {
            pageFocusRequestNonce += 1L
        }
    }
}

@Composable
internal fun rememberBrowsePagerRuntime(
    initialPage: Int,
    initialSection: BrowseSection,
    pageCount: () -> Int,
): BrowsePagerRuntime {
    val pageStateHolder = rememberSaveableStateHolder()
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = pageCount)
    return remember(pagerState, pageStateHolder) {
        BrowsePagerRuntime(pagerState, pageStateHolder, initialSection)
    }
}

// BrowsePagerSectionFocusEffect
@Composable
internal fun BrowsePagerSectionFocusEffect(
    effectiveSection: BrowseSection,
    usePager: Boolean,
    dpadFocusEnabled: Boolean,
    runtime: BrowsePagerRuntime,
    onRequestSectionTabsFocus: (BrowseSection, Boolean) -> Boolean,
) {
    LaunchedEffect(effectiveSection) {
        if (runtime.pageFocusRequestSection == effectiveSection) return@LaunchedEffect
        runtime.pageFocusRequestSection = effectiveSection
        if (runtime.keepTabsFocusedForSectionChange) {
            retainSectionTabFocus(
                effectiveSection = effectiveSection,
                dpadFocusEnabled = dpadFocusEnabled,
                runtime = runtime,
                onRequestSectionTabsFocus = onRequestSectionTabsFocus,
            )
        } else {
            runtime.pendingTabsFocusSection = null
            if (canRequestPageFocus(usePager, dpadFocusEnabled, runtime)) {
                runtime.pageFocusRequestNonce += 1L
            }
        }
    }
}

private suspend fun retainSectionTabFocus(
    effectiveSection: BrowseSection,
    dpadFocusEnabled: Boolean,
    runtime: BrowsePagerRuntime,
    onRequestSectionTabsFocus: (BrowseSection, Boolean) -> Boolean,
) {
    val targetFocusSection = runtime.pendingTabsFocusSection ?: effectiveSection
    runtime.pendingTabsFocusSection = null
    runtime.keepTabsFocusedForSectionChange = false
    if (dpadFocusEnabled) {
        withFrameNanos { }
        onRequestSectionTabsFocus(targetFocusSection, false)
    }
}

private fun canRequestPageFocus(
    usePager: Boolean,
    dpadFocusEnabled: Boolean,
    runtime: BrowsePagerRuntime,
): Boolean {
    return dpadFocusEnabled &&
        (!usePager ||
            (runtime.programmaticScrollTarget == null && runtime.transitionFocusSourcePage == null))
}

// BrowsePagerSettlementEffect
@Composable
internal fun BrowsePagerSettlementEffect(
    active: Boolean,
    effectiveSection: BrowseSection,
    pagerSections: List<BrowseSection>,
    pagerPage: Int,
    usePager: Boolean,
    runtime: BrowsePagerRuntime,
    onBrowseSectionChange: (BrowseSection) -> Unit,
) {
    val latestOnBrowseSectionChange by rememberUpdatedState(onBrowseSectionChange)
    val latestEffectiveSection by rememberUpdatedState(effectiveSection)
    LaunchedEffect(active, runtime.pagerState, pagerPage, effectiveSection, pagerSections) {
        if (!usePager) return@LaunchedEffect
        snapshotFlow { runtime.alignment() }
            .distinctUntilChanged()
            .collect { alignment ->
                settleBrowsePagerAlignment(
                    active = active,
                    alignment = alignment,
                    effectiveSection = latestEffectiveSection,
                    pagerSections = pagerSections,
                    pagerPage = pagerPage,
                    runtime = runtime,
                    onBrowseSectionChange = latestOnBrowseSectionChange,
                )
            }
    }
}

private suspend fun settleBrowsePagerAlignment(
    active: Boolean,
    alignment: PagerAlignmentState,
    effectiveSection: BrowseSection,
    pagerSections: List<BrowseSection>,
    pagerPage: Int,
    runtime: BrowsePagerRuntime,
    onBrowseSectionChange: (BrowseSection) -> Unit,
) {
    if (!active) {
        restoreInactivePagerTarget(alignment, pagerPage, runtime)
        return
    }
    if (consumePendingPagerTarget(runtime, pagerPage)) return
    val settledSection = settledBrowseSection(alignment, effectiveSection, pagerSections) ?: return
    if (alignment.currentPage != alignment.settledPage || abs(alignment.offset) > PagerEffectAlignmentTolerance) {
        runtime.pagerState.scrollToPage(alignment.settledPage)
    }
    if (settledSection != effectiveSection) onBrowseSectionChange(settledSection)
}

private suspend fun restoreInactivePagerTarget(
    alignment: PagerAlignmentState,
    pagerPage: Int,
    runtime: BrowsePagerRuntime,
) {
    if (alignment.currentPage != pagerPage || abs(alignment.offset) > PagerEffectAlignmentTolerance) {
        runtime.pagerState.scrollToPage(pagerPage)
    }
}

private fun consumePendingPagerTarget(
    runtime: BrowsePagerRuntime,
    pagerPage: Int,
): Boolean {
    val programmaticTarget = runtime.programmaticScrollTarget
    if (programmaticTarget == null && runtime.transitionFocusSourcePage == null) return false
    if (programmaticTarget != null && runtime.isSettledAt(programmaticTarget)) {
        runtime.finishProgrammaticTarget(programmaticTarget, pagerPage)
    }
    return true
}

private fun settledBrowseSection(
    alignment: PagerAlignmentState,
    effectiveSection: BrowseSection,
    pagerSections: List<BrowseSection>,
): BrowseSection? {
    if (alignment.isScrollInProgress || effectiveSection !in pagerSections) return null
    return pagerSections.getOrNull(alignment.settledPage)
}

private const val PagerEffectAlignmentTolerance = 0.001f

// BrowsePagerTargetAlignmentEffect
@Composable
internal fun BrowsePagerTargetAlignmentEffect(
    active: Boolean,
    effectiveSection: BrowseSection,
    pagerSections: List<BrowseSection>,
    pagerPage: Int,
    usePager: Boolean,
    runtime: BrowsePagerRuntime,
    topBarProgressFor: (BrowseSection) -> Float,
) {
    LaunchedEffect(active, pagerPage, effectiveSection, pagerSections) {
        alignBrowsePagerTarget(
            active = active,
            effectiveSection = effectiveSection,
            pagerSections = pagerSections,
            pagerPage = pagerPage,
            usePager = usePager,
            runtime = runtime,
            topBarProgressFor = topBarProgressFor,
        )
    }
}

private suspend fun alignBrowsePagerTarget(
    active: Boolean,
    effectiveSection: BrowseSection,
    pagerSections: List<BrowseSection>,
    pagerPage: Int,
    usePager: Boolean,
    runtime: BrowsePagerRuntime,
    topBarProgressFor: (BrowseSection) -> Float,
) {
    if (!usePager) {
        runtime.wasAligned = true
        return
    }
    if (targetNeedsAlignment(effectiveSection, pagerSections, pagerPage, runtime)) {
        alignTargetPage(active, effectiveSection, pagerPage, runtime, topBarProgressFor)
    } else if (runtime.programmaticScrollTarget == pagerPage) {
        runtime.finishProgrammaticTarget(pagerPage, pagerPage)
    }
    runtime.wasAligned = true
}

private fun targetNeedsAlignment(
    effectiveSection: BrowseSection,
    pagerSections: List<BrowseSection>,
    pagerPage: Int,
    runtime: BrowsePagerRuntime,
): Boolean {
    return effectiveSection in pagerSections &&
        (runtime.pagerState.currentPage != pagerPage || runtime.pagerState.currentPageOffsetFraction != 0f)
}

private suspend fun alignTargetPage(
    active: Boolean,
    effectiveSection: BrowseSection,
    pagerPage: Int,
    runtime: BrowsePagerRuntime,
    topBarProgressFor: (BrowseSection) -> Float,
) {
    if (active && runtime.wasAligned) {
        if (runtime.programmaticScrollTarget == null) {
            runtime.programmaticScrollTarget = pagerPage
            runtime.topBarProgrammaticTargetProgress = topBarProgressFor(effectiveSection)
        }
        try {
            runtime.pagerState.animateScrollToPage(pagerPage)
        } finally {
            runtime.finishProgrammaticTarget(pagerPage, pagerPage)
        }
    } else {
        runtime.pagerState.scrollToPage(pagerPage)
        runtime.finishProgrammaticTarget(pagerPage, pagerPage)
    }
}
