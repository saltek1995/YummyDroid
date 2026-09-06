package me.yummydroid.app.data

import java.io.IOException
import java.net.ServerSocket
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YummyAnimeRepositoryTest {
    @Test
    fun disconnectedStartupDoesNotRequestCatalogFiltersScheduleOrDetails() = runBlocking {
        var requests = 0
        val client = OkHttpClient.Builder().addInterceptor {
            requests += 1
            error("No HTTP requests are allowed without connectivity")
        }.build()
        val repository = YummyAnimeRepository(api = YummyAnimeApi(client), isNetworkAvailable = { false })
        assertTrue(repository.getFeatured(BrowseFilters()).offlineFallback)
        assertTrue(repository.search("anime", BrowseFilters()).offlineFallback)
        assertEquals(FilterCatalog.Empty, repository.getFilterCatalog())
        assertTrue(repository.getSchedule().isEmpty())
        assertFailsWith<IOException> { repository.getAnimeWithVideos(100) }
        assertEquals(0, requests)
    }

    @Test
    fun qualityDiscoveryRequestsOneEpisodePerChosenSourceAndNeverFetchesSubtitles() = runBlocking {
        val requestedUrls = java.util.Collections.synchronizedList(mutableListOf<String>())
        val manifest = "#EXTM3U\n#EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID=\"subs\",NAME=\"RU\",URI=\"subtitle.m3u8\"\n" +
            "#EXT-X-STREAM-INF:BANDWIDTH=2000000,RESOLUTION=1280x720,SUBTITLES=\"subs\"\n720p.m3u8\n"
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requestedUrls += chain.request().url.toString()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(manifest.toResponseBody()).build()
        }.build()
        val first = video(id = 1, episode = "1").copy(player = "CVH", url = "https://media.example.test/1/manifest")
        val next = first.copy(id = 2, episode = "2", url = "https://media.example.test/2/manifest")
        val unselected = first.copy(id = 3, dubbing = "Other", url = "https://media.example.test/3/manifest")
        val repository = YummyAnimeRepository(videoStreamResolver = VideoStreamResolver(client = client))
        val result = repository.resolveSampledDownloadQualities(setOf(first.downloadPlanVoiceKey), listOf(first, next, unselected))
        assertEquals(listOf(PreferredQuality.P720), result[first.downloadPlanVoiceKey])
        assertTrue(requestedUrls.isNotEmpty())
        assertEquals(listOf(next.url), requestedUrls.toList())
    }

    @Test
    fun forbiddenDownloadPausesTheProviderAcrossEpisodesAndRepositoryInstances() = runBlocking {
        var now = 1_000L
        val preferences = InMemoryPlaybackPreferences()
        val cooldowns = DownloadSourceCooldowns(preferences) { now }
        val repository = YummyAnimeRepository(downloadSourceCooldowns = cooldowns)
        val first = video(id = 1, episode = "1").copy(player = "Alloha")
        val next = video(id = 2, episode = "2").copy(player = "Alloha", dubbing = "Other voice")
        val alternative = first.copy(id = 3, player = "Kodik")
        var requests = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += 1
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(if (requests == 1) 403 else 200).message("test").body("payload".toResponseBody()).build()
        }.build()
        suspend fun download(video: VideoVariant) = repository.withDownloadSource(video) {
            client.withCancellableResponse(Request.Builder().url("https://example.test/media").build()) { it.body!!.string() }
        }
        val rejected = assertFailsWith<DownloadSourceCoolingDown> { download(first) }
        assertEquals(301_000L, rejected.retryAtMs)
        assertFailsWith<DownloadSourceCoolingDown> { download(next) }
        assertEquals(1, requests)
        assertEquals("payload", download(alternative))
        assertEquals(2, requests)

        now += 30_000L
        val restarted = DownloadSourceCooldowns(preferences) { now }
        assertEquals(rejected.retryAtMs, restarted.restrict(next).retryAtMs)
        assertFalse(restarted.isAvailable(first))
        assertTrue(restarted.isAvailable(alternative))
        now = rejected.retryAtMs
        assertTrue(restarted.isAvailable(next))
        assertEquals("payload", download(next))
        assertEquals(3, requests)
    }

    @Test
    fun downloadResolutionSkipsRestrictedSourcesAndWaitsOnlyWhenNoAlternativeWorks() = runBlocking {
        val cooldowns = DownloadSourceCooldowns(InMemoryPlaybackPreferences()) { 1_000L }
        val first = video(id = 1, episode = "1").copy(player = "Alloha", url = "https://cdn.example.test/1080p.mp4")
        val alternative = first.copy(id = 2, player = "Kodik")
        cooldowns.restrict(first)
        val repository = YummyAnimeRepository(downloadSourceCooldowns = cooldowns)

        val playbacks = repository.repositoryResolveDownloadPlaybacks(first, listOf(first, alternative), PreferredQuality.P1080)
        assertEquals(listOf(alternative.id), playbacks.map { it.video.id })
        assertFailsWith<DownloadSourceCoolingDown> {
            repository.repositoryResolveDownloadPlaybacks(first, listOf(first), PreferredQuality.P1080)
        }
        Unit
    }

    @Test
    fun successfulHttpReadDoesNotCancelTheCompletedCall() = runBlocking {
        lateinit var call: okhttp3.Call
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            call = chain.call()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body("complete".toResponseBody()).build()
        }.build()
        val body = client.withCancellableResponse(Request.Builder().url("https://example.test/").build()) {
            it.body!!.string()
        }
        assertEquals("complete", body)
        assertFalse(call.isCanceled())
    }

    @Test
    fun lateCatalogResponsesCannotPopulateAnotherAccountLanguageOrClearedCache() = runBlocking {
        for (change in listOf("logout", "login", "account", "language", "language-round-trip", "clear")) {
            val directory = Files.createTempDirectory("catalog-context").toFile()
            val cache = AnimeContentCacheStorage(directory)
            val storage = AuthStorage(InMemoryPlaybackPreferences())
            if (change != "login") storage.saveSession("old", profile(42))
            lateinit var repository: YummyAnimeRepository
            var requests = 0
            val api = accountApi { request ->
                requests += 1
                if (requests == 1) {
                    assertEquals(if (change == "login") null else "Bearer old", request.header("Authorization"))
                    assertEquals("ru", request.header("Lang"))
                    when (change) {
                        "logout" -> storage.clear()
                        "login", "account" -> storage.saveSession("new", profile(84))
                        "language" -> repository.updateContentLanguage(ContentLanguage.English)
                        "language-round-trip" -> {
                            repository.updateContentLanguage(ContentLanguage.English)
                            repository.updateContentLanguage(ContentLanguage.Russian)
                        }
                        "clear" -> AnimeContentCacheStorage(directory).clear()
                    }
                }
                200 to """{"response":[]}"""
            }
            repository = YummyAnimeRepository(api = api, authStorage = storage, contentCache = cache)
            try {
                assertEquals(emptyList(), repository.getFeatured(BrowseFilters()).value)
                for (language in ContentLanguage.entries) {
                    for (userId in listOf(null, 42L, 84L)) {
                        assertNull(cache.readFeatured(language, userId, BrowseFilters(), 0, REPOSITORY_PAGE_SIZE), change)
                    }
                }
                assertEquals(emptyList(), repository.getFeatured(BrowseFilters()).value)
                assertEquals(2, requests)
                assertEquals(emptyList(), cache.readFeatured(repository.contentLanguage, storage.readProfile()?.id,
                    BrowseFilters(), 0, REPOSITORY_PAGE_SIZE))
            } finally {
                cache.clear()
                directory.deleteRecursively()
            }
        }
    }

    @Test
    fun scheduleResponseRetainsItsOriginalLanguageBoundary() = runBlocking {
        val directory = Files.createTempDirectory("schedule-language").toFile()
        val cache = AnimeContentCacheStorage(directory)
        lateinit var repository: YummyAnimeRepository
        repository = YummyAnimeRepository(contentCache = cache, api = accountApi {
            repository.updateContentLanguage(ContentLanguage.Ukrainian)
            200 to """{"response":[]}"""
        })
        try {
            repository.getSchedule()
            assertNull(cache.readSchedule(ContentLanguage.Russian))
            assertNull(cache.readSchedule(ContentLanguage.Ukrainian))
            repository.getSchedule()
            assertEquals(emptyList(), cache.readSchedule(ContentLanguage.Ukrainian))
        } finally {
            cache.clear()
            directory.deleteRecursively()
        }
    }

    @Test
    fun cancellationClosesAStalledHttpCallAndDrainsTheBodyReader() = runBlocking {
        for (sendHeaders in listOf(false, true)) {
            ServerSocket(0).use { server ->
                val ready = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val reading = CompletableDeferred<Unit>()
                val readerExited = AtomicBoolean(false)
                val serverJob = launch(Dispatchers.IO) {
                    server.accept().use { socket ->
                        val input = socket.getInputStream().bufferedReader()
                        while (!input.readLine().isNullOrEmpty()) Unit
                        if (sendHeaders) {
                            socket.getOutputStream().apply {
                                write("HTTP/1.1 200 OK\r\nContent-Length: 100\r\n\r\na".toByteArray())
                                flush()
                            }
                        }
                        ready.complete(Unit)
                        release.await()
                    }
                }
                val client = OkHttpClient.Builder().readTimeout(10, TimeUnit.SECONDS).build()
                val request = launch {
                    client.withCancellableResponse(Request.Builder().url("http://127.0.0.1:${server.localPort}/").build()) {
                        reading.complete(Unit)
                        try {
                            it.body!!.string()
                        } finally {
                            readerExited.set(true)
                        }
                    }
                    error("Cancelled response was published")
                }
                try {
                    withTimeout(3_000) {
                        ready.await()
                        if (sendHeaders) reading.await()
                        request.cancelAndJoin()
                    }
                    assertTrue(request.isCancelled)
                    if (sendHeaders) assertTrue(readerExited.get())
                } finally {
                    release.complete(Unit)
                    request.cancelAndJoin()
                    serverJob.join()
                    client.connectionPool.evictAll()
                }
            }
        }
    }

    @Test
    fun notificationResponseCannotPublishAfterAccountSwitchOrLogout() = runBlocking {
        for (replacement in listOf("new", null)) {
            val storage = AuthStorage(InMemoryPlaybackPreferences())
            storage.saveSession("old", profile(42))
            val api = accountApi {
                if (replacement == null) storage.clear() else storage.saveSession(replacement, profile(84))
                200 to """{"response":[]}"""
            }
            YummyAnimeRepository(api = api, authStorage = storage).synchronizeProfileNotifications(
                onNotifications = { _, _ -> error("Stale notifications were published") },
                onUnauthorized = { error("Successful response cleared authentication") },
            )
            assertEquals(replacement, storage.readToken())
        }
    }

    @Test
    fun rejectedNotificationRequestClearsOnlyItsOwnSession() = runBlocking {
        for (switchAccount in listOf(false, true)) {
            val storage = AuthStorage(InMemoryPlaybackPreferences())
            storage.saveSession("old", profile(42))
            var clearedNotifications = false
            val api = accountApi {
                if (switchAccount) storage.saveSession("new", profile(84))
                401 to """{"error":"expired"}"""
            }
            YummyAnimeRepository(api = api, authStorage = storage).synchronizeProfileNotifications(
                onNotifications = { _, _ -> error("Unauthorized response was published") },
                onUnauthorized = { clearedNotifications = true },
            )
            assertEquals(!switchAccount, clearedNotifications)
            assertEquals(if (switchAccount) "new" else null, storage.readToken())
        }
    }

    @Test
    fun successfulNotificationSyncPublishesForCurrentProfileAndNetworkFailureRetainsIt() = runBlocking {
        val storage = AuthStorage(InMemoryPlaybackPreferences())
        storage.saveSession("current", profile(42))
        var result: UserProfile? = null
        val api = accountApi { 200 to """{"response":[]}""" }
        YummyAnimeRepository(api = api, authStorage = storage).synchronizeProfileNotifications(
            onNotifications = { current, notifications ->
                result = current
                assertEquals(emptyList(), notifications)
            },
            onUnauthorized = { error("Current session was cleared") },
        )
        assertEquals(profile(42), result)
        val offline = accountApi { throw IOException("offline") }
        assertFailsWith<IOException> {
            YummyAnimeRepository(api = offline, authStorage = storage).synchronizeProfileNotifications(
                onNotifications = { _, _ -> error("Failed response was published") },
                onUnauthorized = { error("Network failure cleared session") },
            )
        }
        assertEquals("current", storage.readToken())
    }

    @Test
    fun failedLoginProfileRequestKeepsExistingSessionIntact() = runBlocking {
        val storage = AuthStorage(InMemoryPlaybackPreferences())
        storage.saveSession("existing", profile(42))
        val api = accountApi { request ->
            if (request.url.encodedPath.endsWith("/login")) {
                200 to """{"response":{"success":true,"token":"new"}}"""
            } else {
                assertEquals("existing", storage.readToken())
                503 to """{"error":"offline"}"""
            }
        }

        assertFailsWith<ApiHttpException> {
            YummyAnimeRepository(api = api, authStorage = storage).login("test", "test")
        }

        assertEquals("existing", storage.readToken())
        assertEquals(profile(42), storage.readProfile())
    }

    @Test
    fun restoredProfileResponseAfterLogoutCannotRestoreCredentials() = runBlocking {
        val storage = AuthStorage(InMemoryPlaybackPreferences())
        storage.saveSession("existing", profile(42))
        val api = accountApi { request ->
            if (request.url.encodedPath.endsWith("/token")) {
                200 to """{"response":{"token":"rotated"}}"""
            } else {
                storage.clear()
                200 to """{"response":{"id":42,"nickname":"stale"}}"""
            }
        }

        assertNull(YummyAnimeRepository(api = api, authStorage = storage).restoreProfile())
        assertNull(storage.readToken())
        assertNull(storage.readProfile())
    }

    @Test
    fun oldTokenRejectionDuringNewLoginKeepsNewSession() = runBlocking {
        val storage = AuthStorage(InMemoryPlaybackPreferences())
        storage.saveSession("old", profile(42))
        val api = accountApi {
            storage.saveSession("new", profile(84))
            401 to """{"error":"old token expired"}"""
        }

        assertEquals(profile(84), YummyAnimeRepository(api = api, authStorage = storage).restoreProfile())
        assertEquals("new", storage.readToken())
    }

    @Test
    fun rotatedTokenSurvivesTransientProfileFailureWithCachedProfile() = runBlocking {
        val storage = AuthStorage(InMemoryPlaybackPreferences())
        storage.saveSession("old", profile(42))
        val api = accountApi { request ->
            if (request.url.encodedPath.endsWith("/token")) {
                200 to """{"response":{"token":"rotated"}}"""
            } else {
                503 to """{"error":"offline"}"""
            }
        }

        assertEquals(profile(42), YummyAnimeRepository(api = api, authStorage = storage).restoreProfile())
        assertEquals("rotated", storage.readToken())
    }

    private fun accountApi(respond: (Request) -> Pair<Int, String>): YummyAnimeApi {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val (code, body) = respond(request)
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("test")
                .body(body.toResponseBody())
                .build()
        }.build()
        return YummyAnimeApi(client)
    }

    @Test
    fun sessionStoragePublishesTokenAndProfileTogether() {
        val storage = AuthStorage(InMemoryPlaybackPreferences())
        val profile = profile(42)

        storage.saveSession("token", profile)

        assertEquals("token", storage.readToken())
        assertEquals(profile, storage.readProfile())
    }

    @Test
    fun backgroundProfileCannotRecreateLoggedOutSession() {
        val preferences = InMemoryPlaybackPreferences()
        val account = AuthStorage(preferences)
        val background = AuthStorage(preferences)
        account.saveSession("old", profile(42))
        val oldToken = background.readToken()!!
        account.clear()

        assertFalse(background.refreshToken(oldToken, "refreshed"))
        assertFalse(background.refreshProfile(oldToken, profile(42)))
        assertFalse(background.updateUnreadNotifications(42, 8))
        assertNull(account.readToken())
        assertNull(account.readProfile())
    }

    @Test
    fun oldAuthenticationFailureCannotClearNewAccount() {
        val storage = AuthStorage(InMemoryPlaybackPreferences())
        storage.saveSession("old", profile(42))
        storage.saveSession("new", profile(84))

        assertFalse(storage.clearIfToken("old"))
        assertFalse(storage.clearIfToken(null))
        assertFalse(storage.refreshProfile("old", profile(42)))
        assertFalse(storage.updateUnreadNotifications(42, 100))
        assertEquals("new", storage.readToken())
        assertEquals(profile(84), storage.readProfile())
    }

    @Test
    fun notificationCounterUpdatePreservesCurrentProfileFields() {
        val storage = AuthStorage(InMemoryPlaybackPreferences())
        val current = profile(42).copy(nickname = "new nickname", unreadMessages = 6)
        storage.saveSession("token", current)

        assertTrue(storage.updateUnreadNotifications(42, 5))
        assertEquals(current.copy(unreadNotifications = 5), storage.readProfile())
        assertTrue(storage.updateUnreadNotifications(42, -1))
        assertEquals(0, storage.readProfile()?.unreadNotifications)
    }

    @Test
    fun tokenRotationKeepsCachedProfileUntilRefreshCompletes() {
        val storage = AuthStorage(InMemoryPlaybackPreferences())
        val cached = profile(42)
        storage.saveSession("old", cached)

        assertTrue(storage.refreshToken("old", "rotated"))
        assertEquals(cached, storage.readProfile())
        assertFalse(storage.refreshProfile("old", cached.copy(nickname = "stale")))
        assertTrue(storage.refreshProfile("rotated", cached.copy(nickname = "fresh")))
        assertEquals("fresh", storage.readProfile()?.nickname)
        assertTrue(storage.clearIfToken("rotated"))
        assertNull(storage.readProfile())
    }

    private fun profile(id: Long) = UserProfile(
        id = id,
        nickname = "profile $id",
        avatarUrl = "",
        about = "",
        roles = emptyList(),
    )

    @Test
    fun sampleSelectionUsesRepresentativeEpisodeInsteadOfFirstEpisode() {
        val firstEpisode = video(id = 1, episode = "1")
        val latestEpisode = video(id = 12, episode = "12")

        val selected = listOf(firstEpisode, latestEpisode).selectDownloadQualitySampleCandidate()

        assertEquals(latestEpisode.id, selected?.id)
    }

    @Test
    fun sampleSelectionPrefersCandidateWithKnownQualities() {
        val latestWithoutQualities = video(id = 12, episode = "12")
        val knownQualityEpisode = video(
            id = 5,
            episode = "5",
            sourceQualities = listOf(SourceQuality(height = 1080)),
        )

        val selected = listOf(latestWithoutQualities, knownQualityEpisode).selectDownloadQualitySampleCandidate()

        assertEquals(knownQualityEpisode.id, selected?.id)
    }

    @Test
    fun repositoryWithoutContextOrSessionUsesSafeLocalResults() = runBlocking {
        val repository = YummyAnimeRepository()
        val requested = video(id = 1, episode = "1")

        assertEquals(emptyList(), repository.offlineAnime())
        assertFailsWith<IllegalStateException> { repository.getVideoSubscriptions(userId = 42) }
        assertEquals(
            emptyList(),
            repository.resolveAvailableDownloadQualities(
                requested = requested,
                videos = emptyList(),
                allEpisodes = false,
            ),
        )
        assertEquals(
            emptyMap(),
            repository.resolveSampledDownloadQualities(
                voiceKeys = emptySet(),
                videos = listOf(requested),
            ),
        )
        assertNull(repository.getAnimeMark(animeId = 100))
        assertFalse(
            repository.saveWatchProgress(
                PlaybackProgress(
                    animeId = 100,
                    videoId = 1,
                    groupKey = "voice",
                    episode = "1",
                    positionMs = 0,
                    durationMs = 1_000,
                    updatedAtMs = 1,
                ),
            ),
        )
        assertFalse(repository.getFeatured(BrowseFilters(offlineOnly = true)).offlineFallback)
    }

    @Test
    fun downloadFailureMessageIncludesAtMostThreeSourceFailures() {
        assertEquals("Could not download episode", downloadFailureMessage(emptyList()))
        assertEquals(
            "Could not download episode: A: first; B: second; C: third",
            downloadFailureMessage(
                listOf(
                    "A: first",
                    "B: second",
                    "C: third",
                    "D: fourth",
                ),
            ),
        )
    }

    @Test
    fun offlineStreamResolutionAndEmptyPlaybackSelectionDoNotUseNetwork() = runBlocking {
        val repository = YummyAnimeRepository()
        val offlineVideo = video(
            id = 7,
            episode = "7",
            offlineFiles = listOf(
                OfflineVideoFile(
                    playbackUrl = "file:///offline/episode-7.mp4",
                    mimeType = "video/mp4",
                    bytes = 1_024,
                    qualityTitle = "1080p",
                ),
            ),
        )

        val stream = repository.resolveVideoStream(offlineVideo)

        assertEquals("file:///offline/episode-7.mp4", stream.url)
        assertEquals("video/mp4", stream.mimeType)
        assertEquals(emptyMap(), stream.headers)
        assertNull(stream.maxVideoHeight)
        assertEquals(
            "No sources are available for the episode",
            assertFailsWith<IOException> {
                repository.resolveBestPlaybackSource(
                    candidates = emptyList(),
                    preferredQuality = PreferredQuality.Auto,
                )
            }.message,
        )
    }

    private fun video(
        id: Long,
        episode: String,
        sourceQualities: List<SourceQuality> = emptyList(),
        offlineFiles: List<OfflineVideoFile> = emptyList(),
    ): VideoVariant {
        return VideoVariant(
            id = id,
            animeId = 100,
            player = "Player",
            playerId = 1,
            dubbing = "Voice",
            episode = episode,
            url = "https://example.test/$id",
            index = id.toInt(),
            durationSeconds = 1_400,
            views = 0,
            sourceQualities = sourceQualities,
            localFiles = offlineFiles,
        )
    }
}
