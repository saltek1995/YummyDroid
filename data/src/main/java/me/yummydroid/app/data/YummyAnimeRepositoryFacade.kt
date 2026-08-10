package me.yummydroid.app.data

import android.content.Context

class YummyAnimeRepository(
    internal val api: YummyAnimeApi = YummyAnimeApi(),
    context: Context? = null,
    internal val siteDomainResolver: SiteDomainResolver = SiteDomainResolver(),
    internal val videoStreamResolver: VideoStreamResolver = VideoStreamResolver(
        context = context,
        siteDomainResolver = siteDomainResolver,
    ),
    internal val authStorage: AuthStorage? = null,
    internal val downloadBandwidthLimiter: DownloadBandwidthLimiter = NoOpDownloadBandwidthLimiter,
) {
    internal val offlineStorage = context?.let(::OfflineAnimeStorage)
    internal val sourceQualityCache = context?.let(::SourceQualityCacheStorage)
    internal val contentCache = context?.let(::AnimeContentCacheStorage)

    @Volatile
    internal var contentLanguage: ContentLanguage = ContentLanguage.Russian

    @Volatile
    internal var offlineFallbackActive: Boolean = false

    internal val downloadClient = defaultVideoDownloadClient()

    fun updateContentLanguage(language: ContentLanguage) {
        repositoryUpdateContentLanguage(language)
    }

    suspend fun getFeatured(
        filters: BrowseFilters,
        offset: Int = 0,
        limit: Int = REPOSITORY_PAGE_SIZE,
    ): List<Anime> = repositoryGetFeatured(filters, offset, limit)

    suspend fun search(
        query: String,
        filters: BrowseFilters,
        offset: Int = 0,
        limit: Int = REPOSITORY_PAGE_SIZE,
    ): List<Anime> = repositorySearch(query, filters, offset, limit)

    suspend fun getFilterCatalog(): FilterCatalog = repositoryGetFilterCatalog()

    suspend fun getAnimeWithVideos(
        animeId: Long,
    ): Pair<AnimeDetails, List<VideoVariant>> = repositoryGetAnimeWithVideos(animeId)

    suspend fun getAnime(animeId: Long): AnimeDetails = repositoryGetAnime(animeId)

    suspend fun getAnimeOnline(animeId: Long): AnimeDetails = repositoryGetAnimeOnline(animeId)

    suspend fun getVideos(animeId: Long): List<VideoVariant> = repositoryGetVideos(animeId)

    suspend fun getSchedule(): List<ScheduleAnime> = repositoryGetSchedule()

    suspend fun getCollections(
        offset: Int = 0,
        limit: Int = REPOSITORY_PAGE_SIZE,
    ): List<AnimeCollectionSummary> = repositoryGetCollections(offset, limit)

    suspend fun getCollection(id: Long): AnimeCollectionSummary = repositoryGetCollection(id)

    suspend fun getAnimeCollections(
        animeId: Long,
    ): List<AnimeCollectionSummary> = repositoryGetAnimeCollections(animeId)

    suspend fun getAnimeComments(
        animeId: Long,
        offset: Int = 0,
        limit: Int = 20,
    ): List<AnimeComment> = repositoryGetAnimeComments(animeId, offset, limit)

    suspend fun addAnimeComment(animeId: Long, text: String): AnimeComment? =
        repositoryAddAnimeComment(animeId, text)

    suspend fun getAnimeRecommendations(animeId: Long): List<Anime> =
        repositoryGetAnimeRecommendations(animeId)

    suspend fun getAnimeRatingSummary(animeId: Long): AnimeRatingSummary =
        repositoryGetAnimeRatingSummary(animeId)

    suspend fun setAnimeRating(animeId: Long, rating: Int): AnimeRatingSummary =
        repositorySetAnimeRating(animeId, rating)

    suspend fun deleteAnimeRating(animeId: Long): AnimeRatingSummary =
        repositoryDeleteAnimeRating(animeId)

    suspend fun subscribeVideo(videoId: Long): Boolean = repositorySubscribeVideo(videoId)

    suspend fun unsubscribeVideo(videoId: Long): Boolean = repositoryUnsubscribeVideo(videoId)

    suspend fun getVideoSubscriptions(): List<VideoSubscription> = repositoryGetVideoSubscriptions()

    suspend fun getNewEpisodeNotifications(limit: Int = 50): List<SiteNotification> =
        repositoryGetNewEpisodeNotifications(limit)

    suspend fun getProfileNotifications(
        types: List<String> = emptyList(),
        subTypes: List<String> = emptyList(),
        offset: Int = 0,
        limit: Int = 50,
    ): List<SiteNotification> = repositoryGetProfileNotifications(types, subTypes, offset, limit)

    suspend fun markProfileNotificationsRead(): Boolean = repositoryMarkProfileNotificationsRead()

    suspend fun markProfileNotificationRead(notificationId: Long): Boolean =
        repositoryMarkProfileNotificationRead(notificationId)

    suspend fun deleteProfileNotification(notificationId: Long): Boolean =
        repositoryDeleteProfileNotification(notificationId)

    suspend fun resolveVideoStream(
        video: VideoVariant,
        preferredQuality: PreferredQuality = PreferredQuality.Auto,
        waitForRuntimeSubtitles: Boolean = true,
    ): ResolvedVideoStream = repositoryResolveVideoStream(
        video = video,
        preferredQuality = preferredQuality,
        waitForRuntimeSubtitles = waitForRuntimeSubtitles,
    )

    suspend fun resolveAvailableDownloadQualities(
        requested: VideoVariant,
        videos: List<VideoVariant>,
        allEpisodes: Boolean,
    ): List<PreferredQuality> = repositoryResolveAvailableDownloadQualities(
        requested = requested,
        videos = videos,
        allEpisodes = allEpisodes,
    )

    suspend fun resolveSampledDownloadQualities(
        voiceKeys: Set<String>,
        videos: List<VideoVariant>,
    ): Map<String, List<PreferredQuality>> = repositoryResolveSampledDownloadQualities(
        voiceKeys = voiceKeys,
        videos = videos,
    )

    suspend fun offlineAnime(): List<OfflineAnimeEntry> = repositoryOfflineAnime()

    fun isOfflineFallbackActive(): Boolean = offlineFallbackActive

    suspend fun deleteOfflineVideo(
        animeId: Long,
        videoId: Long,
        playbackUrl: String? = null,
    ) = repositoryDeleteOfflineVideo(animeId, videoId, playbackUrl)

    suspend fun deleteOfflineAnime(animeId: Long) = repositoryDeleteOfflineAnime(animeId)

    suspend fun clearAppContentCache(
        playbackProgressStorage: PlaybackProgressStorage,
    ) = repositoryClearAppContentCache(playbackProgressStorage)

    suspend fun downloadVideo(
        details: AnimeDetails,
        videos: List<VideoVariant>,
        video: VideoVariant,
        preferredQuality: PreferredQuality = PreferredQuality.Auto,
        onProgress: (VideoVariant, DownloadProgressInfo) -> Unit,
        isCancelled: () -> Boolean = { false },
        deletePartialOnCancel: () -> Boolean = { true },
    ): VideoVariant = repositoryDownloadVideo(
        details = details,
        videos = videos,
        video = video,
        preferredQuality = preferredQuality,
        onProgress = onProgress,
        isCancelled = isCancelled,
        deletePartialOnCancel = deletePartialOnCancel,
    )

    fun cachedSiteBaseUrl(): String = siteDomainResolver.cachedOrDefaultBaseUrl()

    suspend fun activeSiteBaseUrl(): String = siteDomainResolver.activeBaseUrl()

    suspend fun checkReachableSiteBaseUrl(): String? = siteDomainResolver.checkReachableBaseUrl()

    suspend fun resolveBestPlaybackSource(
        candidates: List<VideoVariant>,
        preferredQuality: PreferredQuality,
        metadataCandidates: List<VideoVariant> = candidates,
        waitForRuntimeSubtitles: Boolean = true,
    ): ResolvedPlayback = repositoryResolveBestPlaybackSource(
        candidates = candidates,
        preferredQuality = preferredQuality,
        metadataCandidates = metadataCandidates,
        waitForRuntimeSubtitles = waitForRuntimeSubtitles,
    )

    suspend fun resolvePlaybackMetadata(
        playback: ResolvedPlayback,
        metadataCandidates: List<VideoVariant>,
        preferredQuality: PreferredQuality,
    ): ResolvedPlayback = repositoryResolvePlaybackMetadata(
        playback = playback,
        metadataCandidates = metadataCandidates,
        preferredQuality = preferredQuality,
    )

    fun cachedProfile(): UserProfile? = authStorage?.readProfile()

    suspend fun restoreProfile(): UserProfile? = repositoryRestoreProfile()

    suspend fun login(
        login: String,
        password: String,
        captchaResponse: String? = null,
    ): UserProfile = repositoryLogin(login, password, captchaResponse)

    fun submitCaptchaResponse(response: String) {
        api.submitCaptchaResponse(response)
    }

    fun logout() {
        authStorage?.clear()
    }

    suspend fun getAnimeMark(animeId: Long): UserAnimeMark? = repositoryGetAnimeMark(animeId)

    suspend fun setAnimeListMark(
        animeId: Long,
        mark: UserAnimeListMark,
    ): UserAnimeMark = repositorySetAnimeListMark(animeId, mark)

    suspend fun removeAnimeListMark(animeId: Long): UserAnimeMark =
        repositoryRemoveAnimeListMark(animeId)

    suspend fun setFavorite(animeId: Long, isFavorite: Boolean): UserAnimeMark =
        repositorySetFavorite(animeId, isFavorite)

    suspend fun getWatchHistory(
        limit: Int = 100,
        offset: Int = 0,
    ): List<PlaybackProgress> = repositoryGetWatchHistory(limit, offset)

    suspend fun saveWatchProgress(progress: PlaybackProgress): Boolean =
        repositorySaveWatchProgress(progress)

    suspend fun deleteWatchProgress(videoIds: List<Long>): Boolean =
        repositoryDeleteWatchProgress(videoIds)

    internal fun requireToken(): String {
        return authStorage?.readToken() ?: error("Sign in is required")
    }

    internal fun applyCachedSourceQualities(videos: List<VideoVariant>): List<VideoVariant> {
        return sourceQualityCache?.applyTo(videos) ?: videos
    }

    internal fun cacheUserId(): Long? {
        return authStorage?.readProfile()?.id?.takeIf { it > 0L }
    }
}

internal const val REPOSITORY_PAGE_SIZE = 36
internal const val FAVORITES_FILTER_ID = 4
