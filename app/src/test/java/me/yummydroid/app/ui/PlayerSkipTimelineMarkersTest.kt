package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import me.yummydroid.app.data.VideoSkipKind
import me.yummydroid.app.data.VideoSkipSegment

class PlayerSkipTimelineMarkersTest {
    @Test
    fun timelineMarkerSegmentsClampToDuration() {
        val markers = listOf(
            VideoSkipSegment(VideoSkipKind.Opening, 10_000L, 90_000L),
            VideoSkipSegment(VideoSkipKind.Ending, 1_350_000L, 1_500_000L),
        ).timelineMarkerSegments(durationMs = 1_440_000L)

        assertEquals(
            listOf(
                SkipTimelineMarkerSegment(10_000L, 90_000L),
                SkipTimelineMarkerSegment(1_350_000L, 1_440_000L),
            ),
            markers,
        )
    }

    @Test
    fun timelineMarkerSegmentsIgnoreSegmentsOutsideDuration() {
        val markers = listOf(
            VideoSkipSegment(VideoSkipKind.Opening, 1_500_000L, 1_590_000L),
            VideoSkipSegment(VideoSkipKind.Ending, 100_000L, 100_000L),
        ).timelineMarkerSegments(durationMs = 1_440_000L)

        assertEquals(emptyList(), markers)
    }

    @Test
    fun timelineMarkerSegmentsRequireKnownDuration() {
        val markers = listOf(
            VideoSkipSegment(VideoSkipKind.Opening, 10_000L, 90_000L),
        ).timelineMarkerSegments(durationMs = null)

        assertEquals(emptyList(), markers)
    }
}
