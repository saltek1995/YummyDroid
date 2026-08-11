package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged
import me.yummydroid.app.BrowseSection

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
