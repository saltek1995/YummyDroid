package me.yummydroid.app.data

import android.content.Context
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

// RepositoryAccountData
internal suspend fun YummyAnimeRepository.repositoryRestoreProfile(): UserProfile? =
    withContext(Dispatchers.IO) {
        val storage = authStorage ?: return@withContext null
        val token = storage.readToken() ?: run {
            storage.clear()
            return@withContext null
        }
        val cachedProfile = storage.readProfile()
        val refreshedToken = runCatching { api.refreshToken(token) }.getOrElse { throwable ->
            throwable.throwIfCancellation()
            if (throwable.isUnauthorizedApiError()) {
                storage.clear()
                throw throwable
            }
            token
        }
        if (refreshedToken != token) {
            storage.saveToken(refreshedToken)
        }
        runCatching { api.getProfile(refreshedToken) }
            .onSuccess { storage.saveProfile(it) }
            .getOrElse { throwable ->
                throwable.throwIfCancellation()
                if (throwable.isUnauthorizedApiError()) {
                    storage.clear()
                    throw throwable
                }
                cachedProfile ?: throw throwable
            }
    }

internal suspend fun YummyAnimeRepository.repositoryLogin(
    login: String,
    password: String,
    captchaResponse: String?,
): UserProfile = withContext(Dispatchers.IO) {
    val token = api.login(login, password, captchaResponse)
    authStorage?.saveToken(token)
    api.getProfile(token).also { profile ->
        authStorage?.saveProfile(profile)
    }
}

internal suspend fun YummyAnimeRepository.repositoryGetAnimeMark(
    animeId: Long,
): UserAnimeMark? = withContext(Dispatchers.IO) {
    val token = authStorage?.readToken() ?: return@withContext null
    api.getAnimeMark(animeId, token)
}

internal suspend fun YummyAnimeRepository.repositorySetAnimeListMark(
    animeId: Long,
    mark: UserAnimeListMark,
): UserAnimeMark = withContext(Dispatchers.IO) {
    api.setAnimeListMark(animeId, mark, requireToken())
}

internal suspend fun YummyAnimeRepository.repositoryRemoveAnimeListMark(
    animeId: Long,
): UserAnimeMark = withContext(Dispatchers.IO) {
    api.removeAnimeListMark(animeId, requireToken())
}

internal suspend fun YummyAnimeRepository.repositorySetFavorite(
    animeId: Long,
    isFavorite: Boolean,
): UserAnimeMark = withContext(Dispatchers.IO) {
    api.setFavorite(animeId, isFavorite, requireToken())
}

internal suspend fun YummyAnimeRepository.repositoryGetWatchHistory(
    limit: Int,
    offset: Int,
): List<PlaybackProgress> = withContext(Dispatchers.IO) {
    api.getWatchHistory(requireToken(), limit, offset)
}

internal suspend fun YummyAnimeRepository.repositorySaveWatchProgress(
    progress: PlaybackProgress,
): Boolean = withContext(Dispatchers.IO) {
    val token = authStorage?.readToken() ?: return@withContext false
    api.saveWatchProgress(progress, token)
}

internal suspend fun YummyAnimeRepository.repositoryDeleteWatchProgress(
    videoIds: List<Long>,
): Boolean = withContext(Dispatchers.IO) {
    api.deleteWatchProgress(videoIds, requireToken())
}

// RepositoryAnimeDetailsData
internal suspend fun YummyAnimeRepository.repositoryGetAnimeWithVideos(
    animeId: Long,
): Pair<AnimeDetails, List<VideoVariant>> = repositoryGetAnimeWithVideos(
    cachedAnimeId = animeId,
) {
    api.getAnimeWithVideos(animeId, authStorage?.readToken())
}

internal suspend fun YummyAnimeRepository.repositoryGetAnimeWithVideos(
    animeAlias: String,
): Pair<AnimeDetails, List<VideoVariant>> = repositoryGetAnimeWithVideos(cachedAnimeId = null) {
    api.getAnimeWithVideos(animeAlias, authStorage?.readToken())
}

