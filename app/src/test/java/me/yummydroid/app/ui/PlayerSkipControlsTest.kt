package me.yummydroid.app.ui

import androidx.media3.common.C
import androidx.media3.common.Player
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import me.yummydroid.app.data.VideoSkipKind
import me.yummydroid.app.data.VideoSkipSegment
import me.yummydroid.app.data.VideoVariant

class PlayerSkipControlsTest {
    @Test
    fun normalizedDurationRejectsUnsetAndNonPositiveValues() {
        assertEquals(0L, C.TIME_UNSET.normalizedDurationMs())
        assertEquals(0L, 0L.normalizedDurationMs())
        assertEquals(0L, (-1L).normalizedDurationMs())
        assertEquals(90_000L, 90_000L.normalizedDurationMs())
    }

    @Test
    fun skipControlsStartOnlyAfterTheTimelineIsReady() {
        assertFalse(hasReadyPlaybackTimeline(Player.STATE_BUFFERING, 90_000L))
        assertFalse(hasReadyPlaybackTimeline(Player.STATE_READY, C.TIME_UNSET))
        assertFalse(hasReadyPlaybackTimeline(Player.STATE_READY, 0L))
        assertTrue(hasReadyPlaybackTimeline(Player.STATE_READY, 90_000L))
    }

    @Test
    fun skipPromptBindingKeyChangesWithPlaybackSource() {
        val segment = VideoSkipSegment(VideoSkipKind.Opening, 10_000L, 90_000L)
        val alloha = video(player = "Alloha", url = "https://alloha.example/player", skipSegments = listOf(segment))
        val cvh = video(player = "CVH", url = "https://cvh.example/player", skipSegments = listOf(segment))

        assertNotEquals(alloha.skipPromptBindingKey(), cvh.skipPromptBindingKey())
    }

    @Test
    fun skipPromptBindingKeyChangesWithSkipSegments() {
        val first = video(
            player = "Alloha",
            url = "https://alloha.example/player",
            skipSegments = listOf(VideoSkipSegment(VideoSkipKind.Opening, 10_000L, 90_000L)),
        )
        val second = first.copy(
            skipSegments = listOf(VideoSkipSegment(VideoSkipKind.Opening, 15_000L, 95_000L)),
        )

        assertNotEquals(first.skipPromptBindingKey(), second.skipPromptBindingKey())
    }

    @Test
    fun activeSkipPromptIsUsefulOnlyInsideItsCluster() {
        val segment = VideoSkipSegment(VideoSkipKind.Opening, 10_000L, 90_000L)
        val prompt = ActiveSkipPrompt(
            key = segment.key,
            segment = segment,
            activeStartMs = segment.startMs,
            targetEndMs = segment.endMs,
        )

        assertFalse(prompt.hasUsefulSkipAt(5_000L))
        assertTrue(prompt.hasUsefulSkipAt(20_000L))
        assertFalse(prompt.hasUsefulSkipAt(89_000L))
        assertFalse(prompt.hasUsefulSkipAt(95_000L))
    }

    @Test
    fun skipPromptSchedulingTargetsKnownSegmentPositions() {
        val segment = VideoSkipSegment(VideoSkipKind.Opening, 10_000L, 90_000L)

        assertEquals(10_000L, listOf(segment).nextPendingSkipSegmentStartMs(0L, emptySet()))
        assertEquals(null, listOf(segment).nextPendingSkipSegmentStartMs(0L, setOf(segment.key)))
        assertEquals(null, listOf(segment).nextPendingSkipSegmentStartMs(90_000L, emptySet()))
        assertEquals(
            10_000L,
            skipPromptScheduledDelayMs(
                positionMs = 0L,
                targetPositionMs = 10_000L,
                isPlaying = true,
                playbackSpeed = 1f,
            ),
        )
        assertEquals(
            5_000L,
            skipPromptScheduledDelayMs(
                positionMs = 0L,
                targetPositionMs = 10_000L,
                isPlaying = true,
                playbackSpeed = 2f,
            ),
        )
        assertEquals(
            0L,
            skipPromptScheduledDelayMs(
                positionMs = 10_000L,
                targetPositionMs = 10_000L,
                isPlaying = true,
                playbackSpeed = 1f,
            ),
        )
        assertEquals(
            null,
            skipPromptScheduledDelayMs(
                positionMs = 0L,
                targetPositionMs = 10_000L,
                isPlaying = false,
                playbackSpeed = 1f,
            ),
        )
        assertEquals(
            null,
            skipPromptScheduledDelayMs(
                positionMs = 91_000L,
                targetPositionMs = null,
                isPlaying = true,
                playbackSpeed = 1f,
            ),
        )
    }

    private fun video(
        player: String,
        url: String,
        skipSegments: List<VideoSkipSegment>,
    ): VideoVariant {
        return VideoVariant(
            id = 101L,
            animeId = 22L,
            player = player,
            dubbing = "AniLibria",
            episode = "5",
            url = url,
            index = 5,
            durationSeconds = 1_440,
            views = 0,
            skipSegments = skipSegments,
        )
    }
}
