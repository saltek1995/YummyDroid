package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.yummydroid.app.AnimeDetailsExtras
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.DownloadPlan
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.InputAction
import me.yummydroid.app.LoadState
import me.yummydroid.app.ui.theme.YummyColors

@Composable
internal fun DetailsHeroSiteInfo(
    details: AnimeDetails,
    compact: Boolean,
    isWide: Boolean,
    watchVideo: VideoVariant?,
    resumeTarget: HeroResumeTarget?,
    downloadVideos: List<VideoVariant>,
    downloadedSummary: String?,
    episodeSummary: String,
    apiEpisodeCount: Int,
    auth: AuthUiState,
    detailsExtras: LoadState<AnimeDetailsExtras>,
    showHeroRating: Boolean,
    onGenreFilterSelected: (Long, FilterOption) -> Unit,
    onYearFilterSelected: (Long, Int) -> Unit,
    onStudioFilterSelected: (Long, FilterOption) -> Unit,
    onCreatorFilterSelected: (Long, FilterOption) -> Unit,
    onSetAnimeRating: (Int?) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    onPlayVideoAt: (VideoVariant, Long) -> Unit,
    defaultDownloadQuality: PreferredQuality,
    onResolveSampledDownloadQualities: suspend (Set<String>, List<VideoVariant>) -> Map<String, List<PreferredQuality>>,
    onDownloadAllVideos: (DownloadPlan) -> Unit,
    onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
    canDownload: Boolean,
    hasWatchProgress: Boolean,
    onResetWatchProgress: () -> Unit,
    actionsFocusRequestNonce: Long,
    modifier: Modifier = Modifier,
    heroFocusGridState: VisualFocusGridState? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp),
    ) {
        DetailsHeroHeading(
            details = details,
            compact = compact,
            isWide = isWide,
            detailsExtras = detailsExtras,
            auth = auth,
            showHeroRating = showHeroRating,
            onSetAnimeRating = onSetAnimeRating,
            onRegisterModalInputActionHandler = onRegisterModalInputActionHandler,
            heroFocusGridState = heroFocusGridState,
        )
        DetailsHeroActions(
            animeId = details.id,
            animeTitle = details.title,
            watchVideo = watchVideo,
            resumeTarget = resumeTarget,
            downloadVideos = downloadVideos,
            onPlayVideo = onPlayVideo,
            onPlayVideoAt = onPlayVideoAt,
            defaultDownloadQuality = defaultDownloadQuality,
            onResolveSampledDownloadQualities = onResolveSampledDownloadQualities,
            onDownloadAllVideos = onDownloadAllVideos,
            onRegisterModalInputActionHandler = onRegisterModalInputActionHandler,
            canDownload = canDownload,
            hasWatchProgress = hasWatchProgress,
            onResetWatchProgress = onResetWatchProgress,
            externalPrimaryFocusRequester = heroFocusGridState?.requester(DetailsHeroFocusIndex.PrimaryAction),
            focusRequestNonce = actionsFocusRequestNonce,
            heroFocusGridState = heroFocusGridState,
        )
        DetailsHeroProgressSummary(episodeSummary, downloadedSummary)
        DetailsHeroFactRows(
            details = details,
            apiEpisodeCount = apiEpisodeCount,
            narrow = !isWide,
            compact = compact,
            onGenreFilterSelected = { genre -> onGenreFilterSelected(details.id, genre) },
            onYearFilterSelected = { year -> onYearFilterSelected(details.id, year) },
            onStudioFilterSelected = { studio -> onStudioFilterSelected(details.id, studio) },
            onCreatorFilterSelected = { creator -> onCreatorFilterSelected(details.id, creator) },
            heroFocusGridState = heroFocusGridState,
        )
    }
}

@Composable
private fun DetailsHeroProgressSummary(episodeSummary: String, downloadedSummary: String?) {
    if (episodeSummary.isBlank() && downloadedSummary == null) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (episodeSummary.isNotBlank()) {
            Text(
                text = episodeSummary,
                style = MaterialTheme.typography.labelLarge,
                color = YummyColors.watched,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        downloadedSummary?.let { summary ->
            Text(
                text = summary,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal fun String.isPresentFactValue(): Boolean {
    val normalized = trim()
    return normalized.isNotBlank() &&
        !normalized.equals("unknown", ignoreCase = true) &&
        !normalized.equals("null", ignoreCase = true) &&
        normalized != "-" &&
        normalized != "\u2014" &&
        normalized != "\u0432\u0402\u201d"
}
