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

internal data class BrowseSectionFocusPlan(
    val keepTabsFocused: Boolean,
    val requestContentFocus: Boolean,
)

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
    focusableContentSections: Set<BrowseSection>,
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
    BrowsePagerBindingEffects(
        active = active,
        effectiveSection = effectiveSection,
        pagerSections = pagerSections,
        pagerPage = pagerPage,
        usePager = usePager,
        dpadFocusEnabled = dpadFocusEnabled,
        runtime = runtime,
        pagerPosition = presentation.pagerPosition,
        topBarProgressFor = topBarProgressFor,
        onBrowseSectionChange = onBrowseSectionChange,
        onHomeBrowseBackStateChange = onHomeBrowseBackStateChange,
        onRequestSectionTabsFocus = onRequestSectionTabsFocus,
    )
    val selectSection = rememberBrowseSectionSelector(
        runtime = runtime,
        effectiveSection = effectiveSection,
        pagerSections = pagerSections,
        pagerPage = pagerPage,
        usePager = usePager,
        dpadFocusEnabled = dpadFocusEnabled,
        focusableContentSections = focusableContentSections,
        topBarProgressFor = topBarProgressFor,
        onRequestSectionTabsFocus = onRequestSectionTabsFocus,
        onBrowseSectionChange = onBrowseSectionChange,
    )
    return createBrowsePagerBinding(
        runtime = runtime,
        pagerPage = pagerPage,
        usePager = usePager,
        presentation = presentation,
        focusRequestNonce = dpadLayerFocusRequestNonce + runtime.pageFocusRequestNonce,
        pagerSections = pagerSections,
        selectSection = selectSection,
    )
}

