package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun DetailsHeroSiteInfo(
    model: DetailsHeroModel,
    actions: DetailsHeroActions,
    compact: Boolean,
    isWide: Boolean,
    heroFocusGridState: VisualFocusGridState?,
    modifier: Modifier = Modifier,
) {
    val details = model.details
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp),
    ) {
        DetailsHeroHeading(
            details = details,
            compact = compact,
            isWide = isWide,
            detailsExtras = model.detailsExtras,
            auth = model.auth,
            showHeroRating = model.showHeroRating,
            onSetAnimeRating = actions.onSetAnimeRating,
            onRegisterModalInputActionHandler = actions.onRegisterModalInputActionHandler,
            heroFocusGridState = heroFocusGridState,
        )
        DetailsHeroActions(
            animeId = details.id,
            animeTitle = details.title,
            watchVideo = model.watchVideo,
            resumeTarget = model.resumeTarget,
            downloadVideos = model.downloadVideos,
            onPlayVideo = actions.onPlayVideo,
            onPlayVideoAt = actions.onPlayVideoAt,
            defaultDownloadQuality = model.defaultDownloadQuality,
            onResolveSampledDownloadQualities = actions.onResolveSampledDownloadQualities,
            onDownloadAllVideos = actions.onDownloadAllVideos,
            onRegisterModalInputActionHandler = actions.onRegisterModalInputActionHandler,
            canDownload = model.canDownload,
            hasWatchProgress = model.hasWatchProgress,
            onResetWatchProgress = actions.onResetWatchProgress,
            externalPrimaryFocusRequester = heroFocusGridState?.requester(DetailsHeroFocusIndex.PrimaryAction),
            focusRequestNonce = model.activeFocusRequestNonce,
            heroFocusGridState = heroFocusGridState,
        )
        DetailsHeroProgressSummary(model.episodeSummary, model.downloadedSummary)
        DetailsHeroFactRows(
            details = details,
            apiEpisodeCount = model.apiEpisodeCount,
            narrow = !isWide,
            compact = compact,
            onGenreFilterSelected = { genre -> actions.onGenreFilterSelected(details.id, genre) },
            onYearFilterSelected = { year -> actions.onYearFilterSelected(details.id, year) },
            onStudioFilterSelected = { studio -> actions.onStudioFilterSelected(details.id, studio) },
            onCreatorFilterSelected = { creator -> actions.onCreatorFilterSelected(details.id, creator) },
            heroFocusGridState = heroFocusGridState,
        )
    }
}
