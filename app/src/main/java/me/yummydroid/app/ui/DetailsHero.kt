package me.yummydroid.app.ui

import me.yummydroid.app.AnimeDetailsExtras
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.DownloadPlan
import me.yummydroid.app.InputAction
import me.yummydroid.app.LoadState
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.UserAnimeListMark
import me.yummydroid.app.data.UserAnimeMark
import me.yummydroid.app.data.VideoVariant

internal const val DETAILS_HERO_FOCUS_GRAPH_SIZE = 80

internal object DetailsHeroFocusIndex {
    const val PrimaryAction = 0
    const val DownloadAction = 1
    const val ResetAction = 2
    const val RatingBadge = 3
    const val Poster = 4
    const val MarkStart = 24
    const val FactGenreStart = 32
    const val FactYear = 40
    const val FactStudioStart = 41
    const val FactCreatorStart = 47
}

internal data class DetailsHeroModel(
    val details: AnimeDetails,
    val activeFocusRequestNonce: Long,
    val watchVideo: VideoVariant?,
    val resumeTarget: HeroResumeTarget?,
    val downloadVideos: List<VideoVariant>,
    val downloadedSummary: String?,
    val episodeSummary: String,
    val apiEpisodeCount: Int,
    val auth: AuthUiState,
    val animeMark: LoadState<UserAnimeMark?>,
    val detailsExtras: LoadState<AnimeDetailsExtras>,
    val showMarkPanel: Boolean,
    val showHeroRating: Boolean,
    val defaultDownloadQuality: PreferredQuality,
    val canDownload: Boolean,
    val hasWatchProgress: Boolean,
)

internal data class DetailsHeroActions(
    val onOpenLogin: () -> Unit,
    val onGenreFilterSelected: (Long, FilterOption) -> Unit,
    val onYearFilterSelected: (Long, Int) -> Unit,
    val onStudioFilterSelected: (Long, FilterOption) -> Unit,
    val onCreatorFilterSelected: (Long, FilterOption) -> Unit,
    val onSelectListMark: (UserAnimeListMark) -> Unit,
    val onToggleFavorite: () -> Unit,
    val onSetAnimeRating: (Int?) -> Unit,
    val onPlayVideo: (VideoVariant) -> Unit,
    val onPlayVideoAt: (VideoVariant, Long) -> Unit,
    val onResolveSampledDownloadQualities: suspend (
        Set<String>,
        List<VideoVariant>,
    ) -> Map<String, List<PreferredQuality>>,
    val onDownloadAllVideos: (DownloadPlan) -> Unit,
    val onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
    val onResetWatchProgress: () -> Unit,
)
