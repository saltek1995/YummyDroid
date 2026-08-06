package me.yummydroid.app.data

import android.content.Context
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

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

        val heights = resolveSourceQualityResults(candidates).availableDownloadHeights(allEpisodes)

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

        resolveSourceQualityResults(candidates)
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

    private suspend fun resolveSourceQualityResults(candidates: List<VideoVariant>): List<SourceQualityResolveResult> {
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
        return knownQualities + resolvedQualities
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
