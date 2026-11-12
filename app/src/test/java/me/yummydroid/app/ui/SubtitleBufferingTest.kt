package me.yummydroid.app.ui

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.LoadingInfo
import androidx.media3.exoplayer.source.SequenceableLoader
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = android.app.Application::class)
class SubtitleBufferingTest {
    @Test fun waitingCaptionCannotStarveFurtherAudioBuffering() {
        val audio = Loader(905_000)
        val text = Loader(0)
        val composite = AvBufferingLoaderFactory().create(listOf(audio, text),
            listOf(listOf(C.TRACK_TYPE_AUDIO), listOf(C.TRACK_TYPE_TEXT)))

        composite.continueLoading(LoadingInfo.Builder().setPlaybackPositionUs(0).build())

        assertEquals(1, audio.continuations)
        assertEquals(1, text.continuations)
        assertEquals(905_000, composite.bufferedPositionUs)
        assertEquals(905_000, composite.nextLoadPositionUs)
    }

    @Test fun waitingCaptionPreservesBackpressureBetweenAudioAndVideo() {
        val audio = Loader(9_000)
        val video = Loader(5_000)
        val text = Loader(0)
        val composite = AvBufferingLoaderFactory().create(listOf(audio, video, text),
            listOf(listOf(C.TRACK_TYPE_AUDIO), listOf(C.TRACK_TYPE_VIDEO), listOf(C.TRACK_TYPE_TEXT)))

        composite.continueLoading(LoadingInfo.Builder().setPlaybackPositionUs(0).build())

        assertEquals(0, audio.continuations)
        assertEquals(1, video.continuations)
        assertEquals(1, text.continuations)
        assertEquals(5_000, composite.nextLoadPositionUs)
    }

    @Test fun dormantSidecarStillStartsWhileAnotherCaptionIsWaiting() {
        val audio = Loader(9_000)
        val waiting = Loader(0).apply { makesProgress = true }
        val dormant = Loader(1_000).apply { loadingOverride = false }
        val completed = Loader(C.TIME_END_OF_SOURCE)
        val composite = AvBufferingLoaderFactory().create(listOf(audio, waiting, dormant, completed),
            listOf(listOf(C.TRACK_TYPE_AUDIO), listOf(C.TRACK_TYPE_TEXT), listOf(C.TRACK_TYPE_TEXT), listOf(C.TRACK_TYPE_TEXT)))

        assertEquals(1_000, composite.nextLoadPositionUs)
        assertTrue(composite.continueLoading(LoadingInfo.Builder().setPlaybackPositionUs(0).build()))
        assertEquals(1, waiting.continuations)
        assertEquals(1, dormant.continuations)
        assertEquals(0, completed.continuations)
        dormant.loadingOverride = true
        assertEquals(9_000, composite.nextLoadPositionUs)
        audio.buffered = C.TIME_END_OF_SOURCE
        assertEquals(0, composite.nextLoadPositionUs)
    }

    @Test fun completedAudioDoesNotRetainZeroBufferWhileCaptionIsLoading() {
        val audio = Loader(0)
        val text = Loader(0)
        val composite = AvBufferingLoaderFactory().create(listOf(audio, text),
            listOf(listOf(C.TRACK_TYPE_AUDIO), listOf(C.TRACK_TYPE_TEXT)))
        assertEquals(0, composite.bufferedPositionUs)
        audio.buffered = C.TIME_END_OF_SOURCE
        assertEquals(C.TIME_END_OF_SOURCE, composite.bufferedPositionUs)
        assertEquals(0, composite.nextLoadPositionUs)
        assertTrue(composite.isLoading)
        composite.continueLoading(LoadingInfo.Builder().setPlaybackPositionUs(0).build())
        assertEquals(1, text.continuations)
        assertEquals(0, audio.continuations)
        composite.reevaluateBuffer(123)
        assertEquals(123, text.reevaluatedAt)
    }

    @Test fun initiallyCompletedAudioAndVideoDoNotWaitForCaption() {
        val composite = AvBufferingLoaderFactory().create(
            listOf(Loader(C.TIME_END_OF_SOURCE), Loader(C.TIME_END_OF_SOURCE), Loader(0)),
            listOf(listOf(C.TRACK_TYPE_AUDIO), listOf(C.TRACK_TYPE_VIDEO), listOf(C.TRACK_TYPE_TEXT)))
        assertEquals(C.TIME_END_OF_SOURCE, composite.bufferedPositionUs)
        assertTrue(composite.isLoading)
    }

    @Test fun allUnfinishedAudioAndVideoStillConstrainBuffer() {
        val audio = Loader(9_000)
        val video = Loader(5_000)
        val composite = AvBufferingLoaderFactory().create(listOf(audio, video, Loader(0)),
            listOf(listOf(C.TRACK_TYPE_AUDIO), listOf(C.TRACK_TYPE_VIDEO), listOf(C.TRACK_TYPE_TEXT)))
        assertEquals(5_000, composite.bufferedPositionUs)
        video.buffered = C.TIME_END_OF_SOURCE
        assertEquals(9_000, composite.bufferedPositionUs)
    }

    @Test fun textOnlyAndEmptySourcesRetainTheirBufferingBehavior() {
        val factory = AvBufferingLoaderFactory()
        val text = Loader(0)
        val composite = factory.create(listOf(text), listOf(listOf(C.TRACK_TYPE_TEXT)))
        assertEquals(0, composite.bufferedPositionUs)
        text.buffered = C.TIME_END_OF_SOURCE
        assertEquals(C.TIME_END_OF_SOURCE, composite.bufferedPositionUs)
        assertEquals(C.TIME_END_OF_SOURCE, factory.empty().bufferedPositionUs)
    }

    @Test fun metadataCanUpdateButChangedSidecarsRequireRebuildingSource() {
        val item = MediaItem.Builder().setUri("memory://audio.m4a")
            .setSubtitleConfigurations(listOf(MediaItem.SubtitleConfiguration.Builder(
                android.net.Uri.parse("memory://caption.vtt")).setMimeType("text/vtt").build())).build()
        val source = SubtitleMediaSourceFactory(DataSource.Factory { ByteArrayDataSource(byteArrayOf(1)) })
            .createMediaSource(item)
        assertEquals(item, source.mediaItem)
        val updated = item.buildUpon().setMediaMetadata(MediaMetadata.Builder().setTitle("Updated").build()).build()
        assertTrue(source.canUpdateMediaItem(updated))
        source.updateMediaItem(updated)
        assertEquals(updated, source.mediaItem)
        assertFalse(source.canUpdateMediaItem(updated.buildUpon().setSubtitleConfigurations(emptyList()).build()))
    }

    private class Loader(var buffered: Long) : SequenceableLoader {
        var continuations = 0
        var reevaluatedAt = -1L
        var loadingOverride: Boolean? = null
        var makesProgress = false
        override fun getBufferedPositionUs(): Long = buffered
        override fun getNextLoadPositionUs(): Long = buffered
        override fun isLoading(): Boolean = loadingOverride ?: (buffered != C.TIME_END_OF_SOURCE)
        override fun continueLoading(loadingInfo: LoadingInfo): Boolean { continuations++; return makesProgress }
        override fun reevaluateBuffer(positionUs: Long) { reevaluatedAt = positionUs }
    }
}