private suspend fun YummyAnimeRepository.repositoryGetAnimeWithVideos(
    cachedAnimeId: Long?,
    fetch: suspend () -> Pair<AnimeDetails, List<VideoVariant>>,
): Pair<AnimeDetails, List<VideoVariant>> = withContext(Dispatchers.IO) {
    var offline = cachedAnimeId?.let { offlineStorage?.read(it) }
    if (cachedAnimeId != null) {
        contentCache?.readAnimeWithVideos(
            language = contentLanguage,
            userId = cacheUserId(),
            animeId = cachedAnimeId,
        )?.let { cached ->
            offlineFallbackActive = false
            return@withContext cached.details to applyCachedSourceQualities(
                cached.videos.withOfflineDownloads(offline?.videos.orEmpty(), cached.details),
            )
        }
    }

    try {
        offlineFallbackActive = false
        val (details, videos) = fetch()
        if (offline == null) offline = offlineStorage?.read(details.id)
        val mergedVideos = applyCachedSourceQualities(
            videos.withOfflineDownloads(offline?.videos.orEmpty(), details),
        )
        contentCache?.saveAnimeWithVideos(
            language = contentLanguage,
            userId = cacheUserId(),
            animeId = details.id,
            value = CachedAnimeWithVideos(
                details = details,
                videos = mergedVideos.map { it.withoutOfflinePlayback() },
            ),
        )
        offlineStorage?.saveAnime(details, mergedVideos)
        details to mergedVideos
    } catch (throwable: Throwable) {
        throwable.throwIfCancellation()
        offline?.let {
            offlineFallbackActive = true
            it.details to it.videos
        } ?: throw throwable
    }
}

internal suspend fun YummyAnimeRepository.repositoryGetAnime(
    animeId: Long,
): AnimeDetails = withContext(Dispatchers.IO) {
    contentCache?.readAnimeWithVideos(
        language = contentLanguage,
        userId = cacheUserId(),
        animeId = animeId,
    )?.details?.let { return@withContext it }

    try {
        api.getAnime(animeId, authStorage?.readToken())
    } catch (throwable: Throwable) {
        throwable.throwIfCancellation()
        offlineStorage?.read(animeId)?.details ?: throw throwable
    }
}

internal suspend fun YummyAnimeRepository.repositoryGetAnimeOnline(
    animeId: Long,
): AnimeDetails = withContext(Dispatchers.IO) {
    api.getAnime(animeId, authStorage?.readToken())
}

internal suspend fun YummyAnimeRepository.repositoryGetVideos(
    animeId: Long,
): List<VideoVariant> = withContext(Dispatchers.IO) {
    val offline = offlineStorage?.read(animeId)
    contentCache?.readVideos(
        language = contentLanguage,
        userId = cacheUserId(),
        animeId = animeId,
    )?.let { cached ->
        offlineFallbackActive = false
        val merged = offline?.let { entry ->
            cached.withOfflineDownloads(entry.videos, entry.details)
        } ?: cached
        return@withContext applyCachedSourceQualities(merged)
    }
    contentCache?.readAnimeWithVideos(
        language = contentLanguage,
        userId = cacheUserId(),
        animeId = animeId,
    )?.let { cached ->
        offlineFallbackActive = false
        return@withContext applyCachedSourceQualities(
            cached.videos.withOfflineDownloads(offline?.videos.orEmpty(), cached.details),
        )
    }

    try {
        val videos = api.getVideos(animeId, authStorage?.readToken())
        val mergedVideos = offline?.let { entry ->
            videos.withOfflineDownloads(entry.videos, entry.details)
        } ?: videos
        val cachedQualities = applyCachedSourceQualities(mergedVideos)
        contentCache?.saveVideos(
            language = contentLanguage,
            userId = cacheUserId(),
            animeId = animeId,
            videos = cachedQualities.map { it.withoutOfflinePlayback() },
        )
        cachedQualities
    } catch (throwable: Throwable) {
        throwable.throwIfCancellation()
        offline?.videos ?: throw throwable
    }
}

// RepositoryCatalogData
internal fun YummyAnimeRepository.repositoryUpdateContentLanguage(language: ContentLanguage) {
    contentLanguage = language
    api.updateContentLanguage(language)
}

internal suspend fun YummyAnimeRepository.repositoryGetFeatured(
    filters: BrowseFilters,
    offset: Int,
    limit: Int,
): List<Anime> = withContext(Dispatchers.IO) {
    loadRepositoryAnimePage(
        query = null,
        filters = filters,
        offset = offset,
        limit = limit,
    )
}

internal suspend fun YummyAnimeRepository.repositorySearch(
    query: String,
    filters: BrowseFilters,
    offset: Int,
    limit: Int,
): List<Anime> = withContext(Dispatchers.IO) {
    loadRepositoryAnimePage(
        query = query,
        filters = filters,
        offset = offset,
        limit = limit,
    )
}

