package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.LoadState
import me.yummydroid.app.PagingUiState
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.PosterCardSize
import me.yummydroid.app.ui.theme.YummyDroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowseSearchFocusTest {
    @get:Rule val compose = createComposeRule()

    @Test fun searchKeepsFocusAcrossCurrentFocusRestoration() = checkFocus(firstRequest = false)
    @Test fun searchKeepsFocusAcrossFirstFocusRestoration() = checkFocus(firstRequest = true)

    private fun checkFocus(firstRequest: Boolean) {
        val results = mutableStateOf<LoadState<List<Anime>>>(LoadState.Ready(listOf(anime(1))))
        val open = mutableStateOf(true)
        val query = mutableStateOf("")
        val firstNonce = mutableLongStateOf(if (firstRequest) 1L else 0L)
        val navigation = AppNavigationController()
        compose.setContent {
            val mode = LocalInputModeManager.current
            val input = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                mode.requestInputMode(InputMode.Keyboard)
                input.requestFocus()
            }
            CompositionLocalProvider(LocalAppNavigationController provides navigation) {
                YummyDroidTheme {
                    Box {
                        BrowseBackgroundFocusBoundary(dialogOpen = open.value) {
                            AnimeGridSection(
                                contentState = results.value,
                                pagingState = PagingUiState(canLoadMore = false),
                                gridState = rememberLazyGridState(),
                                cardSize = PosterCardSize.Standard,
                                focusFirstRequest = FocusFirstRequest(persistentNonce = firstNonce.longValue),
                                focusCurrentRequestNonce = 1L,
                                contentFocusEnabled = !open.value,
                                currentFocusedIndex = { 0 }, onFocusedIndexChange = {},
                                backToTopSection = BrowseSection.Catalog,
                                emptyMessage = "Empty", onRetry = {}, onLoadMore = {}, onOpenAnime = {},
                            )
                        }
                        if (open.value) BasicTextField(
                            value = query.value,
                            onValueChange = { query.value = it; results.value = LoadState.Loading },
                            modifier = Modifier.focusRequester(input).testTag("search-input"),
                        )
                    }
                }
            }
        }
        val field = compose.onNodeWithTag("search-input")
        field.assertIsFocused()
        repeat(3) { index ->
            field.performTextInput("a")
            field.assertIsFocused()
            compose.runOnIdle { results.value = LoadState.Ready(listOf(anime(index + 2L))) }
            field.assertIsFocused()
        }
        compose.runOnIdle { results.value = LoadState.Error("Search failed") }
        field.assertIsFocused()
        field.performTextInput("b")
        compose.runOnIdle { results.value = LoadState.Ready(listOf(anime(10))) }
        field.assertIsFocused()
        field.assertTextEquals("aaab")
        // Down exits the overlay and issues a nonce, which must run after reactivation.
        compose.runOnIdle { open.value = false; firstNonce.longValue += 1 }
        val focusedResult = (hasText("Result 10") or hasAnyDescendant(hasText("Result 10"))) and isFocused()
        compose.waitUntil(5_000) { compose.onAllNodes(focusedResult).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(focusedResult).assertExists()
    }

    private fun anime(id: Long) = Anime(
        id = id, title = "Result $id", description = "", posterUrl = "", animeUrl = "",
        year = 2026, rating = 8.0, views = 0, status = "", type = "", genres = emptyList(), blockedIn = emptyList(),
    )
}
