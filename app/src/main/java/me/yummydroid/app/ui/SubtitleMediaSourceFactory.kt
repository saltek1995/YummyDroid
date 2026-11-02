package me.yummydroid.app.ui

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.source.CompositeSequenceableLoader
import androidx.media3.exoplayer.source.CompositeSequenceableLoaderFactory
import androidx.media3.exoplayer.source.DefaultCompositeSequenceableLoaderFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ForwardingTimeline
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SequenceableLoader
import androidx.media3.exoplayer.source.WrappingMediaSource
import androidx.media3.exoplayer.source.YummySubtitleSourceBridge
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleExtractor

/** Keeps optional sidecar loading out of the A/V startup buffer decision. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class SubtitleMediaSourceFactory private constructor(
    private val dataSources: DataSource.Factory,
    private val delegate: DefaultMediaSourceFactory,
) : MediaSource.Factory by delegate {
    constructor(dataSources: DataSource.Factory) : this(dataSources, DefaultMediaSourceFactory(dataSources))

    private var loadErrorPolicy: LoadErrorHandlingPolicy? = null

    override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy): SubtitleMediaSourceFactory {
        loadErrorPolicy = policy
        delegate.setLoadErrorHandlingPolicy(policy)
        return this
    }

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val local = requireNotNull(mediaItem.localConfiguration)
        val parsers = DefaultSubtitleParserFactory()
        val formats = local.subtitleConfigurations.map { subtitle ->
            Format.Builder().setSampleMimeType(subtitle.mimeType).setLanguage(subtitle.language)
                .setSelectionFlags(subtitle.selectionFlags).setRoleFlags(subtitle.roleFlags)
                .setLabel(subtitle.label).setId(subtitle.id).build()
        }
        // App playback uses ordinary, unclipped A/V items. Preserve stock handling for other source shapes.
        if (formats.isEmpty() || formats.any { !parsers.supportsFormat(it) } ||
            mediaItem.clippingConfiguration != MediaItem.ClippingConfiguration.UNSET ||
            local.adsConfiguration != null || local.uri.scheme == C.SSAI_SCHEME) {
            return delegate.createMediaSource(mediaItem)
        }
        val primary = SubtitleItemSource(delegate.createMediaSource(mediaItem.withoutSidecars()), mediaItem)
        val sources = local.subtitleConfigurations.mapIndexed { index, subtitle ->
            val format = formats[index]
            val cuesFormat = format.buildUpon().setSampleMimeType(MimeTypes.APPLICATION_MEDIA3_CUES)
                .setCodecs(format.sampleMimeType)
                .setCueReplacementBehavior(parsers.getCueReplacementBehavior(format)).build()
            // Matches Media3's lazy sidecar extraction: disabled captions never open their data source.
            val factory = YummySubtitleSourceBridge.enableLazyTrack(
                ProgressiveMediaSource.Factory(dataSources, ExtractorsFactory {
                    arrayOf(SubtitleExtractor(parsers.create(format), null))
                }), SubtitleExtractor.TRACK_ID, cuesFormat)
            loadErrorPolicy?.let(factory::setLoadErrorHandlingPolicy)
            factory.createMediaSource(MediaItem.fromUri(subtitle.uri))
        }
        return MergingMediaSource(false, false, AvBufferingLoaderFactory(), primary, *sources.toTypedArray())
    }
}

/** Media3's stock composite can retain a zero A/V buffer after fast A/V reaches EOF. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class AvBufferingLoaderFactory : CompositeSequenceableLoaderFactory {
    private val defaults = DefaultCompositeSequenceableLoaderFactory()
    override fun empty(): SequenceableLoader = defaults.empty()

    @Deprecated("Media3 compatibility")
    @Suppress("DEPRECATION")
    override fun createCompositeSequenceableLoader(vararg loaders: SequenceableLoader): SequenceableLoader =
        defaults.createCompositeSequenceableLoader(*loaders)

    override fun create(loaders: List<SequenceableLoader>, loaderTrackTypes: List<List<Int>>): SequenceableLoader {
        val composite = CompositeSequenceableLoader(loaders, loaderTrackTypes)
        val avLoaders = loaders.filterIndexed { index, _ ->
            loaderTrackTypes[index].any { it == C.TRACK_TYPE_AUDIO || it == C.TRACK_TYPE_VIDEO || it == C.TRACK_TYPE_IMAGE }
        }
        if (avLoaders.isEmpty()) return composite
        return object : SequenceableLoader by composite {
            override fun getBufferedPositionUs(): Long = avLoaders.asSequence()
                .map { it.bufferedPositionUs }.filter { it != C.TIME_END_OF_SOURCE }
                .minOrNull() ?: C.TIME_END_OF_SOURCE
        }
    }
}

private fun MediaItem.withoutSidecars(): MediaItem = buildUpon().setSubtitleConfigurations(emptyList()).build()

/** Retains caption configuration in both the initial item and every prepared timeline. */
@androidx.annotation.OptIn(UnstableApi::class)
private class SubtitleItemSource(source: MediaSource, @Volatile private var item: MediaItem) : WrappingMediaSource(source) {
    private var childTimeline: Timeline? = null
    override fun getMediaItem(): MediaItem = item
    override fun getInitialTimeline(): Timeline? = super.getInitialTimeline()?.withItem(item)
    override fun onChildSourceInfoRefreshed(newTimeline: Timeline) {
        childTimeline = newTimeline
        refreshSourceInfo(newTimeline.withItem(item))
    }

    override fun releaseSourceInternal() {
        childTimeline = null
        super.releaseSourceInternal()
    }

    override fun canUpdateMediaItem(mediaItem: MediaItem): Boolean =
        item.localConfiguration?.subtitleConfigurations == mediaItem.localConfiguration?.subtitleConfigurations &&
            mediaSource.canUpdateMediaItem(mediaItem.withoutSidecars())

    override fun updateMediaItem(mediaItem: MediaItem) {
        check(canUpdateMediaItem(mediaItem))
        item = mediaItem
        mediaSource.updateMediaItem(mediaItem.withoutSidecars())
        // ProgressiveMediaSource updates its item without publishing a new timeline itself.
        childTimeline?.let { refreshSourceInfo(it.withItem(item)) }
    }

    private fun Timeline.withItem(snapshot: MediaItem): Timeline = object : ForwardingTimeline(this) {
        override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window =
            super.getWindow(windowIndex, window, defaultPositionProjectionUs).apply { mediaItem = snapshot }
    }
}