private suspend fun YummyAnimeRepository.loadRepositoryAnimePage(
    query: String?,
    filters: BrowseFilters,
    offset: Int,
    limit: Int,
): List<Anime> {
    if (filters.offlineOnly) {
        offlineFallbackActive = false
        return offlineAnimePage(query.orEmpty(), filters, offset, limit)
    }

    val token = authStorage?.readToken()
    val userMarkIds = resolveUserMarkAnimeIds(filters, token)
    if (userMarkIds?.includedIds != null && userMarkIds.includedIds.isEmpty()) return emptyList()

    readCachedAnimePage(query, filters, offset, limit)?.let { cached ->
        offlineFallbackActive = false
        return cached
    }

    return try {
        offlineFallbackActive = false
        fetchAnimePage(query, filters, offset, limit, token, userMarkIds)
            .filterNot { it.id in userMarkIds?.excludedIds.orEmpty() }
            .also { animes -> saveCachedAnimePage(query, filters, offset, limit, animes) }
    } catch (throwable: Throwable) {
        throwable.throwIfCancellation()
        val offline = offlineFallbackAnimePage(query.orEmpty(), filters, offset, limit)
        if (offline != null) {
            offlineFallbackActive = true
            offline
        } else {
            throw throwable
        }
    }
}

private fun YummyAnimeRepository.readCachedAnimePage(
    query: String?,
    filters: BrowseFilters,
    offset: Int,
    limit: Int,
): List<Anime>? {
    val cache = contentCache ?: return null
    return if (query == null) {
        cache.readFeatured(
            language = contentLanguage,
            userId = cacheUserId(),
            filters = filters,
            offset = offset,
            limit = limit,
        )
    } else {
        cache.readSearch(
            language = contentLanguage,
            userId = cacheUserId(),
            query = query,
            filters = filters,
            offset = offset,
            limit = limit,
        )
    }
}

private suspend fun YummyAnimeRepository.fetchAnimePage(
    query: String?,
    filters: BrowseFilters,
    offset: Int,
    limit: Int,
    token: String?,
    userMarkIds: UserMarkFilterIds?,
): List<Anime> {
    return if (query == null) {
        api.featuredAnime(
            limit = limit,
            offset = offset,
            filters = filters,
            authToken = token,
            ids = userMarkIds?.includedIds.orEmpty(),
        )
    } else {
        api.search(
            query = query,
            limit = limit,
            offset = offset,
            filters = filters,
            authToken = token,
            ids = userMarkIds?.includedIds.orEmpty(),
        )
    }
}

private fun YummyAnimeRepository.saveCachedAnimePage(
    query: String?,
    filters: BrowseFilters,
    offset: Int,
    limit: Int,
    animes: List<Anime>,
) {
    val cache = contentCache ?: return
    if (query == null) {
        cache.saveFeatured(
            language = contentLanguage,
            userId = cacheUserId(),
            filters = filters,
            offset = offset,
            limit = limit,
            animes = animes,
        )
    } else {
        cache.saveSearch(
            language = contentLanguage,
            userId = cacheUserId(),
            query = query,
            filters = filters,
            offset = offset,
            limit = limit,
            animes = animes,
        )
    }
}

private fun YummyAnimeRepository.offlineAnimePage(
    query: String,
    filters: BrowseFilters,
    offset: Int,
    limit: Int,
): List<Anime> {
    return offlineStorage?.readAll()
        .orEmpty()
        .filteredOfflineAnime(query = query, filters = filters)
        .drop(offset)
        .take(limit)
}

private fun YummyAnimeRepository.offlineFallbackAnimePage(
    query: String,
    filters: BrowseFilters,
    offset: Int,
    limit: Int,
): List<Anime>? = offlineAnimePage(
    query = query,
    filters = filters,
    offset = offset,
    limit = limit,
).takeIf { it.isNotEmpty() }

internal suspend fun YummyAnimeRepository.repositoryGetFilterCatalog(): FilterCatalog =
    withContext(Dispatchers.IO) {
        contentCache?.readFilterCatalog(contentLanguage)?.let { return@withContext it }
        api.getFilterCatalog().also { catalog ->
            contentCache?.saveFilterCatalog(contentLanguage, catalog)
        }
    }

internal suspend fun YummyAnimeRepository.repositoryGetSchedule(): List<ScheduleAnime> =
    withContext(Dispatchers.IO) {
        contentCache?.readSchedule(contentLanguage)?.let { return@withContext it }
        api.getSchedule().also { schedule ->
            contentCache?.saveSchedule(contentLanguage, schedule)
        }
    }

