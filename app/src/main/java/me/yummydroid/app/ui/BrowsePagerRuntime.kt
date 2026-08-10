package me.yummydroid.app.ui

import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import me.yummydroid.app.BrowseSection

internal class BrowsePagerRuntime(
    val pagerState: PagerState,
    val pageStateHolder: SaveableStateHolder,
    initialSection: BrowseSection,
) {
    var suppressedContentFocusSection by mutableStateOf<BrowseSection?>(null)
    var transitionFocusSourcePage by mutableStateOf<Int?>(null)
    var requestContentFocusOnFinish by mutableStateOf(false)
    var pageFocusRequestNonce by mutableLongStateOf(0L)
    var pageFocusRequestSection by mutableStateOf(initialSection)
    var keepTabsFocusedForSectionChange by mutableStateOf(false)
    var pendingTabsFocusSection by mutableStateOf<BrowseSection?>(null)
    var programmaticScrollTarget by mutableStateOf<Int?>(null)
    var topBarProgrammaticTargetProgress by mutableStateOf<Float?>(null)
    var programmaticTabTargetPosition by mutableStateOf<Float?>(null)
    var wasAligned by mutableStateOf(false)

    val sectionTabsFocusEnabled: Boolean
        get() = transitionFocusSourcePage == null

    fun alignment(): PagerAlignmentState {
        return PagerAlignmentState(
            isScrollInProgress = pagerState.isScrollInProgress,
            settledPage = pagerState.settledPage,
            currentPage = pagerState.currentPage,
            offset = pagerState.currentPageOffsetFraction,
        )
    }

    fun isSettledAt(page: Int): Boolean = alignment().isSettledAt(page)

    fun releaseFocusTransition() {
        transitionFocusSourcePage = null
        requestContentFocusOnFinish = false
    }

    fun finishProgrammaticTarget(targetPage: Int, currentTargetPage: Int) {
        if (programmaticScrollTarget != targetPage) return
        if (currentTargetPage != targetPage) return
        if (!isSettledAt(targetPage)) return
        val shouldRequestContentFocus = transitionFocusSourcePage != null
        programmaticScrollTarget = null
        transitionFocusSourcePage = null
        val requestContentFocus = requestContentFocusOnFinish && shouldRequestContentFocus
        requestContentFocusOnFinish = false
        topBarProgrammaticTargetProgress = null
        programmaticTabTargetPosition = null
        if (requestContentFocus) {
            pageFocusRequestNonce += 1L
        }
    }
}

@Composable
internal fun rememberBrowsePagerRuntime(
    initialPage: Int,
    initialSection: BrowseSection,
    pageCount: () -> Int,
): BrowsePagerRuntime {
    val pageStateHolder = rememberSaveableStateHolder()
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = pageCount)
    return remember(pagerState, pageStateHolder) {
        BrowsePagerRuntime(pagerState, pageStateHolder, initialSection)
    }
}
