package me.yummydroid.app.ui

import kotlin.math.abs

internal data class FocusFirstRequest(
    val persistentNonce: Long = 0L,
    val transientNonce: Long = 0L,
)

internal data class PagerAlignmentState(
    val isScrollInProgress: Boolean,
    val settledPage: Int,
    val currentPage: Int,
    val offset: Float,
)

internal fun PagerAlignmentState.isSettledAt(page: Int): Boolean {
    return !isScrollInProgress &&
        settledPage == page &&
        currentPage == page &&
        abs(offset) <= BrowsePagerAlignmentTolerance
}

internal fun browsePageCanReceiveFocus(
    active: Boolean,
    dpadFocusEnabled: Boolean,
    contentFocusSuppressed: Boolean,
    page: Int,
    targetPage: Int,
    pagerSettledAtTarget: Boolean,
    programmaticScrollTarget: Int?,
    transitionFocusSourcePage: Int?,
): Boolean {
    if (focusIsDisabled(active, dpadFocusEnabled, contentFocusSuppressed)) return false
    return page.isSettledFocusTarget(targetPage, pagerSettledAtTarget) ||
        page.isProgrammaticFocusTarget(targetPage, programmaticScrollTarget) ||
        page.isTransitionFocusSource(
            pagerSettledAtTarget = pagerSettledAtTarget,
            programmaticScrollTarget = programmaticScrollTarget,
            transitionFocusSourcePage = transitionFocusSourcePage,
        )
}

private fun focusIsDisabled(
    active: Boolean,
    dpadFocusEnabled: Boolean,
    contentFocusSuppressed: Boolean,
): Boolean = !active || !dpadFocusEnabled || contentFocusSuppressed

private fun Int.isSettledFocusTarget(
    targetPage: Int,
    pagerSettledAtTarget: Boolean,
): Boolean = this == targetPage && pagerSettledAtTarget

private fun Int.isProgrammaticFocusTarget(
    targetPage: Int,
    programmaticScrollTarget: Int?,
): Boolean = programmaticScrollTarget == this && this == targetPage

private fun Int.isTransitionFocusSource(
    pagerSettledAtTarget: Boolean,
    programmaticScrollTarget: Int?,
    transitionFocusSourcePage: Int?,
): Boolean {
    return transitionFocusSourcePage == this &&
        programmaticScrollTarget != null &&
        !pagerSettledAtTarget
}

private const val BrowsePagerAlignmentTolerance = 0.001f