private suspend fun YummyAnimeRepository.resolveUserMarkAnimeIds(
    filters: BrowseFilters,
    token: String?,
): UserMarkFilterIds? {
    if (filters.userMarks.isEmpty() && filters.excludedUserMarks.isEmpty()) return null
    val userId = authStorage?.readProfile()?.id
        ?: return UserMarkFilterIds(emptySet(), emptySet())
    val authToken = token?.takeIf { it.isNotBlank() }
        ?: return UserMarkFilterIds(emptySet(), emptySet())
    val selectedMarkIds = filters.userMarks.mapNotNull { it.toIntOrNull() }.toSet()
    val excludedMarkIds = filters.excludedUserMarks.mapNotNull { it.toIntOrNull() }.toSet()

    suspend fun resolve(markIds: Set<Int>): Set<Long> = buildSet {
        markIds.filterNot { it == FAVORITES_FILTER_ID }
            .forEach { listId -> addAll(api.getUserListAnimeIds(userId, listId, authToken)) }

        if (FAVORITES_FILTER_ID in markIds) {
            addAll(api.getUserFavoriteAnimeIds(userId, authToken))
        }
    }

    val includedIds = if (selectedMarkIds.isNotEmpty()) resolve(selectedMarkIds) else null
    val excludedIds = if (excludedMarkIds.isNotEmpty()) resolve(excludedMarkIds) else emptySet()
    return UserMarkFilterIds(
        includedIds = includedIds,
        excludedIds = excludedIds,
    )
}

// RepositoryCommunityData
internal suspend fun YummyAnimeRepository.repositoryGetCollections(
    offset: Int,
    limit: Int,
): List<AnimeCollectionSummary> = withContext(Dispatchers.IO) {
    api.getCollections(offset = offset, limit = limit)
}

internal suspend fun YummyAnimeRepository.repositoryGetCollection(
    id: Long,
): AnimeCollectionSummary = withContext(Dispatchers.IO) {
    api.getCollection(id)
}

internal suspend fun YummyAnimeRepository.repositoryGetAnimeCollections(
    animeId: Long,
): List<AnimeCollectionSummary> = withContext(Dispatchers.IO) {
    api.getAnimeCollections(animeId)
}

internal suspend fun YummyAnimeRepository.repositoryGetAnimeComments(
    animeId: Long,
    offset: Int,
    limit: Int,
): List<AnimeComment> = withContext(Dispatchers.IO) {
    api.getAnimeComments(animeId, offset = offset, limit = limit)
}

internal suspend fun YummyAnimeRepository.repositoryAddAnimeComment(
    animeId: Long,
    text: String,
): AnimeComment? = withContext(Dispatchers.IO) {
    api.addAnimeComment(animeId, text, requireToken())
}

internal suspend fun YummyAnimeRepository.repositoryGetAnimeRecommendations(
    animeId: Long,
): List<Anime> = withContext(Dispatchers.IO) {
    api.getAnimeRecommendations(animeId)
}

internal suspend fun YummyAnimeRepository.repositoryGetAnimeRatingSummary(
    animeId: Long,
): AnimeRatingSummary = withContext(Dispatchers.IO) {
    api.getAnimeRatingSummary(animeId)
}

internal suspend fun YummyAnimeRepository.repositorySetAnimeRating(
    animeId: Long,
    rating: Int,
): AnimeRatingSummary = withContext(Dispatchers.IO) {
    api.setAnimeRating(animeId, rating, requireToken())
}

internal suspend fun YummyAnimeRepository.repositoryDeleteAnimeRating(
    animeId: Long,
): AnimeRatingSummary = withContext(Dispatchers.IO) {
    api.deleteAnimeRating(animeId, requireToken())
}

internal suspend fun YummyAnimeRepository.repositorySubscribeVideo(
    videoId: Long,
): Boolean = withContext(Dispatchers.IO) {
    api.subscribeVideo(videoId, requireToken())
}

internal suspend fun YummyAnimeRepository.repositoryUnsubscribeVideo(
    videoId: Long,
): Boolean = withContext(Dispatchers.IO) {
    api.unsubscribeVideo(videoId, requireToken())
}

internal suspend fun YummyAnimeRepository.repositoryGetVideoSubscriptions(userId: Long): List<VideoSubscription> =
    withContext(Dispatchers.IO) {
        api.getVideoSubscriptions(userId, requireToken())
    }

internal suspend fun YummyAnimeRepository.repositoryGetNewEpisodeNotifications(
    limit: Int,
): List<SiteNotification> {
    return repositoryGetProfileNotifications(
        types = listOf("anime_episode"),
        subTypes = listOf("new_episode"),
        offset = 0,
        limit = limit,
    )
}

internal suspend fun YummyAnimeRepository.repositoryGetProfileNotifications(
    types: List<String>,
    subTypes: List<String>,
    offset: Int,
    limit: Int,
): List<SiteNotification> = withContext(Dispatchers.IO) {
    api.getProfileNotifications(
        token = requireToken(),
        types = types,
        subTypes = subTypes,
        offset = offset,
        limit = limit,
    )
}

