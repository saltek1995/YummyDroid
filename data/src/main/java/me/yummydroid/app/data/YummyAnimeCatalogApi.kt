package me.yummydroid.app.data

internal class YummyAnimeCatalogApi(
    private val transport: YummyAnimeApiTransport,
) {
    suspend fun featuredAnime(
        limit: Int,
        offset: Int,
        filters: BrowseFilters,
        authToken: String?,
        ids: Set<Long>,
    ): List<Anime> {
        return loadAnime(
            filters.toApiParams() + listOf(
                "limit" to limit.toString(),
                "offset" to offset.coerceAtLeast(0).toString(),
            ) + ids.map { "ids" to it.toString() },
            authToken,
        )
    }

    suspend fun search(
        query: String,
        limit: Int,
        offset: Int,
        filters: BrowseFilters,
        authToken: String?,
        ids: Set<Long>,
    ): List<Anime> {
        return loadAnime(
            filters.toApiParams() + listOf(
                "q" to query,
                "limit" to limit.toString(),
                "offset" to offset.coerceAtLeast(0).toString(),
            ) + ids.map { "ids" to it.toString() },
            authToken,
        )
    }

    suspend fun getFilterCatalog(): FilterCatalog {
        return transport.get<CatalogDto>(path = "/anime/catalog")
            .toFilterCatalog(transport.locale)
    }

    suspend fun getAnime(animeId: Long, token: String?): AnimeDetails {
        return transport.get<AnimeDto>(path = "/anime/$animeId", authToken = token)
            .toDetails(transport.locale)
    }

    suspend fun getAnimeWithVideos(
        animeId: Long,
        token: String?,
    ): Pair<AnimeDetails, List<VideoVariant>> {
        return loadAnimeWithVideos(animeId, token).toDetailsWithVideos(transport.locale)
    }

    suspend fun getVideos(animeId: Long, token: String?): List<VideoVariant> {
        return loadAnimeWithVideos(animeId, token)
            .toDetailsWithVideos(transport.locale)
            .second
    }

    suspend fun getSchedule(): List<ScheduleAnime> {
        return transport.get<List<ScheduleAnimeDto>>(path = "/anime/schedule")
            .mapNotNull { it.toScheduleAnime() }
    }

    private suspend fun loadAnime(params: List<Pair<String, String>>, authToken: String?): List<Anime> {
        return transport.get<List<AnimeDto>>(
            path = "/anime",
            params = params,
            authToken = authToken,
        ).map { it.toAnime() }
    }

    private suspend fun loadAnimeWithVideos(animeId: Long, token: String?): AnimeDto {
        return transport.get(
            path = "/anime/$animeId",
            params = listOf("need_videos" to "true"),
            authToken = token,
        )
    }
}
