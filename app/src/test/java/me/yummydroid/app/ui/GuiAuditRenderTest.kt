package me.yummydroid.app.ui

import android.app.Application
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import me.yummydroid.app.ui.components.LocalBackdropBlurEnabled
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.unit.dp
import java.io.File
import me.yummydroid.app.AppRoute
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.DownloadQueueSnapshot
import me.yummydroid.app.DownloadTaskState
import me.yummydroid.app.DownloadTaskUi
import me.yummydroid.app.LoadState
import me.yummydroid.app.YummyDroidUiState
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.RatingDetails
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.ui.theme.YummyDroidTheme
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Offline visual audit only: no action can start an app runtime or provider request. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
class GuiAuditRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun phone_catalog_ready() = phone("phone-catalog-ready", catalog(), "Fixture Anime 1")
    @Test fun phone_catalog_error() = phone("phone-catalog-error", catalog().copy(featured = LoadState.Error("Offline fixture error")), "Offline fixture error")
    @Test fun phone_catalog_empty() = phone("phone-catalog-empty", catalog().copy(featured = LoadState.Ready(emptyList())))
    @Test fun phone_history_empty() = phone("phone-history-empty", catalog().copy(homeSection = BrowseSection.History))
    @Test fun phone_downloads_queue() = phone("phone-downloads-queue", catalog().copy(
        homeSection = BrowseSection.Downloads, offlineEntries = LoadState.Ready(emptyList()),
        downloadQueue = DownloadQueueSnapshot(listOf(downloadTask())),
    ), "Episode 1")
    @Test fun phone_details_large_title() = phone("phone-details-1181-variants", catalog().copy(
        route = AppRoute.Details(1), details = LoadState.Ready(details()),
        videos = LoadState.Ready((1..1181).map(::video)), selectedVideoGroup = video(1).groupKey,
    ), "Fixture Anime")
    @Test fun phone_settings() = dialog(DialogScene.Settings)
    @Test fun phone_search() = dialog(DialogScene.Search)
    @Test fun phone_next_title() = dialog(DialogScene.NextTitle)

    @Test fun phone_episode_grid_large_title() {
        val videos = (1..1181).map(::video)
        compose.setContent {
            CompositionLocalProvider(LocalAppNavigationController provides remember { AppNavigationController() }) {
                YummyDroidTheme {
                    androidx.compose.material3.Surface(Modifier.requiredSize(360.dp, 800.dp)) {
                        androidx.compose.foundation.layout.Column {
                            EpisodeGrid(videos, videos, emptyMap(), emptyList(), "audit", false,
                                {}, { _, _ -> }, null, 0, null)
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        capture("phone-episode-grid-1181", 360, 800, setOf("1181"))
    }

    @Test @Config(qualifiers = "w960dp-h540dp-mdpi")
    fun tv_downloads_empty() = television("tv-downloads-empty", catalog().copy(
        homeSection = BrowseSection.Downloads, offlineEntries = LoadState.Ready(emptyList()),
    ))
    @Test @Config(qualifiers = "w960dp-h540dp-mdpi")
    fun tv_player_loading() = television("tv-player-loading", playerState(LoadState.Loading))
    @Test @Config(qualifiers = "w960dp-h540dp-mdpi")
    fun tv_player_error() = television("tv-player-error", playerState(LoadState.Error("Offline stream unavailable")), "Offline stream unavailable")

    private fun catalog() = YummyDroidUiState(
        featured = LoadState.Ready(listOf(anime(1), anime(2))), searchResults = LoadState.Ready(emptyList()),
        filterCatalog = LoadState.Ready(me.yummydroid.app.data.FilterCatalog()),
    )
    private fun playerState(stream: LoadState<me.yummydroid.app.data.ResolvedVideoStream>) = catalog().copy(
        route = AppRoute.Player(video(1), "Fixture Anime"), playerStream = stream,
        details = LoadState.Ready(details()), videos = LoadState.Ready((1..8).map(::video)), selectedVideoGroup = video(1).groupKey,
    )
    private fun phone(name: String, state: YummyDroidUiState, expected: String? = null) =
        auditScenes(listOf(Scene(name, state, 360, 800, setOfNotNull(expected))))
    private fun television(name: String, state: YummyDroidUiState, expected: String? = null) =
        auditScenes(listOf(Scene(name, state, 960, 540, setOfNotNull(expected))))

    private fun dialog(scene: DialogScene) {
        compose.activity.window.decorView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        compose.setContent {
            CompositionLocalProvider(LocalAppNavigationController provides remember { AppNavigationController() }) {
                YummyDroidTheme {
                    androidx.compose.material3.Surface(
                        modifier = Modifier.requiredSize(360.dp, 800.dp),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.background,
                        contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                    ) {
                        when (scene) {
                            DialogScene.Settings -> SettingsDialog(
                                me.yummydroid.app.data.AppSettings(), LoadState.Ready(emptyList()), 42_000_000L,
                                LoadState.Ready(null), {}, { _, _, _ -> }, {}, {}, {}, {}, {}, {},
                            )
                            DialogScene.Search -> SearchDialog(query = "Fixture search", onQueryChange = {}, onDismiss = {})
                            DialogScene.NextTitle -> PlayerNextTitleDialog(me.yummydroid.app.NextTitleSuggestion(1, 1, 2, "Next fixture"), {}, {})
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        capture("phone-dialog-${scene.name.lowercase()}", 360, 800, setOf(scene.fixtureText))
    }

    private fun auditScenes(scenes: List<Scene>) {
        val scene = mutableStateOf(scenes.first())
        compose.mainClock.autoAdvance = true
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenWidthDp = scene.value.width
                screenHeightDp = scene.value.height
            }
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalBackdropBlurEnabled provides false) {
                YummyDroidTheme {
                    androidx.compose.material3.Surface(
                        modifier = Modifier.requiredSize(scene.value.width.dp, scene.value.height.dp),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.background,
                        contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                    ) {
                        YummyDroidApp(scene.value.state, false, false, 0, noOpActions())
                    }
                }
            }
        }
        scenes.forEach { next ->
            compose.runOnIdle { scene.value = next }
            compose.mainClock.advanceTimeBy(600)
            compose.waitForIdle()
            capture(next.name, next.width, next.height, next.expectedText)
        }
    }

    private fun capture(name: String, width: Int, height: Int, expectedText: Set<String> = emptySet()) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(48))
        compose.waitForIdle()
        val roots = compose.onAllNodes(isRoot()).fetchSemanticsNodes()
        assertTrue("$name must have a rendered semantics root", roots.isNotEmpty())
        val semanticsRoot = roots.last()
        assertTrue("$name root must stay within the requested viewport", semanticsRoot.boundsInRoot.left >= 0f &&
            semanticsRoot.boundsInRoot.top >= 0f && semanticsRoot.boundsInRoot.right <= width.toFloat() &&
                semanticsRoot.boundsInRoot.bottom <= height.toFloat())
        val semantics = roots.joinToString("\n--- WINDOW ---\n") { semanticsDump(it) }
        File("build/reports/gui-audit").mkdirs()
        File("build/reports/gui-audit/$name.txt").writeText(semantics)
        expectedText.forEach { text ->
            assertTrue("$name semantics must include fixture text '$text'", semantics.contains(text))
        }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(48))
        val image = compose.runOnIdle {
            val content = android.view.inspector.WindowInspector.getGlobalWindowViews()
                .lastOrNull { it.isShown && it.width > 0 && it.height > 0 }
                ?: compose.activity.findViewById<View>(android.R.id.content)
            Bitmap.createBitmap(content.width, content.height, Bitmap.Config.ARGB_8888).also { bitmap ->
                content.draw(Canvas(bitmap))
            }
        }
        assertTrue("$name window must fit the mdpi viewport", image.width in 1..width && image.height in 1..height)
        assertTrue("$name must paint a non-blank frame", (0 until image.width step 8).any { x ->
            (0 until image.height step 8).any { y -> image.getPixel(x, y) != 0 }
        })
        val output = File("build/reports/gui-audit").apply { mkdirs() }
        File(output, "$name.txt").writeText(semantics)
        File(output, "$name.png").outputStream().use { stream ->
            assertTrue("$name screenshot must be written", image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream))
        }
    }

    private fun semanticsDump(node: SemanticsNode, depth: Int = 0): String = buildString {
        append("  ".repeat(depth)).append(node.config).append(" bounds=").append(node.boundsInRoot).appendLine()
        node.children.forEach { child -> append(semanticsDump(child, depth + 1)) }
    }

    private data class Scene(
        val name: String,
        val state: YummyDroidUiState,
        val width: Int,
        val height: Int,
        val expectedText: Set<String> = emptySet(),
    )

    private enum class DialogScene(val fixtureText: String) {
        Settings("42"),
        Search("Fixture search"),
        NextTitle("Next fixture"),
    }

    private fun anime(id: Long) = Anime(
        id = id, title = "Fixture Anime $id", description = "Offline render fixture", posterUrl = "", animeUrl = "",
        year = 2025, rating = 8.2, views = 42, status = "ongoing", type = "TV", genres = listOf("Action"), blockedIn = emptyList(),
        episodeAired = 3, episodeCount = 12,
    )

    private fun details() = AnimeDetails(
        id = 1, title = "Fixture Anime", otherTitles = emptyList(), description = "Offline render fixture", posterUrl = "",
        backdropUrl = null, year = 2025, rating = 8.2, views = 42, status = "ongoing", type = "TV", minAge = "16+",
        genreTags = emptyList(), genres = listOf("Action"), episodeSummary = "3 / 12", episodeAired = 3, episodeCount = 12,
        nextEpisodeText = "", durationSeconds = 1_400, ratingDetails = RatingDetails(), studios = emptyList(), creators = emptyList(),
        original = "", commentsCount = 0, listsCount = 0, translations = emptyList(), relatedAnime = emptyList(), screenshots = emptyList(), blockedIn = emptyList(),
    )

    private fun video(id: Int) = VideoVariant(
        id = id.toLong(), animeId = 1, player = "Fixture Player", dubbing = "Fixture Voice", episode = id.toString(),
        url = "", index = id, durationSeconds = 1_400, views = 0,
    )

    private fun downloadTask() = DownloadTaskUi(
        id = 1, animeId = 1, videoId = 1, title = "Fixture Anime", episodeTitle = "Episode 1", progress = .42f,
        state = DownloadTaskState.Running,
    )

    private fun noOpActions() = YummyDroidAppActions(
        onQueryChange = {}, onSearchSubmitted = {}, onSearchHistorySelected = {}, onRefresh = {}, onRefreshFilterCatalog = {}, onLoadMoreAnime = {},
        onBrowseSectionChange = {}, onFiltersChange = {}, onResetFilters = {}, onSettingsChange = {}, onOpenAnime = {}, onOpenAnimeTarget = {},
        onFilterByGenre = { _, _ -> }, onFilterByYear = { _, _ -> }, onFilterByStudio = { _, _ -> }, onFilterByCreator = { _, _ -> },
        onSelectVideoGroup = {}, onPlayVideo = {}, onPlayVideoWithResumeChoice = { _, _ -> }, onPlayVideoAt = { _, _ -> },
        onPlayVideoAtQuality = { _, _, _ -> }, onSelectPlaybackSource = { _, _ -> }, onChoosePlayerResumePosition = {}, onRetryVideo = {},
        onPlaybackFailed = { _, _, _ -> }, onPlaybackStarted = {}, onPlaybackEnded = {}, onPlaybackProgress = { _, _, _ -> },
        onResetAnimeWatchProgress = {}, onEnterPictureInPicture = {}, onLogin = { _, _, _ -> }, onCaptchaSolved = {}, onCaptchaCanceled = {},
        onLogout = {}, onConfirmLocalWatchHistoryMerge = {}, onDismissLocalWatchHistoryMerge = {}, onOpenLibraryFilter = {}, onSelectAnimeListMark = {},
        onToggleFavorite = {}, onSetAnimeRating = {}, onAddAnimeComment = {}, onLoadMoreAnimeComments = {}, onToggleVideoSubscription = {},
        onTogglePlayerVideoSubscription = {}, onUnsubscribeVideoSubscription = {}, onRefreshVideoSubscriptions = {}, onRefreshProfileNotifications = {},
        onMarkProfileNotificationRead = {}, onMarkAllProfileNotificationsRead = {}, onDeleteProfileNotification = {},
        onResolveSampledDownloadQualities = { _, _ -> emptyMap() }, onDownloadAllVideos = {}, onDeleteOfflineVideo = { _, _, _ -> },
        onDeleteOfflineAnime = {}, onClearAppContentCache = {}, onRefreshAppContentCacheSize = {}, onRefreshOfflineDownloads = {},
        onClearDownloadHistory = {}, onCancelDownload = {}, onPauseDownload = {}, onResumeDownload = {}, onCheckForUpdates = {},
        onConsumePlayerNotice = {}, onBack = {}, onExitApp = {}, onProfileNotificationsRequestConsumed = {}, registerInputActionHandler = {},
    )
}
