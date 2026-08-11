package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import me.yummydroid.app.BrowseSection

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
