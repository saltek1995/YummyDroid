package me.yummydroid.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

fun Context.hasInternetConnection(): Boolean {
    val connectivity = getSystemService(ConnectivityManager::class.java) ?: return false
    val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

// A response may only populate the language/account/cache generation that issued it.
private class RepositoryContentRequest(
    private val repository: YummyAnimeRepository,
    val language: ContentLanguage,
    private val revision: Long,
    private val session: StoredAuthSession?,
    private val cacheGeneration: Long?,
) {
    val token: String? get() = session?.token
    val userId: Long? get() = session?.profile?.id

    fun publish(action: () -> Unit) = synchronized(repository.contentContextLock) {
        if (repository.contentRevision != revision) return@synchronized
        val publish = {
            val cache = repository.contentCache
            if (cache != null && cacheGeneration != null) cache.publishIfCurrent(cacheGeneration, action) else action()
        }
        val auth = repository.authStorage
        if (auth == null) publish() else auth.withSession(session, publish)
        Unit
    }
}

private fun YummyAnimeRepository.contentRequest(): RepositoryContentRequest = synchronized(contentContextLock) {
    RepositoryContentRequest(this, contentLanguage, contentRevision, authStorage?.readSession(), contentCache?.generation())
}

// RepositoryAccountData
internal suspend fun YummyAnimeRepository.repositoryRestoreProfile(): UserProfile? =
    withContext(Dispatchers.IO) {
        val storage = authStorage ?: return@withContext null
        val token = storage.readToken() ?: run {
            storage.clearIfToken(null)
            return@withContext storage.readProfile()
        }
        val cachedProfile = storage.readProfile()
        if (!isNetworkAvailable()) return@withContext cachedProfile
        val refreshedToken = runCatching { api.refreshToken(token) }.getOrElse { throwable ->
            currentCoroutineContext().ensureActive()
            throwable.throwIfCancellation()
            if (throwable.isUnauthorizedApiError()) {
                if (storage.clearIfToken(token)) throw throwable
                return@withContext storage.readProfile()
            }
            token
        }
        currentCoroutineContext().ensureActive()
        if (!storage.refreshToken(token, refreshedToken)) return@withContext storage.readProfile()
        val profile = try {
            api.getProfile(refreshedToken)
        } catch (throwable: Throwable) {
            currentCoroutineContext().ensureActive()
            throwable.throwIfCancellation()
            if (storage.readToken() != refreshedToken) return@withContext storage.readProfile()
            if (throwable.isUnauthorizedApiError()) {
                if (storage.clearIfToken(refreshedToken)) throw throwable
                return@withContext storage.readProfile()
            }
            return@withContext cachedProfile ?: throw throwable
        }
        currentCoroutineContext().ensureActive()
        if (storage.refreshProfile(refreshedToken, profile)) profile else storage.readProfile()
    }

internal suspend fun YummyAnimeRepository.repositoryLogin(
    login: String,
    password: String,
    captchaResponse: String?,
): UserProfile = withContext(Dispatchers.IO) {
    val token = api.login(login, password, captchaResponse)
    val profile = api.getProfile(token)
    currentCoroutineContext().ensureActive()
    authStorage?.saveSession(token, profile)
    profile
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

// The origin belongs to this response, never to the repository's latest request.
data class RepositoryContent<out T>(val value: T, val offlineFallback: Boolean = false)

// RepositoryAnimeDetailsData
internal suspend fun YummyAnimeRepository.repositoryGetAnimeWithVideos(
    animeId: Long,
): RepositoryContent<Pair<AnimeDetails, List<VideoVariant>>> = repositoryGetAnimeWithVideos(
    cachedAnimeId = animeId,
) { token ->
    api.getAnimeWithVideos(animeId, token)
}

internal suspend fun YummyAnimeRepository.repositoryGetAnimeWithVideos(
    animeAlias: String,
): RepositoryContent<Pair<AnimeDetails, List<VideoVariant>>> = repositoryGetAnimeWithVideos(cachedAnimeId = null) { token ->
    api.getAnimeWithVideos(animeAlias, token)
}

private suspend fun YummyAnimeRepository.repositoryGetAnimeWithVideos(
    cachedAnimeId: Long?,
    fetch: suspend (String?) -> Pair<AnimeDetails, List<VideoVariant>>,
): RepositoryContent<Pair<AnimeDetails, List<VideoVariant>>> = withContext(Dispatchers.IO) {
    val request = contentRequest()
    var offline = cachedAnimeId?.let { offlineStorage?.read(it) }
    if (!isNetworkAvailable()) {
        return@withContext getOfflineAnimeWithVideos(cachedAnimeId ?: 0L)
    }
    if (cachedAnimeId != null) {
        contentCache?.readAnimeWithVideos(
            language = request.language,
            userId = request.userId,
            animeId = cachedAnimeId,
        )?.let { cached ->
            return@withContext RepositoryContent(cached.details to applyCachedSourceQualities(
                cached.videos.withOfflineDownloads(offline?.videos.orEmpty()),
            ))
        }
    }

    try {
        val (details, videos) = fetch(request.token)
        if (offline == null) offline = offlineStorage?.read(details.id)
        val mergedVideos = applyCachedSourceQualities(
            videos.withOfflineDownloads(offline?.videos.orEmpty()),
        )
        request.publish {
            contentCache?.saveAnimeWithVideos(
                language = request.language,
                userId = request.userId,
                animeId = details.id,
                value = CachedAnimeWithVideos(
                    details = details,
                    videos = mergedVideos.map { it.withoutOfflinePlayback() },
                ),
            )
            offlineStorage?.saveAnime(details, mergedVideos)
        }
        RepositoryContent(details to mergedVideos)
    } catch (throwable: Throwable) {
        throwable.throwIfCancellation()
        offline?.let {
            RepositoryContent(it.details to it.videos, offlineFallback = true)
        } ?: throw throwable
    }
}

internal suspend fun YummyAnimeRepository.repositoryGetAnime(
    animeId: Long,
): AnimeDetails = withContext(Dispatchers.IO) {
    val request = contentRequest()
    contentCache?.readAnimeWithVideos(
        language = request.language,
        userId = request.userId,
        animeId = animeId,
    )?.details?.let { return@withContext it }

    try {
        api.getAnime(animeId, request.token)
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
    val request = contentRequest()
    val offline = offlineStorage?.read(animeId)
    contentCache?.readVideos(
        language = request.language,
        userId = request.userId,
        animeId = animeId,
    )?.let { cached ->
        val merged = offline?.let { entry ->
            cached.withOfflineDownloads(entry.videos)
        } ?: cached
        return@withContext applyCachedSourceQualities(merged)
    }
    contentCache?.readAnimeWithVideos(
        language = request.language,
        userId = request.userId,
        animeId = animeId,
    )?.let { cached ->
        return@withContext applyCachedSourceQualities(
            cached.videos.withOfflineDownloads(offline?.videos.orEmpty()),
        )
    }

    try {
        val videos = api.getVideos(animeId, request.token)
        val mergedVideos = offline?.let { entry ->
            videos.withOfflineDownloads(entry.videos)
        } ?: videos
        val cachedQualities = applyCachedSourceQualities(mergedVideos)
        request.publish {
            contentCache?.saveVideos(
                language = request.language,
                userId = request.userId,
                animeId = animeId,
                videos = cachedQualities.map { it.withoutOfflinePlayback() },
            )
        }
        cachedQualities
    } catch (throwable: Throwable) {
        throwable.throwIfCancellation()
        offline?.videos ?: throw throwable
    }
}

// RepositoryCatalogData
internal fun YummyAnimeRepository.repositoryUpdateContentLanguage(language: ContentLanguage) {
    synchronized(contentContextLock) {
        if (contentLanguage != language) contentRevision += 1L
        contentLanguage = language
        api.updateContentLanguage(language)
    }
}

internal suspend fun YummyAnimeRepository.repositoryGetFeatured(
    filters: BrowseFilters,
    offset: Int,
    limit: Int,
): RepositoryContent<List<Anime>> = withContext(Dispatchers.IO) {
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
): RepositoryContent<List<Anime>> = withContext(Dispatchers.IO) {
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
): RepositoryContent<List<Anime>> {
    val disconnected = !isNetworkAvailable()
    if (filters.offlineOnly || disconnected) {
        return RepositoryContent(offlineAnimePage(query.orEmpty(), filters, offset, limit), offlineFallback = disconnected)
    }

    val request = contentRequest()
    val userMarkIds = resolveUserMarkAnimeIds(filters, request.token, request.userId)
    if (userMarkIds?.includedIds != null && userMarkIds.includedIds.isEmpty()) return RepositoryContent(emptyList())

    readCachedAnimePage(request, query, filters, offset, limit)?.let { cached ->
        return RepositoryContent(cached)
    }

    return try {
        fetchAnimePage(query, filters, offset, limit, request.token, userMarkIds)
            .filterNot { it.id in userMarkIds?.excludedIds.orEmpty() }
            .also { animes -> request.publish { saveCachedAnimePage(request, query, filters, offset, limit, animes) } }
            .let { RepositoryContent(it) }
    } catch (throwable: Throwable) {
        throwable.throwIfCancellation()
        val offline = offlineFallbackAnimePage(query.orEmpty(), filters, offset, limit)
        if (offline != null) {
            RepositoryContent(offline, offlineFallback = true)
        } else {
            throw throwable
        }
    }
}

private fun YummyAnimeRepository.readCachedAnimePage(
    request: RepositoryContentRequest,
    query: String?,
    filters: BrowseFilters,
    offset: Int,
    limit: Int,
): List<Anime>? {
    val cache = contentCache ?: return null
    return if (query == null) {
        cache.readFeatured(
            language = request.language,
            userId = request.userId,
            filters = filters,
            offset = offset,
            limit = limit,
        )
    } else {
        cache.readSearch(
            language = request.language,
            userId = request.userId,
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
    request: RepositoryContentRequest,
    query: String?,
    filters: BrowseFilters,
    offset: Int,
    limit: Int,
    animes: List<Anime>,
) {
    val cache = contentCache ?: return
    if (query == null) {
        cache.saveFeatured(
            language = request.language,
            userId = request.userId,
            filters = filters,
            offset = offset,
            limit = limit,
            animes = animes,
        )
    } else {
        cache.saveSearch(
            language = request.language,
            userId = request.userId,
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
        val request = contentRequest()
        contentCache?.readFilterCatalog(request.language)?.let { return@withContext it }
        if (!isNetworkAvailable()) return@withContext FilterCatalog()
        api.getFilterCatalog().also { catalog ->
            request.publish { contentCache?.saveFilterCatalog(request.language, catalog) }
        }
    }

internal suspend fun YummyAnimeRepository.repositoryGetSchedule(): List<ScheduleAnime> =
    withContext(Dispatchers.IO) {
        val request = contentRequest()
        contentCache?.readSchedule(request.language)?.let { return@withContext it }
        if (!isNetworkAvailable()) return@withContext emptyList()
        api.getSchedule().also { schedule ->
            request.publish { contentCache?.saveSchedule(request.language, schedule) }
        }
    }

private suspend fun YummyAnimeRepository.resolveUserMarkAnimeIds(
    filters: BrowseFilters,
    token: String?,
    userId: Long?,
): UserMarkFilterIds? {
    if (filters.userMarks.isEmpty() && filters.excludedUserMarks.isEmpty()) return null
    if (userId == null) return UserMarkFilterIds(emptySet(), emptySet())
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

internal suspend fun YummyAnimeRepository.repositoryGetVideoSubscriptions(
    userId: Long,
): List<VideoSubscription> = withContext(Dispatchers.IO) {
    val token = requireToken()
    val subscriptions = api.getVideoSubscriptions(userId, token)
    val unresolvedAnimeIds = subscriptions
        .asSequence()
        .filter { subscription -> subscription.matchingVoiceKey.isBlank() }
        .map(VideoSubscription::animeId)
        .filter { animeId -> animeId > 0L }
        .distinct()
        .toList()
    if (unresolvedAnimeIds.isEmpty()) return@withContext subscriptions

    val requestLimiter = Semaphore(SUBSCRIPTION_DETAILS_MAX_CONCURRENCY)
    val videosByAnime = supervisorScope {
        unresolvedAnimeIds.map { animeId ->
            async {
                requestLimiter.withPermit {
                    runCatching { api.getVideos(animeId, token) }
                        .onFailure { throwable ->
                            throwable.throwIfCancellation()
                            if (throwable.isUnauthorizedApiError()) throw throwable
                        }
                        .getOrNull()
                        ?.let { videos -> animeId to videos }
                }
            }
        }.awaitAll().filterNotNull().toMap()
    }
    subscriptions.withResolvedSubscriptionVoices(videosByAnime)
}

private const val SUBSCRIPTION_DETAILS_MAX_CONCURRENCY = 3

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

internal suspend fun YummyAnimeRepository.repositorySynchronizeProfileNotifications(
    limit: Int,
    onNotifications: (UserProfile, List<SiteNotification>) -> Unit,
    onUnauthorized: () -> Unit,
): Unit = withContext(Dispatchers.IO) {
    val storage = authStorage ?: return@withContext
    val session = storage.readSession() ?: return@withContext
    val notifications = try {
        api.getProfileNotifications(session.token, emptyList(), emptyList(), 0, limit)
    } catch (failure: Throwable) {
        currentCoroutineContext().ensureActive()
        failure.throwIfCancellation()
        if (!failure.isUnauthorizedApiError()) throw failure
        storage.withSession(session) {
            storage.clear()
            onUnauthorized()
        }
        return@withContext
    }
    currentCoroutineContext().ensureActive()
    storage.withSession(session) { onNotifications(session.profile, notifications) }
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
    val request = contentRequest()
    video.offlinePlayback(preferredQuality)?.let { return it.stream }
    return videoStreamResolver.resolve(
        video = video,
        preferredQuality = preferredQuality,
        waitForRuntimeSubtitles = waitForRuntimeSubtitles,
    ).also { stream ->
        withContext(Dispatchers.IO) {
            runCatching { request.publish { sourceQualityCache?.save(video, stream) } }
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
            .toList()
            .downloadQualitySamples()
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
        missingCandidates.filter { downloadSourceCooldowns?.isAvailable(it) != false }.map { candidate ->
            async {
                runCatching {
                    withTimeout(candidate.sourceResolveTimeoutMs()) {
                        SourceQualityResolveResult(
                            candidate,
                            withDownloadSource(candidate) {
                                repositoryResolveVideoStream(
                                    video = candidate,
                                    preferredQuality = PreferredQuality.Auto,
                                    waitForRuntimeSubtitles = false,
                                ).availableQualities
                            },
                        )
                    }
                }.getOrElse {
                    currentCoroutineContext().ensureActive()
                    SourceQualityResolveResult(candidate, emptyList())
                }
            }
        }.awaitAll()
    }
    val results = knownQualities + resolvedQualities
    if (results.all { it.qualities.isEmpty() }) downloadSourceCooldowns?.waitingFor(candidates)?.let { throw it }
    return results
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

    val availableCandidates = uniqueCandidates.filter { downloadSourceCooldowns?.isAvailable(it) != false }
    if (availableCandidates.isEmpty()) downloadSourceCooldowns?.waitingFor(uniqueCandidates)?.let { throw it }
    val attempts = repositoryResolveCandidateAttempts(availableCandidates, preferredQuality, forDownload = true)
    val playbacks = attempts.downloadPlaybacks(preferredQuality)
    if (playbacks.isNotEmpty()) return playbacks
    downloadSourceCooldowns?.waitingFor(uniqueCandidates)?.let { throw it }

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
    forDownload: Boolean = false,
): List<SourceResolveAttempt> {
    val request = contentRequest()
    return supervisorScope {
        candidates.mapIndexed { index, candidate ->
            async {
                runCatching {
                    withTimeout(candidate.sourceResolveTimeoutMs()) {
                        val resolve = suspend {
                            videoStreamResolver.resolve(
                                video = candidate,
                                preferredQuality = preferredQuality,
                                waitForRuntimeSubtitles = waitForRuntimeSubtitles,
                            )
                        }
                        if (forDownload) withDownloadSource(candidate, resolve) else resolve()
                    }
                }.fold(
                    onSuccess = { stream ->
                        withContext(Dispatchers.IO) {
                            runCatching { request.publish { sourceQualityCache?.save(candidate, stream) } }
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
                        currentCoroutineContext().ensureActive()
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
    internal val contentCache: AnimeContentCacheStorage? = context?.let(::AnimeContentCacheStorage),
    internal val downloadSourceCooldowns: DownloadSourceCooldowns? = context?.let(::DownloadSourceCooldowns),
    val isNetworkAvailable: () -> Boolean = { context?.hasInternetConnection() ?: true },
) {
    internal val offlineStorage = context?.let(::OfflineAnimeStorage)
    internal val sourceQualityCache = context?.let(::SourceQualityCacheStorage)
    internal val contentContextLock = Any()
    internal var contentRevision = 0L

    @Volatile
    internal var contentLanguage: ContentLanguage = ContentLanguage.Russian

    internal val downloadClient = defaultVideoDownloadClient()

    fun updateContentLanguage(language: ContentLanguage) {
        repositoryUpdateContentLanguage(language)
    }

    suspend fun getFeatured(
        filters: BrowseFilters,
        offset: Int = 0,
        limit: Int = REPOSITORY_PAGE_SIZE,
    ): RepositoryContent<List<Anime>> = repositoryGetFeatured(filters, offset, limit)

    suspend fun search(
        query: String,
        filters: BrowseFilters,
        offset: Int = 0,
        limit: Int = REPOSITORY_PAGE_SIZE,
    ): RepositoryContent<List<Anime>> = repositorySearch(query, filters, offset, limit)

    suspend fun getFilterCatalog(): FilterCatalog = repositoryGetFilterCatalog()

    suspend fun getOfflineAnimeWithVideos(animeId: Long): RepositoryContent<Pair<AnimeDetails, List<VideoVariant>>> =
        withContext(Dispatchers.IO) {
            val entry = offlineStorage?.read(animeId) ?: throw IOException("Anime is not saved on this device")
            RepositoryContent(entry.details to entry.videos, offlineFallback = true)
        }

    suspend fun getAnimeWithVideos(
        animeId: Long,
    ): RepositoryContent<Pair<AnimeDetails, List<VideoVariant>>> = repositoryGetAnimeWithVideos(animeId)

    suspend fun getAnimeWithVideos(
        animeAlias: String,
    ): RepositoryContent<Pair<AnimeDetails, List<VideoVariant>>> = repositoryGetAnimeWithVideos(animeAlias)

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

    // Callbacks publish local effects atomically with the session check; they must not block on network I/O.
    suspend fun synchronizeProfileNotifications(
        limit: Int = 50,
        onNotifications: (UserProfile, List<SiteNotification>) -> Unit,
        onUnauthorized: () -> Unit,
    ) = repositorySynchronizeProfileNotifications(limit, onNotifications, onUnauthorized)

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

    val offlineContentChanges: Flow<Long> get() = offlineStorage?.changes ?: emptyFlow()

    suspend fun offlineAnime(): List<OfflineAnimeEntry> = repositoryOfflineAnime()

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

}

internal const val REPOSITORY_PAGE_SIZE = 36
internal const val FAVORITES_FILTER_ID = 4
