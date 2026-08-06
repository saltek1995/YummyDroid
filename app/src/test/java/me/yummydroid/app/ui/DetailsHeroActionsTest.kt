package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import me.yummydroid.app.data.VideoVariant

class DetailsHeroActionsTest {
    @Test
    fun actionsAreVisibleForWatchVideoOrProgress() {
        assertFalse(detailsHeroShouldShowActions(watchVideo = null, hasWatchProgress = false))
        assertTrue(detailsHeroShouldShowActions(watchVideo = video(id = 1), hasWatchProgress = false))
        assertTrue(detailsHeroShouldShowActions(watchVideo = null, hasWatchProgress = true))
    }

    @Test
    fun primaryFocusIndexMatchesPrimaryAction() {
        assertEquals(DetailsHeroFocusIndex.PrimaryAction, detailsHeroPrimaryActionFocusIndex(video(id = 1)))
        assertEquals(DetailsHeroFocusIndex.ResetAction, detailsHeroPrimaryActionFocusIndex(null))
    }

    @Test
    fun selectedDownloadVideoPrefersResumeTarget() {
        val watchVideo = video(id = 1)
        val resumeVideo = video(id = 2)

        assertSame(resumeVideo, detailsHeroSelectedDownloadVideo(HeroResumeTarget(resumeVideo, 12_000), watchVideo))
        assertSame(watchVideo, detailsHeroSelectedDownloadVideo(resumeTarget = null, watchVideo = watchVideo))
    }

    private fun video(id: Long): VideoVariant {
        return VideoVariant(
            id = id,
            animeId = 10,
            player = "CVH",
            dubbing = "Voice",
            episode = "1",
            url = "https://example.test/$id",
            index = id.toInt(),
            durationSeconds = null,
            views = 0,
        )
    }
}
