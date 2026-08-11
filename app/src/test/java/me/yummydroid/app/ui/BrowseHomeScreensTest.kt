package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.InputAction

class BrowseHomeScreensTest {
    @Test
    fun chromePolicyKeepsWideOfflineUiInTvLayout() {
        assertEquals(
            BrowseChromePolicy(
                pinTopChrome = true,
                showTvSectionTabs = false,
                showBottomChrome = false,
            ),
            resolveBrowseChromePolicy(isWide = true, forcedOfflineMode = true),
        )
        assertEquals(
            BrowseChromePolicy(
                pinTopChrome = false,
                showTvSectionTabs = false,
                showBottomChrome = true,
            ),
            resolveBrowseChromePolicy(isWide = false, forcedOfflineMode = true),
        )
        assertEquals(
            BrowseChromePolicy(
                pinTopChrome = true,
                showTvSectionTabs = true,
                showBottomChrome = false,
            ),
            resolveBrowseChromePolicy(isWide = true, forcedOfflineMode = false),
        )
    }

    @Test
    fun pagerSectionsRespectAuthorizationAndForcedOfflineMode() {
        assertEquals(
            listOf(BrowseSection.Catalog, BrowseSection.Schedule),
            resolveBrowsePagerSections(isAuthorized = false, forcedOfflineMode = false),
        )
        assertEquals(
            listOf(BrowseSection.Catalog, BrowseSection.History, BrowseSection.Schedule),
            resolveBrowsePagerSections(isAuthorized = true, forcedOfflineMode = false),
        )
        assertEquals(
            listOf(BrowseSection.Downloads),
            resolveBrowsePagerSections(isAuthorized = true, forcedOfflineMode = true),
        )
    }

    @Test
    fun effectiveSectionCorrectsUnavailableDestinations() {
        assertEquals(
            BrowseSection.Catalog,
            resolveEffectiveBrowseSection(BrowseSection.History, isAuthorized = false, forcedOfflineMode = false),
        )
        assertEquals(
            BrowseSection.History,
            resolveEffectiveBrowseSection(BrowseSection.History, isAuthorized = true, forcedOfflineMode = false),
        )
        assertEquals(
            BrowseSection.Downloads,
            resolveEffectiveBrowseSection(BrowseSection.Catalog, isAuthorized = true, forcedOfflineMode = true),
        )
        assertNull(
            resolveBrowseSectionCorrection(BrowseSection.Schedule, isAuthorized = false, forcedOfflineMode = false),
        )
        assertEquals(
            BrowseSection.Catalog,
            resolveBrowseSectionCorrection(BrowseSection.History, isAuthorized = false, forcedOfflineMode = false),
        )
    }

    @Test
    fun tabPositionUsesTransitionThenStableFallbackPriority() {
        assertEquals(
            2f,
            resolveBrowseTabPosition(
                active = false,
                useBrowsePager = true,
                pagerPage = 2,
                pagerPosition = 1.4f,
                programmaticTabTargetPosition = 1f,
                programmaticTabPosition = 0.7f,
                pagerDriven = true,
                effectiveSectionVisible = true,
                programmaticScrollPending = true,
            ),
        )
        assertEquals(
            0.7f,
            resolveBrowseTabPosition(
                active = true,
                useBrowsePager = true,
                pagerPage = 2,
                pagerPosition = 1.4f,
                programmaticTabTargetPosition = 1f,
                programmaticTabPosition = 0.7f,
                pagerDriven = true,
                effectiveSectionVisible = true,
                programmaticScrollPending = true,
            ),
        )
        assertEquals(
            1.4f,
            resolveBrowseTabPosition(
                active = true,
                useBrowsePager = true,
                pagerPage = 2,
                pagerPosition = 1.4f,
                programmaticTabTargetPosition = null,
                programmaticTabPosition = 0f,
                pagerDriven = true,
                effectiveSectionVisible = true,
                programmaticScrollPending = false,
            ),
        )
        assertEquals(
            2f,
            resolveBrowseTabPosition(
                active = true,
                useBrowsePager = true,
                pagerPage = 2,
                pagerPosition = 1.4f,
                programmaticTabTargetPosition = null,
                programmaticTabPosition = 0f,
                pagerDriven = false,
                effectiveSectionVisible = true,
                programmaticScrollPending = false,
            ),
        )
        assertNull(
            resolveBrowseTabPosition(
                active = true,
                useBrowsePager = false,
                pagerPage = 0,
                pagerPosition = 0f,
                programmaticTabTargetPosition = null,
                programmaticTabPosition = 0f,
                pagerDriven = false,
                effectiveSectionVisible = false,
                programmaticScrollPending = false,
            ),
        )
    }

