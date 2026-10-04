package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.yummydroid.app.ui.theme.YummyDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the production episode-grid effects without providers or media playback. */
@RunWith(AndroidJUnit4::class)
class EpisodePagerGestureTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var pager: PagerState

    private fun showPager() {
        compose.setContent {
            CompositionLocalProvider(LocalAppNavigationController provides remember { AppNavigationController() }) {
                YummyDroidTheme {
                    var listKey by remember { mutableIntStateOf(0) }
                    var requestedPage by remember(listKey) { mutableIntStateOf(0) }
                    var pageRequest by remember(listKey) { mutableStateOf<EpisodePageRequest?>(EpisodePageRequest(0)) }
                    var width by remember { mutableStateOf(400.dp) }
                    val layout = episodeGridLayout(width, 40, requestedPage)
                    pager = rememberPagerState { layout.pageCount }
                    EpisodeGridEffects(
                        requestedPage = requestedPage,
                        pageRequest = pageRequest,
                        layout = layout,
                        pagerState = pager,
                        pendingFocusSlot = null,
                        visibleItemCount = layout.pageEnd - layout.pageStart,
                        navigator = EpisodeGridNavigator(
                            layout, 40, layout.pageEnd - layout.pageStart,
                            null, 0, { false }, { _, _ -> false },
                        ),
                        onRequestedPageChange = { requestedPage = it },
                        onPageRequestHandled = { if (pageRequest === it) pageRequest = null },
                        onPagerSettled = { requestedPage = it },
                        onPendingFocusHandled = {},
                    )
                    Column {
                        HorizontalPager(
                            state = pager,
                            modifier = Modifier.fillMaxWidth().height(240.dp).testTag("episodes"),
                        ) { Text("Page $it") }
                        Button(onClick = { requestedPage++; pageRequest = EpisodePageRequest(requestedPage) }, modifier = Modifier.testTag("next")) {
                            Text("Next")
                        }
                        Button(onClick = { requestedPage--; pageRequest = EpisodePageRequest(requestedPage) }, modifier = Modifier.testTag("previous")) {
                            Text("Previous")
                        }
                        Button(onClick = { listKey++ }, modifier = Modifier.testTag("reset")) {
                            Text("Reset list")
                        }
                        Button(onClick = { width = 1200.dp }, modifier = Modifier.testTag("resize")) {
                            Text("Resize")
                        }
                        Text("Selected $requestedPage", modifier = Modifier.testTag("selected"))
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    @Test fun reversingHeldSwipeKeepsOriginalPage() {
        showPager()
        val grid = compose.onNodeWithTag("episodes")
        grid.performTouchInput {
            down(Offset(width * .85f, centerY))
            moveTo(Offset(width * .2f, centerY), 300)
        }
        compose.mainClock.advanceTimeBy(64)
        grid.performTouchInput {
            moveTo(Offset(width * .85f, centerY), 500)
            advanceEventTime(200)
            up()
        }
        compose.mainClock.advanceTimeBy(1500)
        assertAtPage(0)
    }

    @Test fun grabbingSettlingSwipeAllowsReturningToOriginalPage() {
        showPager()
        val grid = compose.onNodeWithTag("episodes")
        grid.performTouchInput {
            swipe(Offset(width * .9f, centerY), Offset(width * .25f, centerY), 400)
        }
        compose.mainClock.advanceTimeBy(32)
        grid.performTouchInput { down(Offset(width * .15f, centerY)) }
        compose.mainClock.advanceTimeBy(32)
        var grabbedPosition = 0f
        compose.runOnUiThread { grabbedPosition = pager.currentPage + pager.currentPageOffsetFraction }
        compose.mainClock.advanceTimeBy(240)
        compose.runOnUiThread {
            val position = pager.currentPage + pager.currentPageOffsetFraction
            assertEquals("Page must stay under the held finger", grabbedPosition, position, .02f)
        }
        // Release in this batch: waiting for Espresso idle with overscroll held
        // would wait for an effect that cannot finish until the finger is lifted.
        grid.performTouchInput {
            moveTo(Offset(width * .9f, centerY), 500)
            advanceEventTime(200)
            up()
        }
        compose.mainClock.advanceTimeBy(1500)
        assertAtPage(0)
    }

    @Test fun nextButtonStillCompletesPageTransition() {
        showPager()
        compose.onNodeWithTag("next").performClick()
        compose.mainClock.advanceTimeBy(1500)
        compose.runOnIdle {
            assertEquals(1, pager.settledPage)
            assertTrue(!pager.isScrollInProgress)
        }
    }

    @Test fun immediatePreviousReversesButtonAnimation() {
        showPager()
        compose.onNodeWithTag("next").performClick()
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("previous").performClick()
        compose.mainClock.advanceTimeBy(1500)
        assertAtPage(0)
        compose.onNodeWithTag("episodes").performTouchInput { swipeLeft() }
        compose.mainClock.advanceTimeBy(1500)
        assertAtPage(1)
    }

    @Test fun resettingListOnLaterPageAndDuringAnimationReturnsToStart() {
        showPager()
        compose.onNodeWithTag("next").performClick()
        compose.mainClock.advanceTimeBy(1500)
        assertAtPage(1)
        compose.onNodeWithTag("reset").performClick()
        compose.mainClock.advanceTimeBy(1500)
        assertAtPage(0)
        compose.onNodeWithTag("next").performClick()
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("reset").performClick()
        compose.mainClock.advanceTimeBy(1500)
        assertAtPage(0)
        compose.onNodeWithTag("episodes").performTouchInput { swipeLeft() }
        compose.mainClock.advanceTimeBy(1500)
        assertAtPage(1)
    }

    @Test fun touchCanCancelButtonAnimationAndReturnToStart() {
        showPager()
        compose.onNodeWithTag("next").performClick()
        compose.mainClock.advanceTimeBy(64)
        val grid = compose.onNodeWithTag("episodes")
        grid.performTouchInput { down(Offset(width * .1f, centerY)) }
        compose.mainClock.advanceTimeBy(32)
        grid.performTouchInput {
            moveTo(Offset(width * .9f, centerY), 500)
            advanceEventTime(200)
            up()
        }
        compose.mainClock.advanceTimeBy(1500)
        assertAtPage(0)
    }

    @Test fun resizeDuringAnimationKeepsPageCenteredAndInRange() {
        showPager()
        repeat(3) {
            compose.onNodeWithTag("next").performClick()
            compose.mainClock.advanceTimeBy(1500)
        }
        compose.onNodeWithTag("next").performClick()
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("resize").performClick()
        compose.mainClock.advanceTimeBy(1500)
        compose.runOnIdle {
            assertTrue(pager.settledPage in 0 until pager.pageCount)
            assertEquals(0f, pager.currentPageOffsetFraction, .001f)
        }
    }

    private fun assertAtPage(page: Int) {
        compose.runOnIdle {
            assertEquals(page, pager.settledPage)
            assertEquals(0f, pager.currentPageOffsetFraction, .001f)
        }
        compose.onNodeWithTag("selected").assertTextEquals("Selected $page")
    }
}
