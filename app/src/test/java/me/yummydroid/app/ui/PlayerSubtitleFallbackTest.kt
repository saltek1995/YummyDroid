package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import androidx.media3.common.MimeTypes

class PlayerSubtitleFallbackTest {
    @Test
    fun materializedAssSubtitleMapsToMedia3SsaMimeType() {
        assertEquals(
            MimeTypes.TEXT_SSA,
            subtitleMimeTypeForMedia3(
                uri = "file:///data/user/0/me.yummydroid.app/cache/subtitle_streams/subtitle_abcdef.ass",
                mimeType = "text/x-ssa",
            ),
        )
    }

    @Test
    fun singleResolvedSubtitleNamesSingleGenericMedia3Track() {
        val reference = ResolvedSubtitleTrackReference(
            media3Id = "external-subtitle:file:///cache/subtitle.vtt::Alloha signs",
            label = "Alloha signs",
        )

        assertEquals(
            reference,
            singleResolvedSubtitleFallback(
                resolvedSubtitles = listOf(reference),
                media3SubtitleTrackCount = 1,
            ),
        )
        assertNull(
            singleResolvedSubtitleFallback(
                resolvedSubtitles = listOf(reference),
                media3SubtitleTrackCount = 2,
            ),
        )
    }

    @Test
    fun orderedResolvedSubtitleFallbackNamesGenericMedia3TracksWhenCountsMatch() {
        val first = ResolvedSubtitleTrackReference(
            media3Id = "",
            label = "Signs",
            sourceIndex = 1,
        )
        val second = ResolvedSubtitleTrackReference(
            media3Id = "",
            label = "Full subtitles",
            sourceIndex = 2,
        )

        assertEquals(
            first,
            orderedResolvedSubtitleFallback(
                resolvedSubtitles = listOf(second, first),
                media3SubtitleTrackCount = 2,
                media3SubtitleTrackIndex = 0,
            ),
        )
        assertEquals(
            second,
            orderedResolvedSubtitleFallback(
                resolvedSubtitles = listOf(second, first),
                media3SubtitleTrackCount = 2,
                media3SubtitleTrackIndex = 1,
            ),
        )
        assertNull(
            orderedResolvedSubtitleFallback(
                resolvedSubtitles = listOf(first, second),
                media3SubtitleTrackCount = 3,
                media3SubtitleTrackIndex = 0,
            ),
        )
    }

    @Test
    fun unresolvedResolvedSubtitleReferencesKeepMedia3TracksVisible() {
        val reference = ResolvedSubtitleTrackReference(
            media3Id = "external-subtitle:file:///cache/subtitle.vtt::Signs",
            label = "Signs",
        )

        assertFalse(
            shouldShowOnlyResolvedSubtitleOptions(
                resolvedSubtitles = listOf(reference),
                hasResolvedOptions = false,
            ),
        )
        assertTrue(
            shouldShowOnlyResolvedSubtitleOptions(
                resolvedSubtitles = listOf(reference),
                hasResolvedOptions = true,
            ),
        )
    }
}
