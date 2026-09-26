package me.yummydroid.app.ui

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.TransferListener
import java.io.IOException
import java.io.InterruptedIOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import me.yummydroid.app.data.DeferredPlaybackSubtitle

/** Media3's lazy text period calls this on its own loader, independently of audio/video. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class DeferredSubtitleDataSource(
    private val upstream: DataSource,
    private val subtitles: Map<String, DeferredPlaybackSubtitle>,
) : DataSource {
    private val listeners = mutableListOf<TransferListener>()
    private var active: DataSource = upstream
    @Volatile private var loading: Job? = null

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val subtitle = subtitles[dataSpec.uri.toString()] ?: return upstream.open(dataSpec)
        val job = Job()
        loading = job
        val localUri = try {
            // Loader.cancelLoading interrupts this thread; runBlocking cancels the HTTP coroutine.
            runBlocking(job) { subtitle.resolve() }
        } catch (failure: InterruptedException) {
            throw InterruptedIOException("Subtitle load cancelled").apply { initCause(failure) }
        } catch (failure: CancellationException) {
            throw InterruptedIOException("Subtitle load cancelled").apply { initCause(failure) }
        } finally {
            loading = null
            job.cancel()
        }
        if (localUri != null && Uri.parse(localUri).scheme == "file") {
            active = FileDataSource().also { source -> listeners.forEach(source::addTransferListener) }
            try {
                return active.open(dataSpec.withUri(Uri.parse(localUri)))
            } catch (_: IOException) {
                // Cache clearing may race selection. An optional caption must not fail the video.
                active.close()
            }
        }
        active = ByteArrayDataSource("WEBVTT\n\n".toByteArray()).also { source ->
            listeners.forEach(source::addTransferListener)
        }
        return active.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = active.read(buffer, offset, length)
    override fun getUri(): Uri? = active.uri
    override fun getResponseHeaders(): Map<String, List<String>> = active.responseHeaders
    override fun close() {
        loading?.cancel()
        active.close()
        active = upstream
    }
}
