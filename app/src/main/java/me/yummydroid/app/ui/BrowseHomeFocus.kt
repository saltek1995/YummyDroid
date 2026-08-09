package me.yummydroid.app.ui

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.yummydroid.app.BrowseSection

internal data class BrowseFocusFirstRequests(
    val catalog: FocusFirstRequest,
    val schedule: FocusFirstRequest,
    val history: FocusFirstRequest,
)

internal fun resolveBrowseFocusFirstRequests(
    section: BrowseSection,
    persistentCatalogNonce: Long,
    transientNonce: Long,
): BrowseFocusFirstRequests {
    return BrowseFocusFirstRequests(
        catalog = FocusFirstRequest(
            persistentNonce = persistentCatalogNonce,
            transientNonce = transientNonce.takeIf { section == BrowseSection.Catalog } ?: 0L,
        ),
        schedule = FocusFirstRequest(
            transientNonce = transientNonce.takeIf { section == BrowseSection.Schedule } ?: 0L,
        ),
        history = FocusFirstRequest(
            transientNonce = transientNonce.takeIf { section == BrowseSection.History } ?: 0L,
        ),
    )
}

internal class BrowseFocusRuntime(
    private val scope: CoroutineScope,
    val topActionsFocusRequester: FocusRequester,
) {
    var contentFocusRequestNonce by mutableLongStateOf(0L)
    var firstFocusRequestNonce by mutableLongStateOf(0L)
    var layerHasFocus by mutableStateOf(false)
    var scheduleCalendarFocusRequestNonce by mutableLongStateOf(0L)
    var activeHomeBackToTopHandler by mutableStateOf<HomeBackToTopHandler?>(null)

    fun layerFocusRequestNonce(dpadFocusEnabled: Boolean, activeFocusRequestNonce: Long): Long {
        return if (dpadFocusEnabled && activeFocusRequestNonce > 0L) {
            activeFocusRequestNonce * 1_000_000L + contentFocusRequestNonce
        } else {
            0L
        }
    }

    fun requestCurrentContentFocus(pagerRuntime: BrowsePagerRuntime): Boolean {
        pagerRuntime.suppressedContentFocusSection = null
        contentFocusRequestNonce += 1L
        return true
    }

    fun requestFirstContentFocus(
        section: BrowseSection,
        pagerRuntime: BrowsePagerRuntime,
    ): Boolean {
        pagerRuntime.suppressedContentFocusSection = null
        if (section == BrowseSection.Downloads) {
            contentFocusRequestNonce += 1L
        } else {
            firstFocusRequestNonce += 1L
        }
        return true
    }

    fun recoverFirstContentFocus(
        section: BrowseSection,
        pagerRuntime: BrowsePagerRuntime,
    ): Boolean {
        if (layerHasFocus) return false
        return requestFirstContentFocus(section, pagerRuntime)
    }

    fun requestScheduleCalendarFocus(
        showPhoneCalendar: Boolean,
        scheduleGridState: LazyGridState,
        browseCoordinator: BrowseRootUiCoordinator,
        pagerRuntime: BrowsePagerRuntime,
    ): Boolean {
        pagerRuntime.suppressedContentFocusSection = null
        if (showPhoneCalendar) {
            scheduleCalendarFocusRequestNonce += 1L
            return true
        }
        scope.launch {
            if (scheduleGridState.firstVisibleItemIndex != 0 || scheduleGridState.firstVisibleItemScrollOffset != 0) {
                browseCoordinator.scrollToTop(BrowseSection.Schedule)
            }
            withFrameNanos { }
            scheduleCalendarFocusRequestNonce += 1L
        }
        return true
    }

    fun requestTopActionsFocus(
        topBarFullyVisible: Boolean,
        dpadFocusEnabled: Boolean,
        section: BrowseSection,
        browseCoordinator: BrowseRootUiCoordinator,
    ): Boolean {
        if (topBarFullyVisible && dpadFocusEnabled && topActionsFocusRequester.requestFocusSafely()) {
            return true
        }
        scope.launch {
            browseCoordinator.scrollToTop(section)
            withFrameNanos { }
            if (dpadFocusEnabled) {
                topActionsFocusRequester.requestFocusSafely()
            }
        }
        return true
    }

    fun requestSectionTabsFocus(
        section: BrowseSection,
        releasePagerFocusTransition: Boolean,
        dpadFocusEnabled: Boolean,
        forcedOfflineMode: Boolean,
        sectionFocusRequesters: Map<BrowseSection, FocusRequester>,
        pagerRuntime: BrowsePagerRuntime,
    ): Boolean {
        if (!dpadFocusEnabled || forcedOfflineMode) return false
        if (releasePagerFocusTransition) {
            pagerRuntime.releaseFocusTransition()
        }
        val requester = sectionFocusRequesters[section] ?: return false
        if (releasePagerFocusTransition) {
            scope.launch {
                withFrameNanos { }
                requester.requestFocusSafely()
            }
            return true
        }
        return requester.requestFocusSafely()
    }

    fun updateHomeBackToTopHandler(
        section: BrowseSection,
        handler: HomeBackToTopHandler?,
        onRegister: (BrowseSection, HomeBackToTopHandler?) -> Unit,
    ) {
        if (handler == null) {
            if (activeHomeBackToTopHandler?.section == section) {
                activeHomeBackToTopHandler = null
            }
        } else {
            activeHomeBackToTopHandler = handler
        }
        onRegister(section, handler)
    }

    fun scrollScheduleToStart(scheduleGridState: LazyGridState) {
        scope.launch { scheduleGridState.animateScrollToItem(0, 0) }
    }
}

