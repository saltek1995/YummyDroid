package me.yummyani.app.data

import android.content.Context
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
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
) {
    suspend fun getFeatured(filters: BrowseFilters, offset: Int = 0, limit: Int = PAGE_SIZE): List<Anime> {
        val token = authStorage?.readToken()
        val userListIds = resolveUserMarkAnimeIds(filters, token)
        if (userListIds != null && userListIds.isEmpty()) return emptyList()

        return api.featuredAnime(
            limit = limit,
            offset = offset,
            filters = filters,
            authToken = token,
            ids = userListIds.orEmpty(),
        )
    }

    suspend fun search(query: String, filters: BrowseFilters, offset: Int = 0, limit: Int = PAGE_SIZE): List<Anime> {
        val token = authStorage?.readToken()
        val userListIds = resolveUserMarkAnimeIds(filters, token)
        if (userListIds != null && userListIds.isEmpty()) return emptyList()

        return api.search(
            query = query,
            limit = limit,
            offset = offset,
            filters = filters,
            authToken = token,
            ids = userListIds.orEmpty(),
        )
    }

    suspend fun getFilterCatalog(): FilterCatalog {
        return api.getFilterCatalog()
    }

    suspend fun getAnimeWithVideos(animeId: Long): Pair<AnimeDetails, List<VideoVariant>> {
        return api.getAnimeWithVideos(animeId, authStorage?.readToken())
    }

    suspend fun getAnime(animeId: Long): AnimeDetails {
        return api.getAnime(animeId, authStorage?.readToken())
    }

    suspend fun getVideos(animeId: Long): List<VideoVariant> {
        return api.getVideos(animeId)
    }

    suspend fun resolveVideoStream(video: VideoVariant): ResolvedVideoStream {
        return videoStreamResolver.resolve(video)
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
                    playback.stream.maxVideoHeight ?: 0
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