internal suspend fun YummyAnimeRepository.repositoryMarkProfileNotificationsRead(): Boolean =
    withContext(Dispatchers.IO) {
        api.markProfileNotificationsRead(requireToken())
    }

internal suspend fun YummyAnimeRepository.repositoryMarkProfileNotificationRead(
    notificationId: Long,
): Boolean = withContext(Dispatchers.IO) {
    api.markProfileNotificationRead(notificationId, requireToken())
}

internal suspend fun YummyAnimeRepository.repositoryDeleteProfileNotification(
    notificationId: Long,
): Boolean = withContext(Dispatchers.IO) {
    api.deleteProfileNotification(notificationId, requireToken())
}

// RepositoryDownloadQualities
internal data class SourceQualityResolveResult(
    val candidate: VideoVariant,
    val qualities: List<SourceQuality>,
)


internal fun List<SourceQualityResolveResult>.availableDownloadHeights(allEpisodes: Boolean): Set<Int> {
    if (isEmpty()) return emptySet()
    if (!allEpisodes) {
        return flatMap { it.qualities }
            .normalizedSourceQualities()
            .mapNotNullTo(mutableSetOf()) { it.height }
    }

    val heightsByEpisode = groupBy { it.candidate.downloadEpisodeSlotKey }
        .values
        .map { episodeSources ->
            episodeSources
                .flatMap { it.qualities }
                .normalizedSourceQualities()
                .mapNotNullTo(mutableSetOf()) { it.height }
        }
        .filter { it.isNotEmpty() }
    if (heightsByEpisode.isEmpty()) return emptySet()
    return heightsByEpisode.reduce { common, episodeHeights ->
        common.intersect(episodeHeights).toMutableSet()
    }
}

// RepositoryPlaybackResolution
internal const val SOURCE_RESOLVE_TIMEOUT_MS = 12_000L
internal const val CVH_SOURCE_RESOLVE_TIMEOUT_MS = 25_000L
internal const val RUNTIME_SOURCE_RESOLVE_TIMEOUT_MS = 45_000L

internal fun VideoVariant.sourceResolveTimeoutMs(): Long {
    val source = listOf(url, player.cleanVideoSourceLabel())
        .joinToString(" ")
        .lowercase(Locale.ROOT)
    return when {
        "alloha" in source || "alloh" in source -> RUNTIME_SOURCE_RESOLVE_TIMEOUT_MS
        "cvh" in source || "cdnvideohub" in source || "iframecvh" in source -> CVH_SOURCE_RESOLVE_TIMEOUT_MS
        else -> SOURCE_RESOLVE_TIMEOUT_MS
    }
}

internal data class SourceResolveAttempt(
    val index: Int,
    val candidate: VideoVariant,
    val playback: ResolvedPlayback? = null,
    val failure: Throwable? = null,
)

private fun List<SourceResolveAttempt>.successfulPlaybacks(): List<Pair<Int, ResolvedPlayback>> {
    return mapNotNull { attempt -> attempt.playback?.let { playback -> attempt.index to playback } }
}

internal fun List<SourceResolveAttempt>.bestPlayback(
    selectableKeys: Set<String>? = null,
): ResolvedPlayback? {
    return successfulPlaybacks()
        .filter { (_, playback) ->
            selectableKeys == null || playback.video.sourceResolveIdentity() in selectableKeys
        }
        .sortedWith(
            compareByDescending<Pair<Int, ResolvedPlayback>> { (_, playback) -> playback.video.isOfflineAvailable }
                .thenByDescending { (_, playback) -> playback.stream.sourceResolutionHeight() }
                .thenByDescending { (_, playback) -> playback.stream.hasSubtitles }
                .thenBy { (index, _) -> index },
        )
        .firstOrNull()
        ?.second
}

internal fun ResolvedPlayback.withMetadataFromAttempts(
    attempts: List<SourceResolveAttempt>,
): ResolvedPlayback {
    val sameEpisodeAttempts = attempts
        .filter { attempt -> attempt.candidate.isSameEpisodeAs(video) }
    val sameVoiceAttempts = sameEpisodeAttempts
        .filter { attempt -> attempt.candidate.hasSameVoiceAs(video) }

    return withMergedPlaybackMetadata(
        metadataPlaybacks = sameVoiceAttempts
            .asSequence()
            .mapNotNull { attempt -> attempt.playback }
            .toList(),
    )
}

