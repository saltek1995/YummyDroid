package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackProviderPriorityTest {
    @Test
    fun resolvedProviderPriorityWinsOverQualityAndInputOrder() {
        val providers = listOf("CVH", "Aksor", "Kodik", "VK", "Alloha", "Sibnet")
        val attempts = providers.mapIndexed { index, player -> attempt(index, player, 240 + index * 240) }.reversed()
        assertEquals("CVH", attempts.bestPlayback()?.video?.player)
        assertEquals("Aksor", attempts.filterNot { it.candidate.player == "CVH" }.bestPlayback()?.video?.player)
    }

    @Test
    fun metadataOnlyHighPrioritySourceCannotReplaceSelectableManualSource() {
        val manual = attempt(0, "Alloha", 480)
        val metadataOnly = attempt(1, "CVH", 1080)
        assertEquals(manual.playback, listOf(metadataOnly, manual).bestPlayback(setOf(manual.candidate.sourceResolveIdentity())))
    }

    @Test
    fun downloadedPlaybackRemainsAheadOfOnlineProviderPriority() {
        val online = attempt(0, "CVH", 1080)
        val offline = attempt(1, "Sibnet", 480).let {
            val video = it.candidate.copy(localPlaybackUrl = "file:///episode.mp4")
            it.copy(candidate = video, playback = it.playback!!.copy(video = video))
        }
        assertEquals(offline.playback, listOf(online, offline).bestPlayback())
    }

    private fun attempt(index: Int, player: String, height: Int): SourceResolveAttempt {
        val video = VideoVariant(
            id = index + 1L, animeId = 10, player = player, dubbing = "Voice", episode = "1",
            url = "https://example.test/$player", index = index, durationSeconds = null, views = 0,
        )
        return SourceResolveAttempt(index, video, ResolvedPlayback(video,
            ResolvedVideoStream("https://stream.test/$player", "video/mp4", emptyMap(), maxVideoHeight = height)))
    }
}
