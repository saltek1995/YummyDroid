package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class SubtitleMediaLabelTest {
    @Test
    fun technicalCacheFileNameIsIgnored() {
        assertEquals(
            "",
            subtitleLabelForMedia3(
                label = "",
                uri = "file:///data/user/0/me.yummydroid.app/cache/subtitles/subtitle_abcdef1234567890abcdef1234567890.vtt",
            ),
        )
    }

    @Test
    fun resolvedTrackLabelTakesPriority() {
        assertEquals(
            "Alloha ru 2",
            subtitleLabelForMedia3(
                label = "Alloha ru 2",
                uri = "file:///data/user/0/me.yummydroid.app/cache/subtitles/subtitle_original_hash.vtt",
            ),
        )
    }

    @Test
    fun readableSourceFileNameIsKept() {
        assertEquals(
            "alloha ru",
            subtitleLabelForMedia3(
                label = "",
                uri = "https://example.com/subtitles/alloha_ru.vtt",
            ),
        )
    }
}
