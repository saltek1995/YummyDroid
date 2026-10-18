package me.yummydroid.app.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.DownloadQueueSnapshot
import me.yummydroid.app.DownloadTaskState
import me.yummydroid.app.DownloadTaskUi
import me.yummydroid.app.LoadState
import me.yummydroid.app.YummyDroidUiState
import me.yummydroid.app.data.ContentLanguage
import me.yummydroid.app.ui.theme.YummyDroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DownloadsBackTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun offlineDownloadsScrollToTopBeforeExitForTouchAndDpadAndUnregisterWhenInactive() {
        val root = YummyDroidUiState(
            homeSection = BrowseSection.Downloads,
            forcedOfflineMode = true,
            offlineEntries = LoadState.Ready(emptyList()),
            downloadQueue = DownloadQueueSnapshot(tasks = (1L..30L).map { id ->
                DownloadTaskUi(id = id, animeId = id, videoId = id, title = "Task $id",
                    episodeTitle = "Episode 1", state = DownloadTaskState.Paused, createdAtMs = 0L, updatedAtMs = 0L)
            }),
        )
        val state = mutableStateOf(root)
        val enabled = mutableStateOf(true)
        val controller = AppNavigationController()
        var handler: HomeBackToTopHandler? = null
        compose.setContent {
            CompositionLocalProvider(
                LocalAppNavigationController provides controller,
                LocalUiControlEffectsEnabled provides enabled.value,
                LocalUiLanguage provides ContentLanguage.English,
            ) {
                YummyDroidTheme {
                    DownloadsSection(
                        state = state.value, focusCurrentRequestNonce = 0L,
                        onClearHistory = {}, onCancelDownload = {}, onPauseDownload = {}, onResumeDownload = {},
                        onOpenAnime = {}, onRetry = {}, onRegisterBackToTopHandler = { handler = it },
                    )
                }
            }
        }
        val list = compose.onNode(hasScrollAction())
        compose.runOnIdle {
            assertNotNull(handler)
            assertFalse(handler!!.canHandleBackToTop())
            assertTrue(canExitOfflineDownloadsFromBack(root, handler!!.canHandleBackToTop()))
        }
        for (withFocus in listOf(false, true)) {
            list.performScrollToIndex(20)
            compose.runOnIdle {
                assertTrue(handler!!.canHandleBackToTop())
                assertFalse(canExitOfflineDownloadsFromBack(root, handler!!.canHandleBackToTop()))
                assertTrue(handler!!.handleBackToTop(withFocus))
            }
            compose.waitForIdle()
            compose.runOnIdle {
                assertFalse(handler!!.canHandleBackToTop())
                assertTrue(canExitOfflineDownloadsFromBack(root, handler!!.canHandleBackToTop()))
            }
        }
        // A small offset in the first item also needs a scroll, not an exit.
        list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 1f) }
        compose.runOnIdle {
            assertTrue(handler!!.canHandleBackToTop())
            assertTrue(handler!!.handleBackToTop(withFocus = false))
        }
        compose.waitForIdle()
        compose.runOnIdle { enabled.value = false }
        compose.runOnIdle { assertNull(handler) }
        compose.runOnIdle { enabled.value = true }
        compose.runOnIdle { assertNotNull(handler) }
        compose.runOnIdle { state.value = root.copy(downloadQueue = DownloadQueueSnapshot()) }
        compose.runOnIdle { assertNull(handler) }
    }
}
