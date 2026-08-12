package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import me.yummydroid.app.AppBackAction
import me.yummydroid.app.BrowseSection

class YummyDroidAppTest {
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
        val inputState = YummyDroidAppInputState(BrowseSection.Catalog)
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
        val inputState = YummyDroidAppInputState(BrowseSection.Catalog)
        val screenHandler: (me.yummydroid.app.InputAction) -> Boolean = { true }
        val profileHandler: (me.yummydroid.app.InputAction) -> Boolean = { true }
        inputState.registerModalInputActionHandler(AppScreenKey.Home, screenHandler)
        inputState.registerModalInputActionHandler(AppModalInputOwner.ProfileDialog, profileHandler)

        assertNull(inputState.activeModalInputActionHandler(AppScreenKey.Home, AppModalBackTarget.Update))
        assertEquals(
            profileHandler,
            inputState.activeModalInputActionHandler(AppScreenKey.Home, AppModalBackTarget.Profile),
        )
        assertNull(inputState.activeModalInputActionHandler(AppScreenKey.Home, AppModalBackTarget.Settings))
    }

    @Test
    fun layerActivationRemovesOnlyHandlersOwnedByInactiveScreens() {
        val inputState = YummyDroidAppInputState(BrowseSection.Catalog)
        val homeHandler: (me.yummydroid.app.InputAction) -> Boolean = { true }
        val playerHandler: (me.yummydroid.app.InputAction) -> Boolean = { true }
        val profileHandler: (me.yummydroid.app.InputAction) -> Boolean = { true }
        inputState.registerModalInputActionHandler(AppScreenKey.Home, homeHandler)
        inputState.registerModalInputActionHandler(AppScreenKey.Player, playerHandler)
        inputState.registerModalInputActionHandler(AppModalInputOwner.ProfileDialog, profileHandler)

        inputState.activateLayer(AppScreenKey.Player, BrowseSection.Catalog)

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
