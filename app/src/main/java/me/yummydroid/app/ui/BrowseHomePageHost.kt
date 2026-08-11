package me.yummydroid.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.yummydroid.app.BrowseSection

@Composable
internal fun BrowsePageHost(
    state: BrowseHomeLayoutState,
    actions: BrowseHomeLayoutActions,
) {
    when {
        state.effectiveSection == BrowseSection.Downloads -> {
            state.pageStateHolder.SaveableStateProvider(BrowseSection.Downloads) {
                actions.sectionPage(
                    BrowseSection.Downloads,
                    state.pagerPage,
                    state.active && state.dpadFocusEnabled,
                    state.dpadLayerFocusRequestNonce,
                )
            }
        }
        !state.usePager -> BrowseSinglePage(state, actions)
        else -> BrowseHorizontalPager(state, actions)
    }
}

@Composable
private fun BrowseSinglePage(
    state: BrowseHomeLayoutState,
    actions: BrowseHomeLayoutActions,
) {
    val focusSuppressed = state.effectiveSection == state.suppressedContentFocusSection
    state.pageStateHolder.SaveableStateProvider(state.effectiveSection) {
        actions.sectionPage(
            state.effectiveSection,
            state.pagerPage,
            state.active && state.dpadFocusEnabled && !focusSuppressed,
            if (focusSuppressed) 0L else state.contentFocusRequestNonce,
        )
    }
}

@Composable
private fun BrowseHorizontalPager(
    state: BrowseHomeLayoutState,
    actions: BrowseHomeLayoutActions,
) {
    HorizontalPager(
        state = state.pagerState,
        beyondViewportPageCount = 1,
        userScrollEnabled = state.active,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        BrowsePagerPage(state, actions, page)
    }
}

@Composable
private fun BrowsePagerPage(
    state: BrowseHomeLayoutState,
    actions: BrowseHomeLayoutActions,
    page: Int,
) {
    val section = state.pagerSections.getOrNull(page) ?: BrowseSection.Catalog
    val canReceiveFocus = browsePageCanReceiveFocus(
        active = state.active,
        dpadFocusEnabled = state.dpadFocusEnabled,
        contentFocusSuppressed = section == state.suppressedContentFocusSection,
        page = page,
        targetPage = state.pagerPage,
        pagerSettledAtTarget = state.pagerSettledAtTarget,
        programmaticScrollTarget = state.programmaticScrollTarget,
        transitionFocusSourcePage = state.transitionFocusSourcePage,
    )
    val focusRequestNonce = if (canReceiveFocus && page == state.pagerPage) {
        state.contentFocusRequestNonce
    } else {
        0L
    }
    state.pageStateHolder.SaveableStateProvider(section) {
        actions.sectionPage(section, page, canReceiveFocus, focusRequestNonce)
    }
}