internal fun ResolvedPlayback.withMergedPlaybackMetadata(
    metadataPlaybacks: List<ResolvedPlayback>,
): ResolvedPlayback {
    val sameVoicePlaybacks = metadataPlaybacks
        .asSequence()
        .filter { playback -> playback.matchesMetadataTarget(video) }
        .toList()
    val sameSourcePlaybacks = sameVoicePlaybacks
        .filter { playback -> playback.video.sourceResolveIdentity() == video.sourceResolveIdentity() }
    val sameSourceStream = sameSourcePlaybacks.preferredMetadataStream(video, stream)
    val mergedQualities = stream.mergedMetadataQualities(sameSourcePlaybacks)
    val sourceSubtitleSourceKeys = stream.mergedSubtitleSourceKeys(sameSourcePlaybacks)
    if (stream.hasMergedMetadata(sameSourceStream, mergedQualities, sourceSubtitleSourceKeys)) return this

    return copy(
        stream = stream.copy(
            subtitles = sameSourceStream.subtitles,
            embeddedSubtitles = sameSourceStream.embeddedSubtitles,
            hasEmbeddedSubtitles = sameSourceStream.hasEmbeddedSubtitles,
            availableQualities = mergedQualities,
            sourceSubtitleSourceKeys = sourceSubtitleSourceKeys,
        ),
    )
}

private fun ResolvedPlayback.matchesMetadataTarget(target: VideoVariant): Boolean {
    return video.isSameEpisodeAs(target) && video.hasSameVoiceAs(target)
}

private fun List<ResolvedPlayback>.preferredMetadataStream(
    target: VideoVariant,
    fallback: ResolvedVideoStream,
): ResolvedVideoStream {
    val targetSourceIdentity = target.sourceResolveIdentity()
    return filter { playback -> playback.video.sourceResolveIdentity() == targetSourceIdentity }
        .maxWithOrNull(
            compareBy<ResolvedPlayback> { playback -> if (playback.stream.hasSubtitles) 1 else 0 }
                .thenBy { playback -> playback.stream.availableQualities.size },
        )
        ?.stream
        ?: fallback
}

private fun ResolvedVideoStream.mergedMetadataQualities(
    playbacks: List<ResolvedPlayback>,
): List<SourceQuality> {
    return (sourceQualitiesWithMax() + playbacks.flatMap { playback ->
        playback.stream.sourceQualitiesWithMax()
    }).normalizedSourceQualities()
}

private fun ResolvedVideoStream.mergedSubtitleSourceKeys(
    playbacks: List<ResolvedPlayback>,
): Set<String> {
    return (sourceSubtitleSourceKeys + playbacks.mapNotNull(ResolvedPlayback::resolvedSubtitleSourceKey)).toSet()
}

private fun ResolvedPlayback.resolvedSubtitleSourceKey(): String? {
    if (!stream.hasResolvedSubtitles) return null
    return video.matchingSourceKey.takeIf(String::isNotBlank)
}

private fun ResolvedVideoStream.hasMergedMetadata(
    subtitleStream: ResolvedVideoStream,
    qualities: List<SourceQuality>,
    subtitleSourceKeys: Set<String>,
): Boolean {
    val sameSubtitleMetadata = subtitles == subtitleStream.subtitles &&
        embeddedSubtitles == subtitleStream.embeddedSubtitles &&
        hasEmbeddedSubtitles == subtitleStream.hasEmbeddedSubtitles
    val sameQualities = qualities == availableQualities.normalizedSourceQualities()
    return sameSubtitleMetadata && sameQualities && subtitleSourceKeys == sourceSubtitleSourceKeys
}

private fun ResolvedVideoStream.sourceQualitiesWithMax(): List<SourceQuality> {
    return availableQualities + listOfNotNull(maxVideoHeight?.let { SourceQuality(height = it) })
}

internal fun List<SourceResolveAttempt>.downloadPlaybacks(preferredQuality: PreferredQuality): List<ResolvedPlayback> {
    val preferredHeight = preferredQuality.height
    return successfulPlaybacks()
        .filter { (_, playback) ->
            preferredHeight == null || playback.stream.hasExactDownloadQuality(preferredHeight)
        }
        .sortedWith(
            compareByDescending<Pair<Int, ResolvedPlayback>> { (_, playback) ->
                playback.stream.qualityScore(preferredQuality)
            }.thenBy { (index, _) -> index },
        )
        .map { it.second }
}

internal fun List<SourceResolveAttempt>.resolveFailure(message: String): IOException {
    val details = mapNotNull { attempt ->
        attempt.failure?.let { throwable ->
            "${attempt.candidate.groupTitle.ifBlank { attempt.candidate.player }}: ${throwable.message.orEmpty()}"
        }
    }
        .take(4)
        .joinToString("; ")
        .takeIf { it.isNotBlank() }

    return IOException(
        buildString {
            append(message)
            if (details != null) append(": ").append(details)
        },
    )
}

