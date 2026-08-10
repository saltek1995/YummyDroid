package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged
import me.yummydroid.app.BrowseSection

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
            val targetFocusSection = runtime.pendingTabsFocusSection ?: effectiveSection
            runtime.pendingTabsFocusSection = null
            runtime.keepTabsFocusedForSectionChange = false
            if (dpadFocusEnabled) {
                withFrameNanos { }
                onRequestSectionTabsFocus(targetFocusSection, false)
            }
        } else {
            runtime.pendingTabsFocusSection = null
            if (
                dpadFocusEnabled &&
                (!usePager ||
                    (runtime.programmaticScrollTarget == null && runtime.transitionFocusSourcePage == null))
            ) {
                runtime.pageFocusRequestNonce += 1L
            }
        }
    }
}

@Composable
internal fun BrowsePagerAlignmentEffects(
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
private fun BrowsePagerTargetAlignmentEffect(
    active: Boolean,
    effectiveSection: BrowseSection,
    pagerSections: List<BrowseSection>,
    pagerPage: Int,
    usePager: Boolean,
    runtime: BrowsePagerRuntime,
    topBarProgressFor: (BrowseSection) -> Float,
) {
    LaunchedEffect(active, pagerPage, effectiveSection, pagerSections) {
        if (!usePager) {
            runtime.wasAligned = true
            return@LaunchedEffect
        }
        if (
            effectiveSection in pagerSections &&
            (runtime.pagerState.currentPage != pagerPage || runtime.pagerState.currentPageOffsetFraction != 0f)
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
        } else if (runtime.programmaticScrollTarget == pagerPage) {
            runtime.finishProgrammaticTarget(pagerPage, pagerPage)
        }
        runtime.wasAligned = true
    }
}

@Composable
private fun BrowsePagerSettlementEffect(
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
                if (!active) {
                    if (alignment.currentPage != pagerPage || abs(alignment.offset) > 0.001f) {
                        runtime.pagerState.scrollToPage(pagerPage)
                    }
                    return@collect
                }
                val programmaticTarget = runtime.programmaticScrollTarget
                if (programmaticTarget != null || runtime.transitionFocusSourcePage != null) {
                    if (programmaticTarget != null && runtime.isSettledAt(programmaticTarget)) {
                        runtime.finishProgrammaticTarget(programmaticTarget, pagerPage)
                    }
                    return@collect
                }
                if (alignment.isScrollInProgress) return@collect
                if (latestEffectiveSection !in pagerSections) return@collect
                val settledSection = pagerSections.getOrNull(alignment.settledPage) ?: return@collect
                if (alignment.currentPage != alignment.settledPage || abs(alignment.offset) > 0.001f) {
                    runtime.pagerState.scrollToPage(alignment.settledPage)
                }
                if (settledSection != latestEffectiveSection) {
                    latestOnBrowseSectionChange(settledSection)
                }
            }
    }
}

@Composable
internal fun BrowseHomeBackStateEffect(
    active: Boolean,
    effectiveSection: BrowseSection,
    pagerSections: List<BrowseSection>,
    pagerPage: Int,
    usePager: Boolean,
    runtime: BrowsePagerRuntime,
    pagerPosition: Float,
    onHomeBrowseBackStateChange: (HomeBrowseBackState) -> Unit,
) {
    val pagerAwayFromTarget = usePager &&
        (runtime.pagerState.currentPage != pagerPage || abs(runtime.pagerState.currentPageOffsetFraction) > 0.001f)
    val backState = resolveHomeBrowseBackState(
        useBrowsePager = usePager,
        effectiveSection = effectiveSection,
        pagerSections = pagerSections,
        pagerPosition = pagerPosition,
        pagerScrollInProgress = runtime.pagerState.isScrollInProgress,
        pagerAwayFromTarget = pagerAwayFromTarget,
    )
    LaunchedEffect(active, backState) {
        if (active) onHomeBrowseBackStateChange(backState)
    }
}
