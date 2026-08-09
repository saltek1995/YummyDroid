package me.yummydroid.app.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.util.Locale
import me.yummydroid.app.BrowseSection

internal data class BrowseHomeLayoutState(
    val active: Boolean,
    val dpadFocusEnabled: Boolean,
    val chromePolicy: BrowseChromePolicy,
    val chromeHazeState: HazeState,
    val chromeHazeActive: Boolean,
    val homeChromeState: BrowseHomeChromeState,
    val topBarVisible: Boolean,
    val topBarVisibilityProgressProvider: () -> Float,
    val effectiveSection: BrowseSection,
    val pagerSections: List<BrowseSection>,
    val pagerPage: Int,
    val usePager: Boolean,
    val pageStateHolder: SaveableStateHolder,
    val pagerState: PagerState,
    val pagerSettledAtTarget: Boolean,
    val programmaticScrollTarget: Int?,
    val transitionFocusSourcePage: Int?,
    val suppressedContentFocusSection: BrowseSection?,
    val dpadLayerFocusRequestNonce: Long,
    val contentFocusRequestNonce: Long,
    val topActionsFocusRequester: FocusRequester,
    val sectionTabFocusRequesters: Map<BrowseSection, FocusRequester>,
    val sectionTabsFocusEnabled: Boolean,
    val isWide: Boolean,
    val forcedOfflineMode: Boolean,
    val showPhoneScheduleCalendar: Boolean,
    val showPhoneScheduleCalendarVisual: Boolean,
    val phoneScheduleDayGroups: List<ScheduleDayGroup>,
    val selectedScheduleEpochDay: Long,
    val scheduleLocale: Locale,
    val scheduleCalendarFocusRequestNonce: Long,
    val scheduleCalendarVisualProgress: Float,
)

internal data class BrowseHomeLayoutActions(
    val onLayerFocusChanged: (Boolean) -> Unit,
    val onOpenSearch: () -> Unit,
    val onOpenFilters: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenDownloads: () -> Unit,
    val onOpenLogin: () -> Unit,
    val onOpenProfile: () -> Unit,
    val onSectionSelected: (BrowseSection) -> Unit,
    val onRequestTopActionsFocus: () -> Boolean,
    val onRequestSectionTabsFocus: (BrowseSection, Boolean) -> Boolean,
    val onRequestScheduleCalendarFocus: () -> Boolean,
    val onRequestContentFocus: () -> Boolean,
    val onScheduleDaySelected: (Long) -> Unit,
    val onBottomChromeMeasured: (heightPx: Int, expanded: Boolean) -> Unit,
    val sectionPage: @Composable (
        section: BrowseSection,
        page: Int,
        canReceiveFocus: Boolean,
        focusRequestNonce: Long,
    ) -> Unit,
)

@Composable
internal fun BrowseHomeLayout(
    state: BrowseHomeLayoutState,
    actions: BrowseHomeLayoutActions,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onFocusChanged { focusState ->
                actions.onLayerFocusChanged(focusState.isFocused || focusState.hasFocus)
            }
            .focusGroup(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (state.chromePolicy.pinTopChrome) {
                BrowseTopBarChrome(state, actions, collapseWhenHidden = false)
                if (state.chromePolicy.showTvSectionTabs) {
                    BrowseHomeTvSectionTabs(
                        state = state.homeChromeState,
                        sectionFocusRequesters = state.sectionTabFocusRequesters,
                        sectionTabsFocusEnabled = state.sectionTabsFocusEnabled,
                        onSectionSelected = actions.onSectionSelected,
                        onExitUp = actions.onRequestTopActionsFocus,
                        onExitDown = {
                            if (state.effectiveSection == BrowseSection.Schedule) {
                                actions.onRequestScheduleCalendarFocus()
                            } else {
                                actions.onRequestContentFocus()
                            }
                        },
                    )
                }
            } else {
                BrowseTopBarChrome(state, actions)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (state.chromeHazeActive) {
                            Modifier.hazeSource(state.chromeHazeState)
                        } else {
                            Modifier
                        },
                    ),
            ) {
                BrowsePageHost(state, actions)
            }
        }

        if (state.chromePolicy.showBottomChrome) {
            BrowseHomeBottomBar(
                state = state.homeChromeState,
                onOpenSearch = actions.onOpenSearch,
                onOpenFilters = actions.onOpenFilters,
                onOpenSettings = actions.onOpenSettings,
                onOpenDownloads = actions.onOpenDownloads,
                onOpenLogin = actions.onOpenLogin,
                onOpenProfile = actions.onOpenProfile,
                onSectionSelected = actions.onSectionSelected,
                sectionTabsFocusRequester = state.sectionTabFocusRequesters[state.effectiveSection],
                sectionTabFocusRequesters = state.sectionTabFocusRequesters,
                sectionTabsOnExitUp = if (state.showPhoneScheduleCalendar) {
                    actions.onRequestScheduleCalendarFocus
                } else {
                    actions.onRequestContentFocus
                },
                sectionTabsFocusEnabled = state.sectionTabsFocusEnabled,
                hazeState = if (state.chromeHazeActive) state.chromeHazeState else null,
                showScheduleCalendar = state.showPhoneScheduleCalendarVisual,
                scheduleDayGroups = state.phoneScheduleDayGroups,
                selectedScheduleEpochDay = state.selectedScheduleEpochDay,
                scheduleLocale = state.scheduleLocale,
                scheduleCalendarFocusRequestNonce = state.scheduleCalendarFocusRequestNonce,
                scheduleCalendarFocusEnabled = state.dpadFocusEnabled,
                scheduleCalendarOnExitUp = actions.onRequestContentFocus,
                scheduleCalendarOnExitDown = {
                    actions.onRequestSectionTabsFocus(BrowseSection.Schedule, true)
                },
                onScheduleDaySelected = actions.onScheduleDaySelected,
                scheduleCalendarVisibilityProgress = state.scheduleCalendarVisualProgress,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { size ->
                        actions.onBottomChromeMeasured(
                            size.height,
                            state.showPhoneScheduleCalendarVisual,
                        )
                    },
            )
        }
    }
}