// RepositoryVideoSources
internal suspend fun YummyAnimeRepository.repositoryResolveVideoStream(
    video: VideoVariant,
    preferredQuality: PreferredQuality,
    waitForRuntimeSubtitles: Boolean,
): ResolvedVideoStream {
    val localFile = video.primaryOfflineFile()
    if (localFile != null) {
        return ResolvedVideoStream(
            url = localFile.playbackUrl,
            mimeType = localFile.mimeType,
            headers = emptyMap(),
            maxVideoHeight = null,
        )
    }
    return videoStreamResolver.resolve(
        video = video,
        preferredQuality = preferredQuality,
        waitForRuntimeSubtitles = waitForRuntimeSubtitles,
    ).also { stream ->
        withContext(Dispatchers.IO) {
            runCatching { sourceQualityCache?.save(video, stream) }
        }
    }
}

internal suspend fun YummyAnimeRepository.repositoryResolveAvailableDownloadQualities(
    requested: VideoVariant,
    videos: List<VideoVariant>,
    allEpisodes: Boolean,
): List<PreferredQuality> = withContext(Dispatchers.IO) {
    val candidates = applyCachedSourceQualities(
        videos.downloadQualityCandidatesFor(requested, allEpisodes)
            .map { it.withoutOfflinePlayback() },
    ).distinctBy { it.sourceResolveIdentity() }
    if (candidates.isEmpty()) return@withContext emptyList()

    val heights = repositoryResolveSourceQualityResults(candidates)
        .availableDownloadHeights(allEpisodes)

    PreferredQuality.entries
        .asSequence()
        .filter { it.height != null && it.height in heights }
        .sortedByDescending { it.height ?: 0 }
        .toList()
}

internal suspend fun YummyAnimeRepository.repositoryResolveSampledDownloadQualities(
    voiceKeys: Set<String>,
    videos: List<VideoVariant>,
): Map<String, List<PreferredQuality>> = withContext(Dispatchers.IO) {
    val requestedVoiceKeys = voiceKeys.filter { it.isNotBlank() }.toSet()
    if (requestedVoiceKeys.isEmpty()) return@withContext emptyMap()
    val candidates = applyCachedSourceQualities(
        videos
            .asSequence()
            .filter { it.downloadSampleVoiceKey in requestedVoiceKeys }
            .groupBy {
                "${it.downloadSampleVoiceKey}|${it.player.cleanVideoSourceLabel().lowercase(Locale.ROOT)}"
            }
            .values
            .mapNotNull { group -> group.selectDownloadQualitySampleCandidate() }
            .map { it.withoutOfflinePlayback() },
    ).distinctBy { it.sourceResolveIdentity() }
    if (candidates.isEmpty()) return@withContext emptyMap()

    repositoryResolveSourceQualityResults(candidates)
        .groupBy { result -> result.candidate.downloadSampleVoiceKey }
        .mapValues { (_, results) ->
            results
                .flatMap { it.qualities }
                .normalizedSourceQualities()
                .mapNotNull { quality -> quality.height }
                .distinct()
                .sortedDescending()
                .mapNotNull { height -> PreferredQuality.fromHeight(height) }
        }
        .filterValues { qualities -> qualities.isNotEmpty() }
}

private suspend fun YummyAnimeRepository.repositoryResolveSourceQualityResults(
    candidates: List<VideoVariant>,
): List<SourceQualityResolveResult> {
    val knownQualities = candidates.map { candidate ->
        SourceQualityResolveResult(candidate, candidate.sourceQualities)
    }
    val missingCandidates = candidates.filter { it.sourceQualities.isEmpty() }
    val resolvedQualities = supervisorScope {
        missingCandidates.map { candidate ->
            async {
                runCatching {
                    withTimeout(candidate.sourceResolveTimeoutMs()) {
                        SourceQualityResolveResult(
                            candidate,
                            repositoryResolveVideoStream(
                                video = candidate,
                                preferredQuality = PreferredQuality.Auto,
                                waitForRuntimeSubtitles = true,
                            ).availableQualities,
                        )
                    }
                }.getOrElse {
                    sourceQualityCache?.remove(candidate)
                    SourceQualityResolveResult(candidate, emptyList())
                }
            }
        }.awaitAll()
    }
    return knownQualities + resolvedQualities
}

