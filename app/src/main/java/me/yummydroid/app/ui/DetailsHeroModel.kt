package me.yummydroid.app.ui

import me.yummydroid.app.AnimeDetailsExtras
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.LoadState
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.UserAnimeMark
import me.yummydroid.app.data.VideoVariant

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
