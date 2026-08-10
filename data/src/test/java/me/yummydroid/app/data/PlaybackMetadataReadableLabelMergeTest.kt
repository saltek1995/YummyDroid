package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackMetadataReadableLabelMergeTest {
    @Test
    fun normalizesDuplicateSubtitleUrlsAndPrefersReadableLabel() {
        val subtitles = listOf(
            ResolvedSubtitleTrack(
                uri = "https://example.test/sub_rus-2.vtt",
                label = "sub rus 2",
                mimeType = "text/vtt",
            ),
            ResolvedSubtitleTrack(
                uri = "https://example.test/sub_rus-2.vtt",
                label = "(Russian) РЎСѓР±С‚РёС‚СЂС‹",
                language = "rus",
                mimeType = "text/vtt",
            ),
        ).normalizedSubtitleTracks()

        assertEquals(1, subtitles.size)
        assertEquals("(Russian) РЎСѓР±С‚РёС‚СЂС‹", subtitles.single().label)
        assertEquals("rus", subtitles.single().language)
    }
}
