package me.yummydroid.app.data

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
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
    private val downloadBandwidthLimiter: DownloadBandwidthLimiter = NoOpDownloadBandwidthLimiter,
) {
    private val offlineStorage = context?.let(::OfflineAnimeStorage)
    private val sourceQualityCache = context?.let(::SourceQualityCacheStorage)
    private val contentCache = context?.let(::AnimeContentCacheStorage)
    @Volatile
    private var contentLanguage: ContentLanguage = ContentLanguage.Russian
    @Volatile
    private var offlineFallbackActive: Boolean = false
    internal val downloadClient = defaultVideoDownloadClient()

    fun updateContentLanguage(language: ContentLanguage) {
        contentLanguage = language
        api.updateContentLanguage(language)
    }

    suspend fun getFeatured(filters: BrowseFilters, offset: Int = 0, limit: Int = PAGE_SIZE): List<Anime> = withContext(Dispatchers.IO) {
        if (filters.offlineOnly) {
            offlineFallbackActive = false
            return@withContext offlineAnimePage(filters = filters, offset = offset, limit = limit)
        }

        val token = authStorage?.readToken()
        val userMarkIds = resolveUserMarkAnimeIds(filters, token)
        if (userMarkIds?.includedIds != null && userMarkIds.includedIds.isEmpty()) return@withContext emptyList()

        contentCache?.readFeatured(
            language = contentLanguage,
            userId = cacheUserId(),
            filters = filters,
            offset = offset,
            limit = limit,
        )?.let { cached ->
            offlineFallbackActive = false
            return@withContext cached
        }

        try {
            offlineFallbackActive = false
            api.featuredAnime(
                limit = limit,
                offset = offset,
                filters = filters,
                authToken = token,
                ids = userMarkIds?.includedIds.orEmpty(),
            ).filterNot { it.id in userMarkIds?.excludedIds.orEmpty() }
                .also { animes ->
                    contentCache?.saveFeatured(
                        language = contentLanguage,
                        userId = cacheUserId(),
                        filters = filters,
                        offset = offset,
                        limit = limit,
                        animes = animes,
                    )
                }
        } catch (throwable: Throwable) {
            throwable.throwIfCancellation()
            val offline = offlineFallbackAnimePage(filters = filters, offset = offset, limit = limit)
            if (offline != null) {
                offlineFallbackActive = true
                offline
            } else {
                throw throwable
            }
        }
    }

    suspend fun search(query: String, filters: BrowseFilters, offset: Int = 0, limit: Int = PAGE_SIZE): List<Anime> = withContext(Dispatchers.IO) {
        if (filters.offlineOnly) {
            offlineFallbackActive = false
            return@withContext offlineAnimePage(query = query, filters = filters, offset = offset, limit = limit)
        }

        val token = authStorage?.readToken()
        val userMarkIds = resolveUserMarkAnimeIds(filters, token)
        if (userMarkIds?.includedIds != null && userMarkIds.includedIds.isEmpty()) return@withContext emptyList()

        contentCache?.readSearch(
            language = contentLanguage,
            userId = cacheUserId(),
            query = query,
            filters = filters,
            offset = offset,
            limit = limit,
        )?.let { cached ->
            offlineFallbackActive = false
            return@withContext cached
        }

        try {
            offlineFallbackActive = false
            api.search(
                query = query,
                limit = limit,
                offset = offset,
                filters = filters,
                authToken = token,
                ids = userMarkIds?.includedIds.orEmpty(),
            ).filterNot { it.id in userMarkIds?.excludedIds.orEmpty() }
                .also { animes ->
                    contentCache?.saveSearch(
                        language = contentLanguage,
                        userId = cacheUserId(),
                        query = query,
                        filters = filters,
                        offset = offset,
                        limit = limit,
                        animes = animes,
                    )
                }
        } catch (throwable: Throwable) {
            throwable.throwIfCancellation()
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

    suspend fun getFilterCatalog(): FilterCatalog = withContext(Dispatchers.IO) {
        contentCache?.readFilterCatalog(contentLanguage)?.let { return@withContext it }
        api.getFilterCatalog().also { catalog ->
            contentCache?.saveFilterCatalog(contentLanguage, catalog)
        }
    }

    suspend fun getAnimeWithVideos(animeId: Long): Pair<AnimeDetails, List<VideoVariant>> = withContext(Dispatchers.IO) {
        val offline = offlineStorage?.read(animeId)
        contentCache?.readAnimeWithVideos(
            language = contentLanguage,
            userId = cacheUserId(),
            animeId = animeId,
        )?.let { cached ->
            offlineFallbackActive = false
            return@withContext cached.details to cached.videos
                .withOfflineDownloads(offline?.videos.orEmpty(), cached.details)
                .withCachedSourceQualities()
        }

        try {
            offlineFallbackActive = false
            val (details, videos) = api.getAnimeWithVideos(animeId, authStorage?.readToken())
            val mergedVideos = videos.withOfflineDownloads(offline?.videos.orEmpty(), details)
                .withCachedSourceQualities()
            contentCache?.saveAnimeWithVideos(
                language = contentLanguage,
                userId = cacheUserId(),
                animeId = animeId,
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

    suspend fun getAnime(animeId: Long): AnimeDetails = withContext(Dispatchers.IO) {
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

    suspend fun getAnimeOnline(animeId: Long): AnimeDetails = withContext(Dispatchers.IO) {
        api.getAnime(animeId, authStorage?.readToken())
    }

    suspend fun getVideos(animeId: Long): List<VideoVariant> = withContext(Dispatchers.IO) {
        val offline = offlineStorage?.read(animeId)
        contentCache?.readVideos(
            language = contentLanguage,
            userId = cacheUserId(),
            animeId = animeId,
        )?.let { cached ->
            offlineFallbackActive = false
            return@withContext offline?.let { entry ->
                cached.withOfflineDownloads(entry.videos, entry.details).withCachedSourceQualities()
            } ?: cached.withCachedSourceQualities()
        }
        contentCache?.readAnimeWithVideos(
            language = contentLanguage,
            userId = cacheUserId(),
            animeId = animeId,
        )?.let { cached ->
            offlineFallbackActive = false
            return@withContext cached.videos
                .withOfflineDownloads(offline?.videos.orEmpty(), cached.details)
                .withCachedSourceQualities()
        }

        try {
            val videos = api.getVideos(animeId, authStorage?.readToken())
            val mergedVideos = offline?.let { entry ->
                videos.withOfflineDownloads(entry.videos, entry.details)
                    .withCachedSourceQualities()
            } ?: videos.withCachedSourceQualities()
            contentCache?.saveVideos(
                language = contentLanguage,
                userId = cacheUserId(),
                animeId = animeId,
                videos = mergedVideos.map { it.withoutOfflinePlayback() },
            )
            mergedVideos
        } catch (throwable: Throwable) {
            throwable.throwIfCancellation()
            offline?.videos ?: throw throwable
        }
    }

    suspend fun getSchedule(): List<ScheduleAnime> = withContext(Dispatchers.IO) {
        contentCache?.readSchedule(contentLanguage)?.let { return@withContext it }
        api.getSchedule().also { schedule ->
            contentCache?.saveSchedule(contentLanguage, schedule)
        }
    }

    suspend fun getCollections(offset: Int = 0, limit: Int = PAGE_SIZE): List<AnimeCollectionSummary> = withContext(Dispatchers.IO) {
        api.getCollections(offset = offset, limit = limit)
    }

    suspend fun getCollection(id: Long): AnimeCollectionSummary = withContext(Dispatchers.IO) {
        api.getCollection(id)
    }

    suspend fun getAnimeCollections(animeId: Long): List<AnimeCollectionSummary> = withContext(Dispatchers.IO) {
        api.getAnimeCollections(animeId)
    }

    suspend fun getAnimeComments(animeId: Long, offset: Int = 0, limit: Int = 20): List<AnimeComment> = withContext(Dispatchers.IO) {
        api.getAnimeComments(animeId, offset = offset, limit = limit)
    }

    suspend fun addAnimeComment(animeId: Long, text: String): AnimeComment? = withContext(Dispatchers.IO) {
        api.addAnimeComment(animeId, text, requireToken())
    }

    suspend fun getAnimeRecommendations(animeId: Long): List<Anime> = withContext(Dispatchers.IO) {
        api.getAnimeRecommendations(animeId)
    }

    suspend fun getAnimeRatingSummary(animeId: Long): AnimeRatingSummary = withContext(Dispatchers.IO) {
        api.getAnimeRatingSummary(animeId)
    }

    suspend fun setAnimeRating(animeId: Long, rating: Int): AnimeRatingSummary = withContext(Dispatchers.IO) {
        api.setAnimeRating(animeId, rating, requireToken())
    }

    suspend fun deleteAnimeRating(animeId: Long): AnimeRatingSummary = withContext(Dispatchers.IO) {
        api.deleteAnimeRating(animeId, requireToken())
    }

    suspend fun subscribeVideo(videoId: Long): Boolean = withContext(Dispatchers.IO) {
        api.subscribeVideo(videoId, requireToken())
    }

    suspend fun unsubscribeVideo(videoId: Long): Boolean = withContext(Dispatchers.IO) {
        api.unsubscribeVideo(videoId, requireToken())
    }

    suspend fun getVideoSubscriptions(): List<VideoSubscription> = withContext(Dispatchers.IO) {
        val token = authStorage?.readToken() ?: return@withContext emptyList()
        val userId = authStorage.readProfile()?.id ?: return@withContext emptyList()
        api.getVideoSubscriptions(userId, token)
    }

    suspend fun getNewEpisodeNotifications(limit: Int = 50): List<SiteNotification> {
        return getProfileNotifications(
            types = listOf("anime_episode"),
            subTypes = listOf("new_episode"),
            limit = limit,
        )
    }

    suspend fun getProfileNotifications(
        types: List<String> = emptyList(),
        subTypes: List<String> = emptyList(),
        offset: Int = 0,
        limit: Int = 50,
    ): List<SiteNotification> = withContext(Dispatchers.IO) {
        api.getProfileNotifications(
            token = requireToken(),
            types = types,
            subTypes = subTypes,
            offset = offset,
            limit = limit,
        )
    }

    suspend fun markProfileNotificationsRead(): Boolean = withContext(Dispatchers.IO) {
        api.markProfileNotificationsRead(requireToken())
    }

    suspend fun markProfileNotificationRead(notificationId: Long): Boolean = withContext(Dispatchers.IO) {
        api.markProfileNotificationRead(notificationId, requireToken())
    }

    suspend fun deleteProfileNotification(notificationId: Long): Boolean = withContext(Dispatchers.IO) {
        api.deleteProfileNotification(notificationId, requireToken())
    }

    suspend fun resolveVideoStream(
        video: VideoVariant,
        preferredQuality: PreferredQuality = PreferredQuality.Auto,
        waitForRuntimeSubtitles: Boolean = true,
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
                        withTimeout(candidate.sourceResolveTimeoutMs()) {
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

    suspend fun resolveSampledDownloadQualities(
        voiceKeys: Set<String>,
        videos: List<VideoVariant>,
    ): Map<String, List<PreferredQuality>> = withContext(Dispatchers.IO) {
        val requestedVoiceKeys = voiceKeys.filter { it.isNotBlank() }.toSet()
        if (requestedVoiceKeys.isEmpty()) return@withContext emptyMap()
        val candidates = videos
            .asSequence()
            .filter { it.downloadSampleVoiceKey in requestedVoiceKeys }
            .groupBy { "${it.downloadSampleVoiceKey}|${it.player.cleanVideoSourceLabel().lowercase(Locale.ROOT)}" }
            .values
            .mapNotNull { group -> group.selectDownloadQualitySampleCandidate() }
            .map { it.withoutOfflinePlayback() }
            .withCachedSourceQualities()
            .distinctBy { it.sourceResolveIdentity() }
        if (candidates.isEmpty()) return@withContext emptyMap()

        val knownQualities = candidates.map { candidate ->
            SourceQualityResolveResult(candidate, candidate.sourceQualities)
        }
        val missingCandidates = candidates.filter { it.sourceQualities.isEmpty() }
        val resolvedQualities = supervisorScope {
            missingCandidates.map { candidate ->
                async {
                    runCatching {
                        withTimeout(candidate.sourceResolveTimeoutMs()) {
                            SourceQualityResolveResult(candidate, resolveVideoStream(candidate).availableQualities)
                        }
                    }.getOrElse {
                        sourceQualityCache?.remove(candidate)
                        SourceQualityResolveResult(candidate, emptyList())
                    }
                }
            }.awaitAll()
        }

        (knownQualities + resolvedQualities)
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

    suspend fun offlineAnime(): List<OfflineAnimeEntry> = withContext(Dispatchers.IO) {
        offlineStorage?.readAll().orEmpty()
    }

    fun isOfflineFallbackActive(): Boolean {
        return offlineFallbackActive
    }

    suspend fun deleteOfflineVideo(animeId: Long, videoId: Long, playbackUrl: String? = null) = withContext(Dispatchers.IO) {
        offlineStorage?.deleteVideo(animeId, videoId, playbackUrl)
    }

    suspend fun deleteOfflineAnime(animeId: Long) = withContext(Dispatchers.IO) {
        offlineStorage?.deleteAnime(animeId)
    }

    suspend fun clearAppContentCache(playbackProgressStorage: PlaybackProgressStorage) = withContext(Dispatchers.IO) {
        offlineStorage?.clearOfflineCache()
        playbackProgressStorage.clear()
        contentCache?.clear()
        sourceQualityCache?.clear()
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
        check(!isCancelled()) { "Download cancelled" }
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
                        bandwidthLimiter = downloadBandwidthLimiter,
                    )
                } else if (stream.isDashStream()) {
                    throw IOException("DASH offline downloading is not available for this source yet")
                } else {
                    downloadDirectVideo(
                        storage = storage,
                        video = playback.video,
                        stream = stream,
                        preferredQuality = preferredQuality,
                        onProgress = onProgress,
                        isCancelled = isCancelled,
                        deletePartialOnCancel = deletePartialOnCancel,
                        bandwidthLimiter = downloadBandwidthLimiter,
                    )
                }
            }.getOrElse { throwable ->
                throwable.throwIfCancellation()
                if (isCancelled() || throwable.message.equals("Download cancelled", ignoreCase = true)) {
                    throw IllegalStateException("Download cancelled", throwable)
                }
                failures += "${playback.video.groupTitle.ifBlank { playback.video.player }}: ${throwable.message.orEmpty()}"
                null
            } ?: continue

            if (isCancelled()) {
                if (deletePartialOnCancel()) target.delete()
                throw IllegalStateException("Download cancelled")
            }
            storage.markVideoDownloaded(details, videos, playback.video, target, target.name.mimeTypeFromFileName() ?: stream.mimeType)
            val downloaded = storage.read(details.id)
                ?.videos
                ?.firstOrNull { stored ->
                    (stored.id == playback.video.id || stored.downloadVoiceSlotKey == playback.video.downloadVoiceSlotKey) &&
                        stored.offlineFiles.any { it.matchesPreferredQuality(preferredQuality) && it.bytes > 0L }
                }
                ?: throw IOException("Downloaded file was not confirmed by the offline index")
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
                append("Could not download episode")
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
        waitForRuntimeSubtitles: Boolean = true,
    ): ResolvedPlayback {
        val uniqueCandidates = candidates.distinctBy { it.sourceResolveIdentity() }.ifEmpty {
            throw IOException("No sources are available for the episode")
        }

        val selectableKeys = uniqueCandidates.mapTo(mutableSetOf()) { it.sourceResolveIdentity() }
        val uniqueMetadataCandidates = (uniqueCandidates + metadataCandidates)
            .distinctBy { it.sourceResolveIdentity() }

        val attempts = resolveCandidateAttempts(
            candidates = uniqueMetadataCandidates,
            preferredQuality = preferredQuality,
            waitForRuntimeSubtitles = waitForRuntimeSubtitles,
        )

        val best = attempts.bestPlayback(selectableKeys)

        if (best != null) return best.withMetadataFromAttempts(attempts)

        throw attempts.resolveFailure("Could not start any episode source")
    }

    suspend fun resolvePlaybackMetadata(
        playback: ResolvedPlayback,
        metadataCandidates: List<VideoVariant>,
        preferredQuality: PreferredQuality,
    ): ResolvedPlayback {
        val candidates = (listOf(playback.video) + metadataCandidates)
            .filter { candidate -> candidate.isSameEpisodeAs(playback.video) && candidate.hasSameVoiceAs(playback.video) }
            .distinctBy { it.sourceResolveIdentity() }
            .ifEmpty { return playback }
        val attempts = resolveCandidateAttempts(
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
                throw IOException("No online sources are available for downloading this episode")
            }

        val attempts = resolveCandidateAttempts(uniqueCandidates, preferredQuality)

        val playbacks = attempts.downloadPlaybacks(preferredQuality)

        if (playbacks.isNotEmpty()) return playbacks

        val requestedHeight = preferredQuality.height
        if (requestedHeight != null && attempts.any { it.playback != null }) {
            throw IOException("No working source with ${preferredQuality.title} quality is available for download")
        }

        throw attempts.resolveFailure("Could not find a working source for download")
    }

    private suspend fun resolveCandidateAttempts(
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

    fun cachedProfile(): UserProfile? {
        return authStorage?.readProfile()
    }

    suspend fun restoreProfile(): UserProfile? = withContext(Dispatchers.IO) {
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

    suspend fun login(login: String, password: String, captchaResponse: String? = null): UserProfile = withContext(Dispatchers.IO) {
        val token = api.login(login, password, captchaResponse)
        authStorage?.saveToken(token)
        api.getProfile(token).also { profile ->
            authStorage?.saveProfile(profile)
        }
    }

    fun submitCaptchaResponse(response: String) {
        api.submitCaptchaResponse(response)
    }

    fun logout() {
        authStorage?.clear()
    }

    suspend fun getAnimeMark(animeId: Long): UserAnimeMark? = withContext(Dispatchers.IO) {
        val token = authStorage?.readToken() ?: return@withContext null
        api.getAnimeMark(animeId, token)
    }

    suspend fun setAnimeListMark(animeId: Long, mark: UserAnimeListMark): UserAnimeMark = withContext(Dispatchers.IO) {
        val token = requireToken()
        api.setAnimeListMark(animeId, mark, token)
    }

    suspend fun removeAnimeListMark(animeId: Long): UserAnimeMark = withContext(Dispatchers.IO) {
        val token = requireToken()
        api.removeAnimeListMark(animeId, token)
    }

    suspend fun setFavorite(animeId: Long, isFavorite: Boolean): UserAnimeMark = withContext(Dispatchers.IO) {
        val token = requireToken()
        api.setFavorite(animeId, isFavorite, token)
    }

    suspend fun getWatchHistory(limit: Int = 100, offset: Int = 0): List<PlaybackProgress> = withContext(Dispatchers.IO) {
        val token = requireToken()
        api.getWatchHistory(token, limit, offset)
    }

    suspend fun saveWatchProgress(progress: PlaybackProgress): Boolean = withContext(Dispatchers.IO) {
        val token = authStorage?.readToken() ?: return@withContext false
        api.saveWatchProgress(progress, token)
    }

    suspend fun deleteWatchProgress(videoIds: List<Long>): Boolean = withContext(Dispatchers.IO) {
        val token = requireToken()
        api.deleteWatchProgress(videoIds, token)
    }

    private fun requireToken(): String {
        return authStorage?.readToken() ?: error("Sign in is required")
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

    private fun cacheUserId(): Long? {
        return authStorage?.readProfile()?.id?.takeIf { it > 0L }
    }

    private companion object {
        const val PAGE_SIZE = 36
        const val FAVORITES_FILTER_ID = 4
    }
}

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

private fun ResolvedPlayback.withMetadataFromAttempts(
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
        .filter { playback -> playback.video.isSameEpisodeAs(video) && playback.video.hasSameVoiceAs(video) }
        .toList()

    val sameSourceStream = sameVoicePlaybacks
        .filter { playback -> playback.video.sourceResolveIdentity() == video.sourceResolveIdentity() }
        .maxWithOrNull(
            compareBy<ResolvedPlayback> { playback -> if (playback.stream.hasSubtitles) 1 else 0 }
                .thenBy { playback -> playback.stream.availableQualities.size },
        )
        ?.stream
        ?: stream

    val mergedQualities = (stream.sourceQualitiesWithMax() + sameVoicePlaybacks.flatMap { playback ->
        playback.stream.sourceQualitiesWithMax()
    }).normalizedSourceQualities()
    val sourceSubtitleSourceKeys = (stream.sourceSubtitleSourceKeys + sameVoicePlaybacks.mapNotNull { playback ->
        playback.video.matchingSourceKey.takeIf { key -> key.isNotBlank() && playback.stream.hasSubtitles }
    }).toSet()

    if (
        sameSourceStream.subtitles == stream.subtitles &&
        sameSourceStream.embeddedSubtitles == stream.embeddedSubtitles &&
        sameSourceStream.hasEmbeddedSubtitles == stream.hasEmbeddedSubtitles &&
        mergedQualities == stream.availableQualities.normalizedSourceQualities() &&
        sourceSubtitleSourceKeys == stream.sourceSubtitleSourceKeys
    ) {
        return this
    }
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

private fun ResolvedVideoStream.sourceQualitiesWithMax(): List<SourceQuality> {
    return availableQualities + listOfNotNull(maxVideoHeight?.let { SourceQuality(height = it) })
}

private fun List<SourceResolveAttempt>.downloadPlaybacks(preferredQuality: PreferredQuality): List<ResolvedPlayback> {
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
        .replace('\u0451', '\u0435')
        .replace(Regex("[^a-z\\u0430-\\u044f0-9]+"), " ")
        .trim()
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
    return selectedVideoHeight
        ?.qualityPreferenceScore(preferredQuality)
        ?: sourceResolutionHeight().qualityPreferenceScore(preferredQuality)
}

private fun ResolvedVideoStream.hasExactDownloadQuality(height: Int): Boolean {
    selectedVideoHeight?.let { return it == height }
    return maxVideoHeight == height ||
        availableQualities.any { it.height == height } ||
        url.detectDownloadQualityHeight() == height
}

private fun ResolvedVideoStream.requireExactDownloadQuality(preferredQuality: PreferredQuality) {
    val height = preferredQuality.height ?: return
    if (!hasExactDownloadQuality(height)) {
        throw IOException("Source does not contain selected quality ${preferredQuality.title}")
    }
}

private fun String.detectDownloadQualityHeight(): Int? {
    return Regex("""(?i)(?:^|[^\d])(\d{3,4})p(?:[^\d]|$)""")
        .find(substringBefore('?').substringBefore('#'))
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?.takeIf { it in 100..4320 }
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
        ?: "Auto"
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
    preferredQuality: PreferredQuality,
    onProgress: (DownloadProgressInfo) -> Unit,
    isCancelled: () -> Boolean,
    deletePartialOnCancel: () -> Boolean,
    bandwidthLimiter: DownloadBandwidthLimiter,
): File {
    stream.requireExactDownloadQuality(preferredQuality)
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
    var sessionDownloadedBytes = 0L
    var attempt = 0

    while (true) {
        try {
            check(!isCancelled()) { "Download cancelled" }
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
                            check(!isCancelled()) { "Download cancelled" }
                            val read = input.read(buffer)
                            if (read <= 0) break
                            bandwidthLimiter.throttle(read.toLong())
                            output.write(buffer, 0, read)
                            readTotal += read
                            sessionDownloadedBytes += read.toLong()
                            val elapsedMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(1L)
                            val speed = (sessionDownloadedBytes * 1000L / elapsedMs).coerceAtLeast(0L)
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
                if (totalBytes > 0L && temp.length().coerceAtLeast(0L) < totalBytes) {
                    throw IOException("Download incomplete")
                }
            }
            temp.moveCompleteTo(target)
            break
        } catch (throwable: Throwable) {
            throwable.throwIfCancellation()
            if (isCancelled() || throwable.message.equals("Download cancelled", ignoreCase = true)) {
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
    bandwidthLimiter: DownloadBandwidthLimiter,
): File {
    val initialPlaylist = downloadText(stream.url, stream.headers)
    val hlsVariants = initialPlaylist.hlsVariants(stream.url)
    val selectedVariant = if (preferredQuality.height != null && hlsVariants.isNotEmpty()) {
        hlsVariants.selectExactQuality(preferredQuality)
            ?: throw IOException("HLS source does not contain ${preferredQuality.title} quality")
    } else {
        hlsVariants.selectForQuality(preferredQuality)
    }
    if (hlsVariants.isEmpty()) {
        stream.requireExactDownloadQuality(preferredQuality)
    }
    val mediaUrl = selectedVariant?.url ?: stream.url
    val mediaPlaylist = if (mediaUrl == stream.url) initialPlaylist else downloadText(mediaUrl, stream.headers)
    val plan = mediaPlaylist.toHlsSingleFilePlan(mediaUrl, selectedVariant?.bandwidth ?: 0)
    if (plan.segments.isEmpty()) {
        throw IOException("HLS playlist does not contain segments to download")
    }

    val keyCache = mutableMapOf<String, ByteArray>()
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
    var sessionDownloadedBytes = 0L
    val voiceTitle = video.downloadVoiceTitle()

    try {
        FileOutputStream(temp, true).use { output ->
            var initWritten = resumeState?.initWritten ?: false
            var nextSegmentIndex = resumeState?.nextSegmentIndex ?: 0
            if (plan.initUrl != null && !initWritten) {
                val bytes = downloadUrlBytes(plan.initUrl, stream.headers, bandwidthLimiter)
                output.write(bytes)
                output.flush()
                downloadedBytes = temp.length().coerceAtLeast(0L)
                sessionDownloadedBytes += bytes.size.toLong()
                initWritten = true
                stateFile.writeHlsResumeState(signature, initWritten, nextSegmentIndex)
            }
            while (nextSegmentIndex < plan.segments.size) {
                val index = nextSegmentIndex
                val segment = plan.segments[index]
                check(!isCancelled()) { "Download cancelled" }
                val bytes = downloadUrlBytes(segment.url, stream.headers, bandwidthLimiter)
                val payload = segment.encryption?.let { encryption ->
                    decryptHlsSegment(
                        bytes = bytes,
                        encryption = encryption,
                        sequenceNumber = plan.mediaSequence + index,
                        headers = stream.headers,
                        keyCache = keyCache,
                        bandwidthLimiter = bandwidthLimiter,
                    )
                } ?: bytes
                output.write(payload)
                output.flush()
                nextSegmentIndex = index + 1
                downloadedBytes = temp.length().coerceAtLeast(0L)
                sessionDownloadedBytes += payload.size.toLong()
                stateFile.writeHlsResumeState(signature, initWritten = true, nextSegmentIndex = nextSegmentIndex)
                val elapsedMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(1L)
                val speed = (sessionDownloadedBytes * 1000L / elapsedMs).coerceAtLeast(0L)
                val fraction = (nextSegmentIndex.toFloat() / plan.segments.size.toFloat()).coerceIn(0f, 1f)
                onProgress(
                    DownloadProgressInfo(
                        fraction = fraction,
                        downloadedBytes = downloadedBytes,
                        totalBytes = -1L,
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
        throwable.throwIfCancellation()
        if (isCancelled() || throwable.message.equals("Download cancelled", ignoreCase = true)) {
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
    bandwidthLimiter: DownloadBandwidthLimiter,
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
                val body = response.body ?: throw IOException("Empty HLS resource")
                ByteArrayOutputStream().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            bandwidthLimiter.throttle(read.toLong())
                            output.write(buffer, 0, read)
                        }
                    }
                    output.toByteArray()
                }
            }
        } catch (throwable: Throwable) {
            throwable.throwIfCancellation()
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
    bandwidthLimiter: DownloadBandwidthLimiter,
): ByteArray {
    if (!encryption.method.equals("AES-128", ignoreCase = true)) {
        throw IOException("HLS ${encryption.method} is not supported for offline downloading")
    }
    val keyUrl = encryption.keyUrl ?: throw IOException("HLS encryption key was not found")
    val key = keyCache[keyUrl] ?: downloadUrlBytes(keyUrl, headers, bandwidthLimiter).also { keyCache[keyUrl] = it }
    if (key.size != 16) throw IOException("Invalid HLS encryption key")
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

private fun ResolvedVideoStream.withSourceSubtitleVideo(video: VideoVariant): ResolvedVideoStream {
    val sourceKey = video.matchingSourceKey.takeIf { it.isNotBlank() && hasSubtitles } ?: return this
    return copy(sourceSubtitleSourceKeys = sourceSubtitleSourceKeys + sourceKey)
}

private fun Throwable.throwIfCancellation() {
    if (this is CancellationException) throw this
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
    return selectedVideoHeight?.takeIf { it > 0 }?.let { "${it}p" }
        ?: maxVideoHeight?.takeIf { it > 0 }?.let { "${it}p" }
        ?: url.detectDownloadQualityHeight()?.let { "${it}p" }
        ?: ""
}

private fun HlsVariant.qualityTitle(): String {
    return height?.takeIf { it > 0 }?.let { "${it}p" }.orEmpty()
}

private const val DOWNLOAD_RETRY_COUNT = 5
private const val DOWNLOAD_RETRY_DELAY_MS = 700L
