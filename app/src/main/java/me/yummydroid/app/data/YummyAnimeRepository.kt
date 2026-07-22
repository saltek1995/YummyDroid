package me.yummydroid.app.data

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.roundToLong
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Request

class YummyAnimeRepository(
    private val api: YummyAnimeApi = YummyAnimeApi(),
    context: Context? = null,
    private val siteDomainResolver: SiteDomainResolver = SiteDomainResolver(),
    private val videoStreamResolver: VideoStreamResolver = VideoStreamResolver(
        context = context,
        siteDomainResolver = siteDomainResolver,
    ),
    private val authStorage: AuthStorage? = null,
) {
    private val offlineStorage = context?.let(::OfflineAnimeStorage)
    private val sourceQualityCache = context?.let(::SourceQualityCacheStorage)
    @Volatile
    private var offlineFallbackActive: Boolean = false
    internal val downloadClient = defaultVideoDownloadClient()

    fun updateContentLanguage(language: ContentLanguage) {
        api.updateContentLanguage(language)
    }

    suspend fun getFeatured(filters: BrowseFilters, offset: Int = 0, limit: Int = PAGE_SIZE): List<Anime> {
        if (filters.offlineOnly) {
            offlineFallbackActive = false
            return offlineAnimePage(filters = filters, offset = offset, limit = limit)
        }

        val token = authStorage?.readToken()
        val userMarkIds = resolveUserMarkAnimeIds(filters, token)
        if (userMarkIds?.includedIds != null && userMarkIds.includedIds.isEmpty()) return emptyList()

        return try {
            offlineFallbackActive = false
            api.featuredAnime(
                limit = limit,
                offset = offset,
                filters = filters,
                authToken = token,
                ids = userMarkIds?.includedIds.orEmpty(),
            ).filterNot { it.id in userMarkIds?.excludedIds.orEmpty() }
        } catch (throwable: Throwable) {
            val offline = offlineFallbackAnimePage(filters = filters, offset = offset, limit = limit)
            if (offline != null) {
                offlineFallbackActive = true
                offline
            } else {
                throw throwable
            }
        }
    }

    suspend fun search(query: String, filters: BrowseFilters, offset: Int = 0, limit: Int = PAGE_SIZE): List<Anime> {
        if (filters.offlineOnly) {
            offlineFallbackActive = false
            return offlineAnimePage(query = query, filters = filters, offset = offset, limit = limit)
        }

        val token = authStorage?.readToken()
        val userMarkIds = resolveUserMarkAnimeIds(filters, token)
        if (userMarkIds?.includedIds != null && userMarkIds.includedIds.isEmpty()) return emptyList()

        return try {
            offlineFallbackActive = false
            api.search(
                query = query,
                limit = limit,
                offset = offset,
                filters = filters,
                authToken = token,
                ids = userMarkIds?.includedIds.orEmpty(),
            ).filterNot { it.id in userMarkIds?.excludedIds.orEmpty() }
        } catch (throwable: Throwable) {
            val offline = offlineFallbackAnimePage(query = query, filters = filters, offset = offset, limit = limit)
            if (offline != null) {
                offlineFallbackActive = true
                offline
            } else {
                throw throwable
            }
        }
    }

    private fun offlineAnimePage(
        query: String = "",
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

    private fun offlineFallbackAnimePage(
        query: String = "",
        filters: BrowseFilters,
        offset: Int,
        limit: Int,
    ): List<Anime>? = offlineAnimePage(
        query = query,
        filters = filters,
        offset = offset,
        limit = limit,
    ).takeIf { it.isNotEmpty() }

    suspend fun getFilterCatalog(): FilterCatalog {
        return api.getFilterCatalog()
    }

    suspend fun getAnimeWithVideos(animeId: Long): Pair<AnimeDetails, List<VideoVariant>> {
        val offline = offlineStorage?.read(animeId)
        return try {
            offlineFallbackActive = false
            val (details, videos) = api.getAnimeWithVideos(animeId, authStorage?.readToken())
            val mergedVideos = videos.withOfflineDownloads(offline?.videos.orEmpty(), details)
                .withCachedSourceQualities()
            offlineStorage?.saveAnime(details, mergedVideos)
            details to mergedVideos
        } catch (throwable: Throwable) {
            offline?.let {
                offlineFallbackActive = true
                it.details to it.videos
            } ?: throw throwable
        }
    }

    suspend fun getAnime(animeId: Long): AnimeDetails {
        return try {
            api.getAnime(animeId, authStorage?.readToken())
        } catch (throwable: Throwable) {
            offlineStorage?.read(animeId)?.details ?: throw throwable
        }
    }

    suspend fun getAnimeOnline(animeId: Long): AnimeDetails {
        return api.getAnime(animeId, authStorage?.readToken())
    }

    suspend fun getVideos(animeId: Long): List<VideoVariant> {
        return try {
            val videos = api.getVideos(animeId, authStorage?.readToken())
            offlineStorage?.read(animeId)?.let { offline ->
                videos.withOfflineDownloads(offline.videos, offline.details)
                    .withCachedSourceQualities()
            } ?: videos.withCachedSourceQualities()
        } catch (throwable: Throwable) {
            offlineStorage?.read(animeId)?.videos ?: throw throwable
        }
    }

    suspend fun getTopAnime(offset: Int = 0, limit: Int = PAGE_SIZE): List<Anime> {
        return api.featuredAnime(
            limit = limit,
            offset = offset,
            filters = BrowseFilters(sort = AnimeSort.Top),
            authToken = authStorage?.readToken(),
        )
    }

    suspend fun getSchedule(): List<ScheduleAnime> {
        return api.getSchedule()
    }

    suspend fun getCollections(offset: Int = 0, limit: Int = PAGE_SIZE): List<AnimeCollectionSummary> {
        return api.getCollections(offset = offset, limit = limit)
    }

    suspend fun getCollection(id: Long): AnimeCollectionSummary {
        return api.getCollection(id)
    }

    suspend fun getAnimeCollections(animeId: Long): List<AnimeCollectionSummary> {
        return api.getAnimeCollections(animeId)
    }

    suspend fun getAnimeComments(animeId: Long, offset: Int = 0, limit: Int = 20): List<AnimeComment> {
        return api.getAnimeComments(animeId, offset = offset, limit = limit)
    }

    suspend fun addAnimeComment(animeId: Long, text: String): AnimeComment? {
        return api.addAnimeComment(animeId, text, requireToken())
    }

    suspend fun getAnimeTrailers(animeId: Long): List<AnimeTrailer> {
        return api.getAnimeTrailers(animeId)
    }

    suspend fun getAnimeRecommendations(animeId: Long): List<Anime> {
        return api.getAnimeRecommendations(animeId)
    }

    suspend fun getAnimeRatingSummary(animeId: Long): AnimeRatingSummary {
        return api.getAnimeRatingSummary(animeId)
    }

    suspend fun setAnimeRating(animeId: Long, rating: Int): AnimeRatingSummary {
        return api.setAnimeRating(animeId, rating, requireToken())
    }

    suspend fun deleteAnimeRating(animeId: Long): AnimeRatingSummary {
        return api.deleteAnimeRating(animeId, requireToken())
    }

    suspend fun subscribeVideo(videoId: Long): Boolean {
        return api.subscribeVideo(videoId, requireToken())
    }

    suspend fun unsubscribeVideo(videoId: Long): Boolean {
        return api.unsubscribeVideo(videoId, requireToken())
    }

    suspend fun getVideoSubscriptions(): List<VideoSubscription> {
        val token = authStorage?.readToken() ?: return emptyList()
        val userId = authStorage.readProfile()?.id ?: return emptyList()
        return api.getVideoSubscriptions(userId, token)
    }

    suspend fun getNewEpisodeNotifications(limit: Int = 50): List<SiteNotification> {
        return api.getProfileNotifications(
            token = requireToken(),
            types = listOf("anime_episode"),
            subTypes = listOf("new_episode"),
            limit = limit,
        )
    }

    suspend fun getLibraryAnime(): List<Anime> {
        val token = authStorage?.readToken() ?: return emptyList()
        val userId = authStorage.readProfile()?.id ?: return emptyList()
        val markedAnime = UserAnimeListMark.displayOrder.flatMap { mark ->
            runCatching { api.getUserListAnime(userId, mark.id, token) }.getOrDefault(emptyList())
        }
        val favoriteAnime = runCatching { api.getUserFavoriteAnime(userId, token) }.getOrDefault(emptyList())
        return (markedAnime + favoriteAnime).distinctBy { it.id }
    }

    suspend fun resolveVideoStream(video: VideoVariant): ResolvedVideoStream {
        val localFile = video.primaryOfflineFile()
        if (localFile != null) {
            return ResolvedVideoStream(
                url = localFile.playbackUrl,
                mimeType = localFile.mimeType,
                headers = emptyMap(),
                maxVideoHeight = null,
            )
        }
        return videoStreamResolver.resolve(video).also { stream ->
            runCatching { sourceQualityCache?.save(video, stream) }
        }
    }

    suspend fun resolveAvailableDownloadQualities(
        requested: VideoVariant,
        videos: List<VideoVariant>,
        allEpisodes: Boolean,
    ): List<PreferredQuality> = withContext(Dispatchers.IO) {
        val candidates = videos.downloadQualityCandidatesFor(requested, allEpisodes)
            .map { it.withoutOfflinePlayback() }
            .withCachedSourceQualities()
            .distinctBy { it.sourceResolveIdentity() }
        if (candidates.isEmpty()) return@withContext emptyList()

        val knownQualities = candidates.map { candidate ->
            SourceQualityResolveResult(candidate, candidate.sourceQualities)
        }
        val missingCandidates = candidates.filter { it.sourceQualities.isEmpty() }
        val resolvedQualities = supervisorScope {
            missingCandidates.map { candidate ->
                async {
                    runCatching {
                        withTimeout(SOURCE_RESOLVE_TIMEOUT_MS) {
                            SourceQualityResolveResult(candidate, resolveVideoStream(candidate).availableQualities)
                        }
                    }.getOrElse {
                        sourceQualityCache?.remove(candidate)
                        SourceQualityResolveResult(candidate, emptyList())
                    }
                }
            }.awaitAll()
        }

        val heights = (knownQualities + resolvedQualities).availableDownloadHeights(allEpisodes)

        PreferredQuality.entries
            .asSequence()
            .filter { it.height != null && it.height in heights }
            .sortedByDescending { it.height ?: 0 }
            .toList()
    }

    fun offlineAnime(): List<OfflineAnimeEntry> {
        return offlineStorage?.readAll().orEmpty()
    }

    fun isOfflineFallbackActive(): Boolean {
        return offlineFallbackActive
    }

    fun deleteOfflineVideo(animeId: Long, videoId: Long, playbackUrl: String? = null) {
        offlineStorage?.deleteVideo(animeId, videoId, playbackUrl)
    }

    fun deleteOfflineAnime(animeId: Long) {
        offlineStorage?.deleteAnime(animeId)
    }

    fun clearAppContentCache(playbackProgressStorage: PlaybackProgressStorage) {
        offlineStorage?.clearOfflineCache()
        playbackProgressStorage.clear()
    }

    suspend fun downloadVideo(
        details: AnimeDetails,
        videos: List<VideoVariant>,
        video: VideoVariant,
        preferredQuality: PreferredQuality = PreferredQuality.Auto,
        onProgress: (DownloadProgressInfo) -> Unit,
        isCancelled: () -> Boolean = { false },
        deletePartialOnCancel: () -> Boolean = { true },
    ): VideoVariant = withContext(Dispatchers.IO) {
        val storage = offlineStorage ?: error("Offline storage is unavailable")
        check(!isCancelled()) { "Загрузка отменена" }
        val playbacks = resolveDownloadPlaybacks(
            requested = video,
            videos = videos,
            preferredQuality = preferredQuality,
        )
        val failures = mutableListOf<String>()

        for (playback in playbacks) {
            val stream = playback.stream
            val target = runCatching {
                if (stream.isHlsStream()) {
                    downloadHlsAsSingleVideoFile(
                        storage = storage,
                        video = playback.video,
                        stream = stream,
                        preferredQuality = preferredQuality,
                        onProgress = onProgress,
                        isCancelled = isCancelled,
                        deletePartialOnCancel = deletePartialOnCancel,
                    )
                } else if (stream.isDashStream()) {
                    throw IOException("DASH офлайн-скачивание пока недоступно для этого источника")
                } else {
                    downloadDirectVideo(
                        storage = storage,
                        video = playback.video,
                        stream = stream,
                        onProgress = onProgress,
                        isCancelled = isCancelled,
                        deletePartialOnCancel = deletePartialOnCancel,
                    )
                }
            }.getOrElse { throwable ->
                if (isCancelled() || throwable.message.equals("Загрузка отменена", ignoreCase = true)) {
                    throw IllegalStateException("Загрузка отменена", throwable)
                }
                failures += "${playback.video.groupTitle.ifBlank { playback.video.player }}: ${throwable.message.orEmpty()}"
                null
            } ?: continue

            if (isCancelled()) {
                if (deletePartialOnCancel()) target.delete()
                throw IllegalStateException("Загрузка отменена")
            }
            storage.markVideoDownloaded(details, videos, playback.video, target, target.name.mimeTypeFromFileName() ?: stream.mimeType)
            val downloaded = storage.read(details.id)
                ?.videos
                ?.firstOrNull { it.id == playback.video.id || it.downloadVoiceSlotKey == playback.video.downloadVoiceSlotKey }
                ?: playback.video
            val downloadedQualityTitle = target.downloadQualityTitle()
            onProgress(
                DownloadProgressInfo(
                    fraction = 1f,
                    downloadedBytes = target.length().coerceAtLeast(0L),
                    totalBytes = target.length().coerceAtLeast(0L),
                    bytesPerSecond = 0L,
                    qualityTitle = downloadedQualityTitle,
                    voiceTitle = playback.video.downloadVoiceTitle(),
                ),
            )
            return@withContext downloaded
        }

        val detailsText = failures.take(3).joinToString("; ").takeIf { it.isNotBlank() }
        throw IOException(
            buildString {
                append("Не удалось скачать серию")
                if (detailsText != null) append(": ").append(detailsText)
            },
        )
    }

    fun cachedSiteBaseUrl(): String {
        return siteDomainResolver.cachedOrDefaultBaseUrl()
    }

    suspend fun activeSiteBaseUrl(): String {
        return siteDomainResolver.activeBaseUrl()
    }

    suspend fun checkReachableSiteBaseUrl(): String? {
        return siteDomainResolver.checkReachableBaseUrl()
    }

    suspend fun resolveBestPlaybackSource(
        candidates: List<VideoVariant>,
        preferredQuality: PreferredQuality,
        metadataCandidates: List<VideoVariant> = candidates,
    ): ResolvedPlayback {
        val uniqueCandidates = candidates.distinctBy { it.sourceResolveIdentity() }.ifEmpty {
            throw IOException("Нет доступных источников для серии")
        }

        val selectableKeys = uniqueCandidates.mapTo(mutableSetOf()) { it.sourceResolveIdentity() }
        val uniqueMetadataCandidates = (uniqueCandidates + metadataCandidates)
            .distinctBy { it.sourceResolveIdentity() }

        val attempts = resolveCandidateAttempts(uniqueMetadataCandidates, preferredQuality)

        val best = attempts.bestPlayback(preferredQuality, selectableKeys)

        if (best != null) return best.withMetadataFromAttempts(attempts)

        throw attempts.resolveFailure("Не удалось запустить ни один источник серии")
    }

    private suspend fun resolveDownloadPlaybacks(
        requested: VideoVariant,
        videos: List<VideoVariant>,
        preferredQuality: PreferredQuality,
    ): List<ResolvedPlayback> {
        val uniqueCandidates = videos.downloadCandidatesFor(requested)
            .map { it.withoutOfflinePlayback() }
            .withCachedSourceQualities()
            .distinctBy { it.sourceResolveIdentity() }
            .ifEmpty {
                throw IOException("Нет онлайн-источников для скачивания серии")
            }

        val attempts = resolveCandidateAttempts(uniqueCandidates, preferredQuality)

        val playbacks = attempts.downloadPlaybacks(preferredQuality)

        if (playbacks.isNotEmpty()) return playbacks

        throw attempts.resolveFailure("Не удалось найти рабочий источник для скачивания")
    }

    private suspend fun resolveCandidateAttempts(
        candidates: List<VideoVariant>,
        preferredQuality: PreferredQuality,
    ): List<SourceResolveAttempt> {
        return supervisorScope {
            candidates.mapIndexed { index, candidate ->
                async {
                    runCatching {
                        withTimeout(SOURCE_RESOLVE_TIMEOUT_MS) {
                            videoStreamResolver.resolve(candidate, preferredQuality)
                        }
                    }.fold(
                        onSuccess = { stream ->
                            runCatching { sourceQualityCache?.save(candidate, stream) }
                            SourceResolveAttempt(
                                index = index,
                                candidate = candidate,
                                playback = ResolvedPlayback(video = candidate, stream = stream),
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

    fun cachedProfile(): UserProfile? {
        return authStorage?.readProfile()
    }

    suspend fun restoreProfile(): UserProfile? {
        val storage = authStorage ?: return null
        val token = storage.readToken() ?: run {
            storage.clear()
            return null
        }
        val cachedProfile = storage.readProfile()
        val refreshedToken = runCatching { api.refreshToken(token) }.getOrElse { throwable ->
            if (throwable.isUnauthorizedApiError()) {
                storage.clear()
                throw throwable
            }
            token
        }
        if (refreshedToken != token) {
            storage.saveToken(refreshedToken)
        }
        return runCatching { api.getProfile(refreshedToken) }
            .onSuccess { storage.saveProfile(it) }
            .getOrElse { throwable ->
                if (throwable.isUnauthorizedApiError()) {
                    storage.clear()
                    throw throwable
                }
                cachedProfile ?: throw throwable
            }
    }

    suspend fun login(login: String, password: String, captchaResponse: String? = null): UserProfile {
        val token = api.login(login, password, captchaResponse)
        authStorage?.saveToken(token)
        return api.getProfile(token).also { profile ->
            authStorage?.saveProfile(profile)
        }
    }

    fun submitCaptchaResponse(response: String) {
        api.submitCaptchaResponse(response)
    }

    fun logout() {
        authStorage?.clear()
    }

    suspend fun getAnimeMark(animeId: Long): UserAnimeMark? {
        val token = authStorage?.readToken() ?: return null
        return api.getAnimeMark(animeId, token)
    }

    suspend fun setAnimeListMark(animeId: Long, mark: UserAnimeListMark): UserAnimeMark {
        val token = requireToken()
        return api.setAnimeListMark(animeId, mark, token)
    }

    suspend fun removeAnimeListMark(animeId: Long): UserAnimeMark {
        val token = requireToken()
        return api.removeAnimeListMark(animeId, token)
    }

    suspend fun setFavorite(animeId: Long, isFavorite: Boolean): UserAnimeMark {
        val token = requireToken()
        return api.setFavorite(animeId, isFavorite, token)
    }

    suspend fun getWatchHistory(limit: Int = 100, offset: Int = 0): List<PlaybackProgress> {
        val token = requireToken()
        return api.getWatchHistory(token, limit, offset)
    }

    suspend fun saveWatchProgress(progress: PlaybackProgress): Boolean {
        val token = authStorage?.readToken() ?: return false
        return api.saveWatchProgress(progress, token)
    }

    private fun requireToken(): String {
        return authStorage?.readToken() ?: error("Нужно войти в аккаунт")
    }

    private fun List<VideoVariant>.withCachedSourceQualities(): List<VideoVariant> {
        return sourceQualityCache?.applyTo(this) ?: this
    }

    private suspend fun resolveUserMarkAnimeIds(filters: BrowseFilters, token: String?): UserMarkFilterIds? {
        if (filters.userMarks.isEmpty() && filters.excludedUserMarks.isEmpty()) return null
        val userId = authStorage?.readProfile()?.id ?: return UserMarkFilterIds(emptySet(), emptySet())
        val authToken = token?.takeIf { it.isNotBlank() } ?: return UserMarkFilterIds(emptySet(), emptySet())
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

    private companion object {
        const val PAGE_SIZE = 36
        const val FAVORITES_FILTER_ID = 4
        const val SOURCE_RESOLVE_TIMEOUT_MS = 10_000L
    }
}

private data class SourceResolveAttempt(
    val index: Int,
    val candidate: VideoVariant,
    val playback: ResolvedPlayback? = null,
    val failure: Throwable? = null,
)

private fun List<SourceResolveAttempt>.successfulPlaybacks(): List<Pair<Int, ResolvedPlayback>> {
    return mapNotNull { attempt -> attempt.playback?.let { playback -> attempt.index to playback } }
}

private fun List<SourceResolveAttempt>.bestPlayback(
    preferredQuality: PreferredQuality,
    selectableKeys: Set<String>? = null,
): ResolvedPlayback? {
    return successfulPlaybacks()
        .filter { (_, playback) ->
            selectableKeys == null || playback.video.sourceResolveIdentity() in selectableKeys
        }
        .sortedWith(
            compareByDescending<Pair<Int, ResolvedPlayback>> { (_, playback) -> playback.video.isOfflineAvailable }
                .thenByDescending { (_, playback) -> playback.stream.qualityScore(preferredQuality) }
                .thenByDescending { (_, playback) -> playback.stream.hasSubtitles }
                .thenBy { (index, _) -> index },
        )
        .firstOrNull()
        ?.second
}

private fun ResolvedPlayback.withMetadataFromAttempts(
    attempts: List<SourceResolveAttempt>,
): ResolvedPlayback {
    val sameEpisodeAttempts = attempts
        .filter { attempt -> attempt.candidate.isSameEpisodeAs(video) }
    val sameVoiceAttempts = sameEpisodeAttempts
        .filter { attempt -> attempt.candidate.hasSameVoiceAs(video) }

    return withMergedPlaybackMetadata(
        metadataVideos = sameVoiceAttempts.map { attempt -> attempt.candidate },
        metadataPlaybacks = sameVoiceAttempts
            .asSequence()
            .mapNotNull { attempt -> attempt.playback }
            .toList(),
    )
}

internal fun ResolvedPlayback.withMergedPlaybackMetadata(
    metadataVideos: List<VideoVariant>,
    metadataPlaybacks: List<ResolvedPlayback>,
): ResolvedPlayback {
    val sameVoiceVideos = metadataVideos
        .asSequence()
        .filter { candidate -> candidate.isSameEpisodeAs(video) && candidate.hasSameVoiceAs(video) }
        .toList()
    val sameVoicePlaybacks = metadataPlaybacks
        .asSequence()
        .filter { playback -> playback.video.isSameEpisodeAs(video) && playback.video.hasSameVoiceAs(video) }
        .toList()

    val mergedSubtitles = (stream.subtitles + sameVoicePlaybacks.flatMap { playback ->
        playback.stream.subtitles
    }).normalizedSubtitleTracks()
    val mergedEmbeddedSubtitles = stream.hasEmbeddedSubtitles || sameVoicePlaybacks.any { playback ->
        playback.stream.hasEmbeddedSubtitles
    }
    val mergedQualities = (stream.sourceQualitiesWithMax() + sameVoicePlaybacks.flatMap { playback ->
        playback.stream.sourceQualitiesWithMax()
    }).normalizedSourceQualities()
    val mergedSkipSegments = (video.skipSegments + sameVoiceVideos.flatMap { candidate ->
        candidate.skipSegments
    }).normalizedSkipSegments()

    if (
        mergedSubtitles == stream.subtitles &&
        mergedEmbeddedSubtitles == stream.hasEmbeddedSubtitles &&
        mergedQualities == stream.availableQualities.normalizedSourceQualities() &&
        mergedSkipSegments == video.skipSegments.normalizedSkipSegments()
    ) {
        return this
    }
    return copy(
        video = video.copy(skipSegments = mergedSkipSegments),
        stream = stream.copy(
            subtitles = mergedSubtitles,
            hasEmbeddedSubtitles = mergedEmbeddedSubtitles,
            availableQualities = mergedQualities,
        ),
    )
}

private fun ResolvedVideoStream.sourceQualitiesWithMax(): List<SourceQuality> {
    return availableQualities + listOfNotNull(maxVideoHeight?.let { SourceQuality(height = it) })
}

private fun List<SourceResolveAttempt>.downloadPlaybacks(preferredQuality: PreferredQuality): List<ResolvedPlayback> {
    return successfulPlaybacks()
        .sortedWith(
            compareByDescending<Pair<Int, ResolvedPlayback>> { (_, playback) ->
                playback.stream.qualityScore(preferredQuality)
            }.thenBy { (index, _) -> index },
        )
        .map { it.second }
}

private fun List<SourceResolveAttempt>.resolveFailure(message: String): IOException {
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

private data class SourceQualityResolveResult(
    val candidate: VideoVariant,
    val qualities: List<SourceQuality>,
)

private fun List<VideoVariant>.withOfflineDownloads(
    offlineVideos: List<VideoVariant>,
    details: AnimeDetails,
): List<VideoVariant> {
    val availableOfflineVideos = offlineVideos.filter { it.isOfflineAvailable }
    val offlineById = availableOfflineVideos.groupBy { it.id }
    val offlineBySlot = availableOfflineVideos.groupBy { it.sourceSlotKey }
    val offlineByVoiceSlot = availableOfflineVideos.groupBy { it.downloadVoiceSlotKey }

    return map { video ->
        val offlineMatches = buildList {
            addAll(offlineById[video.id].orEmpty())
            addAll(offlineBySlot[video.sourceSlotKey].orEmpty())
            addAll(offlineByVoiceSlot[video.downloadVoiceSlotKey].orEmpty())
        }.distinctBy { it.id to it.localPlaybackUrl }

        if (offlineMatches.isNotEmpty()) {
            val offlineFiles = offlineMatches
                .flatMap { it.offlineFiles }
                .filter { it.playbackUrl.isNotBlank() }
                .distinctBy { it.playbackUrl }
                .sortedWith(compareByDescending<OfflineVideoFile> { it.qualityHeight() }.thenBy { it.qualityTitle })
            val primaryFile = offlineFiles.firstOrNull()
            val fallbackOffline = offlineMatches.first()
            video.copy(
                previewUrl = video.previewUrl.ifBlank { fallbackOffline.previewUrl },
                localPlaybackUrl = primaryFile?.playbackUrl ?: fallbackOffline.localPlaybackUrl,
                localMimeType = primaryFile?.mimeType ?: fallbackOffline.localMimeType,
                localBytes = primaryFile?.bytes ?: fallbackOffline.localBytes,
                localFiles = offlineFiles.ifEmpty { fallbackOffline.offlineFiles },
            )
        } else {
            video
        }
    }
}

private data class UserMarkFilterIds(
    val includedIds: Set<Long>?,
    val excludedIds: Set<Long>,
)

private fun VideoVariant.withoutOfflinePlayback(): VideoVariant {
    return copy(
        localPlaybackUrl = "",
        localMimeType = null,
        localBytes = 0L,
        localFiles = emptyList(),
    )
}

private fun List<OfflineAnimeEntry>.filteredOfflineAnime(
    query: String = "",
    filters: BrowseFilters,
): List<Anime> {
    val normalizedQuery = query.normalizedFilterToken()
    return asSequence()
        .filter { entry ->
            val anime = entry.anime
            val details = entry.details
            val year = details.year ?: anime.year
            val rating = details.rating ?: anime.rating
            val genres = (details.genreTags.map { it.title } + details.genres + anime.genres)
                .map { it.normalizedFilterToken() }
                .filterTo(mutableSetOf()) { it.isNotBlank() }
            val type = details.type.ifBlank { anime.type }.normalizedFilterToken()
            val status = details.status.ifBlank { anime.status }.normalizedFilterToken()
            val episodeCount = entry.downloadedVideos.size

            if (normalizedQuery.isNotBlank()) {
                val haystack = listOf(
                    anime.title,
                    anime.description,
                    details.description,
                    details.otherTitles.joinToString(" "),
                    details.genreTags.joinToString(" ") { it.title },
                    details.genres.joinToString(" "),
                ).joinToString(" ").normalizedFilterToken()
                if (!haystack.contains(normalizedQuery)) return@filter false
            }
            if (filters.fromYear != null && (year == null || year < filters.fromYear)) return@filter false
            if (filters.toYear != null && (year == null || year > filters.toYear)) return@filter false
            if (filters.minRating != null && (rating == null || rating < filters.minRating)) return@filter false
            if (filters.maxRating != null && (rating == null || rating > filters.maxRating)) return@filter false
            if (filters.episodeFrom != null && episodeCount < filters.episodeFrom) return@filter false
            if (filters.episodeTo != null && episodeCount > filters.episodeTo) return@filter false
            if (filters.statuses.isNotEmpty() && filters.statuses.none { status.matchesFilterToken(it) }) return@filter false
            if (filters.types.isNotEmpty() && filters.types.none { type.matchesFilterToken(it) }) return@filter false
            if (filters.genres.isNotEmpty() && genres.none { genre -> filters.genres.any { genre.matchesFilterToken(it) } }) {
                return@filter false
            }
            if (filters.excludedGenres.isNotEmpty() && genres.any { genre ->
                    filters.excludedGenres.any { genre.matchesFilterToken(it) }
                }
            ) {
                return@filter false
            }
            true
        }
        .map { it.anime }
        .toList()
        .sortedOffline(filters.sort)
}

private fun List<Anime>.sortedOffline(sort: AnimeSort): List<Anime> {
    return when (sort) {
        AnimeSort.Title -> sortedBy { it.title.lowercase() }
        AnimeSort.Views -> sortedByDescending { it.views }
        AnimeSort.Year -> sortedByDescending { it.year ?: 0 }
        AnimeSort.Top,
        AnimeSort.Rating -> sortedByDescending { it.rating ?: 0.0 }
        AnimeSort.RatingCounters,
        AnimeSort.Id -> sortedByDescending { it.id }
        AnimeSort.Random -> shuffled()
    }
}

private fun String.matchesFilterToken(selected: String): Boolean {
    val value = normalizedFilterToken()
    val token = selected.normalizedFilterToken().substringAfterLast("/")
    return value == token || value.contains(token) || token.contains(value)
}

private fun String.normalizedFilterToken(): String {
    return trim()
        .lowercase()
        .replace('ё', 'е')
        .replace(Regex("[^a-zа-я0-9]+"), " ")
        .trim()
}

private fun List<VideoVariant>.downloadCandidatesFor(requested: VideoVariant): List<VideoVariant> {
    val sameEpisode = filter { candidate ->
        candidate.animeId == requested.animeId && candidate.isSameEpisodeAs(requested)
    }.ifEmpty { listOf(requested) }
    val requestedVoiceKey = requested.matchingVoiceKey
    val sameVoiceEpisode = sameEpisode
        .filter { candidate -> candidate.matchingVoiceKey == requestedVoiceKey }
        .ifEmpty { listOf(requested) }

    return sameVoiceEpisode.sortedWith(
        compareByDescending<VideoVariant> { it.id == requested.id }
            .thenBy { it.index },
    )
}

private fun List<VideoVariant>.downloadQualityCandidatesFor(
    requested: VideoVariant,
    allEpisodes: Boolean,
): List<VideoVariant> {
    if (!allEpisodes) return downloadCandidatesFor(requested)
    val requestedVoiceKey = requested.matchingVoiceKey
    return filter { candidate ->
        candidate.animeId == requested.animeId &&
            candidate.matchingVoiceKey == requestedVoiceKey
    }.ifEmpty { downloadCandidatesFor(requested) }
}

private fun List<SourceQualityResolveResult>.availableDownloadHeights(allEpisodes: Boolean): Set<Int> {
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

private fun ResolvedVideoStream.qualityScore(preferredQuality: PreferredQuality): Int {
    return (maxVideoHeight ?: 0).qualityPreferenceScore(preferredQuality)
}

private fun VideoVariant.sourceResolveIdentity(): String {
    if (id > 0L) return "id:$id"
    return listOf(
        animeId.toString(),
        matchingEpisodeKey,
        matchingVoiceKey,
        player.cleanVideoSourceLabel().lowercase(),
        url.sourceResolveFingerprint(),
        index.toString(),
    ).joinToString("|")
}

private fun String.sourceResolveFingerprint(): String {
    return trim()
        .substringBefore('#')
        .substringBefore('?')
        .lowercase()
}

private fun VideoVariant.downloadVoiceTitle(): String {
    return matchingDisplayVoiceTitle
}

private fun VideoVariant.primaryOfflineFile(): OfflineVideoFile? {
    val preferredUrl = localPlaybackUrl.takeIf { it.isNotBlank() }
    return offlineFiles.firstOrNull { it.playbackUrl == preferredUrl }
        ?: offlineFiles.maxWithOrNull(compareBy<OfflineVideoFile> { it.qualityHeight() }.thenBy { it.bytes })
}

private fun File.downloadQualityTitle(): String {
    return nameWithoutExtension
        .substringAfter('_', "")
        .replace('_', ' ')
        .takeIf { it.isNotBlank() }
        ?: "Авто"
}

private fun File.isCompletedDownloadFile(): Boolean {
    return exists() && length() >= 256L * 1024L && !extension.equals("m3u8", ignoreCase = true)
}

private fun ResolvedVideoStream.isHlsStream(): Boolean {
    return mimeType?.contains("mpegurl", ignoreCase = true) == true ||
        url.contains(".m3u8", ignoreCase = true)
}

private fun ResolvedVideoStream.isDashStream(): Boolean {
    return mimeType?.contains("dash", ignoreCase = true) == true ||
        url.contains(".mpd", ignoreCase = true)
}

private suspend fun YummyAnimeRepository.downloadDirectVideo(
    storage: OfflineAnimeStorage,
    video: VideoVariant,
    stream: ResolvedVideoStream,
    onProgress: (DownloadProgressInfo) -> Unit,
    isCancelled: () -> Boolean,
    deletePartialOnCancel: () -> Boolean,
): File {
    val qualityTitle = stream.qualityTitle()
    val target = storage.targetFile(video, stream.url.fileExtensionForDownload(), qualityTitle.ifBlank { "auto" })
    if (target.isCompletedDownloadFile()) {
        val voiceTitle = video.downloadVoiceTitle()
        onProgress(
            DownloadProgressInfo(
                fraction = 1f,
                downloadedBytes = target.length().coerceAtLeast(0L),
                totalBytes = target.length().coerceAtLeast(0L),
                bytesPerSecond = 0L,
                qualityTitle = target.downloadQualityTitle(),
                voiceTitle = voiceTitle,
            ),
        )
        return target
    }
    val temp = target.partFile()
    val startedAtMs = System.currentTimeMillis()
    val voiceTitle = video.downloadVoiceTitle()
    var attempt = 0

    while (true) {
        try {
            check(!isCancelled()) { "Загрузка отменена" }
            val existingBytes = temp.length().coerceAtLeast(0L)
            val requestBuilder = Request.Builder()
                .url(stream.url)
                .headers(stream.headers.toOkHttpHeaders())
                .header("Accept-Encoding", "identity")
            if (existingBytes > 0L) {
                requestBuilder.header("Range", "bytes=$existingBytes-")
            }

            downloadClient.newCall(requestBuilder.build()).execute().use { response ->
                if (existingBytes > 0L && response.code == 416) {
                    temp.moveCompleteTo(target)
                    return target
                }
                if (!response.isSuccessful) {
                    throw IOException("Download HTTP ${response.code}")
                }
                val body = response.body ?: throw IOException("Empty download body")
                val canAppend = existingBytes > 0L && response.code == 206
                if (existingBytes > 0L && !canAppend) {
                    temp.delete()
                }
                val startingBytes = if (canAppend) existingBytes else 0L
                val totalBytes = response.header("Content-Range")?.parseContentRangeTotal()
                    ?: body.contentLength()
                        .takeIf { it > 0L }
                        ?.let { length -> if (canAppend) startingBytes + length else length }
                    ?: -1L
                FileOutputStream(temp, canAppend).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var readTotal = startingBytes
                        while (true) {
                            check(!isCancelled()) { "Загрузка отменена" }
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            readTotal += read
                            val elapsedMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(1L)
                            val speed = (readTotal * 1000L / elapsedMs).coerceAtLeast(0L)
                            val fraction = if (totalBytes > 0L) {
                                (readTotal.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            onProgress(
                                DownloadProgressInfo(
                                    fraction = fraction,
                                    downloadedBytes = readTotal,
                                    totalBytes = totalBytes,
                                    bytesPerSecond = speed,
                                    qualityTitle = qualityTitle,
                                    voiceTitle = voiceTitle,
                                ),
                            )
                        }
                    }
                }
            }
            temp.moveCompleteTo(target)
            break
        } catch (throwable: Throwable) {
            if (isCancelled() || throwable.message.equals("Загрузка отменена", ignoreCase = true)) {
                if (deletePartialOnCancel()) temp.delete()
                throw throwable
            }
            attempt += 1
            if (attempt >= DOWNLOAD_RETRY_COUNT) throw throwable
            delay(DOWNLOAD_RETRY_DELAY_MS * attempt)
        }
    }
    onProgress(
        DownloadProgressInfo(
            fraction = 1f,
            downloadedBytes = target.length().coerceAtLeast(0L),
            totalBytes = target.length().coerceAtLeast(0L),
            bytesPerSecond = 0L,
            qualityTitle = qualityTitle,
            voiceTitle = voiceTitle,
        ),
    )
    return target
}

private suspend fun YummyAnimeRepository.downloadHlsAsSingleVideoFile(
    storage: OfflineAnimeStorage,
    video: VideoVariant,
    stream: ResolvedVideoStream,
    preferredQuality: PreferredQuality,
    onProgress: (DownloadProgressInfo) -> Unit,
    isCancelled: () -> Boolean,
    deletePartialOnCancel: () -> Boolean,
): File {
    val initialPlaylist = downloadText(stream.url, stream.headers)
    val selectedVariant = initialPlaylist.selectBestHlsVariant(stream.url, preferredQuality)
    val mediaUrl = selectedVariant?.url ?: stream.url
    val mediaPlaylist = if (mediaUrl == stream.url) initialPlaylist else downloadText(mediaUrl, stream.headers)
    val plan = mediaPlaylist.toHlsSingleFilePlan(mediaUrl, selectedVariant?.bandwidth ?: 0)
    if (plan.segments.isEmpty()) {
        throw IOException("HLS плейлист не содержит сегментов для скачивания")
    }

    val keyCache = mutableMapOf<String, ByteArray>()
    val estimatedTotalBytes = plan.estimatedTotalBytes()
    val startedAtMs = System.currentTimeMillis()
    val qualityTitle = selectedVariant?.qualityTitle() ?: stream.qualityTitle()
    val target = storage.targetFile(video, plan.outputExtension, qualityTitle.ifBlank { "auto" })
    if (target.isCompletedDownloadFile()) {
        val voiceTitle = video.downloadVoiceTitle()
        onProgress(
            DownloadProgressInfo(
                fraction = 1f,
                downloadedBytes = target.length().coerceAtLeast(0L),
                totalBytes = target.length().coerceAtLeast(0L),
                bytesPerSecond = 0L,
                qualityTitle = target.downloadQualityTitle(),
                voiceTitle = voiceTitle,
            ),
        )
        return target
    }
    val temp = target.partFile()
    val stateFile = temp.hlsStateFile()
    val signature = plan.signature()
    val resumeState = stateFile.readHlsResumeState(signature)
    if (temp.exists() && temp.length() > 0L && resumeState == null) {
        temp.delete()
        stateFile.delete()
    }
    var downloadedBytes = temp.length().coerceAtLeast(0L)
    val voiceTitle = video.downloadVoiceTitle()

    try {
        FileOutputStream(temp, true).use { output ->
            var initWritten = resumeState?.initWritten ?: false
            var nextSegmentIndex = resumeState?.nextSegmentIndex ?: 0
            if (plan.initUrl != null && !initWritten) {
                val bytes = downloadUrlBytes(plan.initUrl, stream.headers)
                output.write(bytes)
                output.flush()
                downloadedBytes = temp.length().coerceAtLeast(0L)
                initWritten = true
                stateFile.writeHlsResumeState(signature, initWritten, nextSegmentIndex)
            }
            while (nextSegmentIndex < plan.segments.size) {
                val index = nextSegmentIndex
                val segment = plan.segments[index]
                check(!isCancelled()) { "Загрузка отменена" }
                val bytes = downloadUrlBytes(segment.url, stream.headers)
                val payload = segment.encryption?.let { encryption ->
                    decryptHlsSegment(
                        bytes = bytes,
                        encryption = encryption,
                        sequenceNumber = plan.mediaSequence + index,
                        headers = stream.headers,
                        keyCache = keyCache,
                    )
                } ?: bytes
                output.write(payload)
                output.flush()
                nextSegmentIndex = index + 1
                downloadedBytes = temp.length().coerceAtLeast(0L)
                stateFile.writeHlsResumeState(signature, initWritten = true, nextSegmentIndex = nextSegmentIndex)
                val elapsedMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(1L)
                val speed = (downloadedBytes * 1000L / elapsedMs).coerceAtLeast(0L)
                val fraction = (nextSegmentIndex.toFloat() / plan.segments.size.toFloat()).coerceIn(0f, 1f)
                onProgress(
                    DownloadProgressInfo(
                        fraction = fraction,
                        downloadedBytes = downloadedBytes,
                        totalBytes = estimatedTotalBytes.takeIf { it > 0L } ?: -1L,
                        bytesPerSecond = speed,
                        qualityTitle = qualityTitle,
                        voiceTitle = voiceTitle,
                    ),
                )
            }
        }
        stateFile.delete()
        temp.moveCompleteTo(target)
    } catch (throwable: Throwable) {
        if (isCancelled() || throwable.message.equals("Загрузка отменена", ignoreCase = true)) {
            if (deletePartialOnCancel()) {
                temp.delete()
                stateFile.delete()
            }
        }
        throw throwable
    }

    onProgress(
        DownloadProgressInfo(
            fraction = 1f,
            downloadedBytes = target.length().coerceAtLeast(0L),
            totalBytes = target.length().coerceAtLeast(0L),
            bytesPerSecond = 0L,
            qualityTitle = qualityTitle,
            voiceTitle = voiceTitle,
        ),
    )
    return target
}

private fun YummyAnimeRepository.downloadText(url: String, headers: Map<String, String>): String {
    val request = Request.Builder()
        .url(url)
        .headers(headers.toOkHttpHeaders())
        .build()
    return downloadClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IOException("Download HTTP ${response.code}")
        response.body?.string().orEmpty().takeIf { it.isNotBlank() }
            ?: throw IOException("Empty playlist")
    }
}

private suspend fun YummyAnimeRepository.downloadUrlBytes(
    url: String,
    headers: Map<String, String>,
): ByteArray {
    var attempt = 0
    while (true) {
        try {
            val request = Request.Builder()
                .url(url)
                .headers(headers.toOkHttpHeaders())
                .build()
            return downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Download HTTP ${response.code}")
                response.body?.bytes() ?: throw IOException("Empty HLS resource")
            }
        } catch (throwable: Throwable) {
            attempt += 1
            if (attempt >= DOWNLOAD_RETRY_COUNT) throw throwable
            delay(DOWNLOAD_RETRY_DELAY_MS * attempt)
        }
    }
}

private fun String.fileExtensionForDownload(): String {
    val path = substringBefore('?').substringBefore('#').lowercase()
    return when {
        path.endsWith(".m3u8") -> "m3u8"
        path.endsWith(".mpd") -> "mpd"
        path.endsWith(".m4s") -> "m4s"
        path.endsWith(".ts") -> "ts"
        path.endsWith(".mp4") -> "mp4"
        path.endsWith(".mkv") -> "mkv"
        path.endsWith(".webm") -> "webm"
        else -> "mp4"
    }
}

private fun String.mimeTypeFromFileName(): String? {
    val lower = lowercase()
    return when {
        lower.endsWith(".m3u8") -> "application/x-mpegURL"
        lower.endsWith(".mpd") -> "application/dash+xml"
        lower.endsWith(".mp4") -> "video/mp4"
        lower.endsWith(".m4s") -> "video/mp4"
        lower.endsWith(".ts") -> "video/mp2t"
        lower.endsWith(".mkv") -> "video/x-matroska"
        lower.endsWith(".webm") -> "video/webm"
        else -> null
    }
}

private data class HlsSingleFilePlan(
    val mediaSequence: Long,
    val initUrl: String?,
    val outputExtension: String,
    val variantBandwidth: Int,
    val segments: List<HlsMediaSegment>,
) {
    fun estimatedTotalBytes(): Long {
        val totalDuration = segments.sumOf { it.durationSeconds }
        if (variantBandwidth <= 0 || totalDuration <= 0.0) return -1L
        return ((variantBandwidth.toDouble() * totalDuration) / 8.0).roundToLong().coerceAtLeast(1L)
    }

    fun signature(): String {
        return buildString {
            append(mediaSequence)
            append('|').append(initUrl.orEmpty())
            append('|').append(outputExtension)
            append('|').append(variantBandwidth)
            segments.forEach { segment ->
                append('|').append(segment.url)
                append('@').append(segment.durationSeconds)
                append('@').append(segment.encryption?.method.orEmpty())
                append('@').append(segment.encryption?.keyUrl.orEmpty())
            }
        }
    }
}

private data class HlsMediaSegment(
    val url: String,
    val encryption: HlsEncryption?,
    val durationSeconds: Double,
)

private data class HlsEncryption(
    val method: String,
    val keyUrl: String?,
    val iv: ByteArray?,
)

private fun String.toHlsSingleFilePlan(baseUrl: String, variantBandwidth: Int): HlsSingleFilePlan {
    val segments = mutableListOf<HlsMediaSegment>()
    var encryption: HlsEncryption? = null
    var initUrl: String? = null
    var mediaSequence = 0L
    var nextSegmentDuration = 0.0

    lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        when {
            line.startsWith("#EXT-X-MEDIA-SEQUENCE", ignoreCase = true) -> {
                mediaSequence = line.substringAfter(':', "").trim().toLongOrNull() ?: 0L
            }
            line.startsWith("#EXT-X-KEY", ignoreCase = true) -> {
                encryption = line.toHlsEncryption(baseUrl)
            }
            line.startsWith("#EXT-X-MAP", ignoreCase = true) -> {
                initUrl = line.hlsAttribute("URI")?.let { it.resolveUrlAgainst(baseUrl) }
            }
            line.startsWith("#EXTINF", ignoreCase = true) -> {
                nextSegmentDuration = line.substringAfter(':', "")
                    .substringBefore(',')
                    .trim()
                    .toDoubleOrNull()
                    ?: 0.0
            }
            line.isBlank() || line.startsWith("#") -> Unit
            else -> {
                segments += HlsMediaSegment(
                    url = line.resolveUrlAgainst(baseUrl),
                    encryption = encryption,
                    durationSeconds = nextSegmentDuration,
                )
                nextSegmentDuration = 0.0
            }
        }
    }

    val extension = when {
        initUrl != null -> "mp4"
        segments.any { it.url.fileExtensionForDownload() in setOf("m4s", "mp4") } -> "mp4"
        else -> "ts"
    }
    return HlsSingleFilePlan(
        mediaSequence = mediaSequence,
        initUrl = initUrl,
        outputExtension = extension,
        variantBandwidth = variantBandwidth,
        segments = segments,
    )
}

private fun String.toHlsEncryption(baseUrl: String): HlsEncryption? {
    val method = hlsAttribute("METHOD").orEmpty()
    if (method.equals("NONE", ignoreCase = true)) return null
    val keyUrl = hlsAttribute("URI")?.let { it.resolveUrlAgainst(baseUrl) }
    return HlsEncryption(
        method = method,
        keyUrl = keyUrl,
        iv = hlsAttribute("IV")?.hexToBytes(),
    )
}

private suspend fun YummyAnimeRepository.decryptHlsSegment(
    bytes: ByteArray,
    encryption: HlsEncryption,
    sequenceNumber: Long,
    headers: Map<String, String>,
    keyCache: MutableMap<String, ByteArray>,
): ByteArray {
    if (!encryption.method.equals("AES-128", ignoreCase = true)) {
        throw IOException("HLS ${encryption.method} не поддерживается для офлайн-скачивания")
    }
    val keyUrl = encryption.keyUrl ?: throw IOException("HLS ключ шифрования не найден")
    val key = keyCache[keyUrl] ?: downloadUrlBytes(keyUrl, headers).also { keyCache[keyUrl] = it }
    if (key.size != 16) throw IOException("Некорректный HLS ключ шифрования")
    val iv = encryption.iv ?: sequenceNumber.toAesIv()
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
    return cipher.doFinal(bytes)
}

private fun Long.toAesIv(): ByteArray {
    val result = ByteArray(16)
    var value = this
    for (index in 15 downTo 8) {
        result[index] = (value and 0xff).toByte()
        value = value ushr 8
    }
    return result
}

private fun String.hexToBytes(): ByteArray? {
    val clean = removePrefix("0x").removePrefix("0X").trim()
    if (clean.length % 2 != 0) return null
    return runCatching {
        ByteArray(clean.length / 2) { index ->
            clean.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }.getOrNull()
}

private fun File.partFile(): File {
    return File(parentFile, "$name.part")
}

private fun File.hlsStateFile(): File {
    return File(parentFile, "$name.state")
}

private data class HlsResumeState(
    val initWritten: Boolean,
    val nextSegmentIndex: Int,
)

private fun File.readHlsResumeState(signature: String): HlsResumeState? {
    if (!exists()) return null
    val lines = runCatching { readLines() }.getOrNull() ?: return null
    if (lines.getOrNull(0) != signature) return null
    return HlsResumeState(
        initWritten = lines.getOrNull(1)?.toBooleanStrictOrNull() ?: false,
        nextSegmentIndex = lines.getOrNull(2)?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
    )
}

private fun File.writeHlsResumeState(
    signature: String,
    initWritten: Boolean,
    nextSegmentIndex: Int,
) {
    parentFile?.mkdirs()
    writeText(
        listOf(signature, initWritten.toString(), nextSegmentIndex.coerceAtLeast(0).toString())
            .joinToString("\n"),
    )
}

private fun File.moveCompleteTo(target: File) {
    target.delete()
    if (!renameTo(target)) {
        inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        delete()
    }
}

private fun String.parseContentRangeTotal(): Long? {
    return substringAfter('/', "")
        .takeIf { it.isNotBlank() && it != "*" }
        ?.toLongOrNull()
}

private fun ResolvedVideoStream.qualityTitle(): String {
    return maxVideoHeight?.takeIf { it > 0 }?.let { "${it}p" }.orEmpty()
}

private fun HlsVariant.qualityTitle(): String {
    return height?.takeIf { it > 0 }?.let { "${it}p" }.orEmpty()
}

private const val DOWNLOAD_RETRY_COUNT = 5
private const val DOWNLOAD_RETRY_DELAY_MS = 700L

