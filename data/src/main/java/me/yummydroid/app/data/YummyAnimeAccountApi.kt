package me.yummydroid.app.data

import java.io.IOException
import kotlinx.serialization.json.JsonElement

internal class YummyAnimeAccountApi(
    private val transport: YummyAnimeApiTransport,
) {
    suspend fun getUserListAnimeIds(userId: Long, listId: Int, token: String): Set<Long> {
        return transport.get<List<UserListAnimeDto>>(
            path = "/users/$userId/lists/$listId",
            authToken = token,
        ).positiveAnimeIds()
    }

    suspend fun getUserFavoriteAnimeIds(userId: Long, token: String): Set<Long> {
        return transport.get<List<UserListAnimeDto>>(
            path = "/users/$userId/lists",
            authToken = token,
        ).filter { it.user?.list?.isFavorite == true }
            .positiveAnimeIds()
    }

    suspend fun login(login: String, password: String, captchaResponse: String?): String {
        val response = transport.post<LoginResponseDto, LoginRequestDto>(
            path = "/profile/login",
            body = LoginRequestDto(
                login = login,
                password = password,
                needJson = true,
                recaptchaResponse = captchaResponse,
            ),
        )
        if (!response.success || response.token.isBlank()) throw IOException("Could not sign in")
        return response.token
    }

    suspend fun refreshToken(token: String): String {
        return transport.get<TokenResponseDto>(
            path = "/profile/token",
            authToken = token,
        ).token.takeIf { it.isNotBlank() }
            ?: throw IOException("Could not refresh token")
    }

    suspend fun getProfile(token: String): UserProfile {
        return transport.get<ProfileDto>(path = "/profile", authToken = token).toUserProfile()
    }

    suspend fun getAnimeMark(animeId: Long, token: String): UserAnimeMark {
        return transport.get<UserAnimeMarkDto>(
            path = "/anime/$animeId/list",
            authToken = token,
        ).toUserAnimeMark()
    }

    suspend fun setAnimeListMark(animeId: Long, mark: UserAnimeListMark, token: String): UserAnimeMark {
        transport.put<JsonElement, SetAnimeListRequestDto>(
            path = "/anime/$animeId/list",
            body = SetAnimeListRequestDto(
                list = mark.id,
                date = System.currentTimeMillis() / 1000L,
            ),
            authToken = token,
        )
        return getAnimeMark(animeId, token)
    }

    suspend fun removeAnimeListMark(animeId: Long, token: String): UserAnimeMark {
        transport.delete<JsonElement>(path = "/anime/$animeId/list", authToken = token)
        return getAnimeMark(animeId, token)
    }

    suspend fun setFavorite(animeId: Long, isFavorite: Boolean, token: String): UserAnimeMark {
        if (isFavorite) {
            transport.put<JsonElement, FavoriteRequestDto>(
                path = "/anime/$animeId/list/fav",
                body = FavoriteRequestDto(date = System.currentTimeMillis() / 1000L),
                authToken = token,
            )
        } else {
            transport.delete<JsonElement>(path = "/anime/$animeId/list/fav", authToken = token)
        }
        return getAnimeMark(animeId, token)
    }

    suspend fun getWatchHistory(token: String, limit: Int, offset: Int): List<PlaybackProgress> {
        return transport.get<List<WatchHistoryDto>>(
            path = "/video/watch-history",
            params = listOf(
                "limit" to limit.coerceIn(0, 100).toString(),
                "offset" to offset.coerceIn(0, 100_000).toString(),
            ),
            authToken = token,
        ).mapNotNull { it.toPlaybackProgress() }
    }

    suspend fun saveWatchProgress(progress: PlaybackProgress, token: String): Boolean {
        if (progress.videoId <= 0L) return false
        val positionSeconds = progress.positionMs.toWholeSeconds()
        return transport.put(
            path = "/video/${progress.videoId}",
            body = SetVideoWatchRequestDto(
                time = positionSeconds,
                duration = progress.durationMs.toWholeSeconds(),
                date = (progress.updatedAtMs / 1000L).coerceAtLeast(0L),
                times = listOf(positionSeconds).filter { it > 0 },
            ),
            authToken = token,
        )
    }

    suspend fun deleteWatchProgress(videoIds: List<Long>, token: String): Boolean {
        val normalizedIds = videoIds.filter { it > 0L }.distinct()
        if (normalizedIds.isEmpty()) return true
        return transport.deleteSuccess(
            path = "/video",
            body = DeleteVideoWatchRequestDto(videoIds = normalizedIds),
            authToken = token,
        )
    }

    private fun List<UserListAnimeDto>.positiveAnimeIds(): Set<Long> {
        return mapNotNull { it.animeId.takeIf { animeId -> animeId > 0 } }.toSet()
    }
}
