package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import me.yummydroid.app.BrowseSection

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