internal suspend fun YummyAnimeRepository.repositoryResolveBestPlaybackSource(
    candidates: List<VideoVariant>,
    preferredQuality: PreferredQuality,
    metadataCandidates: List<VideoVariant>,
    waitForRuntimeSubtitles: Boolean,
): ResolvedPlayback {
    val uniqueCandidates = candidates.distinctBy { it.sourceResolveIdentity() }.ifEmpty {
        throw IOException("No sources are available for the episode")
    }

    val selectableKeys = uniqueCandidates.mapTo(mutableSetOf()) { it.sourceResolveIdentity() }
    val uniqueMetadataCandidates = (uniqueCandidates + metadataCandidates)
        .distinctBy { it.sourceResolveIdentity() }

    val attempts = repositoryResolveCandidateAttempts(
        candidates = uniqueMetadataCandidates,
        preferredQuality = preferredQuality,
        waitForRuntimeSubtitles = waitForRuntimeSubtitles,
    )
    val best = attempts.bestPlayback(selectableKeys)
    if (best != null) return best.withMetadataFromAttempts(attempts)

    throw attempts.resolveFailure("Could not start any episode source")
}

internal suspend fun YummyAnimeRepository.repositoryResolvePlaybackMetadata(
    playback: ResolvedPlayback,
    metadataCandidates: List<VideoVariant>,
    preferredQuality: PreferredQuality,
): ResolvedPlayback {
    val candidates = (listOf(playback.video) + metadataCandidates)
        .filter { candidate ->
            candidate.isSameEpisodeAs(playback.video) &&
                candidate.hasSameVoiceAs(playback.video) &&
                candidate.sourceResolveIdentity() == playback.video.sourceResolveIdentity()
        }
        .distinctBy { it.sourceResolveIdentity() }
        .ifEmpty { return playback }
    val attempts = repositoryResolveCandidateAttempts(
        candidates = candidates,
        preferredQuality = preferredQuality,
        waitForRuntimeSubtitles = true,
    )
    return playback.withMetadataFromAttempts(
        attempts + SourceResolveAttempt(
            index = -1,
            candidate = playback.video,
            playback = playback,
        ),
    )
}

internal suspend fun YummyAnimeRepository.repositoryResolveDownloadPlaybacks(
    requested: VideoVariant,
    videos: List<VideoVariant>,
    preferredQuality: PreferredQuality,
): List<ResolvedPlayback> {
    val uniqueCandidates = applyCachedSourceQualities(
        videos.downloadCandidatesFor(requested)
            .map { it.withoutOfflinePlayback() },
    ).distinctBy { it.sourceResolveIdentity() }
        .ifEmpty {
            throw IOException("No online sources are available for downloading this episode")
        }

    val attempts = repositoryResolveCandidateAttempts(uniqueCandidates, preferredQuality)
    val playbacks = attempts.downloadPlaybacks(preferredQuality)
    if (playbacks.isNotEmpty()) return playbacks

    val requestedHeight = preferredQuality.height
    if (requestedHeight != null && attempts.any { it.playback != null }) {
        throw IOException(
            "No working source with ${preferredQuality.title} quality is available for download",
        )
    }
    throw attempts.resolveFailure("Could not find a working source for download")
}

private suspend fun YummyAnimeRepository.repositoryResolveCandidateAttempts(
    candidates: List<VideoVariant>,
    preferredQuality: PreferredQuality,
    waitForRuntimeSubtitles: Boolean = true,
): List<SourceResolveAttempt> {
    return supervisorScope {
        candidates.mapIndexed { index, candidate ->
            async {
                runCatching {
                    withTimeout(candidate.sourceResolveTimeoutMs()) {
                        videoStreamResolver.resolve(
                            video = candidate,
                            preferredQuality = preferredQuality,
                            waitForRuntimeSubtitles = waitForRuntimeSubtitles,
                        )
                    }
                }.fold(
                    onSuccess = { stream ->
                        withContext(Dispatchers.IO) {
                            runCatching { sourceQualityCache?.save(candidate, stream) }
                        }
                        val playback = ResolvedPlayback(
                            video = candidate,
                            stream = stream.withSourceSubtitleVideo(candidate),
                        )
                        SourceResolveAttempt(
                            index = index,
                            candidate = candidate,
                            playback = playback,
                        )
                    },
                    onFailure = { throwable ->
                        SourceResolveAttempt(
                            index = index,
                            candidate = candidate,
                            failure = throwable,
                        )
                    },
                )
            }
        }.awaitAll()
    }
}

// YummyAnimeRepositoryFacade
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

    suspend fun getAnimeWithVideos(
        animeAlias: String,
    ): Pair<AnimeDetails, List<VideoVariant>> = repositoryGetAnimeWithVideos(animeAlias)

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

    suspend fun getVideoSubscriptions(userId: Long): List<VideoSubscription> =
        repositoryGetVideoSubscriptions(userId)

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
