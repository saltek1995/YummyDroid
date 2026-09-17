package me.yummydroid.app.ui

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import java.net.SocketTimeoutException

/** Resume a paused CVH MP4 response through Media3's saved byte position. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class CvhProgressiveDataSource(
    private val delegate: DataSource,
    private val mediaUri: String,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
    private val idleTimeoutMs: Long = 20_000L,
) : DataSource by delegate {
    private var openedSpec: DataSpec? = null
    private var lastReadAtMs: Long? = null
    private var remaining = C.LENGTH_UNSET.toLong()

    override fun open(dataSpec: DataSpec): Long {
        openedSpec = null
        lastReadAtMs = null
        remaining = delegate.open(dataSpec)
        if (dataSpec.uri.toString() == mediaUri) openedSpec = dataSpec
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val spec = openedSpec
        val lastRead = lastReadAtMs
        if (spec != null && remaining != 0L && lastRead != null &&
            elapsedRealtime() - lastRead >= idleTimeoutMs) {
            // Do not consume potentially stale response bytes after a full-buffer pause.
            // Media3 closes this response and resumes the same URL at the extractor's
            // committed position, preserving queued audio/video and the MediaItem.
            throw HttpDataSource.HttpDataSourceException(
                SocketTimeoutException("CVH progressive response idle during buffer pause"),
                spec,
                HttpDataSource.HttpDataSourceException.TYPE_READ,
            )
        }
        return delegate.read(buffer, offset, length).also { count ->
            if (count > 0) {
                lastReadAtMs = elapsedRealtime()
                if (remaining != C.LENGTH_UNSET.toLong()) remaining -= count
            } else if (count == C.RESULT_END_OF_INPUT) remaining = 0L
        }
    }

    override fun close() {
        openedSpec = null
        lastReadAtMs = null
        remaining = C.LENGTH_UNSET.toLong()
        delegate.close()
    }
}
