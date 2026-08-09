package me.yummydroid.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun YummyAnimeRepository.repositoryGetAnimeWithVideos(
    animeId: Long,
): Pair<AnimeDetails, List<VideoVariant>> = withContext(Dispatchers.IO) {
    val offline = offlineStorage?.read(animeId)
    contentCache?.readAnimeWithVideos(
        language = contentLanguage,
        userId = cacheUserId(),
        animeId = animeId,
    )?.let { cached ->
        offlineFallbackActive = false
        return@withContext cached.details to applyCachedSourceQualities(
            cached.videos.withOfflineDownloads(offline?.videos.orEmpty(), cached.details),
        )
    }

    try {
        offlineFallbackActive = false
        val (details, videos) = api.getAnimeWithVideos(animeId, authStorage?.readToken())
        val mergedVideos = applyCachedSourceQualities(
            videos.withOfflineDownloads(offline?.videos.orEmpty(), details),
        )
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
