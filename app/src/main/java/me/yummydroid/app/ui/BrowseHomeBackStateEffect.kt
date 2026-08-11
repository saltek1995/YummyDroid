package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlin.math.abs
import me.yummydroid.app.BrowseSection

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
