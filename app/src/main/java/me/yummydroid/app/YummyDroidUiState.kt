package me.yummydroid.app

import java.util.Locale
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.AnimeComment
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.AnimeRatingSummary
import me.yummydroid.app.data.AnimeTrailer
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.AppUpdateInfo
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.cleanVideoSourceLabel
import me.yummydroid.app.data.DEFAULT_SITE_BASE_URL
import me.yummydroid.app.data.episodeOrderValue
import me.yummydroid.app.data.FilterCatalog
import me.yummydroid.app.data.isSameEpisodeAs
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.OfflineAnimeEntry
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.ResolvedPlayback
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.ScheduleAnime
import me.yummydroid.app.data.UserAnimeMark
import me.yummydroid.app.data.UserProfile
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoVariant

data class YummyDroidUiState(
    val route: AppRoute = AppRoute.Home,
    val navigationBackStack: List<NavigationEntry> = emptyList(),
    val siteBaseUrl: String = DEFAULT_SITE_BASE_URL,
    val homeSection: BrowseSection = BrowseSection.Catalog,
    val featured: LoadState<List<Anime>> = LoadState.Loading,
    val featuredPaging: PagingUiState = PagingUiState(),
    val topAnime: LoadState<List<Anime>> = LoadState.Loading,
    val topPaging: PagingUiState = PagingUiState(),
    val schedule: LoadState<List<ScheduleAnime>> = LoadState.Loading,
    val historyAnime: LoadState<List<Anime>> = LoadState.Ready(emptyList()),
    val libraryAnime: LoadState<List<Anime>> = LoadState.Ready(emptyList()),
    val offlineEntries: LoadState<List<OfflineAnimeEntry>> = LoadState.Ready(emptyList()),
    val downloadQueue: DownloadQueueSnapshot = DownloadQueueSnapshot(),
    val offlineDownload: OfflineDownloadUiState = OfflineDownloadUiState(),
    val forcedOfflineMode: Boolean = false,
    val homeFocusResetNonce: Long = 0L,
    val searchQuery: String = "",
    val searchResults: LoadState<List<Anime>> = LoadState.Ready(emptyList()),
    val searchPaging: PagingUiState = PagingUiState(canLoadMore = false),
    val filters: BrowseFilters = BrowseFilters(),
    val filterCatalog: LoadState<FilterCatalog> = LoadState.Loading,
    val details: LoadState<AnimeDetails> = LoadState.Loading,
    val detailsExtras: LoadState<AnimeDetailsExtras> = LoadState.Loading,
    val globalSubscriptions: LoadState<List<VideoSubscription>> = LoadState.Ready(emptyList()),
    val videos: LoadState<List<VideoVariant>> = LoadState.Loading,
    val selectedVideoGroup: String? = null,
    val playerStream: LoadState<ResolvedVideoStream> = LoadState.Loading,
    val pendingPlaybackRecovery: PlaybackRecoveryCandidate? = null,
    val auth: AuthUiState = AuthUiState(),
    val animeMark: LoadState<UserAnimeMark?> = LoadState.Ready(null),
    val settings: AppSettings = AppSettings(),
    val playbackProgress: PlaybackProgress? = null,
    val playbackHistory: List<PlaybackProgress> = emptyList(),
    val updateState: LoadState<AppUpdateInfo?> = LoadState.Ready(null),
) {
    val canNavigateBack: Boolean
        get() = route != AppRoute.Home || navigationBackStack.isNotEmpty()
            || homeSection == BrowseSection.Downloads || searchQuery.isNotBlank()
}

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

internal data class StandbyPlaybackSource(
    val key: String,
    val playback: ResolvedPlayback,
    val resolvedAtMs: Long,
)

data class PlaybackRecoveryCandidate(
    val id: Long,
    val video: VideoVariant,
    val stream: ResolvedVideoStream,
    val positionMs: Long,
    val preferredQuality: PreferredQuality,
)

data class PagingUiState(
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
    val error: String? = null,
)

internal data class AnimePageMerge(
    val items: List<Anime>,
    val paging: PagingUiState,
)

internal fun mergeAnimePage(
    existing: List<Anime>,
    incoming: List<Anime>,
    reset: Boolean,
    pageSize: Int,
): AnimePageMerge {
    val base = if (reset) emptyList() else existing
    val merged = (base + incoming).distinctBy { it.id }
    return AnimePageMerge(
        items = merged,
        paging = PagingUiState(
            isLoadingMore = false,
            canLoadMore = incoming.size >= pageSize && merged.size > base.size,
        ),
    )
}

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
        DownloadTaskState.Queued -> "В очереди"
        DownloadTaskState.Running -> "Загрузка"
        DownloadTaskState.Paused -> "Пауза"
        DownloadTaskState.Added -> "Добавлено"
        DownloadTaskState.Completed -> "Скачано"
        DownloadTaskState.Failed -> "Ошибка"
        DownloadTaskState.Cancelled -> "Отменено"
    }

enum class BrowseSection(
    val title: String,
) {
    Catalog("Каталог"),
    Schedule("Расписание"),
    History("История"),
    Downloads("Загрузки"),
}

data class AnimeDetailsExtras(
    val comments: List<AnimeComment> = emptyList(),
    val commentsPaging: PagingUiState = PagingUiState(),
    val trailers: List<AnimeTrailer> = emptyList(),
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
    ) : AppRoute
}

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Ready<T>(val data: T) : LoadState<T>
    data class Error(val message: String) : LoadState<Nothing>
}


internal fun YummyDroidUiState.navigationEntry(): NavigationEntry {
    return NavigationEntry(
        route = route,
        homeSection = homeSection,
        filters = filters,
        searchQuery = searchQuery,
        selectedVideoGroup = selectedVideoGroup,
    )
}

