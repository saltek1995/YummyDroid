package me.yummydroid.app.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.yummydroid.app.AnimeDetailsExtras
import me.yummydroid.app.LoadState
import me.yummydroid.app.YummyDroidUiState
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.RatingDetails
import me.yummydroid.app.ui.theme.YummyDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DetailsRecommendationsFocusTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun backThroughLoadingRestoresScrolledRecommendationAndDpadNavigation() {
        val firstScreen = DetailsScreenUiState()
        val secondScreen = DetailsScreenUiState()
        val recommendations = (1..20).map { recommendation(it) }
        val ready = YummyDroidUiState(
            details = LoadState.Ready(details(1)),
            detailsExtras = LoadState.Ready(AnimeDetailsExtras(recommendations = recommendations)),
            videos = LoadState.Ready(emptyList()),
        )
        val current = mutableStateOf(ready)
        val screen = mutableStateOf(firstScreen)
        val restoreNonce = mutableLongStateOf(0L)
        lateinit var scope: CoroutineScope
        var openedAnimeId: Long? = null
        compose.setContent {
            val inputMode = LocalInputModeManager.current
            LaunchedEffect(Unit) { inputMode.requestInputMode(InputMode.Keyboard) }
            scope = rememberCoroutineScope()
            CompositionLocalProvider(LocalAppNavigationController provides remember { AppNavigationController() }) {
                YummyDroidTheme {
                    DetailsScreenModern(
                        state = current.value,
                        screenUiState = screen.value,
                        interactive = true,
                        activeFocusRequestNonce = 0L,
                        retainedFocusRequestNonce = restoreNonce.longValue,
                        onRefresh = {},
                        onOpenAnime = { id ->
                            openedAnimeId = id
                            screen.value = secondScreen
                            current.value = ready.copy(
                                details = LoadState.Ready(details(id)),
                                detailsExtras = LoadState.Ready(AnimeDetailsExtras()),
                            )
                        },
                        onOpenLogin = {},
                        onGenreFilterSelected = { _, _ -> },
                        onYearFilterSelected = { _, _ -> },
                        onStudioFilterSelected = { _, _ -> },
                        onCreatorFilterSelected = { _, _ -> },
                        onSelectVideoGroup = {},
                        onPlayVideo = {},
                        onPlayVideoWithResumeChoice = { _, _ -> },
                        onPlayVideoAt = { _, _ -> },
                        onSelectAnimeListMark = {},
                        onToggleFavorite = {},
                        onSetAnimeRating = {},
                        onAddAnimeComment = {},
                        onLoadMoreAnimeComments = {},
                        onToggleVideoSubscription = {},
                        onResolveSampledDownloadQualities = { _, _ -> emptyMap() },
                        onDownloadAllVideos = {},
                        onResetAnimeWatchProgress = {},
                        onRegisterModalInputActionHandler = {},
                    )
                }
            }
        }

        compose.runOnIdle {
            firstScreen.recommendationsScrollState.requestScrollToItem(10, 31)
            scope.launch { firstScreen.scrollState.scrollTo(firstScreen.scrollState.maxValue) }
        }
        val selected = compose.onNodeWithText("Recommendation 11")
        selected.performSemanticsAction(SemanticsActions.RequestFocus)
        selected.assertIsDisplayed().assertIsFocused()
        var savedIndex = 0
        var savedOffset = 0
        compose.runOnIdle {
            savedIndex = firstScreen.recommendationsScrollState.firstVisibleItemIndex
            savedOffset = firstScreen.recommendationsScrollState.firstVisibleItemScrollOffset
            assertTrue(savedIndex > 0)
        }
        selected.performKeyInput { keyDown(Key.Enter); keyUp(Key.Enter) }
        compose.runOnIdle {
            assertEquals(11L, openedAnimeId)
            assertEquals(0, secondScreen.recommendationsScrollState.firstVisibleItemIndex)
            assertEquals(detailsAnimeRowFocusKey(DetailsFocusBlockKey.Recommendations, 11L), firstScreen.retainedFocusKey)
            screen.value = firstScreen
            current.value = ready.copy(details = LoadState.Loading, detailsExtras = LoadState.Loading)
        }
        // A complete Loading frame removes the row and its focus targets from composition.
        compose.waitForIdle()
        compose.runOnIdle {
            current.value = ready
            restoreNonce.longValue++
        }
        selected.assertIsDisplayed().assertIsFocused()
        compose.runOnIdle {
            assertEquals(savedIndex, firstScreen.recommendationsScrollState.firstVisibleItemIndex)
            assertEquals(savedOffset, firstScreen.recommendationsScrollState.firstVisibleItemScrollOffset)
        }
        selected.performKeyInput { keyDown(Key.DirectionRight); keyUp(Key.DirectionRight) }
        compose.onNodeWithText("Recommendation 12").assertIsDisplayed().assertIsFocused()
        compose.onNodeWithText("Recommendation 12").performKeyInput {
            keyDown(Key.DirectionLeft); keyUp(Key.DirectionLeft)
        }
        selected.assertIsDisplayed().assertIsFocused()
    }

    private fun recommendation(id: Int) = Anime(
        id = id.toLong(), title = "Recommendation $id", description = "", posterUrl = "",
        animeUrl = "", year = null, rating = null, views = 0, status = "", type = "",
        genres = emptyList(), blockedIn = emptyList(),
    )

    private fun details(id: Long) = AnimeDetails(
        id = id, title = "Anime $id", otherTitles = emptyList(), description = "", posterUrl = "",
        backdropUrl = null, year = null, rating = null, views = 0, status = "", type = "", minAge = "",
        genreTags = emptyList(), genres = emptyList(), episodeSummary = "", episodeAired = 0,
        episodeCount = 0, nextEpisodeText = "", durationSeconds = 0, ratingDetails = RatingDetails(),
        studios = emptyList(), creators = emptyList(), original = "", commentsCount = 0, listsCount = 0,
        translations = emptyList(), relatedAnime = emptyList(), screenshots = emptyList(), blockedIn = emptyList(),
    )
}
