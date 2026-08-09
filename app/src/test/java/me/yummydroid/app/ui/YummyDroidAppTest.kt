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
}