internal fun YummyDroidUiState.navigationStackAfterOptionalPush(push: Boolean): List<NavigationEntry> {
    return if (push) {
        navigationBackStack.withNavigationEntry(navigationEntry())
    } else {
        navigationBackStack
    }
}

internal fun YummyDroidUiState.shouldPushHomeMutation(): Boolean {
    return route != AppRoute.Home
}

internal fun List<NavigationEntry>.withNavigationEntry(entry: NavigationEntry): List<NavigationEntry> {
    return if (lastOrNull() == entry) {
        this
    } else {
        (this + entry).takeLast(MAX_NAVIGATION_STACK)
    }
}

internal fun Throwable.userMessage(): String {
    return message?.takeIf { it.isNotBlank() } ?: "Не удалось загрузить данные"
}

internal fun VideoVariant.isFinalEpisodeFor(details: AnimeDetails, allVideos: List<VideoVariant>): Boolean {
    val currentOrder = episodeOrderValue()
    val expectedEpisodeCount = details.episodeCount.takeIf { it > 0 }
    if (expectedEpisodeCount != null && currentOrder != null) {
        return currentOrder >= expectedEpisodeCount.toDouble()
    }

    val lastVideo = allVideos
        .filter { it.animeId == animeId }
        .ifEmpty { listOf(this) }
        .maxWithOrNull(
            compareBy<VideoVariant> { it.episodeOrderValue() ?: 0.0 }
                .thenBy { it.index }
                .thenBy { it.id },
        )
    return lastVideo?.isSameEpisodeAs(this) == true
}

internal fun VideoVariant.hasFollowingEpisodeIn(allVideos: List<VideoVariant>): Boolean {
    val sameAnimeVideos = allVideos
        .filter { it.animeId == animeId }
        .ifEmpty { listOf(this) }
    val currentOrder = episodeOrderValue()
    if (currentOrder != null) {
        return sameAnimeVideos.any { candidate ->
            val candidateOrder = candidate.episodeOrderValue()
            candidateOrder != null && candidateOrder > currentOrder
        }
    }

    val episodeVideos = sameAnimeVideos
        .groupBy { it.matchingEpisodeKey }
        .values
        .mapNotNull { variants ->
            variants.minWithOrNull(
                compareBy<VideoVariant> { it.index }
                    .thenBy { it.id },
            )
        }
        .sortedWith(
            compareBy<VideoVariant> { it.index }
                .thenBy { it.id },
        )
    val currentIndex = episodeVideos.indexOfFirst { it.isSameEpisodeAs(this) }
        .takeIf { it >= 0 }
        ?: return false
    return currentIndex < episodeVideos.lastIndex
}

internal fun PlaybackProgress.isNewerThan(other: PlaybackProgress?): Boolean {
    return updatedAtMs > (other?.updatedAtMs ?: Long.MIN_VALUE)
}

internal data class PlaybackCacheKey(
    val animeId: Long,
    val voiceKey: String,
)

internal data class PlaybackSourceCacheEntry(
    val providerKey: String,
    val maxVideoHeight: Int?,
)

internal fun ResolvedVideoStream.comparableVideoHeight(): Int {
    return maxVideoHeight
        ?: selectedVideoHeight
        ?: 0
}

internal fun ResolvedVideoStream.isLocalPlaybackStream(): Boolean {
    return url.startsWith("file:", ignoreCase = true) || url.startsWith("content:", ignoreCase = true)
}

internal fun VideoVariant.playbackCacheKey(): PlaybackCacheKey {
    return PlaybackCacheKey(animeId = animeId, voiceKey = matchingVoiceKey)
}

internal val VideoVariant.sourceProviderKey: String
    get() = listOf(
        player.cleanVideoSourceLabel().lowercase(Locale.ROOT),
        url.sourceProviderFingerprint(),
    ).filter { it.isNotBlank() }.joinToString("|")

internal val VideoVariant.playbackSourceKey: String
    get() = sourceProviderKey.takeIf { it.isNotBlank() }
        ?: id.takeIf { it > 0L }?.let { "id:$it" }
        ?: listOf(animeId.toString(), matchingEpisodeKey, matchingVoiceKey, index.toString()).joinToString(":")

internal fun VideoVariant.hasSamePlaybackSourceAs(other: VideoVariant): Boolean {
    val leftProviderKey = sourceProviderKey
    val rightProviderKey = other.sourceProviderKey
    if (leftProviderKey.isNotBlank() && rightProviderKey.isNotBlank()) {
        return leftProviderKey == rightProviderKey
    }
    if (id > 0L && other.id > 0L && id == other.id) return true
    return playbackSourceKey == other.playbackSourceKey
}

internal fun String.sourceProviderFingerprint(): String {
    val value = trim().lowercase(Locale.ROOT)
    val host = Regex("""^https?://([^/?#]+)""").find(value)?.groupValues?.getOrNull(1).orEmpty()
    val path = Regex("""^https?://[^/]+/([^?#]+)""").find(value)?.groupValues?.getOrNull(1)
        ?.substringBefore('/')
        .orEmpty()
    return listOf(host, path).filter { it.isNotBlank() }.joinToString("/")
}

internal fun VideoVariant.estimatedSourceMaxVideoHeight(): Int {
    val lowerPlayer = player.lowercase(Locale.ROOT)
    val lowerUrl = url.lowercase(Locale.ROOT)
    return when {
        "cvh" in lowerPlayer || "iframecvh" in lowerUrl -> 1080
        "alloha" in lowerPlayer || "alloha" in lowerUrl -> 1080
        "aksor" in lowerPlayer || "aksor" in lowerUrl -> 1080
        else -> Regex("""(?i)(2160|1440|1080|720|576|540|480|360|240|144)p""")
            .find(url)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: 0
    }
}
