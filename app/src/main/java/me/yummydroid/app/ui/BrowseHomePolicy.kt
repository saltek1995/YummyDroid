package me.yummydroid.app.ui

import kotlin.math.abs
import kotlin.math.roundToInt
import me.yummydroid.app.BrowseSection

internal data class FocusFirstRequest(
    val persistentNonce: Long = 0L,
    val transientNonce: Long = 0L,
)

internal data class BrowseChromePolicy(
    val pinTopChrome: Boolean,
    val showTvSectionTabs: Boolean,
    val showBottomChrome: Boolean,
)

internal fun resolveBrowseChromePolicy(
    isWide: Boolean,
    forcedOfflineMode: Boolean,
): BrowseChromePolicy {
    return BrowseChromePolicy(
        pinTopChrome = isWide,
        showTvSectionTabs = isWide && !forcedOfflineMode,
        showBottomChrome = !isWide,
    )
}

internal fun resolveBrowsePagerSections(
    isAuthorized: Boolean,
    forcedOfflineMode: Boolean,
): List<BrowseSection> {
    if (forcedOfflineMode) return listOf(BrowseSection.Downloads)
    return if (isAuthorized) {
        listOf(BrowseSection.Catalog, BrowseSection.History, BrowseSection.Schedule)
    } else {
        listOf(BrowseSection.Catalog, BrowseSection.Schedule)
    }
}

internal fun resolveEffectiveBrowseSection(
    requestedSection: BrowseSection,
    isAuthorized: Boolean,
    forcedOfflineMode: Boolean,
): BrowseSection {
    return when {
        forcedOfflineMode -> BrowseSection.Downloads
        requestedSection == BrowseSection.History && !isAuthorized -> BrowseSection.Catalog
        else -> requestedSection
    }
}

internal fun resolveBrowseSectionCorrection(
    requestedSection: BrowseSection,
    isAuthorized: Boolean,
    forcedOfflineMode: Boolean,
): BrowseSection? {
    return resolveEffectiveBrowseSection(requestedSection, isAuthorized, forcedOfflineMode)
        .takeUnless { section -> section == requestedSection }
}

internal fun resolveBrowseTabPosition(
    active: Boolean,
    useBrowsePager: Boolean,
    pagerPage: Int,
    pagerPosition: Float,
    programmaticTabTargetPosition: Float?,
    programmaticTabPosition: Float,
    pagerDriven: Boolean,
    effectiveSectionVisible: Boolean,
    programmaticScrollPending: Boolean,
): Float? {
    return when {
        !active -> pagerPage.toFloat()
        useBrowsePager && programmaticTabTargetPosition != null -> programmaticTabPosition
        useBrowsePager && pagerDriven -> pagerPosition
        effectiveSectionVisible || programmaticScrollPending -> pagerPage.toFloat()
        else -> null
    }
}

internal fun resolveHomeBrowseBackState(
    useBrowsePager: Boolean,
    effectiveSection: BrowseSection,
    pagerSections: List<BrowseSection>,
    pagerPosition: Float,
    pagerScrollInProgress: Boolean,
    pagerAwayFromTarget: Boolean,
): HomeBrowseBackState {
    if (!useBrowsePager || effectiveSection == BrowseSection.Downloads || pagerSections.isEmpty()) {
        return HomeBrowseBackState(effectiveSection, settledAtStateSection = true)
    }
    val visiblePage = pagerPosition.roundToInt().coerceIn(0, pagerSections.lastIndex)
    return HomeBrowseBackState(
        visualSection = pagerSections[visiblePage],
        settledAtStateSection = !pagerScrollInProgress && !pagerAwayFromTarget,
    )
}

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
    if (!active || !dpadFocusEnabled || contentFocusSuppressed) return false
    val settledTargetCanFocus = page == targetPage && pagerSettledAtTarget
    val programmaticTargetCanFocus = programmaticScrollTarget == page && page == targetPage
    val transitionSourceCanKeepFocus = transitionFocusSourcePage == page &&
        programmaticScrollTarget != null &&
        !pagerSettledAtTarget
    return settledTargetCanFocus || programmaticTargetCanFocus || transitionSourceCanKeepFocus
}

internal fun resolvePhoneScheduleCalendarProgress(
    isWide: Boolean,
    forcedOfflineMode: Boolean,
    schedulePage: Int,
    hasScheduleDays: Boolean,
    visualPagerPosition: Float,
): Float {
    if (isWide || forcedOfflineMode || schedulePage < 0 || !hasScheduleDays) return 0f
    return (1f - abs(visualPagerPosition - schedulePage)).coerceIn(0f, 1f)
}

internal data class PagerAlignmentState(
    val isScrollInProgress: Boolean,
    val settledPage: Int,
    val currentPage: Int,
    val offset: Float,
)

internal data class HomeBrowseBackState(
    val visualSection: BrowseSection,
    val settledAtStateSection: Boolean,
)

private const val BrowsePagerAlignmentTolerance = 0.001f