@Composable
private fun BrowseTopBarChrome(
    state: BrowseHomeLayoutState,
    actions: BrowseHomeLayoutActions,
    collapseWhenHidden: Boolean = true,
) {
    BrowseHomeTopBar(
        state = state.homeChromeState,
        onOpenSearch = actions.onOpenSearch,
        onOpenFilters = actions.onOpenFilters,
        onOpenSettings = actions.onOpenSettings,
        onOpenDownloads = actions.onOpenDownloads,
        onOpenLogin = actions.onOpenLogin,
        onOpenProfile = actions.onOpenProfile,
        onSectionSelected = actions.onSectionSelected,
        onExitDown = {
            if (state.isWide && !state.forcedOfflineMode) {
                actions.onRequestSectionTabsFocus(state.effectiveSection, false)
            } else {
                actions.onRequestContentFocus()
            }
        },
        actionsFocusRequester = state.topActionsFocusRequester,
        sectionTabsFocusRequester = if (state.isWide && !state.forcedOfflineMode) {
            state.sectionTabFocusRequesters[state.effectiveSection]
        } else {
            null
        },
        sectionTabFocusRequesters = state.sectionTabFocusRequesters,
        sectionTabsFocusEnabled = state.sectionTabsFocusEnabled,
        collapseWhenHidden = collapseWhenHidden,
        visible = state.topBarVisible,
        visibilityProgressProvider = state.topBarVisibilityProgressProvider,
    )
}

@Composable
private fun BrowsePageHost(
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

        !state.usePager -> {
            val contentFocusSuppressed = state.effectiveSection == state.suppressedContentFocusSection
            state.pageStateHolder.SaveableStateProvider(state.effectiveSection) {
                actions.sectionPage(
                    state.effectiveSection,
                    state.pagerPage,
                    state.active && state.dpadFocusEnabled && !contentFocusSuppressed,
                    if (contentFocusSuppressed) 0L else state.contentFocusRequestNonce,
                )
            }
        }

        else -> BrowseHorizontalPager(state, actions)
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
        val pageSection = state.pagerSections.getOrNull(page) ?: BrowseSection.Catalog
        val contentFocusSuppressed = pageSection == state.suppressedContentFocusSection
        val pageCanReceiveFocus = browsePageCanReceiveFocus(
            active = state.active,
            dpadFocusEnabled = state.dpadFocusEnabled,
            contentFocusSuppressed = contentFocusSuppressed,
            page = page,
            targetPage = state.pagerPage,
            pagerSettledAtTarget = state.pagerSettledAtTarget,
            programmaticScrollTarget = state.programmaticScrollTarget,
            transitionFocusSourcePage = state.transitionFocusSourcePage,
        )
        val focusRequestNonce = if (pageCanReceiveFocus && page == state.pagerPage) {
            state.contentFocusRequestNonce
        } else {
            0L
        }
        state.pageStateHolder.SaveableStateProvider(pageSection) {
            actions.sectionPage(pageSection, page, pageCanReceiveFocus, focusRequestNonce)
        }
    }
}
