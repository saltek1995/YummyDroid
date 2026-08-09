package me.yummydroid.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
