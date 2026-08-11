package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import me.yummydroid.app.BrowseSection

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
