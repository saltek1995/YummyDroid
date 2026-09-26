package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.LoadState
import me.yummydroid.app.YummyDroidUiState
import me.yummydroid.app.data.*
import me.yummydroid.app.ui.theme.YummyDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class DetailsDownloadsFocusTest {
    @get:Rule val compose = createComposeRule()

    @Test fun confirmedResetRestoresWatchAndDpadNavigation() = checkReset(hasVideo = true, confirm = true)
    @Test fun confirmedResetWithoutVideoRestoresRemainingHeroTarget() = checkReset(hasVideo = false, confirm = true)
    @Test fun cancelledResetKeepsResetActionFocused() = checkReset(hasVideo = true, confirm = false)

    private fun checkReset(hasVideo: Boolean, confirm: Boolean) {
        val progress = mutableStateOf(true)
        var resetCalls = 0
        var watchText = ""
        var resetText = ""
        var confirmText = ""
        var cancelText = ""
        compose.setContent {
            val mode = LocalInputModeManager.current
            LaunchedEffect(Unit) { mode.requestInputMode(InputMode.Keyboard) }
            CompositionLocalProvider(LocalAppNavigationController provides remember { AppNavigationController() }) {
                YummyDroidTheme {
                    watchText = uiText(UiStringKey.Watch5af041)
                    resetText = uiText(UiStringKey.ResetWatchProgress)
                    confirmText = uiText(UiStringKey.Reset)
                    cancelText = uiText(UiStringKey.Cancel)
                    val grid = rememberVisualFocusGridState(DETAILS_HERO_FOCUS_GRAPH_SIZE)
                    Column {
                        DetailsHeroActionPanel(
                            model = model(hasVideo, progress.value),
                            actions = actions { resetCalls++ },
                            heroFocusGridState = grid,
                        )
                        DialogActionButton(
                            text = "Poster", onClick = {},
                            modifier = Modifier.testTag("poster").visualFocusGridItem(
                                state = grid, index = DetailsHeroFocusIndex.Poster, vertical = true,
                            ),
                        )
                    }
                }
            }
        }
        val reset = compose.onNodeWithText(resetText)
        reset.performSemanticsAction(SemanticsActions.RequestFocus)
        reset.assertIsFocused().performKeyInput { keyDown(Key.Enter); keyUp(Key.Enter) }
        val dialogAction = compose.onNodeWithText(if (confirm) confirmText else cancelText)
        dialogAction.performSemanticsAction(SemanticsActions.RequestFocus)
        dialogAction.performKeyInput { keyDown(Key.Enter); keyUp(Key.Enter) }
        compose.runOnIdle { assertEquals(if (confirm) 1 else 0, resetCalls) }
        if (confirm) {
            // Repository progress emission occurs after modal dismissal, not in the click callback.
            compose.runOnIdle { progress.value = false }
            reset.assertDoesNotExist()
            val restored = if (hasVideo) compose.onNodeWithText(watchText) else compose.onNodeWithTag("poster")
            restored.assertIsFocused()
            if (hasVideo) {
                restored.performKeyInput { keyDown(Key.DirectionDown); keyUp(Key.DirectionDown) }
                compose.onNodeWithTag("poster").assertIsFocused()
            }
        } else {
            reset.assertIsFocused()
            reset.performKeyInput { keyDown(Key.DirectionLeft); keyUp(Key.DirectionLeft) }
            compose.onNodeWithText(watchText).assertIsFocused()
        }
    }

    @Test fun emptyDownloadsReceivesFocusAndDpadUpReturnsToNavigation() = checkDownloadsTabs(false)

    @Test fun emptyDownloadsReleasesInterruptedPagerTransition() = checkDownloadsTabs(true)

    private fun checkDownloadsTabs(interruptedTransition: Boolean) {
        var emptyText = ""
        var catalogText = ""
        var historyText = ""
        var selected: BrowseSection? = null
        val entries = mutableStateOf<LoadState<List<OfflineAnimeEntry>>>(LoadState.Loading)
        val nonce = mutableLongStateOf(1L)
        compose.setContent {
            val mode = LocalInputModeManager.current
            LaunchedEffect(Unit) { mode.requestInputMode(InputMode.Keyboard) }
            CompositionLocalProvider(LocalAppNavigationController provides remember { AppNavigationController() }) {
                YummyDroidTheme {
                    emptyText = uiText(UiStringKey.NoDownloadedEpisodesYet)
                    catalogText = BrowseSection.Catalog.localizedTitle()
                    historyText = BrowseSection.History.localizedTitle()
                    val sections = resolveBrowsePagerSections(isAuthorized = false, forcedOfflineMode = false)
                    val binding = rememberBrowseFocusBinding(sections)
                    val pager = rememberBrowsePagerRuntime(0, BrowseSection.Downloads) { sections.size }
                    LaunchedEffect(Unit) {
                        if (interruptedTransition) {
                            pager.transitionFocusSourcePage = 0
                            pager.programmaticScrollTarget = 1
                        }
                    }
                    val requestTabs = {
                        binding.runtime.requestSectionTabsFocus(
                            BrowseSection.Downloads, true, true, false,
                            binding.sectionFocusRequesters, pager,
                        )
                    }
                    Column {
                        DialogActionButton("Settings", {}, Modifier.testTag("settings").navigationKeyPolicy { event ->
                            event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown && requestTabs()
                        })
                        BrowseTvSectionIndicatorBar(
                            activeSection = BrowseSection.Downloads,
                            visibleSections = sections,
                            onSectionSelected = { selected = it },
                            sectionFocusRequesters = binding.sectionFocusRequesters,
                            sectionTabsFocusEnabled = pager.sectionTabsFocusEnabled,
                            onExitDown = { nonce.longValue++; true },
                            drawBackdrop = false,
                        )
                        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(300.dp)) {
                            DownloadsSection(
                                state = YummyDroidUiState(offlineEntries = entries.value),
                                focusCurrentRequestNonce = nonce.longValue,
                                onClearHistory = {}, onCancelDownload = {}, onPauseDownload = {},
                                onResumeDownload = {}, onOpenAnime = {}, onRetry = {},
                                onRequestSectionTabsFocus = requestTabs,
                            )
                        }
                    }
                }
            }
        }
        compose.runOnIdle { entries.value = LoadState.Ready(emptyList()) }
        val empty = compose.onNode(isFocused() and hasAnyDescendant(hasText(emptyText)))
        empty.assertExists().performKeyInput { keyDown(Key.DirectionUp); keyUp(Key.DirectionUp) }
        compose.onNodeWithText(catalogText).assertIsFocused()
        compose.onNodeWithText(catalogText).performKeyInput { keyDown(Key.DirectionRight); keyUp(Key.DirectionRight) }
        compose.onNodeWithText(historyText).assertIsFocused()
        compose.onNodeWithText(historyText).performKeyInput { keyDown(Key.Enter); keyUp(Key.Enter) }
        compose.runOnIdle { assertEquals(BrowseSection.History, selected) }
        compose.onNodeWithTag("settings").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("settings").performKeyInput { keyDown(Key.DirectionDown); keyUp(Key.DirectionDown) }
        compose.onNodeWithText(catalogText).assertIsFocused()
        compose.runOnIdle { nonce.longValue++ }
        empty.assertExists()
    }

    private fun actions(reset: () -> Unit) = DetailsHeroActions(
        onOpenLogin = {}, onGenreFilterSelected = { _, _ -> }, onYearFilterSelected = { _, _ -> },
        onStudioFilterSelected = { _, _ -> }, onCreatorFilterSelected = { _, _ -> },
        onSelectListMark = {}, onToggleFavorite = {}, onRetry = {}, onSetAnimeRating = {},
        onPlayVideo = {}, onPlayVideoAt = { _, _ -> }, onResolveSampledDownloadQualities = { _, _ -> emptyMap() },
        onDownloadAllVideos = {}, onRegisterModalInputActionHandler = {}, onResetWatchProgress = reset,
    )

    private fun model(hasVideo: Boolean, progress: Boolean) = DetailsHeroModel(
        details = AnimeDetails(
            id = 1, title = "Fixture", otherTitles = emptyList(), description = "", posterUrl = "",
            backdropUrl = null, year = null, rating = null, views = 0, status = "", type = "", minAge = "",
            genreTags = emptyList(), genres = emptyList(), episodeSummary = "", episodeAired = 1,
            episodeCount = 1, nextEpisodeText = "", durationSeconds = 0, ratingDetails = RatingDetails(),
            studios = emptyList(), creators = emptyList(), original = "", commentsCount = 0, listsCount = 0,
            translations = emptyList(), relatedAnime = emptyList(), screenshots = emptyList(), blockedIn = emptyList(),
        ),
        interactive = true, activeFocusRequestNonce = 0L, isWide = true,
        watchVideo = if (hasVideo) VideoVariant(
            id = 1, animeId = 1, player = "Fixture", dubbing = "Voice", episode = "1",
            url = "https://example.test/1", index = 1, durationSeconds = null, views = 0,
        ) else null,
        resumeTarget = null, downloadVideos = emptyList(), downloadedSummary = null, episodeSummary = "",
        apiEpisodeCount = 1, auth = AuthUiState(), animeMark = LoadState.Ready(null),
        detailsExtras = LoadState.Loading, showMarkPanel = false, showHeroRating = false,
        defaultDownloadQuality = PreferredQuality.Auto, canDownload = false, hasWatchProgress = progress,
        playbackHistoryLoading = false,
    )
}
