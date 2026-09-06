package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import me.yummydroid.app.AppBackAction
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.AppRoute
import me.yummydroid.app.YummyDroidUiState
import me.yummydroid.app.navigationStackAfterOptionalPush
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.data.UserProfile

class YummyDroidAppTest {
    @Test
    fun subscriptionsReturnCannotCrossAnAccountChange() {
        val origin = YummyDroidUiState(auth = AuthUiState(profile = UserProfile(10, "Viewer", "")))
        val modals = YummyDroidAppModalState().apply { openSubscribedAnime(origin, 20) }
        val signedOut = origin.copy(auth = AuthUiState(), route = AppRoute.Details(20), navigationBackStack = origin.navigationStackAfterOptionalPush(true))
        assertFalse(modals.returnToSubscriptions(signedOut) { error("Must not navigate") })
        assertFalse(modals.hasSubscriptionReturn)
        assertFalse(modals.profileDialogOpen)
    }

    @Test
    fun backFromSubscribedAnimeReturnsToSubscriptionsAtTheOriginalNavigationDepth() {
        val origin = YummyDroidUiState()
        val modals = YummyDroidAppModalState().apply { openProfile() }
        modals.openSubscribedAnime(origin, 10)
        assertFalse(modals.profileDialogOpen)
        val details = origin.copy(route = AppRoute.Details(10), navigationBackStack = origin.navigationStackAfterOptionalPush(true))
        val nested = details.copy(route = AppRoute.Details(20), navigationBackStack = details.navigationStackAfterOptionalPush(true))
        var backCount = 0
        assertFalse(modals.returnToSubscriptions(nested) { backCount++ })
        assertTrue(modals.returnToSubscriptions(details) { backCount++ })
        assertEquals(1, backCount)
        assertTrue(modals.profileDialogOpen)
        assertFalse(modals.hasSubscriptionReturn)
        assertFalse(modals.returnToSubscriptions(details) { backCount++ })

        modals.openSubscribedAnime(details, 10)
        assertTrue(modals.returnToSubscriptions(details) { backCount++ })
        assertEquals(1, backCount)
    }

    @Test
    fun modalBackTargetUsesRenderedStackPriority() {
        assertEquals(
            AppModalBackTarget.Update,
            resolveAppModalBackTarget(
                pendingUpdateVisible = true,
                settingsDialogOpen = true,
                profileDialogOpen = true,
                loginDialogOpen = true,
            ),
        )
        assertEquals(
            AppModalBackTarget.Settings,
            resolveAppModalBackTarget(false, true, true, true),
        )
        assertEquals(
            AppModalBackTarget.LocalHistoryMerge,
            resolveAppModalBackTarget(
                pendingUpdateVisible = false,
                settingsDialogOpen = true,
                profileDialogOpen = true,
                loginDialogOpen = true,
                localHistoryMergePromptVisible = true,
            ),
        )
        assertEquals(
            AppModalBackTarget.Profile,
            resolveAppModalBackTarget(false, false, true, true),
        )
        assertEquals(
            AppModalBackTarget.Login,
            resolveAppModalBackTarget(false, false, false, true),
        )
        assertNull(resolveAppModalBackTarget(false, false, false, false))
    }

    @Test
    fun touchBackUsesStateSectionInsteadOfTransientPagerSection() {
        assertEquals(
            BrowseSection.Catalog,
            resolveRootHomeBackSection(
                treatAsTouchBack = true,
                inputModeIsTouch = false,
                stateSection = BrowseSection.Catalog,
                visualSection = BrowseSection.Schedule,
            ),
        )
        assertEquals(
            BrowseSection.Catalog,
            resolveRootHomeBackSection(
                treatAsTouchBack = false,
                inputModeIsTouch = true,
                stateSection = BrowseSection.Catalog,
                visualSection = BrowseSection.History,
            ),
        )
    }

    @Test
    fun dpadBackUsesSettledScheduleOrHistoryButNotOtherVisualSections() {
        assertEquals(
            BrowseSection.Schedule,
            resolveRootHomeBackSection(false, false, BrowseSection.Catalog, BrowseSection.Schedule),
        )
        assertEquals(
            BrowseSection.History,
            resolveRootHomeBackSection(false, false, BrowseSection.Catalog, BrowseSection.History),
        )
        assertEquals(
            BrowseSection.Catalog,
            resolveRootHomeBackSection(false, false, BrowseSection.Catalog, BrowseSection.Downloads),
        )
    }

    @Test
    fun repeatedBackIsConsumedOnlyWhenItCouldRepeatAnAction() {
        assertTrue(shouldConsumeRepeatedAppBack(true, AppBackAction.NavigateBack, false))
        assertTrue(shouldConsumeRepeatedAppBack(true, AppBackAction.Ignore, true))
        assertFalse(shouldConsumeRepeatedAppBack(true, AppBackAction.Ignore, false))
        assertFalse(shouldConsumeRepeatedAppBack(false, AppBackAction.NavigateBack, true))
    }

    @Test
    fun touchModeSuppressesLayerFocusNonce() {
        assertEquals(0L, resolveActiveLayerFocusRequestNonce(true, 42L))
        assertEquals(42L, resolveActiveLayerFocusRequestNonce(false, 42L))
    }

    @Test
    fun screenOwnedInputHandlersOnlyRemainActiveForTheirLayer() {
        assertTrue(isAppInputHandlerOwnerActive(AppScreenKey.Home, AppScreenKey.Home))
        assertFalse(isAppInputHandlerOwnerActive(AppScreenKey.Home, AppScreenKey.Player))
        assertTrue(isAppInputHandlerOwnerActive(AppModalInputOwner.SettingsDialog, AppScreenKey.Player))
    }

