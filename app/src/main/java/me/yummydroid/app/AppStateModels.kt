package me.yummydroid.app

import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.AnimeComment
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.AnimeRatingSummary
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.UserAnimeMark
import me.yummydroid.app.data.UserProfile
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoVariant

data class NavigationEntry(
    val route: AppRoute,
    val homeSection: BrowseSection,
    val filters: BrowseFilters,
    val searchQuery: String,
    val selectedVideoGroup: String?,
)

internal data class DetailsRouteCache(
    val details: LoadState.Ready<AnimeDetails>,
    val videos: LoadState<List<VideoVariant>>,
    val detailsExtras: LoadState<AnimeDetailsExtras>,
    val animeMark: LoadState<UserAnimeMark?>,
    val selectedVideoGroup: String?,
    val forcedOfflineMode: Boolean,
    val playbackProgress: PlaybackProgress?,
    val playbackHistory: List<PlaybackProgress>,
)

internal data class CatalogRouteCache(
    val animes: List<Anime>,
    val paging: PagingUiState,
    val forcedOfflineMode: Boolean,
)

data class PlayerNotice(
    val id: Long,
    val message: String,
)

enum class PlaybackFailureKind {
    PlayerError,
    BufferingTimeout,
}

data class PlaybackFailure(
    val kind: PlaybackFailureKind,
    val message: String? = null,
)

data class AuthUiState(
    val profile: UserProfile? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val captchaRequestNonce: Long = 0L,
)

data class OfflineDownloadUiState(
    val videoId: Long? = null,
    val isRunning: Boolean = false,
    val progress: Float = 0f,
    val message: String? = null,
)

internal val DownloadTaskState.title: String
    get() = when (this) {
        DownloadTaskState.Queued -> "Queued"
        DownloadTaskState.Running -> "Downloading"
        DownloadTaskState.Paused -> "Paused"
        DownloadTaskState.Added -> "Added"
        DownloadTaskState.Completed -> "Downloaded"
        DownloadTaskState.Failed -> "Error"
        DownloadTaskState.Cancelled -> "Cancelled"
    }

enum class BrowseSection {
    Catalog,
    Schedule,
    History,
    Downloads,
}

data class AnimeDetailsExtras(
    val comments: List<AnimeComment> = emptyList(),
    val commentsPaging: PagingUiState = PagingUiState(),
    val recommendations: List<Anime> = emptyList(),
    val rating: AnimeRatingSummary = AnimeRatingSummary(),
    val subscriptions: List<VideoSubscription> = emptyList(),
)

sealed interface AppRoute {
    data object Home : AppRoute
    data class Details(val animeId: Long) : AppRoute
    data class Player(
        val video: VideoVariant,
        val animeTitle: String,
        val startPositionMs: Long = 0L,
        val preferredQuality: PreferredQuality = PreferredQuality.Auto,
        val resumeChoicePositionMs: Long? = null,
    ) : AppRoute
}

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Ready<T>(val data: T) : LoadState<T>
    data class Error(val message: String) : LoadState<Nothing>
}

