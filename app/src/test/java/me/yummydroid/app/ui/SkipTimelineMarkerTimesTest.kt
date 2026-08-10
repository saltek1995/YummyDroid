package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertContentEquals

class SkipTimelineMarkerTimesTest {
    @Test
    fun segmentIsSampledAtThreeDpIntervalsIncludingBothEdges() {
        val markers = listOf(SkipTimelineMarkerSegment(100L, 170L))
            .timelineAdMarkerTimes(durationMs = 1_000L, timelineWidthPx = 100, density = 1f)

        assertContentEquals(longArrayOf(100L, 130L, 160L, 170L), markers)
    }

    @Test
    fun overlappingSegmentEdgesAreDeduplicatedAndSorted() {
        val markers = listOf(
            SkipTimelineMarkerSegment(130L, 160L),
            SkipTimelineMarkerSegment(100L, 130L),
        ).timelineAdMarkerTimes(durationMs = 1_000L, timelineWidthPx = 100, density = 1f)

        assertContentEquals(longArrayOf(100L, 130L, 160L), markers)
    }

    @Test
    fun nonPositiveWidthStillIncludesSegmentEdges() {
        val markers = listOf(SkipTimelineMarkerSegment(100L, 170L))
            .timelineAdMarkerTimes(durationMs = 1_000L, timelineWidthPx = 0, density = 1f)

        assertContentEquals(longArrayOf(100L, 170L), markers)
    }

    @Test
    fun markersRequireSegmentsAndKnownPositiveDuration() {
        val segment = listOf(SkipTimelineMarkerSegment(100L, 170L))

        assertContentEquals(longArrayOf(), emptyList<SkipTimelineMarkerSegment>().timelineAdMarkerTimes(1_000L, 100, 1f))
        assertContentEquals(longArrayOf(), segment.timelineAdMarkerTimes(null, 100, 1f))
        assertContentEquals(longArrayOf(), segment.timelineAdMarkerTimes(0L, 100, 1f))
    }
}
