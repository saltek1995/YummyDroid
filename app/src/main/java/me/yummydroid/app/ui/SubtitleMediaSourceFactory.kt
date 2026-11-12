package me.yummydroid.app.ui

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.LoadingInfo
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

/** Optional captions must neither cap the A/V buffer nor starve its next loading request. */
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
        val (avEntries, optionalEntries) = loaders.zip(loaderTrackTypes).partition { (_, types) ->
            types.any { it == C.TRACK_TYPE_AUDIO || it == C.TRACK_TYPE_VIDEO || it == C.TRACK_TYPE_IMAGE }
        }
        if (avEntries.isEmpty()) return composite
        val avLoaders = avEntries.map { it.first }
        val av = CompositeSequenceableLoader(avLoaders, avEntries.map { it.second })
        return object : SequenceableLoader by composite {
            override fun getBufferedPositionUs(): Long = avLoaders.asSequence()
                .map { it.bufferedPositionUs }.filter { it != C.TIME_END_OF_SOURCE }
                .minOrNull() ?: C.TIME_END_OF_SOURCE

            override fun getNextLoadPositionUs(): Long {
                val mediaNext = av.nextLoadPositionUs
                if (mediaNext == C.TIME_END_OF_SOURCE) return composite.nextLoadPositionUs
                // An active caption request must not make LoadControl see a permanent zero buffer.
                // Dormant sidecars remain visible so track selection can start/continue their loaders.
                return optionalEntries.asSequence().map { it.first }.filterNot { it.isLoading }
                    .map { it.nextLoadPositionUs }.filter { it != C.TIME_END_OF_SOURCE }
                    .fold(mediaNext) { next, optionalNext -> minOf(next, optionalNext) }
            }

            override fun continueLoading(loadingInfo: LoadingInfo): Boolean {
                // A waiting text loader at zero must not prevent A/V from loading ahead of playback.
                val mediaProgress = av.continueLoading(loadingInfo)
                var optionalProgress = false
                for ((loader, _) in optionalEntries) {
                    if (loader.nextLoadPositionUs != C.TIME_END_OF_SOURCE) {
                        val progressed = loader.continueLoading(loadingInfo)
                        optionalProgress = optionalProgress || progressed
                    }
                }
                return mediaProgress || optionalProgress
            }
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
