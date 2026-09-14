package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

class OfflineAnimeEntryTest {
    @Test
    fun legacyOfflineIndexAndEveryRepositoryFallbackUseNeutralMetadataWithAccountOverlay() = kotlinx.coroutines.runBlocking {
        val directory = java.nio.file.Files.createTempDirectory("offline-account").toFile()
        val auth = AuthStorage(InMemoryPlaybackPreferences())
        val subscriptions = AccountVideoSubscriptionStorage(InMemoryPlaybackPreferences())
        val payload = java.io.File(directory, "episode.mp4")
        java.io.RandomAccessFile(payload, "rw").use { it.setLength(MIN_COMPLETED_VIDEO_BYTES + 1024) }
        val local = video(11, "1", "CVH", listOf(offlineFile(payload.toURI().toString(), payload.length()))).copy(subscribed = true)
        OfflineDownloadRegistry(directory).upsert(local, local.localFiles.single())
        val entry = offlineEntry(listOf(local)).let { it.copy(anime = it.anime.copy(userRating = 9), details = it.details.copy(userRating = 9)) }
        java.io.File(directory, OFFLINE_ANIME_INDEX_FILE_NAME).writeJson(mapOf(1L to entry))
        val offline = OfflineAnimeStorage(directory)
        val client = okhttp3.OkHttpClient.Builder().addInterceptor { throw java.io.IOException("offline") }.build()
        val repository = YummyAnimeRepository(api = YummyAnimeApi(client), authStorage = auth, offlineStorage = offline,
            subscriptionState = subscriptions, isNetworkAvailable = { false })
        subscriptions.rememberVideos(42, listOf(local))
        try {
            assertEquals(null, offline.read(1)!!.details.userRating)
            assertEquals(false, offline.read(1)!!.videos.single().subscribed)
            for (userId in listOf(42L, 84L, null, 42L)) {
                if (userId == null) auth.clear() else auth.saveSession("token-$userId", UserProfile(userId, "user", ""))
                val expected = userId == 42L
                val direct = repository.getOfflineAnimeWithVideos(1).value
                assertEquals(null, direct.first.userRating)
                assertEquals(expected, direct.second.single().subscribed)
                assertEquals(expected, repository.getAnimeWithVideos(1).value.second.single().subscribed)
                assertEquals(expected, repository.getVideos(1).single().subscribed)
                assertEquals(expected, repository.offlineAnime().single().videos.single().subscribed)
            }
            offline.saveAnime(entry.details, entry.videos)
            val saved = java.io.File(directory, OFFLINE_ANIME_INDEX_FILE_NAME).readJsonOrNull<Map<Long, OfflineAnimeEntry>>()!!.getValue(1L)
            assertEquals(null, saved.details.userRating)
            assertEquals(false, saved.videos.single().subscribed)
            assertEquals(true, payload.exists())
            val browse = repository.getFeatured(BrowseFilters(userMarks = setOf("4")))
            assertEquals(listOf(1L), browse.value.map { it.id })
            assertEquals(setOf(OfflineFilterField.UserMarks), browse.unsupportedOfflineFilters)
        } finally { directory.deleteRecursively(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
    }

    @Test
    fun offlineFiltersUseCanonicalIdentityAndMetadataEpisodeCount() {
        val entry = offlineEntry(listOf(video(11, "1", "CVH", listOf(offlineFile("file:///one.mp4", 320_000)))))
            .let { it.copy(details = it.details.copy(
                title = "Їжак і ґанок", episodeCount = 12, status = "Вышел", type = "ТВ сериал", minAge = "PG-13",
                genreTags = listOf(FilterOption("Приключения", "adventure")),
                studios = listOf(FilterOption("Studio", "10")), creators = listOf(FilterOption("Creator", "1")),
            )) }
        val filters = BrowseFilters(genres = setOf("https://site.test/genres/adventure/?lang=ru"),
            statuses = setOf("released"), types = setOf("tv"), episodeFrom = 12, episodeTo = 12,
            ageRatings = setOf("2"), studios = setOf("10"), creators = setOf("1"))
        assertEquals(listOf(1L), listOf(entry).filteredOfflineAnime("їжак і ґанок", filters).map { it.id })
        assertEquals(emptyList(), listOf(entry).filteredOfflineAnime(filters = filters.copy(studios = setOf("1"))))
        assertEquals(emptyList(), listOf(entry).filteredOfflineAnime(filters = filters.copy(creators = setOf("10"))))
        assertEquals(emptyList(), listOf(entry).filteredOfflineAnime(filters = filters.copy(genres = setOf(""))))
        assertEquals(emptyList(), listOf(entry).filteredOfflineAnime("єжак", filters))
    }

    @Test
    fun unsupportedFiltersAreExplicitAndOrderingIsStable() {
        val one = offlineEntry(emptyList()).let { it.copy(details = it.details.copy(ratingDetails = RatingDetails(counters = 40))) }
        val two = one.copy(anime = one.anime.copy(id = 2), details = one.details.copy(id = 2, ratingDetails = RatingDetails(counters = 3)))
        val filters = BrowseFilters(userMarks = setOf("4"), excludedUserMarks = setOf("0"), seasons = setOf("winter"), translates = setOf("dubbing"), sort = AnimeSort.Random)
        val policy = filters.offlineFilterPolicy(listOf(one, two))
        assertEquals(setOf(OfflineFilterField.UserMarks, OfflineFilterField.Seasons, OfflineFilterField.Translations, OfflineFilterField.Sort), policy.unsupportedFields)
        assertEquals(setOf("4"), filters.userMarks)
        assertEquals(listOf(2L, 1L), listOf(one, two).filteredOfflineAnime(filters = filters).map { it.id })
        assertEquals(listOf(1L, 2L), listOf(one, two).filteredOfflineAnime(filters = BrowseFilters(sort = AnimeSort.RatingCounters)).map { it.id })
    }

    @Test
    fun offlineSnapshotsAreNeutralAndSubscriptionOverlayBelongsToAccount() {
        val preferences = InMemoryPlaybackPreferences()
        val store = AccountVideoSubscriptionStorage(preferences)
        val video = video(11, "1", "CVH", emptyList()).copy(subscribed = true)
        val entry = offlineEntry(listOf(video)).let { it.copy(anime = it.anime.copy(userRating = 9), details = it.details.copy(userRating = 9)) }
        val neutral = entry.withoutAccountPersonalization()
        assertEquals(null, neutral.anime.userRating)
        assertEquals(null, neutral.details.userRating)
        assertEquals(false, neutral.videos.single().subscribed)
        store.rememberVideos(42, listOf(video, video.copy(id = 12, episode = "2")))
        val restored = AccountVideoSubscriptionStorage(preferences)
        assertEquals(true, restored.overlay(42, neutral.videos).single().subscribed)
        assertEquals(false, restored.overlay(84, entry.videos).single().subscribed)
        assertEquals(false, restored.overlay(null, entry.videos).single().subscribed)
        restored.setSubscribed(42, 12, false)
        assertEquals(false, store.overlay(42, entry.videos).single().subscribed)
        store.rememberSubscriptions(42, listOf(VideoSubscription(1, "", "", "CVH", "AniLibria", videoId = 12)))
        assertEquals(true, restored.overlay(42, neutral.videos).single().subscribed)
        restored.rememberSubscriptions(42, emptyList())
        assertEquals(false, store.overlay(42, neutral.videos).single().subscribed)
    }

    @Test
    fun countsDownloadedEpisodesUniquelyAndKeepsFileVariantsSeparate() {
        val entry = offlineEntry(
            videos = listOf(
                video(
                    id = 11,
                    episode = "1",
                    player = "CVH",
                    localFiles = listOf(offlineFile("file:///episode-1-cvh-1080.mp4", bytes = 320_000)),
                ),
                video(
                    id = 12,
                    episode = "1",
                    player = "Kodik",
                    localFiles = listOf(offlineFile("file:///episode-1-kodik-720.mp4", bytes = 310_000)),
                ),
                video(
                    id = 21,
                    episode = "2",
                    player = "CVH",
                    localFiles = listOf(offlineFile("file:///episode-2-cvh-partial.mp4", bytes = 32_000)),
                ),
            ),
        )

        assertEquals(2, entry.downloadedVariants.size)
        assertEquals(1, entry.downloadedVideos.size)
        assertEquals(630_000, entry.totalBytes)
    }

    private fun offlineEntry(videos: List<VideoVariant>): OfflineAnimeEntry {
        return OfflineAnimeEntry(
            anime = animeSummary(),
            details = animeDetails(),
            videos = videos,
            updatedAtMs = 0L,
        )
    }

    private fun animeSummary(): Anime {
        return Anime(
            id = 1,
            title = "Test",
            description = "",
            posterUrl = "",
            animeUrl = "",
            year = null,
            rating = null,
            views = 0,
            status = "",
            type = "",
            genres = emptyList(),
            blockedIn = emptyList(),
        )
    }

    private fun animeDetails(): AnimeDetails {
        return AnimeDetails(
            id = 1,
            title = "Test",
            otherTitles = emptyList(),
            description = "",
            posterUrl = "",
            backdropUrl = null,
            year = null,
            rating = null,
            views = 0,
            status = "",
            type = "",
            minAge = "",
            genreTags = emptyList(),
            genres = emptyList(),
            episodeSummary = "",
            episodeAired = 0,
            episodeCount = 0,
            nextEpisodeText = "",
            durationSeconds = 0,
            ratingDetails = RatingDetails(),
            studios = emptyList(),
            creators = emptyList(),
            original = "",
            commentsCount = 0,
            listsCount = 0,
            translations = emptyList(),
            relatedAnime = emptyList(),
            screenshots = emptyList(),
            blockedIn = emptyList(),
        )
    }

    private fun video(
        id: Long,
        episode: String,
        player: String,
        localFiles: List<OfflineVideoFile>,
    ): VideoVariant {
        return VideoVariant(
            id = id,
            animeId = 1,
            player = player,
            dubbing = "AniLibria",
            episode = episode,
            url = "https://example.test/$id",
            index = episode.toIntOrNull() ?: id.toInt(),
            durationSeconds = null,
            views = 0,
            localFiles = localFiles,
        )
    }

    private fun offlineFile(url: String, bytes: Long): OfflineVideoFile {
        return OfflineVideoFile(
            playbackUrl = url,
            mimeType = "video/mp4",
            bytes = bytes,
            qualityTitle = "1080p",
            voiceTitle = "AniLibria",
            player = "CVH",
        )
    }
}