    @Test
    fun backStateTracksVisualPageAndPagerSettlement() {
        val sections = listOf(BrowseSection.Catalog, BrowseSection.History, BrowseSection.Schedule)

        assertEquals(
            HomeBrowseBackState(BrowseSection.Schedule, settledAtStateSection = true),
            resolveHomeBrowseBackState(
                useBrowsePager = false,
                effectiveSection = BrowseSection.Schedule,
                pagerSections = sections,
                pagerPosition = 0f,
                pagerScrollInProgress = true,
                pagerAwayFromTarget = true,
            ),
        )
        assertEquals(
            HomeBrowseBackState(BrowseSection.Schedule, settledAtStateSection = false),
            resolveHomeBrowseBackState(
                useBrowsePager = true,
                effectiveSection = BrowseSection.Catalog,
                pagerSections = sections,
                pagerPosition = 1.6f,
                pagerScrollInProgress = true,
                pagerAwayFromTarget = true,
            ),
        )
    }

    @Test
    fun pagerSettlementRequiresMatchingIdlePageAndSmallOffset() {
        assertTrue(PagerAlignmentState(false, 1, 1, 0.001f).isSettledAt(1))
        assertFalse(PagerAlignmentState(true, 1, 1, 0f).isSettledAt(1))
        assertFalse(PagerAlignmentState(false, 0, 1, 0f).isSettledAt(1))
        assertFalse(PagerAlignmentState(false, 1, 0, 0f).isSettledAt(1))
        assertFalse(PagerAlignmentState(false, 1, 1, 0.0011f).isSettledAt(1))
    }

    @Test
    fun pagerPresentationInterpolatesTopBarAndRequiresSettledTarget() {
        val sections = listOf(BrowseSection.Catalog, BrowseSection.History)
        val progress = mapOf(BrowseSection.Catalog to 0.2f, BrowseSection.History to 0.8f)

        assertEquals(
            0.5f,
            resolveBrowseTopBarProgress(true, sections, 0.5f, 1f) { progress.getValue(it) },
        )
        assertEquals(
            1f,
            resolveBrowseTopBarProgress(false, sections, 0.5f, 1f) { progress.getValue(it) },
        )
        assertTrue(
            isBrowsePagerSettledAtTarget(
                BrowseSection.History,
                sections,
                pagerPage = 1,
                usePager = true,
                alignment = PagerAlignmentState(false, settledPage = 0, currentPage = 1, offset = 0.001f),
            ),
        )
        assertFalse(
            isBrowsePagerSettledAtTarget(
                BrowseSection.History,
                sections,
                pagerPage = 1,
                usePager = true,
                alignment = PagerAlignmentState(true, 1, 1, 0f),
            ),
        )
    }

    @Test
    fun pagerSectionNavigationSeparatesTabAndContentFocusPolicies() {
        val sections = listOf(BrowseSection.Catalog, BrowseSection.History, BrowseSection.Schedule)

        assertEquals(
            BrowseSectionFocusPlan(keepTabsFocused = true, requestContentFocus = false),
            resolveBrowseSectionFocusPlan(keepTabsFocused = true, dpadFocusEnabled = true),
        )
        assertEquals(
            BrowseSectionFocusPlan(keepTabsFocused = false, requestContentFocus = true),
            resolveBrowseSectionFocusPlan(keepTabsFocused = false, dpadFocusEnabled = true),
        )
        assertEquals(
            BrowseSection.History,
            resolveHorizontalBrowseSection(
                sections,
                page = 0,
                direction = VisualGridDirection.Right,
            ),
        )
        assertNull(
            resolveHorizontalBrowseSection(
                sections,
                page = 0,
                direction = VisualGridDirection.Left,
            ),
        )
        assertNull(
            resolveHorizontalBrowseSection(
                sections,
                page = 1,
                direction = VisualGridDirection.Up,
            ),
        )
    }