    @Test
    fun topModalHandlerDoesNotReplaceTheUnderlyingScreenHandler() {
        val inputState = AppNavigationController(BrowseSection.Catalog)
        val homeHandler: (me.yummydroid.app.InputAction) -> Boolean = { true }
        val settingsHandler: (me.yummydroid.app.InputAction) -> Boolean = { false }

        inputState.registerModalInputActionHandler(AppScreenKey.Home, homeHandler)
        inputState.registerModalInputActionHandler(AppModalInputOwner.SettingsDialog, settingsHandler)

        assertEquals(
            settingsHandler,
            inputState.activeModalInputActionHandler(AppScreenKey.Home, AppModalBackTarget.Settings),
        )
        inputState.registerModalInputActionHandler(AppModalInputOwner.SettingsDialog, null)
        assertEquals(
            homeHandler,
            inputState.activeModalInputActionHandler(AppScreenKey.Home, topAppModal = null),
        )
    }

    @Test
    fun modalHandlerSelectionFollowsTheRenderedModalPriority() {
        val inputState = AppNavigationController(BrowseSection.Catalog)
        val screenHandler: (me.yummydroid.app.InputAction) -> Boolean = { true }
        val profileHandler: (me.yummydroid.app.InputAction) -> Boolean = { true }
        inputState.registerModalInputActionHandler(AppScreenKey.Home, screenHandler)
        inputState.registerModalInputActionHandler(AppModalInputOwner.ProfileDialog, profileHandler)

        assertNull(inputState.activeModalInputActionHandler(AppScreenKey.Home, AppModalBackTarget.Update))
        assertNull(inputState.activeModalInputActionHandler(AppScreenKey.Home, AppModalBackTarget.LocalHistoryMerge))
        assertEquals(
            profileHandler,
            inputState.activeModalInputActionHandler(AppScreenKey.Home, AppModalBackTarget.Profile),
        )
        assertNull(inputState.activeModalInputActionHandler(AppScreenKey.Home, AppModalBackTarget.Settings))
    }

    @Test
    fun layerActivationRemovesOnlyHandlersOwnedByInactiveScreens() {
        val inputState = AppNavigationController(BrowseSection.Catalog)
        val homeHandler: (me.yummydroid.app.InputAction) -> Boolean = { true }
        val playerHandler: (me.yummydroid.app.InputAction) -> Boolean = { true }
        val profileHandler: (me.yummydroid.app.InputAction) -> Boolean = { true }
        inputState.registerModalInputActionHandler(AppScreenKey.Home, homeHandler)
        inputState.registerModalInputActionHandler(AppScreenKey.Player, playerHandler)
        inputState.registerModalInputActionHandler(AppModalInputOwner.ProfileDialog, profileHandler)

        inputState.synchronizeInputContext(
            activeLayerKey = AppScreenKey.Player,
            homeSection = BrowseSection.Catalog,
            topAppModal = null,
        )

        assertNull(inputState.activeModalInputActionHandler(AppScreenKey.Home, topAppModal = null))
        assertEquals(
            playerHandler,
            inputState.activeModalInputActionHandler(AppScreenKey.Player, topAppModal = null),
        )
        assertEquals(
            profileHandler,
            inputState.activeModalInputActionHandler(AppScreenKey.Player, AppModalBackTarget.Profile),
        )
    }

    @Test
    fun inputContextRequestsFocusOncePerSemanticTransition() {
        val inputState = AppNavigationController(BrowseSection.Catalog)

        assertTrue(
            inputState.synchronizeInputContext(
                AppScreenKey.Home,
                BrowseSection.Catalog,
                topAppModal = null,
            ),
        )
        assertEquals(1L, inputState.activeLayerFocusNonce)

        assertFalse(
            inputState.synchronizeInputContext(
                AppScreenKey.Home,
                BrowseSection.Catalog,
                topAppModal = null,
            ),
        )
        assertEquals(1L, inputState.activeLayerFocusNonce)

        inputState.synchronizeInputContext(
            AppScreenKey.Home,
            BrowseSection.Schedule,
            topAppModal = null,
        )
        assertEquals(2L, inputState.activeLayerFocusNonce)

        inputState.synchronizeInputContext(
            AppScreenKey.Home,
            BrowseSection.Schedule,
            topAppModal = AppModalBackTarget.Settings,
        )
        inputState.synchronizeInputContext(
            AppScreenKey.Home,
            BrowseSection.Schedule,
            topAppModal = null,
        )
        assertEquals(3L, inputState.activeLayerFocusNonce)
    }

    @Test
    fun layerChangeAndModalCloseProduceOneFocusRequest() {
        val inputState = AppNavigationController(BrowseSection.Catalog)
        inputState.synchronizeInputContext(
            AppScreenKey.Home,
            BrowseSection.Catalog,
            topAppModal = AppModalBackTarget.Settings,
        )

        inputState.synchronizeInputContext(
            AppScreenKey.Player,
            BrowseSection.Catalog,
            topAppModal = null,
        )

        assertEquals(2L, inputState.activeLayerFocusNonce)
    }

    @Test
    fun closingTopModalMutatesOnlyTheHighestPriorityEntry() {
        val modalState = YummyDroidAppModalState().apply {
            loginDialogOpen = true
            profileDialogOpen = true
            settingsDialogOpen = true
        }

        assertTrue(modalState.closeTopModal(pendingUpdateVisible = true))
        assertTrue(modalState.autoUpdatePromptDismissed)
        assertTrue(modalState.settingsDialogOpen)

        assertTrue(modalState.closeTopModal(pendingUpdateVisible = false))
        assertFalse(modalState.settingsDialogOpen)
        assertTrue(modalState.profileDialogOpen)
        assertTrue(modalState.loginDialogOpen)
    }
}
