package me.yummydroid.app.data

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubtitleMetadataParserRuntimeTest {
    private val parser = SubtitleMetadataParser(
        fallbackSiteBaseUrl = { "https://fallback.example.test" },
        json = Json,
    )

    @Test
    fun structuredMetadataEnrichesAndDeduplicatesDetectedUrl() {
        val tracks = parser.extractTracks(
            body = """
                {
                  "captions": [
                    {
                      "src": "https:\/\/cdn.example.test\/subs\/signs.vtt?token=one&amp;part=two",
                      "label": "Signs",
                      "language": "ru"
                    }
                  ]
                }
            """.trimIndent(),
            baseUrl = "https://video.example.test/player/metadata.json",
        )

        assertEquals(1, tracks.size)
        assertEquals("https://cdn.example.test/subs/signs.vtt?token=one&part=two", tracks.single().uri)
        assertEquals("Signs", tracks.single().label)
        assertEquals("ru", tracks.single().language)
        assertEquals("text/vtt", tracks.single().mimeType)
    }

    @Test
    fun directAndPotentialCandidatesRemainDistinct() {
        val direct = parser.directTrack("https://cdn.example.test/subs/episode.srt")
        val potential = parser.potentialTrack("https://api.example.test/subtitles?id=42")

        assertNotNull(direct)
        assertEquals("application/x-subrip", direct.mimeType)
        assertNotNull(potential)
        assertTrue(parser.isResolvableCandidate(potential.uri))
        assertFalse(parser.isResolvableCandidate("https://cdn.example.test/video/episode.mp4"))
        assertNull(parser.directTrack(potential.uri))
    }

    @Test
    fun hlsManifestSeparatesExternalAndEmbeddedTracks() {
        val detection = parser.extractHlsTracks(
            body = """
                #EXTM3U
                #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subs",NAME="Signs",LANGUAGE="ru",URI="subs/signs.m3u8"
                #EXT-X-MEDIA:TYPE=CLOSED-CAPTIONS,GROUP-ID="cc",NAME="English CC",LANGUAGE="en",INSTREAM-ID="CC1"
            """.trimIndent(),
            baseUrl = "https://video.example.test/live/master.m3u8",
        )

        assertEquals(
            ResolvedSubtitleTrack(
                uri = "https://video.example.test/live/subs/signs.m3u8",
                label = "Signs",
                language = "ru",
                mimeType = "application/x-mpegURL",
            ),
            detection.tracks.single(),
        )
        assertEquals(
            ResolvedEmbeddedSubtitleTrack(id = "CC1", label = "English CC", language = "en"),
            detection.embeddedSubtitles.single(),
        )
        assertTrue(detection.hasEmbeddedSubtitles)
    }

    @Test
    fun dashManifestExposesTextAdaptationSet() {
        val tracks = parser.extractDashEmbeddedTracks(
            """
                <MPD xmlns="urn:mpeg:dash:schema:mpd:2011">
                  <Period>
                    <AdaptationSet id="sub-ru" contentType="text" lang="ru">
                      <Label>Russian signs</Label>
                      <Representation id="sub-ru-vtt" codecs="wvtt" />
                    </AdaptationSet>
                  </Period>
                </MPD>
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                ResolvedEmbeddedSubtitleTrack(
                    id = "sub-ru",
                    label = "Russian signs",
                    language = "ru",
                ),
            ),
            tracks,
        )
    }

    @Test
    fun runtimeTextTracksExposeEmbeddedSubtitleMetadataWithoutUrl() {
        val tracks = parser.extractEmbeddedTracks(
            """
                {
                  "textTracks": [
                    {
                      "id": "sub-ru",
                      "label": "Russian signs",
                      "srclang": "ru",
                      "kind": "subtitles"
                    }
                  ],
                  "settings": ["quality", "audio", "captions"]
                }
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                ResolvedEmbeddedSubtitleTrack(
                    id = "sub-ru",
                    label = "Russian signs",
                    language = "ru",
                ),
            ),
            tracks,
        )
    }
}
