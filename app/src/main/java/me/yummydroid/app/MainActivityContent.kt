package me.yummydroid.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.ui.YummyDroidApp
import me.yummydroid.app.ui.YummyDroidAppActions
import me.yummydroid.app.ui.theme.YummyDroidTheme

@Composable
internal fun MainActivityContent(
    initialRequest: MainActivityRequest,
    systemSearchQuery: String?,
    profileNotificationsOpenRequest: Long,
    isInPictureInPicture: Boolean,
    canUsePictureInPicture: Boolean,
    onViewModelAvailable: (YummyDroidViewModel) -> Unit,
    onSystemSearchConsumed: () -> Unit,
    onPlayerRouteChanged: (Boolean) -> Unit,
    onSettingsChange: (AppSettings) -> Unit,
    onEnterPictureInPicture: () -> Unit,
    onExitApp: () -> Unit,
    onProfileNotificationsRequestConsumed: () -> Unit,
    registerInputActionHandler: (((InputActionEvent) -> Boolean)?) -> Unit,
) {
    val context = LocalContext.current
    val viewModel: YummyDroidViewModel = viewModel()
    onViewModelAvailable(viewModel)
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(
        initialRequest.animeId,
        initialRequest.video,
        profileNotificationsOpenRequest,
    ) {
        when {
            profileNotificationsOpenRequest > 0L -> Unit
            initialRequest.video != null -> {
                viewModel.playVideo(initialRequest.video, initialRequest.animeTitle)
            }
            initialRequest.animeId > 0L -> viewModel.openAnime(initialRequest.animeId)
        }
    }

    LaunchedEffect(systemSearchQuery) {
        val query = systemSearchQuery?.trim().orEmpty()
        if (query.isNotBlank()) {
            viewModel.selectBrowseSection(BrowseSection.Catalog)
            viewModel.updateSearchQuery(query)
            onSystemSearchConsumed()
        }
    }

    LaunchedEffect(state.route) {
        onPlayerRouteChanged(state.route is AppRoute.Player)
    }

    LaunchedEffect(state.auth.profile?.id, state.settings.notificationsEnabled) {
        SubscriptionNotificationScheduler.configureAsync(
            context = context,
            enabled = state.settings.notificationsEnabled && state.auth.profile != null,
        )
    }

    val appActions = remember(viewModel) {
        createMainActivityAppActions(
            viewModel = viewModel,
            onSettingsChange = onSettingsChange,
            onEnterPictureInPicture = onEnterPictureInPicture,
            onExitApp = onExitApp,
            onProfileNotificationsRequestConsumed = onProfileNotificationsRequestConsumed,
            registerInputActionHandler = registerInputActionHandler,
        )
    }

    YummyDroidTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            YummyDroidApp(
                state = state,
                isInPictureInPicture = isInPictureInPicture,
                canUsePictureInPicture = canUsePictureInPicture,
                openProfileNotificationsRequest = profileNotificationsOpenRequest,
                actions = appActions,
            )
        }
    }
}

private fun createMainActivityAppActions(
    viewModel: YummyDroidViewModel,
    onSettingsChange: (AppSettings) -> Unit,
    onEnterPictureInPicture: () -> Unit,
    onExitApp: () -> Unit,
    onProfileNotificationsRequestConsumed: () -> Unit,
    registerInputActionHandler: (((InputActionEvent) -> Boolean)?) -> Unit,
): YummyDroidAppActions {
    return YummyDroidAppActions(
        onQueryChange = viewModel::updateSearchQuery,
        onSearchSubmitted = viewModel::submitSearchQuery,
        onSearchHistorySelected = viewModel::selectSearchHistoryQuery,
        onRefresh = viewModel::refresh,
        onLoadMoreAnime = viewModel::loadMoreAnime,
        onBrowseSectionChange = viewModel::selectBrowseSection,
        onFiltersChange = viewModel::updateFilters,
        onResetFilters = viewModel::resetFilters,
        onSettingsChange = onSettingsChange,
        onOpenAnime = viewModel::openAnime,
        onFilterByGenre = viewModel::filterByGenre,
        onFilterByYear = viewModel::filterByYear,
        onFilterByStudio = viewModel::filterByStudio,
        onFilterByCreator = viewModel::filterByCreator,
        onSelectVideoGroup = viewModel::selectVideoGroup,
        onPlayVideo = viewModel::playVideo,
        onPlayVideoWithResumeChoice = viewModel::playVideoWithResumeChoice,
        onPlayVideoAt = viewModel::playVideoAt,
        onPlayVideoAtQuality = viewModel::playVideoAtQuality,
        onSelectPlaybackSource = viewModel::selectPlaybackSource,
        onChoosePlayerResumePosition = viewModel::choosePlayerResumePosition,
        onRetryVideo = viewModel::retryVideo,
        onPlaybackFailed = viewModel::fallbackPlaybackSource,
        onPlaybackStarted = viewModel::confirmPlaybackSource,
        onPlaybackEnded = viewModel::handlePlaybackEnded,
        onPlaybackProgress = viewModel::savePlaybackProgress,
        onResetAnimeWatchProgress = viewModel::resetAnimeWatchProgress,
        onEnterPictureInPicture = onEnterPictureInPicture,
        onLogin = viewModel::login,
        onCaptchaSolved = viewModel::submitCaptchaResponse,
        onCaptchaCanceled = viewModel::cancelCaptchaChallenge,
        onLogout = viewModel::logout,
        onOpenLibraryFilter = viewModel::openLibraryFilter,
        onSelectAnimeListMark = viewModel::selectAnimeListMark,
        onToggleFavorite = viewModel::toggleFavorite,
        onSetAnimeRating = viewModel::setAnimeRating,
        onAddAnimeComment = viewModel::addAnimeComment,
        onLoadMoreAnimeComments = viewModel::loadMoreAnimeComments,
        onToggleVideoSubscription = viewModel::toggleVideoSubscription,
        onTogglePlayerVideoSubscription = viewModel::togglePlayerVideoSubscription,
        onUnsubscribeVideoSubscription = viewModel::unsubscribeVideoSubscription,
        onRefreshVideoSubscriptions = viewModel::refreshVideoSubscriptions,
        onRefreshProfileNotifications = viewModel::refreshProfileNotifications,
        onMarkProfileNotificationRead = viewModel::markProfileNotificationRead,
        onMarkAllProfileNotificationsRead = viewModel::markAllProfileNotificationsRead,
        onDeleteProfileNotification = viewModel::deleteProfileNotification,
        onResolveSampledDownloadQualities = viewModel::resolveSampledDownloadQualities,
        onDownloadAllVideos = viewModel::downloadAllVideosForOffline,
        onDeleteOfflineVideo = viewModel::deleteOfflineVideo,
        onDeleteOfflineAnime = viewModel::deleteOfflineAnime,
        onClearAppContentCache = viewModel::clearAppContentCache,
        onRefreshAppContentCacheSize = viewModel::refreshAppContentCacheSize,
        onClearDownloadHistory = viewModel::clearDownloadHistory,
        onCancelDownload = viewModel::cancelDownload,
        onPauseDownload = viewModel::pauseDownload,
        onResumeDownload = viewModel::resumeDownload,
        onCheckForUpdates = viewModel::checkForUpdates,
        onConsumePlayerNotice = viewModel::consumePlayerNotice,
        onBack = viewModel::navigateBack,
        onExitApp = onExitApp,
        onProfileNotificationsRequestConsumed = onProfileNotificationsRequestConsumed,
        registerInputActionHandler = registerInputActionHandler,
    )
}
