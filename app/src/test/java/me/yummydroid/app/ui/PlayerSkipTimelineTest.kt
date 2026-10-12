package me.yummydroid.app.ui

import android.view.LayoutInflater
import android.view.View
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R as Media3R
import java.lang.reflect.Proxy
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import me.yummydroid.app.R
import me.yummydroid.app.data.VideoSkipKind
import me.yummydroid.app.data.VideoSkipSegment
import me.yummydroid.app.data.VideoVariant
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class PlayerSkipTimelineTest {
    private val playerView = LayoutInflater.from(RuntimeEnvironment.getApplication())
        .inflate(R.layout.yummy_player_view, null) as PlayerView
    private val timeBar = playerView.findViewById<YummyPlayerTimeBar>(Media3R.id.exo_progress)

    @Test
    fun lateDurationAddsOpeningAndEndingWithoutRebindingController() {
        timeBar.layout(0, 0, 600, 48)
        playerView.bindSkipTimelineMarkers(video())
        assertContentEquals(longArrayOf(), markerTimes())

        timeBar.setDuration(1_440_000L)

        assertSegmentsVisible()
    }

    @Test
    fun durationChangesRestoreEndingPreviouslyOutsideTimeline() {
        timeBar.setDuration(60_000L)
        timeBar.layout(0, 0, 600, 48)
        playerView.bindSkipTimelineMarkers(video())
        assertTrue(markerTimes().all { it <= 60_000L })

        timeBar.setDuration(1_440_000L)

        assertSegmentsVisible()
    }

    @Test
    fun latestEpisodeWinsWhenSegmentsChangeBeforeFirstLayout() {
        timeBar.setDuration(1_440_000L)
        playerView.bindSkipTimelineMarkers(video())
        playerView.bindSkipTimelineMarkers(video().copy(skipSegments = emptyList()))

        timeBar.layout(0, 0, 600, 48)

        assertContentEquals(longArrayOf(), markerTimes())
    }

    @Test
    fun widthChangesResampleHighlightsAndPlayerMarkerUpdatesPreserveThem() {
        timeBar.setDuration(1_440_000L)
        timeBar.layout(0, 0, 300, 48)
        playerView.bindSkipTimelineMarkers(video())
        val initialCount = markerTimes().size

        timeBar.layout(0, 0, 1_200, 48)
        assertTrue(markerTimes().size > initialCount)
        timeBar.setAdGroupTimesMs(longArrayOf(500_000L), booleanArrayOf(true), 1)
        assertSegmentsVisible()
        assertTrue(500_000L in markerTimes())
        timeBar.setAdGroupTimesMs(null, null, 0)
        assertSegmentsVisible()
        assertTrue(500_000L !in markerTimes())

        timeBar.setDuration(C.TIME_UNSET)
        assertContentEquals(longArrayOf(), markerTimes())
        timeBar.setDuration(1_440_000L)
        assertSegmentsVisible()
    }

    @Test
    fun displayedMarkersAndSkipActionsKeepExactOriginalTimesAfterDurationAndWidthChanges() {
        val segments = listOf(
            VideoSkipSegment(VideoSkipKind.Opening, 10_123L, 90_456L),
            VideoSkipSegment(VideoSkipKind.Ending, 1_350_789L, 1_420_987L),
        )
        val probe = SkipPlayerProbe()
        try {
            segments.forEach { segment ->
                // Metadata must not scale or offset the original timestamps.
                val current = video().copy(durationSeconds = 60, skipSegments = listOf(segment))
                playerView.bindSkipTimelineMarkers(current)
                playerView.bindSkipControls(probe.player, current, defaultPlayerControlTexts)
                listOf(1_440_000L, C.TIME_UNSET, 60_000L, 1_500_000L).forEachIndexed { index, duration ->
                    timeBar.setDuration(duration)
                    timeBar.layout(0, 0, 300 * (index + 1), 48)
                }
                assertTrue(segment.startMs in markerTimes() && segment.endMs in markerTimes())
                assertEquals(segment, current.skipSegments.single())

                probe.positionMs = segment.startMs - 1L
                checkPrompt()
                assertNull(activePrompt())
                probe.positionMs = segment.startMs
                checkPrompt()
                assertEquals(segment.startMs, activePrompt()?.activeStartMs)
                assertEquals(segment.endMs, activePrompt()?.targetEndMs)

                playerView.findViewById<View>(R.id.yummy_skip_skip).performClick()

                assertEquals(segment.endMs, probe.seeks.last())
                checkPrompt()
                assertNull(activePrompt())
            }
        } finally {
            playerView.unbindSkipControls()
        }
    }

    @Test
    fun staleSkipButtonCannotSeekAfterPositionLeavesOriginalRange() {
        val segment = video().skipSegments.first()
        val probe = SkipPlayerProbe()
        try {
            playerView.bindSkipControls(probe.player, video(), defaultPlayerControlTexts)
            probe.positionMs = segment.startMs
            checkPrompt()
            assertEquals(segment.key, activePrompt()?.key)

            // A seek can precede delivery of the player event that removes the button.
            probe.positionMs = segment.startMs - 1L
            playerView.findViewById<View>(R.id.yummy_skip_skip).performClick()

            assertTrue(probe.seeks.isEmpty(), "A stale prompt must not skip unmarked content")
            assertNull(activePrompt())
            probe.positionMs = segment.startMs
            checkPrompt()
            assertEquals(segment.key, activePrompt()?.key, "Stale clicks must not dismiss a valid later visit")
        } finally {
            playerView.unbindSkipControls()
        }
    }

    @Test
    fun automaticSkipSeeksToExactOriginalEndAfterTimelineUpdates() {
        val segment = VideoSkipSegment(VideoSkipKind.Ending, 1_350_789L, 1_420_987L)
        val current = video().copy(skipSegments = listOf(segment))
        val probe = SkipPlayerProbe().apply { positionMs = segment.startMs }
        try {
            playerView.bindSkipTimelineMarkers(current)
            playerView.bindSkipControls(probe.player, current, defaultPlayerControlTexts)
            checkPrompt()
            timeBar.setDuration(1_500_000L)
            timeBar.layout(0, 0, 600, 48)

            val finish = completeCountdown()
            ShadowSystemClock.advanceBy(Duration.ofMillis(SKIP_PROMPT_ZERO_DISPLAY_MS))
            finish.run()

            assertEquals(listOf(segment.endMs), probe.seeks)
            assertTrue(segment.startMs in markerTimes() && segment.endMs in markerTimes())
        } finally {
            playerView.unbindSkipControls()
        }
    }

    @Test
    fun automaticSkipRechecksRangeAtFinalDelayedCallback() {
        val segment = video().skipSegments.first()
        val probe = SkipPlayerProbe().apply { positionMs = segment.startMs }
        try {
            playerView.bindSkipControls(probe.player, video(), defaultPlayerControlTexts)
            checkPrompt()
            val finish = completeCountdown()

            probe.positionMs = segment.startMs - 1L
            ShadowSystemClock.advanceBy(Duration.ofMillis(SKIP_PROMPT_ZERO_DISPLAY_MS))
            finish.run()

            assertTrue(probe.seeks.isEmpty())
            assertNull(activePrompt())
        } finally {
            playerView.unbindSkipControls()
        }
    }

    private fun completeCountdown(): Runnable {
        val state = playerView.tagValue<SkipCountdownState>(R.id.yummy_player_skip_auto_cancelled)!!
        ShadowSystemClock.advanceBy(Duration.ofMillis(state.deadlineMs - SystemClock.elapsedRealtime()))
        val tick = playerView.tagValue<Runnable>(R.id.yummy_player_skip_countdown_runnable)!!
        tick.run()
        val finish = playerView.tagValue<Runnable>(R.id.yummy_player_skip_countdown_runnable)!!
        assertTrue(finish !== tick, "Completed countdown must schedule the final skip callback")
        return finish
    }

    private fun checkPrompt() {
        playerView.tagValue<Runnable>(R.id.yummy_player_skip_poll_runnable)!!.run()
    }

    private fun activePrompt() = playerView.tagValue<ActiveSkipPrompt>(R.id.yummy_player_active_skip_segment)

    private class SkipPlayerProbe {
        var positionMs = 0L
        val seeks = mutableListOf<Long>()
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) {
            _, method, args ->
            when (method.name) {
                "getCurrentPosition" -> positionMs
                "isPlaying" -> false
                "getPlaybackParameters" -> PlaybackParameters.DEFAULT
                "addListener", "removeListener" -> null
                "seekTo" -> {
                    positionMs = args!![0] as Long
                    seeks.add(positionMs)
                    null
                }
                else -> error("Unexpected player call: ${method.name}")
            }
        } as Player
    }

    private fun assertSegmentsVisible() {
        val markers = markerTimes()
        assertTrue(10_000L in markers && 90_000L in markers, "Opening edges must be visible")
        assertTrue(1_350_000L in markers && 1_440_000L in markers, "Ending must be clipped to displayed duration")
        assertTrue(markers.any { it > 10_000L && it < 90_000L }, "Opening must be highlighted inside its edges")
    }

    private fun markerTimes(): LongArray {
        val count = DefaultTimeBar::class.java.getDeclaredField("adGroupCount").apply { isAccessible = true }
            .getInt(timeBar)
        if (count == 0) return longArrayOf()
        return (DefaultTimeBar::class.java.getDeclaredField("adGroupTimesMs").apply { isAccessible = true }
            .get(timeBar) as LongArray).copyOf(count)
    }

    private fun video() = VideoVariant(
        id = 1L, animeId = 1L, player = "test", dubbing = "test", episode = "1",
        url = "", index = 0, durationSeconds = null, views = 0L,
        skipSegments = listOf(
            VideoSkipSegment(VideoSkipKind.Opening, 10_000L, 90_000L),
            VideoSkipSegment(VideoSkipKind.Ending, 1_350_000L, 1_500_000L),
        ),
    )
}
