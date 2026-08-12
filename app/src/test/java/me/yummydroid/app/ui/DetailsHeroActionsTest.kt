package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import me.yummydroid.app.data.VideoVariant

class DetailsHeroActionsTest {
    @Test
    fun emptyPolicyHidesActionPanel() {
        val policy = actionPolicy()

        assertFalse(policy.showPanel)
        assertFalse(policy.showDownload)
        assertFalse(policy.showReset)
        assertEquals(DetailsHeroFocusIndex.ResetAction, policy.primaryFocusIndex)
        assertEquals(null, policy.selectedDownloadVideo)
    }

    @Test
    fun watchVideoEnablesPrimaryAndEligibleDownloadActions() {
        val watchVideo = video(id = 1)
        val policy = actionPolicy(
            watchVideo = watchVideo,
            canDownload = true,
            hasDownloadVideos = true,
        )

        assertTrue(policy.showPanel)
        assertTrue(policy.showDownload)
        assertFalse(policy.showReset)
        assertEquals(DetailsHeroFocusIndex.PrimaryAction, policy.primaryFocusIndex)
        assertSame(watchVideo, policy.primaryVideo)
        assertSame(watchVideo, policy.selectedDownloadVideo)
    }

    @Test
    fun downloadRequiresPrimaryVideoPermissionAndCandidates() {
        assertFalse(actionPolicy(watchVideo = video(id = 1)).showDownload)
        assertFalse(
            actionPolicy(
                watchVideo = video(id = 1),
                canDownload = true,
            ).showDownload,
        )
        assertFalse(
            actionPolicy(
                canDownload = true,
                hasDownloadVideos = true,
            ).showDownload,
        )
    }

    @Test
    fun progressKeepsResetAsOnlyFocusableActionWithoutVideo() {
        val policy = actionPolicy(hasWatchProgress = true)

        assertTrue(policy.showPanel)
        assertTrue(policy.showReset)
        assertEquals(DetailsHeroFocusIndex.ResetAction, policy.primaryFocusIndex)
    }

    @Test
    fun selectedDownloadVideoPrefersResumeTarget() {
        val watchVideo = video(id = 1)
        val resumeVideo = video(id = 2)
        val resumeTarget = HeroResumeTarget(resumeVideo, 12_000)
        val policy = actionPolicy(watchVideo = watchVideo, resumeTarget = resumeTarget)

        assertSame(resumeTarget, policy.resumeTarget)
        assertSame(resumeVideo, policy.selectedDownloadVideo)
    }

    private fun actionPolicy(
        watchVideo: VideoVariant? = null,
        resumeTarget: HeroResumeTarget? = null,
        canDownload: Boolean = false,
        hasDownloadVideos: Boolean = false,
        hasWatchProgress: Boolean = false,
    ): DetailsHeroActionPolicy = resolveDetailsHeroActionPolicy(
        watchVideo = watchVideo,
        resumeTarget = resumeTarget,
        canDownload = canDownload,
        hasDownloadVideos = hasDownloadVideos,
        hasWatchProgress = hasWatchProgress,
    )

    private fun video(id: Long): VideoVariant = VideoVariant(
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