@Composable
private fun BrowsePagerBindingEffects(
    active: Boolean,
    effectiveSection: BrowseSection,
    pagerSections: List<BrowseSection>,
    pagerPage: Int,
    usePager: Boolean,
    dpadFocusEnabled: Boolean,
    runtime: BrowsePagerRuntime,
    pagerPosition: Float,
    topBarProgressFor: (BrowseSection) -> Float,
    onBrowseSectionChange: (BrowseSection) -> Unit,
    onHomeBrowseBackStateChange: (HomeBrowseBackState) -> Unit,
    onRequestSectionTabsFocus: (BrowseSection, Boolean) -> Boolean,
) {
    BrowsePagerControlledTransitionEffect(
        active = active,
        effectiveSection = effectiveSection,
        pagerSections = pagerSections,
        pagerPage = pagerPage,
        usePager = usePager,
        dpadFocusEnabled = dpadFocusEnabled,
        runtime = runtime,
        topBarProgressFor = topBarProgressFor,
        onRequestSectionTabsFocus = onRequestSectionTabsFocus,
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
    BrowseHomeBackStateEffect(
        active = active,
        effectiveSection = effectiveSection,
        pagerSections = pagerSections,
        pagerPage = pagerPage,
        usePager = usePager,
        runtime = runtime,
        pagerPosition = pagerPosition,
        onHomeBrowseBackStateChange = onHomeBrowseBackStateChange,
    )
}

@Composable
private fun rememberBrowseSectionSelector(
    runtime: BrowsePagerRuntime,
    effectiveSection: BrowseSection,
    pagerSections: List<BrowseSection>,
    pagerPage: Int,
    usePager: Boolean,
    dpadFocusEnabled: Boolean,
    focusableContentSections: Set<BrowseSection>,
    topBarProgressFor: (BrowseSection) -> Float,
    onRequestSectionTabsFocus: (BrowseSection, Boolean) -> Boolean,
    onBrowseSectionChange: (BrowseSection) -> Unit,
): (BrowseSection, Boolean) -> Boolean {
    val latestOnBrowseSectionChange by rememberUpdatedState(onBrowseSectionChange)
    return { section, keepTabsFocused ->
        runtime.selectSection(
            section = section,
            effectiveSection = effectiveSection,
            pagerSections = pagerSections,
            pagerPage = pagerPage,
            usePager = usePager,
            dpadFocusEnabled = dpadFocusEnabled,
            keepTabsFocused = keepTabsFocused,
            targetContentFocusable = section in focusableContentSections,
            topBarProgressFor = topBarProgressFor,
            onRequestSectionTabsFocus = onRequestSectionTabsFocus,
            onBrowseSectionChange = latestOnBrowseSectionChange,
        )
    }
}

private fun createBrowsePagerBinding(
    runtime: BrowsePagerRuntime,
    pagerPage: Int,
    usePager: Boolean,
    presentation: BrowsePagerPresentation,
    focusRequestNonce: Long,
    pagerSections: List<BrowseSection>,
    selectSection: (BrowseSection, Boolean) -> Boolean,
): BrowsePagerBinding {
    return BrowsePagerBinding(
        runtime = runtime,
        pagerPage = pagerPage,
        usePager = usePager,
        tabPosition = presentation.tabPosition,
        topBarVisible = presentation.topBarVisible,
        topBarVisibilityProgress = presentation.topBarVisibilityProgress,
        pagerSettledAtTarget = presentation.pagerSettledAtTarget,
        focusRequestNonce = focusRequestNonce,
        onSectionSelected = { section -> selectSection(section, true) },
        onHorizontalExit = { page, direction ->
            val targetSection = resolveHorizontalBrowseSection(pagerSections, page, direction)
            targetSection != null && selectSection(targetSection, false)
        },
    )
}

internal fun resolveHorizontalBrowseSection(
    pagerSections: List<BrowseSection>,
    page: Int,
    direction: VisualGridDirection,
): BrowseSection? {
    val pageDelta = when (direction) {
        VisualGridDirection.Left -> -1
        VisualGridDirection.Right -> 1
        VisualGridDirection.Up,
        VisualGridDirection.Down -> return null
    }
    return pagerSections.getOrNull(page + pageDelta)
}

private fun BrowsePagerRuntime.selectSection(
    section: BrowseSection,
    effectiveSection: BrowseSection,
    pagerSections: List<BrowseSection>,
    pagerPage: Int,
    usePager: Boolean,
    dpadFocusEnabled: Boolean,
    keepTabsFocused: Boolean,
    targetContentFocusable: Boolean,
    topBarProgressFor: (BrowseSection) -> Float,
    onRequestSectionTabsFocus: (BrowseSection, Boolean) -> Boolean,
    onBrowseSectionChange: (BrowseSection) -> Unit,
): Boolean {
    if (section !in pagerSections) return false
    val focusPlan = resolveBrowseSectionFocusPlan(
        keepTabsFocused = keepTabsFocused,
        dpadFocusEnabled = dpadFocusEnabled,
        targetContentFocusable = targetContentFocusable,
    )
    if (section == effectiveSection) {
        if (focusPlan.keepTabsFocused) onRequestSectionTabsFocus(section, false)
        return true
    }
    applySectionFocusPlan(section, focusPlan, onRequestSectionTabsFocus)
    if (usePager) {
        prepareSectionTransition(
            targetPage = pagerSections.indexOf(section),
            sourcePage = pagerPage,
            topBarTargetProgress = topBarProgressFor(section),
            requestContentFocus = focusPlan.requestContentFocus,
        )
    }
    onBrowseSectionChange(section)
    return true
}

internal fun resolveBrowseSectionFocusPlan(
    keepTabsFocused: Boolean,
    dpadFocusEnabled: Boolean,
    targetContentFocusable: Boolean = true,
): BrowseSectionFocusPlan {
    val keepSafeFocusOnTabs = dpadFocusEnabled && (keepTabsFocused || !targetContentFocusable)
    return BrowseSectionFocusPlan(
        keepTabsFocused = keepSafeFocusOnTabs,
        requestContentFocus = dpadFocusEnabled && !keepSafeFocusOnTabs,
    )
}

private fun BrowsePagerRuntime.applySectionFocusPlan(
    section: BrowseSection,
    plan: BrowseSectionFocusPlan,
    onRequestSectionTabsFocus: (BrowseSection, Boolean) -> Boolean,
) {
    keepTabsFocusedForSectionChange = plan.keepTabsFocused
    pendingTabsFocusSection = section.takeIf { plan.keepTabsFocused }
    suppressedContentFocusSection = section.takeIf { plan.keepTabsFocused }
    if (plan.keepTabsFocused) onRequestSectionTabsFocus(section, false)
}

private fun BrowsePagerRuntime.prepareSectionTransition(
    targetPage: Int,
    sourcePage: Int,
    topBarTargetProgress: Float,
    requestContentFocus: Boolean,
) {
    programmaticScrollTarget = targetPage
    programmaticTabTargetPosition = targetPage.toFloat()
    topBarProgrammaticTargetProgress = topBarTargetProgress
    transitionFocusSourcePage = sourcePage.takeIf { requestContentFocus }
    requestContentFocusOnFinish = requestContentFocus
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
    val pagerPositionState = rememberBrowsePagerPosition(runtime)
    val topBarTargetProgressState = rememberBrowseTopBarTargetProgress(
        effectiveSection,
        browseCoordinator,
        topBarCollapseDistancePx,
        isWide,
        forcedOfflineMode,
        topBarProgressFor,
    )
    val topBarEffectiveTargetProgressState = rememberBrowseTopBarEffectiveTargetProgress(
        runtime,
        topBarTargetProgressState,
    )
    val pagerDriven = runtime.isPagerDriven(usePager)
    val topBarVisibilityProgressState = rememberBrowseTopBarVisibilityProgress(
        pagerSections = pagerSections,
        runtime = runtime,
        browseCoordinator = browseCoordinator,
        effectiveTargetProgress = topBarEffectiveTargetProgressState,
        topBarCollapseDistancePx = topBarCollapseDistancePx,
        isWide = isWide,
        forcedOfflineMode = forcedOfflineMode,
        pagerDriven = pagerDriven,
        pagerPosition = pagerPositionState,
        topBarProgressFor = topBarProgressFor,
    )
    val topBarVisibleState = rememberBrowseTopBarVisible(
        topBarEffectiveTargetProgressState,
        topBarVisibilityProgressState,
    )
    val programmaticTabPosition = rememberBrowseProgrammaticTabPosition(runtime, pagerPage)
    return BrowsePagerPresentation(
        pagerPosition = pagerPositionState.value,
        tabPosition = resolveBrowseTabPosition(
            active = active,
            useBrowsePager = usePager,
            pagerPage = pagerPage,
            pagerPosition = pagerPositionState.value,
            programmaticTabTargetPosition = runtime.programmaticTabTargetPosition,
            programmaticTabPosition = programmaticTabPosition,
            pagerDriven = runtime.hasPagerTransition(),
            effectiveSectionVisible = effectiveSection in pagerSections,
            programmaticScrollPending = runtime.programmaticScrollTarget != null,
        ),
        topBarVisible = topBarVisibleState.value,
        topBarVisibilityProgress = topBarVisibilityProgressState,
        pagerSettledAtTarget = isBrowsePagerSettledAtTarget(
            effectiveSection = effectiveSection,
            pagerSections = pagerSections,
            pagerPage = pagerPage,
            usePager = usePager,
            alignment = runtime.alignment(),
        ),
    )
}

@Composable
private fun rememberBrowsePagerPosition(runtime: BrowsePagerRuntime): State<Float> {
    return remember(runtime.pagerState) {
        derivedStateOf { runtime.pagerState.currentPage + runtime.pagerState.currentPageOffsetFraction }
    }
}

@Composable
private fun rememberBrowseTopBarTargetProgress(
    effectiveSection: BrowseSection,
    browseCoordinator: BrowseRootUiCoordinator,
    topBarCollapseDistancePx: Float,
    isWide: Boolean,
    forcedOfflineMode: Boolean,
    topBarProgressFor: (BrowseSection) -> Float,
): State<Float> {
    return remember(
        effectiveSection,
        browseCoordinator,
        topBarCollapseDistancePx,
        isWide,
        forcedOfflineMode,
    ) {
        derivedStateOf { topBarProgressFor(effectiveSection) }
    }
}

@Composable
private fun rememberBrowseTopBarEffectiveTargetProgress(
    runtime: BrowsePagerRuntime,
    targetProgress: State<Float>,
): State<Float> {
    return remember(
        runtime.topBarProgrammaticTargetProgress,
        targetProgress,
    ) {
        derivedStateOf { runtime.topBarProgrammaticTargetProgress ?: targetProgress.value }
    }
}

@Composable
private fun rememberBrowseTopBarVisibilityProgress(
    pagerSections: List<BrowseSection>,
    runtime: BrowsePagerRuntime,
    browseCoordinator: BrowseRootUiCoordinator,
    effectiveTargetProgress: State<Float>,
    topBarCollapseDistancePx: Float,
    isWide: Boolean,
    forcedOfflineMode: Boolean,
    pagerDriven: Boolean,
    pagerPosition: State<Float>,
    topBarProgressFor: (BrowseSection) -> Float,
): State<Float> {
    return remember(
        pagerSections,
        runtime.pagerState,
        browseCoordinator,
        effectiveTargetProgress,
        topBarCollapseDistancePx,
        isWide,
        forcedOfflineMode,
        pagerDriven,
    ) {
        derivedStateOf {
            resolveBrowseTopBarProgress(
                pagerDriven = pagerDriven,
                pagerSections = pagerSections,
                pagerPosition = pagerPosition.value,
                effectiveTargetProgress = effectiveTargetProgress.value,
                topBarProgressFor = topBarProgressFor,
            )
        }
    }
}

internal fun resolveBrowseTopBarProgress(
    pagerDriven: Boolean,
    pagerSections: List<BrowseSection>,
    pagerPosition: Float,
    effectiveTargetProgress: Float,
    topBarProgressFor: (BrowseSection) -> Float,
): Float {
    if (!pagerDriven || pagerSections.isEmpty()) return effectiveTargetProgress
    val maxPage = pagerSections.lastIndex
    val position = pagerPosition.coerceIn(0f, maxPage.toFloat())
    val startPage = position.toInt().coerceIn(0, maxPage)
    val endPage = (startPage + 1).coerceAtMost(maxPage)
    val fraction = (position - startPage).coerceIn(0f, 1f)
    val startProgress = topBarProgressFor(pagerSections[startPage])
    val endProgress = topBarProgressFor(pagerSections[endPage])
    return startProgress + (endProgress - startProgress) * fraction
}

@Composable
private fun rememberBrowseTopBarVisible(
    effectiveTargetProgress: State<Float>,
    visibilityProgress: State<Float>,
): State<Boolean> {
    return remember(effectiveTargetProgress, visibilityProgress) {
        derivedStateOf {
            effectiveTargetProgress.value > 0.001f || visibilityProgress.value > 0.001f
        }
    }
}

@Composable
private fun rememberBrowseProgrammaticTabPosition(
    runtime: BrowsePagerRuntime,
    pagerPage: Int,
): Float {
    val position by animateFloatAsState(
        targetValue = runtime.programmaticTabTargetPosition ?: pagerPage.toFloat(),
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "browseProgrammaticTabPosition",
    )
    return position
}

internal fun isBrowsePagerSettledAtTarget(
    effectiveSection: BrowseSection,
    pagerSections: List<BrowseSection>,
    pagerPage: Int,
    usePager: Boolean,
    alignment: PagerAlignmentState,
): Boolean {
    if (effectiveSection !in pagerSections) return false
    return !usePager ||
        (!alignment.isScrollInProgress &&
            alignment.currentPage == pagerPage &&
            abs(alignment.offset) <= PagerEffectAlignmentTolerance)
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

    fun hasPagerTransition(): Boolean {
        return pagerState.isScrollInProgress ||
            programmaticScrollTarget != null ||
            transitionFocusSourcePage != null
    }

    fun isPagerDriven(usePager: Boolean): Boolean = usePager && hasPagerTransition()

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

// BrowsePagerControlledTransition
@Composable
internal fun BrowsePagerControlledTransitionEffect(
    active: Boolean,
    effectiveSection: BrowseSection,
    pagerSections: List<BrowseSection>,
    pagerPage: Int,
    usePager: Boolean,
    dpadFocusEnabled: Boolean,
    runtime: BrowsePagerRuntime,
    topBarProgressFor: (BrowseSection) -> Float,
    onRequestSectionTabsFocus: (BrowseSection, Boolean) -> Boolean,
) {
    val sectionChanged = runtime.pageFocusRequestSection != effectiveSection
    val alignmentRequired = usePager && (
        targetNeedsAlignment(effectiveSection, pagerSections, pagerPage, runtime) ||
            runtime.programmaticScrollTarget == pagerPage
        )
    UiControlEffect(
        active,
        effectiveSection,
        pagerSections,
        pagerPage,
        usePager,
        dpadFocusEnabled,
        operation = UiControlOperation.PageTransitionLatest,
        enabled = sectionChanged || alignmentRequired || !runtime.wasAligned,
    ) {
        val retainTabs = sectionChanged && runtime.keepTabsFocusedForSectionChange
        if (sectionChanged) {
            runtime.pageFocusRequestSection = effectiveSection
            if (!retainTabs) {
                runtime.pendingTabsFocusSection = null
                if (canRequestPageFocus(usePager, dpadFocusEnabled, runtime)) {
                    runtime.pageFocusRequestNonce += 1L
                }
            }
        }
        if (retainTabs) {
            retainSectionTabFocus(
                effectiveSection = effectiveSection,
                dpadFocusEnabled = dpadFocusEnabled,
                runtime = runtime,
                onRequestSectionTabsFocus = onRequestSectionTabsFocus,
            )
        }
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
