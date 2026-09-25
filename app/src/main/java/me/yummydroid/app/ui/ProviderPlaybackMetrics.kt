package me.yummydroid.app.ui

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import me.yummydroid.app.data.PlaybackRuntimeSession
import java.io.IOException

/** Counts only completed media loads, with an originating session lease per load. */
internal class CompletedProviderMediaLoads {
    private var owner: Any? = null
    private val loads = mutableMapOf<Long, Any>()
    var bytes: Long = 0
        private set

    fun select(session: Any?) {
        if (owner === session) return
        owner = session
        loads.clear()
        bytes = 0
    }

    fun started(id: Long, session: Any?) {
        if (session != null && session === owner) loads[id] = session
    }

    fun completed(id: Long, loadedBytes: Long) {
        val session = loads.remove(id) ?: return
        if (session === owner && loadedBytes > 0) bytes += loadedBytes.coerceAtMost(Long.MAX_VALUE - bytes)
    }

    fun canceled(id: Long) { loads.remove(id) }
    fun retireLoads() { loads.clear() }
}

/** Observation only: never adjusts buffer sizes, renderers, request scheduling or volume. */
@OptIn(UnstableApi::class)
internal class ProviderPlaybackMetrics(
    private val session: () -> PlaybackRuntimeSession?,
    private val changed: () -> Unit,
) : AnalyticsListener {
    private val mediaLoads = CompletedProviderMediaLoads()
    private var owner: PlaybackRuntimeSession? = null
    private var mediaGeneration: Any? = null
    private var decoderCounters: DecoderCounters? = null
    private var previousDropped = 0L
    private var baselineAfterFirstFrame = false
    val completedMediaBytes: Long get() = mediaLoads.bytes
    var droppedFrames: Long = 0
        private set
    var firstFrame: Boolean = false
        private set

    fun selectSession(currentCounters: DecoderCounters? = null) {
        val next = session()
        if (owner === next) return
        owner = next
        mediaLoads.select(next)
        droppedFrames = 0
        decoderCounters = currentCounters
        currentCounters?.ensureUpdated()
        previousDropped = currentCounters?.droppedBufferCount?.toLong()?.coerceAtLeast(0) ?: 0L
        baselineAfterFirstFrame = currentCounters != null
        firstFrame = false
    }

    fun sampleDecoderCounters(current: DecoderCounters?) {
        if (owner == null || owner !== session() || current == null || !firstFrame) return
        current.ensureUpdated()
        val count = current.droppedBufferCount.toLong().coerceAtLeast(0)
        if (baselineAfterFirstFrame && decoderCounters === current) previousDropped = count
        baselineAfterFirstFrame = false
        val baseline = if (decoderCounters === current) previousDropped else 0L
        droppedFrames += (count - baseline).coerceAtLeast(0)
        decoderCounters = current
        previousDropped = count
    }

    fun beginMediaItem(item: MediaItem): MediaItem {
        val generation = Any()
        mediaGeneration = generation
        mediaLoads.retireLoads()
        firstFrame = false
        return item.buildUpon().setTag(generation).build()
    }

    private fun accepts(eventTime: EventTime): Boolean {
        if (owner == null || owner !== session() || mediaGeneration == null) return false
        if (eventTime.windowIndex !in 0 until eventTime.timeline.windowCount) return false
        return eventTime.timeline.getWindow(eventTime.windowIndex, Timeline.Window())
            .mediaItem.localConfiguration?.tag === mediaGeneration
    }

    override fun onLoadStarted(eventTime: EventTime, loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData) {
        if (accepts(eventTime) && mediaLoadData.trackType != C.TRACK_TYPE_TEXT &&
            (mediaLoadData.dataType == C.DATA_TYPE_MEDIA || mediaLoadData.dataType == C.DATA_TYPE_MEDIA_INITIALIZATION)) {
            mediaLoads.started(loadEventInfo.loadTaskId, session())
        }
    }

    override fun onLoadCompleted(eventTime: EventTime, loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData) {
        if (accepts(eventTime)) mediaLoads.completed(loadEventInfo.loadTaskId, loadEventInfo.bytesLoaded)
        else mediaLoads.canceled(loadEventInfo.loadTaskId)
    }

    override fun onLoadCanceled(eventTime: EventTime, loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData) {
        mediaLoads.canceled(loadEventInfo.loadTaskId)
    }

    override fun onLoadError(eventTime: EventTime, loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData,
        error: IOException, wasCanceled: Boolean) {
        // Retried loads keep their task ID; only successful completion counts their bytes.
        if (wasCanceled) mediaLoads.canceled(loadEventInfo.loadTaskId)
    }

    override fun onRenderedFirstFrame(eventTime: EventTime, output: Any, renderTimeMs: Long) {
        if (!accepts(eventTime)) return
        firstFrame = true
        changed()
    }
}