    @Test
    fun pageFocusAllowsOnlySettledTargetOrActiveTransitionParticipants() {
        assertTrue(
            browsePageCanReceiveFocus(
                active = true,
                dpadFocusEnabled = true,
                contentFocusSuppressed = false,
                page = 1,
                targetPage = 1,
                pagerSettledAtTarget = true,
                programmaticScrollTarget = null,
                transitionFocusSourcePage = null,
            ),
        )
        assertTrue(
            browsePageCanReceiveFocus(
                active = true,
                dpadFocusEnabled = true,
                contentFocusSuppressed = false,
                page = 2,
                targetPage = 2,
                pagerSettledAtTarget = false,
                programmaticScrollTarget = 2,
                transitionFocusSourcePage = 1,
            ),
        )
        assertTrue(
            browsePageCanReceiveFocus(
                active = true,
                dpadFocusEnabled = true,
                contentFocusSuppressed = false,
                page = 1,
                targetPage = 2,
                pagerSettledAtTarget = false,
                programmaticScrollTarget = 2,
                transitionFocusSourcePage = 1,
            ),
        )
        assertFalse(
            browsePageCanReceiveFocus(
                active = true,
                dpadFocusEnabled = true,
                contentFocusSuppressed = true,
                page = 1,
                targetPage = 1,
                pagerSettledAtTarget = true,
                programmaticScrollTarget = null,
                transitionFocusSourcePage = null,
            ),
        )
        assertFalse(
            browsePageCanReceiveFocus(
                active = false,
                dpadFocusEnabled = true,
                contentFocusSuppressed = false,
                page = 1,
                targetPage = 1,
                pagerSettledAtTarget = true,
                programmaticScrollTarget = null,
                transitionFocusSourcePage = null,
            ),
        )
    }

    @Test
    fun phoneScheduleCalendarProgressFollowsNearbyPagerPosition() {
        assertEquals(
            1f,
            resolvePhoneScheduleCalendarProgress(false, false, 2, true, visualPagerPosition = 2f),
        )
        assertEquals(
            0.5f,
            resolvePhoneScheduleCalendarProgress(false, false, 2, true, visualPagerPosition = 1.5f),
        )
        assertEquals(
            0f,
            resolvePhoneScheduleCalendarProgress(false, false, 2, true, visualPagerPosition = 1f),
        )
        assertEquals(
            0f,
            resolvePhoneScheduleCalendarProgress(true, false, 2, true, visualPagerPosition = 2f),
        )
        assertEquals(
            0f,
            resolvePhoneScheduleCalendarProgress(false, true, 2, true, visualPagerPosition = 2f),
        )
    }

    @Test
    fun focusFirstRequestsRouteTransientNonceToActiveSection() {
        assertEquals(
            BrowseFocusFirstRequests(
                catalog = FocusFirstRequest(persistentNonce = 11L),
                schedule = FocusFirstRequest(transientNonce = 7L),
                history = FocusFirstRequest(),
            ),
            resolveBrowseFocusFirstRequests(
                section = BrowseSection.Schedule,
                persistentCatalogNonce = 11L,
                transientNonce = 7L,
            ),
        )
    }

    @Test
    fun searchBackDismissesKeyboardBeforeClosingDialog() {
        val runtime = BrowseCatalogDialogRuntime().apply { searchDialogOpen = true }

        assertTrue(runtime.handleInputAction(InputAction.Back))
        assertTrue(runtime.searchDialogOpen)
        assertTrue(runtime.searchKeyboardBackConsumed)
        assertEquals(1L, runtime.searchKeyboardDismissRequest)

        assertTrue(runtime.handleInputAction(InputAction.Back))
        assertFalse(runtime.searchDialogOpen)
    }

    @Test
    fun modalInputRoutesNavigationOnlyToOpenCatalogDialog() {
        val runtime = BrowseCatalogDialogRuntime().apply { searchDialogOpen = true }

        assertTrue(runtime.handleInputAction(InputAction.Right))
        assertEquals(InputAction.Right, runtime.searchInputAction)
        assertEquals(1L, runtime.searchInputActionRequest)

        runtime.searchDialogOpen = false
        runtime.filtersDialogOpen = true
        assertFalse(runtime.handleInputAction(InputAction.Right))
        assertTrue(runtime.handleInputAction(InputAction.Back))
        assertFalse(runtime.filtersDialogOpen)
    }
}