internal data class BrowseFocusBinding(
    val runtime: BrowseFocusRuntime,
    val sectionFocusRequesters: Map<BrowseSection, FocusRequester>,
)

internal data class BrowseFocusActions(
    val requestCurrentContentFocus: () -> Boolean,
    val requestFirstContentFocus: () -> Boolean,
    val recoverFirstContentFocus: () -> Boolean,
    val requestScheduleCalendarFocus: () -> Boolean,
    val requestTopActionsFocus: () -> Boolean,
    val requestSectionTabsFocus: (BrowseSection, Boolean) -> Boolean,
    val updateHomeBackToTopHandler: (BrowseSection, HomeBackToTopHandler?) -> Unit,
)

internal fun BrowseFocusRuntime.bindActions(
    section: BrowseSection,
    dpadFocusEnabled: Boolean,
    forcedOfflineMode: Boolean,
    showPhoneScheduleCalendar: Boolean,
    scheduleGridState: LazyGridState,
    browseCoordinator: BrowseRootUiCoordinator,
    sectionFocusRequesters: Map<BrowseSection, FocusRequester>,
    pagerRuntime: BrowsePagerRuntime,
    topBarFullyVisible: () -> Boolean,
    onRegisterHomeBackToTopHandler: (BrowseSection, HomeBackToTopHandler?) -> Unit,
): BrowseFocusActions {
    return BrowseFocusActions(
        requestCurrentContentFocus = { requestCurrentContentFocus(pagerRuntime) },
        requestFirstContentFocus = { requestFirstContentFocus(section, pagerRuntime) },
        recoverFirstContentFocus = { recoverFirstContentFocus(section, pagerRuntime) },
        requestScheduleCalendarFocus = {
            requestScheduleCalendarFocus(
                showPhoneScheduleCalendar,
                scheduleGridState,
                browseCoordinator,
                pagerRuntime,
            )
        },
        requestTopActionsFocus = {
            requestTopActionsFocus(
                topBarFullyVisible(),
                dpadFocusEnabled,
                section,
                browseCoordinator,
            )
        },
        requestSectionTabsFocus = { targetSection, releaseTransition ->
            requestSectionTabsFocus(
                targetSection,
                releaseTransition,
                dpadFocusEnabled,
                forcedOfflineMode,
                sectionFocusRequesters,
                pagerRuntime,
            )
        },
        updateHomeBackToTopHandler = { targetSection, handler ->
            updateHomeBackToTopHandler(targetSection, handler, onRegisterHomeBackToTopHandler)
        },
    )
}

@Composable
internal fun rememberBrowseFocusBinding(sections: List<BrowseSection>): BrowseFocusBinding {
    val scope = rememberCoroutineScope()
    val topActionsFocusRequester = remember { FocusRequester() }
    val runtime = remember(scope, topActionsFocusRequester) {
        BrowseFocusRuntime(scope, topActionsFocusRequester)
    }
    val sectionFocusRequesters = remember(sections) {
        sections.associateWith { FocusRequester() }
    }
    return remember(runtime, sectionFocusRequesters) {
        BrowseFocusBinding(runtime, sectionFocusRequesters)
    }
}
