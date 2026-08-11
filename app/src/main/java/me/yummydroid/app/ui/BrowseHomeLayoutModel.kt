package me.yummydroid.app.ui

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.ui.focus.FocusRequester
import dev.chrisbanes.haze.HazeState
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
