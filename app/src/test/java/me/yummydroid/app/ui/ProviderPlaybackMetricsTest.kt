package me.yummydroid.app.ui

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import me.yummydroid.app.data.PlaybackRuntimeSession
import me.yummydroid.app.data.PlaybackRuntimeState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ProviderPlaybackMetricsTest {
    @Test fun frameCountsIncludeDropsBelowNotificationThresholdAndResetAcrossSessions() {
        var session: PlaybackRuntimeSession = Session()
        val metrics = ProviderPlaybackMetrics({ session }, {})
        metrics.selectSession()
        var time = event(metrics.beginMediaItem(MediaItem.fromUri("https://media.test/one.m3u8")))
        metrics.onRenderedFirstFrame(time, Any(), 0)
        val counters = DecoderCounters().apply { droppedBufferCount = 3 }
        metrics.sampleDecoderCounters(counters)
        assertEquals(3L, metrics.droppedFrames)
        metrics.sampleDecoderCounters(counters)
        assertEquals(3L, metrics.droppedFrames)
        session = Session()
        metrics.selectSession(counters)
        time = event(metrics.beginMediaItem(MediaItem.fromUri("https://media.test/two.m3u8")))
        counters.droppedBufferCount = 4 // Retiring decoder drops cannot belong to the new view.
        metrics.sampleDecoderCounters(counters)
        assertEquals(0L, metrics.droppedFrames)
        metrics.onRenderedFirstFrame(time, Any(), 1)
        metrics.sampleDecoderCounters(counters)
        counters.droppedBufferCount = 6
        metrics.sampleDecoderCounters(counters)
        assertEquals(2L, metrics.droppedFrames)
        metrics.sampleDecoderCounters(DecoderCounters().apply { droppedBufferCount = 1 })
        assertEquals(3L, metrics.droppedFrames)
    }

    @Test fun queuedOldTimelineCannotContributeToNewMediaGeneration() {
        var session: PlaybackRuntimeSession = Session()
        val metrics = ProviderPlaybackMetrics({ session }, {})
        metrics.selectSession()
        val oldEvent = event(metrics.beginMediaItem(MediaItem.fromUri("https://media.test/old.m3u8")))
        metrics.onLoadStarted(oldEvent, load(1), media())
        session = Session()
        metrics.selectSession()
        val currentEvent = event(metrics.beginMediaItem(MediaItem.fromUri("https://media.test/new.m3u8")))
        // Both period IDs within this queued EventTime still refer to the old period.
        metrics.onLoadStarted(oldEvent, load(2), media())
        metrics.onLoadCompleted(oldEvent, load(1, 100), media())
        metrics.onLoadCompleted(oldEvent, load(2, 200), media())
        metrics.onRenderedFirstFrame(oldEvent, Any(), 10)
        metrics.onDroppedVideoFrames(oldEvent, 40, 100)
        assertEquals(0L, metrics.completedMediaBytes)
        assertEquals(0L, metrics.droppedFrames)
        assertFalse(metrics.firstFrame)
        metrics.onLoadStarted(currentEvent, load(3), media())
        metrics.onLoadCompleted(currentEvent, load(3, 300), media())
        assertEquals(300L, metrics.completedMediaBytes)
    }

    @Test fun qualityReloadKeepsSessionBytesButWaitsForNewFrameAndExcludesSubtitles() {
        val session = Session()
        val metrics = ProviderPlaybackMetrics({ session }, {})
        metrics.selectSession()
        val first = event(metrics.beginMediaItem(MediaItem.fromUri("https://media.test/720.m3u8")))
        metrics.onLoadStarted(first, load(1), media())
        metrics.onLoadCompleted(first, load(1, 1000), media())
        metrics.onRenderedFirstFrame(first, Any(), 10)
        assertTrue(metrics.firstFrame)
        val next = event(metrics.beginMediaItem(MediaItem.fromUri("https://media.test/1080.m3u8")))
        assertFalse(metrics.firstFrame)
        metrics.onRenderedFirstFrame(first, Any(), 11)
        assertFalse(metrics.firstFrame)
        metrics.onLoadStarted(next, load(2), media(C.TRACK_TYPE_TEXT))
        metrics.onLoadCompleted(next, load(2, 2000), media(C.TRACK_TYPE_TEXT))
        assertEquals(1000L, metrics.completedMediaBytes)
        metrics.onRenderedFirstFrame(next, Any(), 12)
        assertTrue(metrics.firstFrame)
    }

    private fun event(item: MediaItem): EventTime {
        val timeline = SinglePeriodTimeline(60_000_000L, true, false, false, null, item)
        val period = MediaPeriodId(timeline.getUidOfPeriod(0))
        return EventTime(0, timeline, 0, period, 0, timeline, 0, period, 0, 0)
    }
    private fun media(type: Int = C.TRACK_TYPE_VIDEO) =
        MediaLoadData(C.DATA_TYPE_MEDIA, type, null, C.SELECTION_REASON_UNKNOWN, null, 0, 6000)
    private fun load(id: Long, bytes: Long = 0): LoadEventInfo {
        val uri = Uri.parse("https://media.test/segment.m4s")
        return LoadEventInfo(id, DataSpec(uri), uri, emptyMap(), 0, 20, bytes)
    }
    private class Session : PlaybackRuntimeSession {
        override fun requestHeaders(url: String) = emptyMap<String, String>()
        override fun update(state: PlaybackRuntimeState) = Unit
        override fun seek(positionMs: Long) = Unit
        override fun ended() = Unit
        override fun close() = Unit
    }
}
