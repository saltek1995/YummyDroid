package me.yummydroid.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlin.math.abs
import me.yummydroid.app.BrowseSection

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
