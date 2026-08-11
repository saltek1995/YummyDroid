package me.yummydroid.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.yummydroid.app.LoadState
import me.yummydroid.app.data.canShowVideoSubscriptions

@Composable
internal fun DetailsContentSections(
    model: DetailsContentModel,
    actions: DetailsContentActions,
    presentation: DetailsContentPresentation,
    focusGridState: VisualFocusGridState,
) {
    DetailsOverviewSections(model, actions, presentation, focusGridState)
    DetailsVideoSection(model, actions, presentation, focusGridState)
    if (!model.forcedOfflineMode) {
        DetailsOnlineSections(model, actions, presentation, focusGridState)
    }
}

@Composable
private fun DetailsOverviewSections(
    model: DetailsContentModel,
    actions: DetailsContentActions,
    presentation: DetailsContentPresentation,
    focusGridState: VisualFocusGridState,
) {
    val details = model.details
    DetailsHeroModern(
        details = details,
        activeFocusRequestNonce = model.activeFocusRequestNonce,
        isWide = presentation.isWide,
        watchVideo = presentation.watchVideo,
        resumeTarget = presentation.resumeTarget,
        downloadVideos = presentation.playableVideos,
        downloadedSummary = presentation.downloadedSummary,
        episodeSummary = presentation.episodeSummary,
        apiEpisodeCount = presentation.apiEpisodeCount,
        auth = model.auth,
        animeMark = model.animeMark,
        detailsExtras = model.detailsExtras,
        showMarkPanel = !model.forcedOfflineMode && model.auth.profile != null,
        showHeroRating = !model.forcedOfflineMode,
        onOpenLogin = actions.onOpenLogin,
        onGenreFilterSelected = actions.onGenreFilterSelected,
        onYearFilterSelected = actions.onYearFilterSelected,
        onStudioFilterSelected = actions.onStudioFilterSelected,
        onCreatorFilterSelected = actions.onCreatorFilterSelected,
        onSelectListMark = actions.onSelectAnimeListMark,
        onToggleFavorite = actions.onToggleFavorite,
        onSetAnimeRating = actions.onSetAnimeRating,
        onResolveSampledDownloadQualities = actions.onResolveSampledDownloadQualities,
        onPlayVideo = actions.onPlayVideo,
        onPlayVideoAt = actions.onPlayVideoAt,
        defaultDownloadQuality = model.settings.defaultQuality,
        onDownloadAllVideos = actions.onDownloadAllVideos,
        onRegisterModalInputActionHandler = actions.onRegisterModalInputActionHandler,
        canDownload = !model.forcedOfflineMode,
        hasWatchProgress = presentation.hasWatchProgress,
        onResetWatchProgress = { actions.onResetAnimeWatchProgress(details.id) },
        focusGridState = focusGridState,
        modifier = Modifier.fillMaxWidth(),
    )
    DetailsDescriptionSection(description = details.description)
    DetailsScreenshotsSection(
        screenshots = details.screenshots,
        onRegisterInputActionHandler = actions.onRegisterModalInputActionHandler,
        focusGridState = focusGridState,
        focusIndexOffset = presentation.focusLayout.offset(DetailsFocusBlock.Screenshots),
        focusBlockKey = DetailsFocusBlockKey.Screenshots,
    )
    DetailsRelatedAnimeSection(
        relatedAnime = details.relatedAnime,
        expanded = model.screenUiState.relatedExpanded,
        onExpandedChange = { model.screenUiState.relatedExpanded = it },
        onOpenAnime = { animeId, focusKey -> model.openAnime(actions, animeId, focusKey) },
        focusGridState = focusGridState,
        focusIndexOffset = presentation.focusLayout.offset(DetailsFocusBlock.RelatedAnime),
        focusBlockKey = DetailsFocusBlockKey.RelatedAnime,
    )
}

@Composable
private fun DetailsVideoSection(
    model: DetailsContentModel,
    actions: DetailsContentActions,
    presentation: DetailsContentPresentation,
    focusGridState: VisualFocusGridState,
) {
    when (val videos = model.videos) {
        LoadState.Loading -> LoadingPane(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp),
        )
        is LoadState.Error -> ErrorPane(
            message = videos.message,
            onRetry = actions.onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp),
        )
        is LoadState.Ready -> VideoPickerModern(
            videos = videos.data,
            selectedGroup = model.selectedGroup,
            playbackHistory = model.playbackHistory,
            onSelectGroup = actions.onSelectVideoGroup,
            onPlayVideo = actions.onPlayVideo,
            onPlayVideoWithResumeChoice = actions.onPlayVideoWithResumeChoice,
            forcedOfflineMode = model.forcedOfflineMode,
            modifier = Modifier.fillMaxWidth(),
            focusGridState = focusGridState,
            focusIndexOffset = presentation.focusLayout.offset(DetailsFocusBlock.Episodes),
            focusBlockKey = DetailsFocusBlockKey.Episodes,
        )
    }
}

@Composable
private fun DetailsOnlineSections(
    model: DetailsContentModel,
    actions: DetailsContentActions,
    presentation: DetailsContentPresentation,
    focusGridState: VisualFocusGridState,
) {
    val screenUiState = model.screenUiState
    val extras = when (val extrasState = model.detailsExtras) {
        is LoadState.Ready -> extrasState.data
        LoadState.Loading,
        is LoadState.Error,
        -> return
    }
    if (model.details.canShowVideoSubscriptions()) {
        DetailsSubscriptionsSection(
            auth = model.auth,
            videos = presentation.readyVideos,
            subscriptions = extras.subscriptions,
            expanded = screenUiState.subscriptionsExpanded,
            onExpandedChange = { screenUiState.subscriptionsExpanded = it },
            onToggleVideoSubscription = actions.onToggleVideoSubscription,
            focusGridState = focusGridState,
            focusIndexOffset = presentation.focusLayout.offset(DetailsFocusBlock.Subscriptions),
            focusBlockKey = DetailsFocusBlockKey.Subscriptions,
        )
    }
    DetailsAnimeRowSection(
        title = uiText(UiStringKey.Similar),
        animes = extras.recommendations,
        onOpenAnime = { animeId, focusKey -> model.openAnime(actions, animeId, focusKey) },
        focusGridState = focusGridState,
        focusIndexOffset = presentation.focusLayout.offset(DetailsFocusBlock.Recommendations),
        focusBlockKey = DetailsFocusBlockKey.Recommendations,
    )
    DetailsCommentsSection(
        comments = extras.comments,
        totalComments = model.details.commentsCount,
        commentsPaging = extras.commentsPaging,
        isAuthorized = model.auth.profile != null,
        scrollState = screenUiState.scrollState,
        expanded = screenUiState.commentsExpanded,
        onExpandedChange = { screenUiState.commentsExpanded = it },
        onAddAnimeComment = actions.onAddAnimeComment,
        onLoadMoreAnimeComments = actions.onLoadMoreAnimeComments,
        focusGridState = focusGridState,
        focusIndexOffset = presentation.focusLayout.offset(DetailsFocusBlock.Comments),
        focusBlockKey = DetailsFocusBlockKey.Comments,
    )
}

private fun DetailsContentModel.openAnime(
    actions: DetailsContentActions,
    animeId: Long,
    focusKey: Any?,
) {
    if (focusKey != null) screenUiState.retainedFocusKey = focusKey
    actions.onOpenAnime(animeId)
}
