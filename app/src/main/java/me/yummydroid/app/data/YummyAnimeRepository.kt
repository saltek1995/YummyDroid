package me.yummydroid.app.data

import android.content.Context
import java.io.IOException
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
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
    @Volatile
    private var offlineFallbackActive: Boolean = false
    internal val downloadClient = OkHttpClient.Builder()
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun updateContentLanguage(language: ContentLanguage) {
        api.updateContentLanguage(language)
    }

    suspend fun getFeatured(filters: BrowseFilters, offset: Int = 0, limit: Int = PAGE_SIZE): List<Anime> {
        if (filters.offlineOnly) {
            offlineFallbackActive = false
            return offlineStorage?.readAll()
                .orEmpty()
                .map { it.anime }
                .drop(offset)
                .take(limit)
        }

        val token = authStorage?.readToken()
        val userListIds = resolveUserMarkAnimeIds(filters, token)
        if (userListIds != null && userListIds.isEmpty()) return emptyList()

        return try {
            offlineFallbackActive = false
            api.featuredAnime(
                limit = limit,
                offset = offset,
                filters = filters,
                authToken = token,
                ids = userListIds.orEmpty(),
            )
        } catch (throwable: Throwable) {
            val offline = offlineStorage?.readAll()
                ?.map { it.anime }
                ?.drop(offset)
                ?.take(limit)
                ?.takeIf { it.isNotEmpty() }
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
            return offlineStorage?.searchOffline(query, offset, limit).orEmpty()
        }

        val token = authStorage?.readToken()
        val userListIds = resolveUserMarkAnimeIds(filters, token)
        if (userListIds != null && userListIds.isEmpty()) return emptyList()

        return try {
            offlineFallbackActive = false
            api.search(
                query = query,
                limit = limit,
                offset = offset,
                filters = filters,
                authToken = token,
                ids = userListIds.orEmpty(),
            )
        } catch (throwable: Throwable) {
            val offline = offlineStorage?.searchOffline(query, offset, limit)
                ?.takeIf { it.isNotEmpty() }
            if (offline != null) {
                offlineFallbackActive = true
                offline
            } else {
                throw throwable
            }
        }
    }

    suspend fun getFilterCatalog(): FilterCatalog {
        return api.getFilterCatalog()
    }

    suspend fun getAnimeWithVideos(animeId: Long): Pair<AnimeDetails, List<VideoVariant>> {
        val offline = offlineStorage?.read(animeId)
        return try {
            offlineFallbackActive = false
            val (details, videos) = api.getAnimeWithVideos(animeId, authStorage?.readToken())
            val mergedVideos = videos.withOfflineDownloads(offline?.videos.orEmpty(), details)
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

    suspend fun getVideos(animeId: Long): List<VideoVariant> {
        return try {
            val videos = api.getVideos(animeId)
            offlineStorage?.read(animeId)?.let { offline ->
                videos.withOfflineDownloads(offline.videos, offline.details)
            } ?: videos
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

    suspend fun getAnimeComments(animeId: Long): List<AnimeComment> {
        return api.getAnimeComments(animeId)
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
        if (video.localPlaybackUrl.isNotBlank()) {
            return ResolvedVideoStream(
                url = video.localPlaybackUrl,
                mimeType = video.localMimeType,
                headers = emptyMap(),
                maxVideoHeight = null,
            )
        }
        return videoStreamResolver.resolve(video)
    }

    fun offlineAnime(): List<OfflineAnimeEntry> {
        return offlineStorage?.readAll().orEmpty()
    }

    fun isOfflineFallbackActive(): Boolean {
        return offlineFallbackActive
    }

    fun deleteOfflineVideo(animeId: Long, videoId: Long) {
        offlineStorage?.deleteVideo(animeId, videoId)
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
        onProgress: (Float) -> Unit,
    ): VideoVariant = withContext(Dispatchers.IO) {
        val storage = offlineStorage ?: error("Offline storage is unavailable")
        val playback = resolveBestPlaybackSource(videos.downloadCandidatesFor(video), preferredQuality)
        val stream = playback.stream
        val target = if (stream.isHlsStream()) {
            downloadHlsPackage(storage, playback.video, stream, preferredQuality, onProgress)
        } else if (stream.isDashStream()) {
            throw IOException("DASH офлайн-скачивание пока недоступно для этого источника")
        } else {
            downloadDirectVideo(storage, playback.video, stream, onProgress)
        }
        storage.markVideoDownloaded(details, videos, playback.video, target, stream.mimeType ?: target.name.mimeTypeFromFileName())
        val downloaded = storage.read(details.id)
            ?.videos
            ?.firstOrNull { it.id == playback.video.id }
            ?: playback.video
        onProgress(1f)
        downloaded
    }

    fun cachedSiteBaseUrl(): String {
        return siteDomainResolver.cachedOrDefaultBaseUrl()
    }

    suspend fun activeSiteBaseUrl(): String {
        return siteDomainResolver.activeBaseUrl()
    }

    suspend fun resolveFirstPlaybackSource(candidates: List<VideoVariant>): ResolvedPlayback {
        val uniqueCandidates = candidates.distinctBy { it.id }.ifEmpty {
            throw IOException("Нет доступных источников для серии")
        }
        val failures = mutableListOf<String>()

        uniqueCandidates.forEach { candidate ->
            runCatching { withTimeout(SOURCE_RESOLVE_TIMEOUT_MS) { videoStreamResolver.resolve(candidate) } }
                .onSuccess { stream ->
                    return ResolvedPlayback(video = candidate, stream = stream)
                }
                .onFailure { throwable ->
                    failures += "${candidate.groupTitle.ifBlank { candidate.player }}: ${throwable.message.orEmpty()}"
                }
        }

        val details = failures.take(4).joinToString("; ").takeIf { it.isNotBlank() }
        throw IOException(
            buildString {
                append("Не удалось запустить ни один источник серии")
                if (details != null) append(": ").append(details)
            },
        )
    }

    suspend fun resolveBestPlaybackSource(candidates: List<VideoVariant>): ResolvedPlayback {
        return resolveBestPlaybackSource(candidates, PreferredQuality.Auto)
    }

    suspend fun resolveBestPlaybackSource(
        candidates: List<VideoVariant>,
        preferredQuality: PreferredQuality,
    ): ResolvedPlayback {
        val uniqueCandidates = candidates.distinctBy { it.id }.ifEmpty {
            throw IOException("Нет доступных источников для серии")
        }

        val attempts = supervisorScope {
            uniqueCandidates.mapIndexed { index, candidate ->
                async {
                    runCatching {
                        withTimeout(SOURCE_RESOLVE_TIMEOUT_MS) {
                            videoStreamResolver.resolve(candidate)
                        }
                    }.fold(
                        onSuccess = { stream ->
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

        val best = attempts
            .mapNotNull { it.playback?.let { playback -> it.index to playback } }
            .sortedWith(
                compareByDescending<Pair<Int, ResolvedPlayback>> { (_, playback) ->
                    playback.stream.qualityScore(preferredQuality)
                }.thenBy { (index, _) -> index },
            )
            .firstOrNull()
            ?.second

        if (best != null) return best

        val details = attempts
            .mapNotNull { attempt ->
                attempt.failure?.let { throwable ->
                    "${attempt.candidate.groupTitle.ifBlank { attempt.candidate.player }}: ${throwable.message.orEmpty()}"
                }
            }
            .take(4)
            .joinToString("; ")
            .takeIf { it.isNotBlank() }
        throw IOException(
            buildString {
                append("Не удалось запустить ни один источник серии")
                if (details != null) append(": ").append(details)
            },
        )
    }

    fun cachedProfile(): UserProfile? {
        return authStorage?.readProfile()
    }

    suspend fun restoreProfile(): UserProfile? {
        val storage = authStorage ?: return null
        val token = storage.readToken() ?: return null
        val cachedProfile = storage.readProfile()
        val refreshedToken = runCatching { api.refreshToken(token) }.getOrElse { token }
        if (refreshedToken != token) {
            storage.saveToken(refreshedToken)
        }
        return runCatching { api.getProfile(refreshedToken) }
            .onSuccess { storage.saveProfile(it) }
            .getOrElse { cachedProfile ?: throw it }
    }

    suspend fun login(login: String, password: String, captchaResponse: String? = null): UserProfile {
        val token = api.login(login, password, captchaResponse)
        authStorage?.saveToken(token)
        return api.getProfile(token).also { profile ->
            authStorage?.saveProfile(profile)
        }
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
        val token = authStorage?.readToken() ?: return emptyList()
        return api.getWatchHistory(token, limit, offset)
    }

    suspend fun saveWatchProgress(progress: PlaybackProgress): Boolean {
        val token = authStorage?.readToken() ?: return false
        return api.saveWatchProgress(progress, token)
    }

    private fun requireToken(): String {
        return authStorage?.readToken() ?: error("Нужно войти в аккаунт")
    }

    private suspend fun resolveUserMarkAnimeIds(filters: BrowseFilters, token: String?): Set<Long>? {
        if (filters.userMarks.isEmpty()) return null
        val userId = authStorage?.readProfile()?.id ?: return emptySet()
        val authToken = token?.takeIf { it.isNotBlank() } ?: return emptySet()
        val selectedMarkIds = filters.userMarks.mapNotNull { it.toIntOrNull() }.toSet()

        return buildSet {
            selectedMarkIds
                .filterNot { it == FAVORITES_FILTER_ID }
                .forEach { listId -> addAll(api.getUserListAnimeIds(userId, listId, authToken)) }

            if (FAVORITES_FILTER_ID in selectedMarkIds) {
                addAll(api.getUserFavoriteAnimeIds(userId, authToken))
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = 36
        const val FAVORITES_FILTER_ID = 4
        const val SOURCE_RESOLVE_TIMEOUT_MS = 15_000L
    }
}

private data class SourceResolveAttempt(
    val index: Int,
    val candidate: VideoVariant,
    val playback: ResolvedPlayback? = null,
    val failure: Throwable? = null,
)

private fun List<VideoVariant>.withOfflineDownloads(
    offlineVideos: List<VideoVariant>,
    details: AnimeDetails,
): List<VideoVariant> {
    val offlineById = offlineVideos
        .filter { it.isOfflineAvailable }
        .associateBy { it.id }
    val offlineBySlot = offlineVideos
        .filter { it.isOfflineAvailable }
        .associateBy { it.offlineSlotKey() }

    return mapIndexed { index, video ->
        val offline = offlineById[video.id] ?: offlineBySlot[video.offlineSlotKey()]
        val preview = video.previewUrl.ifBlank {
            details.screenshots.getOrNull(index % details.screenshots.size.coerceAtLeast(1)).orEmpty()
        }.ifBlank {
            details.backdropUrl.orEmpty()
        }.ifBlank {
            details.posterUrl
        }

        if (offline != null) {
            video.copy(
                previewUrl = preview.ifBlank { offline.previewUrl },
                localPlaybackUrl = offline.localPlaybackUrl,
                localMimeType = offline.localMimeType,
                localBytes = offline.localBytes,
            )
        } else {
            video.copy(previewUrl = preview)
        }
    }
}

private fun List<VideoVariant>.downloadCandidatesFor(requested: VideoVariant): List<VideoVariant> {
    val sameEpisode = filter { candidate ->
        candidate.animeId == requested.animeId &&
            (
                candidate.episode.isNotBlank() && candidate.episode == requested.episode ||
                    candidate.episode.isBlank() && requested.episode.isBlank() && candidate.index == requested.index
                )
    }.ifEmpty { listOf(requested) }

    return sameEpisode.sortedWith(
        compareByDescending<VideoVariant> { it.id == requested.id }
            .thenByDescending { it.dubbing.cleanVoiceKey() == requested.dubbing.cleanVoiceKey() }
            .thenBy { it.index },
    )
}

private fun ResolvedVideoStream.qualityScore(preferredQuality: PreferredQuality): Int {
    val height = maxVideoHeight ?: 0
    val preferredHeight = preferredQuality.height ?: return height
    return when {
        height <= 0 -> 0
        height == preferredHeight -> 1_000_000
        height > preferredHeight -> 900_000 - (height - preferredHeight).coerceAtLeast(0)
        else -> height
    }
}

private fun String.cleanVoiceKey(): String {
    return trim()
        .lowercase()
        .replace(Regex("""\s+"""), " ")
}

private fun VideoVariant.offlineSlotKey(): String {
    return listOf(
        animeId.toString(),
        episode.ifBlank { index.toString() },
        player,
        dubbing,
    ).joinToString("|") { it.trim().lowercase() }
}

private fun ResolvedVideoStream.isHlsStream(): Boolean {
    return mimeType?.contains("mpegurl", ignoreCase = true) == true ||
        url.contains(".m3u8", ignoreCase = true)
}

private fun ResolvedVideoStream.isDashStream(): Boolean {
    return mimeType?.contains("dash", ignoreCase = true) == true ||
        url.contains(".mpd", ignoreCase = true)
}

private fun YummyAnimeRepository.downloadDirectVideo(
    storage: OfflineAnimeStorage,
    video: VideoVariant,
    stream: ResolvedVideoStream,
    onProgress: (Float) -> Unit,
): File {
    val target = storage.targetFile(video, stream.url.fileExtensionForDownload())
    val request = Request.Builder()
        .url(stream.url)
        .headers(stream.headers.toOkHttpHeaders())
        .build()

    downloadClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            throw IOException("Download HTTP ${response.code}")
        }
        val body = response.body ?: throw IOException("Empty download body")
        val totalBytes = body.contentLength().takeIf { it > 0L } ?: -1L
        target.outputStream().use { output ->
            body.byteStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var readTotal = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    readTotal += read
                    if (totalBytes > 0L) onProgress((readTotal.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f))
                }
            }
        }
    }
    onProgress(1f)
    return target
}

private fun YummyAnimeRepository.downloadHlsPackage(
    storage: OfflineAnimeStorage,
    video: VideoVariant,
    stream: ResolvedVideoStream,
    preferredQuality: PreferredQuality,
    onProgress: (Float) -> Unit,
): File {
    val playlistFile = storage.targetFile(video, "m3u8")
    val segmentDir = File(playlistFile.parentFile, playlistFile.nameWithoutExtension + "_segments").apply {
        deleteRecursively()
        mkdirs()
    }

    val initialPlaylist = downloadText(stream.url, stream.headers)
    val mediaUrl = initialPlaylist.selectBestHlsVariantUrl(stream.url, preferredQuality) ?: stream.url
    val mediaPlaylist = if (mediaUrl == stream.url) initialPlaylist else downloadText(mediaUrl, stream.headers)
    val segmentLines = mediaPlaylist.lines().filter { line ->
        line.trim().isNotBlank() && !line.trimStart().startsWith("#")
    }
    if (segmentLines.isEmpty()) {
        throw IOException("HLS плейлист не содержит сегментов для скачивания")
    }

    var segmentIndex = 0
    val rewritten = mediaPlaylist.lineSequence()
        .map { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("#EXT-X-KEY", ignoreCase = true) && line.contains("URI=\"") ->
                    rawLine.rewriteQuotedUri(mediaUrl, stream.headers, segmentDir, "key")
                line.startsWith("#EXT-X-MAP", ignoreCase = true) && line.contains("URI=\"") ->
                    rawLine.rewriteQuotedUri(mediaUrl, stream.headers, segmentDir, "init")
                line.isBlank() || line.startsWith("#") -> rawLine
                else -> {
                    val segmentUrl = resolvePlaylistUrl(mediaUrl, line)
                    val extension = segmentUrl.fileExtensionForDownload().takeIf { it != "mp4" } ?: "ts"
                    val segmentFile = File(segmentDir, "segment_${segmentIndex.toString().padStart(5, '0')}.$extension")
                    downloadUrlToFile(segmentUrl, stream.headers, segmentFile)
                    segmentIndex += 1
                    onProgress((segmentIndex.toFloat() / segmentLines.size.toFloat()).coerceIn(0f, 1f))
                    "${segmentDir.name}/${segmentFile.name}"
                }
            }
        }
        .joinToString("\n")

    playlistFile.writeText(rewritten)
    onProgress(1f)
    return playlistFile
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

private fun YummyAnimeRepository.downloadUrlToFile(
    url: String,
    headers: Map<String, String>,
    target: File,
) {
    val request = Request.Builder()
        .url(url)
        .headers(headers.toOkHttpHeaders())
        .build()
    downloadClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IOException("Download HTTP ${response.code}")
        val body = response.body ?: throw IOException("Empty segment")
        target.outputStream().use { output ->
            body.byteStream().use { input -> input.copyTo(output) }
        }
    }
}

private fun String.selectBestHlsVariantUrl(
    baseUrl: String,
    preferredQuality: PreferredQuality,
): String? {
    val lines = lines()
    val variants = mutableListOf<HlsVariant>()
    lines.forEachIndexed { index, line ->
        if (line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true)) {
            val height = Regex("""(?i)RESOLUTION=\d+x(\d+)""")
                .find(line)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
            val bandwidth = Regex("""(?i)BANDWIDTH=(\d+)""")
                .find(line)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?: 0
            val variant = lines.drop(index + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") }
            if (!variant.isNullOrBlank()) {
                variants += HlsVariant(
                    height = height,
                    bandwidth = bandwidth,
                    url = resolvePlaylistUrl(baseUrl, variant.trim()),
                )
            }
        }
    }
    return variants.selectForQuality(preferredQuality)?.url
}

private data class HlsVariant(
    val height: Int?,
    val bandwidth: Int,
    val url: String,
)

private fun List<HlsVariant>.selectForQuality(preferredQuality: PreferredQuality): HlsVariant? {
    if (isEmpty()) return null
    val preferredHeight = preferredQuality.height
    if (preferredHeight == null) {
        return maxWithOrNull(compareBy<HlsVariant> { it.height ?: 0 }.thenBy { it.bandwidth })
    }

    return filter { (it.height ?: 0) <= preferredHeight }
        .maxWithOrNull(compareBy<HlsVariant> { it.height ?: 0 }.thenBy { it.bandwidth })
        ?: minWithOrNull(compareBy<HlsVariant> { it.height ?: Int.MAX_VALUE }.thenBy { it.bandwidth })
}

private fun String.rewriteQuotedUri(
    baseUrl: String,
    headers: Map<String, String>,
    segmentDir: File,
    prefix: String,
): String {
    val uri = Regex("""URI="([^"]+)"""").find(this)?.groupValues?.getOrNull(1) ?: return this
    val absoluteUrl = resolvePlaylistUrl(baseUrl, uri)
    val extension = absoluteUrl.fileExtensionForDownload()
    val file = File(segmentDir, "$prefix.$extension")
    downloadUrlToFileForRewrite(absoluteUrl, headers, file)
    return replace("""URI="$uri"""", """URI="${segmentDir.name}/${file.name}"""")
}

private fun downloadUrlToFileForRewrite(url: String, headers: Map<String, String>, target: File) {
    val request = Request.Builder()
        .url(url)
        .headers(headers.toOkHttpHeaders())
        .build()
    OkHttpClient().newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IOException("Download HTTP ${response.code}")
        val body = response.body ?: throw IOException("Empty HLS resource")
        target.outputStream().use { output ->
            body.byteStream().use { input -> input.copyTo(output) }
        }
    }
}

private fun resolvePlaylistUrl(baseUrl: String, value: String): String {
    return baseUrl.toHttpUrlOrNull()?.resolve(value)?.toString() ?: value
}

private fun String.fileExtensionForDownload(): String {
    val path = substringBefore('?').substringBefore('#').lowercase()
    return when {
        path.endsWith(".m3u8") -> "m3u8"
        path.endsWith(".mpd") -> "mpd"
        path.endsWith(".m4s") -> "m4s"
        path.endsWith(".ts") -> "ts"
        path.endsWith(".mp4") -> "mp4"
        else -> "mp4"
    }
}

private fun String.mimeTypeFromFileName(): String? {
    val lower = lowercase()
    return when {
        lower.endsWith(".m3u8") -> "application/x-mpegURL"
        lower.endsWith(".mpd") -> "application/dash+xml"
        lower.endsWith(".mp4") -> "video/mp4"
        else -> null
    }
}

private fun Map<String, String>.toOkHttpHeaders(): okhttp3.Headers {
    return okhttp3.Headers.Builder().also { builder ->
        forEach { (name, value) -> builder.set(name, value) }
    }.build()
}

