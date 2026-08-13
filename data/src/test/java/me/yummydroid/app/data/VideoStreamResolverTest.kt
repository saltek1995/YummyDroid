package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VideoStreamResolverTest {
    private val metadataInspector = PlayerMetadataInspector(
        subtitleMetadataParser = SubtitleMetadataParser(
            fallbackSiteBaseUrl = { TEST_SITE_BASE_URL },
            json = VIDEO_RESOLVER_JSON,
        ),
        playbackRequestHeaders = PlaybackRequestHeaders(
            fallbackSiteBaseUrl = { TEST_SITE_BASE_URL },
            cookieProvider = PlaybackCookieProvider { null },
        ),
        fallbackSiteBaseUrl = { TEST_SITE_BASE_URL },
    )

    @Test
    fun onlyAllohaPlayerEndpointsAreInspectedAsMetadata() {
        assertTrue(metadataInspector.isInspectableUrl("https://alloha.yani.tv/movies/123"))
        assertTrue(metadataInspector.isInspectableUrl("https://cdn.allohastream.test/player/123"))
        assertFalse(metadataInspector.isInspectableUrl("https://alloha.yani.tv/assets/player.js"))
        assertFalse(metadataInspector.isInspectableUrl("https://example.test/movies/123"))
        assertFalse(metadataInspector.isInspectableUrl("not a URL"))
    }

    @Test
    fun extensionlessHlsManifestResponseIsCapturedAsPlayback() {
        val url = "https://alloha.yani.tv/playlist/6a8d259b6bd5b6b609329cf9e0f0c3"
        val body = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-STREAM-INF:BANDWIDTH=2400000,RESOLUTION=1920x1080
            chunklist_1080.m3u8
        """.trimIndent()

        val capture = inspectMetadataBody(
            url = url,
            body = body,
            sourceUrl = "https://alloha.yani.tv/?translation=210&season=1&episode=14",
        )
        val playback = capture.playback

        assertNotNull(playback)
        assertEquals(url, playback.url)
        assertEquals("application/x-mpegURL", playback.mimeType)
        assertEquals(1080, playback.maxVideoHeight)
    }

    @Test
    fun allohaRuntimeQualityMapHonorsRequestedQuality() {
        val capture = inspectMetadataBody(
            url = "https://alloha.yani.tv/?translation=210&season=1&episode=14",
            body = """
                {
                  "hlsSource": [
                    {
                      "quality": {
                        "360": "39https://cdn.example.test/360/master.m3u8 or https://mirror.example.test/360/master.m3u8",
                        "720": "https://cdn.example.test/720/master.m3u8",
                        "1080": "https://cdn.example.test/1080/master.m3u8"
                      }
                    }
                  ]
                }
            """.trimIndent(),
            sourceUrl = "https://alloha.yani.tv/?translation=210&season=1&episode=14",
            preferredQuality = PreferredQuality.P360,
        )

        val playback = capture.playback

        assertNotNull(playback)
        assertEquals("https://cdn.example.test/360/master.m3u8", playback.url)
        assertEquals(360, playback.selectedVideoHeight)
        assertEquals(listOf(1080, 720, 360), playback.availableQualities.mapNotNull(SourceQuality::height))
        assertEquals(
            listOf(
                "https://mirror.example.test/360/master.m3u8",
                "https://cdn.example.test/720/master.m3u8",
                "https://cdn.example.test/1080/master.m3u8",
            ),
            playback.fallbackUrls,
        )
        assertFalse(playback.skipPlaybackProbe)
    }

    @Test
    fun allohaRuntimeQualityMapReadsNestedStreamValues() {
        val capture = inspectMetadataBody(
            url = "https://alloha.yani.tv/?translation=79&season=1&episode=1",
            body = """
                {
                  "sources": [
                    {
                      "quality": {
                        "360p": [
                          {"file": "39https://cdn.example.test/360/master.m3u8"},
                          {"src": "https://mirror.example.test/360/master.m3u8"}
                        ],
                        "480": {"url": "https://cdn.example.test/480/master.m3u8"},
                        "720": {"hls": "https://cdn.example.test/720/master.m3u8"},
                        "1080": "https://cdn.example.test/1080/master.m3u8"
                      }
                    }
                  ]
                }
            """.trimIndent(),
            sourceUrl = "https://alloha.yani.tv/?translation=79&season=1&episode=1",
            preferredQuality = PreferredQuality.P1080,
        )

        val playback = capture.playback

        assertNotNull(playback)
        assertEquals("https://cdn.example.test/1080/master.m3u8", playback.url)
        assertEquals(1080, playback.selectedVideoHeight)
        assertEquals(listOf(1080, 720, 480, 360), playback.availableQualities.mapNotNull(SourceQuality::height))
        assertEquals(
            listOf(
                "https://cdn.example.test/720/master.m3u8",
                "https://cdn.example.test/480/master.m3u8",
                "https://cdn.example.test/360/master.m3u8",
                "https://mirror.example.test/360/master.m3u8",
            ),
            playback.fallbackUrls,
        )
    }

    @Test
    fun allohaRuntimeStateKeepsTextTrackSubtitles() {
        val capture = inspectMetadataBody(
            url = "https://alloha.yani.tv/?translation=79&season=1&episode=1",
            body = """
                {
                  "hlsSource": [
                    {
                      "quality": {
                        "480": "https://cdn.example.test/480/master.m3u8"
                      },
                      "textTracks": [
                        {
                          "src": "https://cdn.example.test/subtitles/russian-signs.vtt",
                          "label": "Russian signs",
                          "srclang": "ru",
                          "kind": "subtitles"
                        }
                      ]
                    }
                  ]
                }
            """.trimIndent(),
            sourceUrl = "https://alloha.yani.tv/?translation=79&season=1&episode=1",
        )

        val subtitle = capture.subtitles.single()

        assertEquals("https://cdn.example.test/subtitles/russian-signs.vtt", subtitle.uri)
        assertEquals("Russian signs", subtitle.label)
        assertEquals("ru", subtitle.language)
        assertTrue(capture.embeddedSubtitles.isEmpty())
    }

    @Test
    fun allohaRuntimeStateKeepsEmbeddedTextTrackMetadata() {
        val capture = inspectMetadataBody(
            url = "https://alloha.yani.tv/?translation=79&season=1&episode=1",
            body = """
                {
                  "hlsSource": [
                    {
                      "quality": {
                        "480": "https://cdn.example.test/480/master.m3u8"
                      }
                    }
                  ],
                  "textTracks": [
                    {
                      "id": "sub-ru",
                      "label": "Russian signs",
                      "srclang": "ru",
                      "kind": "subtitles"
                    }
                  ]
                }
            """.trimIndent(),
            sourceUrl = "https://alloha.yani.tv/?translation=79&season=1&episode=1",
        )

        val embeddedSubtitle = capture.embeddedSubtitles.single()

        assertTrue(capture.hasEmbeddedSubtitles)
        assertEquals("sub-ru", embeddedSubtitle.id)
        assertEquals("Russian signs", embeddedSubtitle.label)
        assertEquals("ru", embeddedSubtitle.language)
    }

    @Test
    fun allohaRuntimeProgressiveStreamCanSkipManifestProbe() {
        val capture = inspectMetadataBody(
            url = "https://alloha.yani.tv/?translation=210&season=1&episode=14",
            body = """
                {
                  "source": {
                    "quality": {
                      "480": "https://cdn.example.test/video-480.mp4"
                    }
                  }
                }
            """.trimIndent(),
            sourceUrl = "https://alloha.yani.tv/?translation=210&season=1&episode=14",
        )

        val playback = capture.playback

        assertNotNull(playback)
        assertEquals("https://cdn.example.test/video-480.mp4", playback.url)
        assertTrue(playback.skipPlaybackProbe)
    }

    @Test
    fun capturedManifestUrlDropsAllohaNumericProtectionPrefix() {
        val capture = inspectMetadataBody(
            url = "39https://cdn.example.test/360/master.m3u8",
            body = """
                #EXTM3U
                #EXT-X-VERSION:3
                #EXT-X-STREAM-INF:BANDWIDTH=900000,RESOLUTION=640x360
                chunklist.m3u8
            """.trimIndent(),
            sourceUrl = "https://alloha.yani.tv/?translation=210&season=1&episode=14",
        )
        val playback = capture.playback

        assertNotNull(playback)
        assertEquals("https://cdn.example.test/360/master.m3u8", playback.url)
        assertEquals("application/x-mpegURL", playback.mimeType)
        assertFalse(playback.skipPlaybackProbe)
    }

    @Test
    fun hlsClosedCaptionMetadataIsCapturedForEmbeddedSubtitleNames() {
        val capture = inspectMetadataBody(
            url = "https://cdn.example.test/master.m3u8",
            body = """
                #EXTM3U
                #EXT-X-MEDIA:TYPE=CLOSED-CAPTIONS,GROUP-ID="cc",NAME="Signs",LANGUAGE="ru",INSTREAM-ID="CC1"
                #EXT-X-STREAM-INF:BANDWIDTH=2400000,RESOLUTION=1920x1080,CLOSED-CAPTIONS="cc"
                chunklist_1080.m3u8
            """.trimIndent(),
            sourceUrl = "https://alloha.yani.tv/?translation=210&season=1&episode=14",
        )

        val embeddedSubtitles = capture.embeddedSubtitles

        assertTrue(capture.hasEmbeddedSubtitles)
        assertEquals(1, embeddedSubtitles.size)
        assertEquals("CC1", embeddedSubtitles.single().id)
        assertEquals("Signs", embeddedSubtitles.single().label)
        assertEquals("ru", embeddedSubtitles.single().language)
    }

    @Test
    fun dashTextAdaptationSetMetadataIsCapturedForEmbeddedSubtitleNames() {
        val capture = inspectMetadataBody(
            url = "https://cdn.example.test/video.mpd",
            body = """
                <?xml version="1.0" encoding="UTF-8"?>
                <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static">
                    <Period>
                        <AdaptationSet id="video" contentType="video" mimeType="video/mp4">
                            <Representation id="video-720" height="720" />
                        </AdaptationSet>
                        <AdaptationSet id="subs" contentType="text" lang="ru">
                            <Label>Signs</Label>
                            <Representation id="subs-ru" mimeType="text/vtt" codecs="wvtt" />
                        </AdaptationSet>
                    </Period>
                </MPD>
            """.trimIndent(),
            sourceUrl = "https://cvh.example/player",
        )

        val embeddedSubtitles = capture.embeddedSubtitles

        assertTrue(capture.hasEmbeddedSubtitles)
        assertEquals(1, embeddedSubtitles.size)
        assertEquals("subs", embeddedSubtitles.single().id)
        assertEquals("Signs", embeddedSubtitles.single().label)
        assertEquals("ru", embeddedSubtitles.single().language)
    }

    @Test
    fun jsonEncodedSubtitleListKeepsTrackLabel() {
        val capture = inspectMetadataBody(
            url = "https://alloha.yani.tv/player-data",
            body = """
                {
                  "subtitle": "[{\"file\":\"https:\/\/cdn.example.test\/subs\/signs.vtt\",\"label\":\"Signs\",\"language\":\"ru\"}]"
                }
            """.trimIndent(),
            sourceUrl = "https://alloha.yani.tv/?translation=210&season=1&episode=14",
        )

        val subtitle = capture.subtitles.single()

        assertEquals("https://cdn.example.test/subs/signs.vtt", subtitle.uri)
        assertEquals("Signs", subtitle.label)
        assertEquals("ru", subtitle.language)
    }

    @Test
    fun hlsRelativeSubtitlePlaylistKeepsManifestNameAndLanguage() {
        val capture = inspectMetadataBody(
            url = "https://cdn.example.test/video/master.m3u8",
            body = """
                #EXTM3U
                #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subs",NAME="Signs",LANGUAGE="ru",URI="subs/signs.m3u8"
                #EXT-X-STREAM-INF:BANDWIDTH=2400000,RESOLUTION=1920x1080,SUBTITLES="subs"
                chunklist_1080.m3u8
            """.trimIndent(),
            sourceUrl = "https://alloha.yani.tv/?translation=210&season=1&episode=14",
        )

        val subtitle = capture.subtitles.single()

        assertEquals("https://cdn.example.test/video/subs/signs.m3u8", subtitle.uri)
        assertEquals("Signs", subtitle.label)
        assertEquals("ru", subtitle.language)
        assertEquals("application/x-mpegURL", subtitle.mimeType)
    }

    @Test
    fun structuredExtensionlessSubtitleEndpointKeepsMetadata() {
        val capture = inspectMetadataBody(
            url = "https://alloha.yani.tv/player-data",
            body = """
                {
                  "subtitles": [
                    {
                      "file": "https://cdn.example.test/track?id=ru",
                      "label": "Russian signs",
                      "language": "ru"
                    }
                  ]
                }
            """.trimIndent(),
            sourceUrl = "https://alloha.yani.tv/?translation=210&season=1&episode=14",
        )

        val subtitle = capture.subtitles.single()

        assertEquals("https://cdn.example.test/track?id=ru", subtitle.uri)
        assertEquals("Russian signs", subtitle.label)
        assertEquals("ru", subtitle.language)
    }

    @Test
    fun ordinaryVideoAndPosterUrlsDoNotCreateSubtitleTracks() {
        val capture = inspectMetadataBody(
            url = "https://player.example.test/metadata",
            body = """
                {
                  "source": "https://cdn.example.test/video/master.m3u8",
                  "poster": "https://cdn.example.test/posters/title.jpg"
                }
            """.trimIndent(),
            sourceUrl = "https://player.example.test/embed/1",
        )

        assertTrue(capture.subtitles.isEmpty())
    }

    @Test
    fun sourceResolveTimeoutsMatchProviderFlowCost() {
        assertEquals(
            SOURCE_RESOLVE_TIMEOUT_MS,
            timeoutVideo(player = "Kodik", url = "https://kodik.example/player").sourceResolveTimeoutMs(),
        )
        assertEquals(
            CVH_SOURCE_RESOLVE_TIMEOUT_MS,
            timeoutVideo(player = "CVH", url = "https://iframecvh.example/player").sourceResolveTimeoutMs(),
        )
        assertEquals(
            RUNTIME_SOURCE_RESOLVE_TIMEOUT_MS,
            timeoutVideo(player = "Alloha", url = "https://alloha.yani.tv/?translation=210").sourceResolveTimeoutMs(),
        )
        assertTrue(RUNTIME_SOURCE_RESOLVE_TIMEOUT_MS > STREAM_WEBVIEW_RESOLVE_TIMEOUT_MS)
    }

    private fun inspectMetadataBody(
        url: String,
        body: String,
        sourceUrl: String,
        preferredQuality: PreferredQuality = PreferredQuality.Auto,
    ): PlayerMetadataCapture {
        return metadataInspector.inspect(
            url = url,
            body = body,
            requestHeaders = emptyMap(),
            sourceUrl = sourceUrl,
            siteBaseUrl = TEST_SITE_BASE_URL,
            preferredQuality = preferredQuality,
        )
    }

    private fun timeoutVideo(player: String, url: String): VideoVariant {
        return VideoVariant(
            id = 1,
            animeId = 5500,
            player = player,
            dubbing = "AniLibria",
            episode = "14",
            url = url,
            index = 14,
            durationSeconds = 1_400,
            views = 1,
        )
    }

    private companion object {
        const val TEST_SITE_BASE_URL = "https://ru.yummyani.me"
    }
}
