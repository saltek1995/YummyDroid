package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VideoStreamResolverTest {
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
        val playback = capture.fieldValue("playback")

        assertNotNull(playback)
        assertEquals(url, playback.fieldValue("url"))
        assertEquals("application/x-mpegURL", playback.fieldValue("mimeType"))
        assertEquals(1080, playback.fieldValue("maxVideoHeight"))
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

        val playback = capture.fieldValue("playback")

        assertNotNull(playback)
        assertEquals("https://cdn.example.test/360/master.m3u8", playback.fieldValue("url"))
        assertEquals(
            listOf(
                "https://mirror.example.test/360/master.m3u8",
                "https://cdn.example.test/720/master.m3u8",
                "https://cdn.example.test/1080/master.m3u8",
            ),
            playback.fieldValue("fallbackUrls"),
        )
        assertFalse(playback.fieldValue("skipPlaybackProbe") as Boolean)
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
        val playback = capture.fieldValue("playback")

        assertNotNull(playback)
        assertEquals("https://cdn.example.test/360/master.m3u8", playback.fieldValue("url"))
        assertEquals("application/x-mpegURL", playback.fieldValue("mimeType"))
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

        val embeddedSubtitles = capture.embeddedSubtitles()

        assertTrue(capture.fieldValue("hasEmbeddedSubtitles") as Boolean)
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

        val embeddedSubtitles = capture.embeddedSubtitles()

        assertTrue(capture.fieldValue("hasEmbeddedSubtitles") as Boolean)
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

        val subtitles = capture.fieldValue("subtitles") as List<*>
        val subtitle = subtitles.single() as ResolvedSubtitleTrack

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

        val subtitle = capture.subtitleTracks().single()

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

        val subtitle = capture.subtitleTracks().single()

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

        assertTrue(capture.subtitleTracks().isEmpty())
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
        assertTrue(RUNTIME_SOURCE_RESOLVE_TIMEOUT_MS > VideoStreamResolver.WEBVIEW_RESOLVE_TIMEOUT_MS)
    }

    private fun inspectMetadataBody(
        url: String,
        body: String,
        sourceUrl: String,
        preferredQuality: PreferredQuality = PreferredQuality.Auto,
    ): Any {
        val method = VideoStreamResolver::class.java.getDeclaredMethod(
            "inspectPlayerMetadataBody",
            String::class.java,
            String::class.java,
            Map::class.java,
            String::class.java,
            String::class.java,
            PreferredQuality::class.java,
        )
        method.isAccessible = true
        return method.invoke(
            VideoStreamResolver(),
            url,
            body,
            emptyMap<String, String>(),
            sourceUrl,
            "https://ru.yummyani.me",
            preferredQuality,
        ) as Any
    }

    @Suppress("UNCHECKED_CAST")
    private fun Any.embeddedSubtitles(): List<ResolvedEmbeddedSubtitleTrack> {
        return fieldValue("embeddedSubtitles") as List<ResolvedEmbeddedSubtitleTrack>
    }

    @Suppress("UNCHECKED_CAST")
    private fun Any.subtitleTracks(): List<ResolvedSubtitleTrack> {
        return fieldValue("subtitles") as List<ResolvedSubtitleTrack>
    }

    private fun Any.fieldValue(name: String): Any? {
        val field = javaClass.getDeclaredField(name)
        field.isAccessible = true
        return field.get(this)
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
}
