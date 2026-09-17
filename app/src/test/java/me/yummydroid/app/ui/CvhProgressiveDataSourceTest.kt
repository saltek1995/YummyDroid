package me.yummydroid.app.ui

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class CvhProgressiveDataSourceTest {
    private val mediaUri = "https://cvh.test/episode.mp4?token=signed"

    @Test
    fun idleAfterProgressThrowsBeforeReadingOrClosingDelegate() {
        var clock = 0L
        val delegate = FakeDataSource(byteArrayOf(1, 2, 3))
        val source = CvhProgressiveDataSource(delegate, mediaUri, { clock }, idleTimeoutMs = 20_000)
        source.open(spec(mediaUri))
        assertEquals(1, source.read(ByteArray(1), 0, 1))
        clock += 20_001

        assertFailsWith<HttpDataSource.HttpDataSourceException> { source.read(ByteArray(1), 0, 1) }
        assertEquals(1, delegate.readCalls)
        assertEquals(0, delegate.closeCalls)
    }

    @Test
    fun continuousReadsRefreshIdleDeadlineBeyondCumulativeTimeout() {
        var clock = 0L
        val delegate = FakeDataSource(byteArrayOf(1, 2, 3, 4))
        val source = CvhProgressiveDataSource(delegate, mediaUri, { clock }, idleTimeoutMs = 20_000)
        source.open(spec(mediaUri))

        repeat(4) {
            assertEquals(1, source.read(ByteArray(1), 0, 1))
            clock += 10_001
        }

        assertEquals(4, delegate.readCalls)
    }

    @Test
    fun otherUriDoesNotEnableIdleGuard() {
        var clock = 0L
        val delegate = FakeDataSource(byteArrayOf(1, 2))
        val source = CvhProgressiveDataSource(delegate, mediaUri, { clock }, idleTimeoutMs = 20_000)
        source.open(spec("https://cvh.test/sibling.mp4?token=signed"))
        source.read(ByteArray(1), 0, 1)
        clock += 20_001

        assertEquals(1, source.read(ByteArray(1), 0, 1))
        assertEquals(2, delegate.readCalls)
    }

    @Test
    fun knownLengthFullyReadReturnsEndOfInputWithoutIdleGuard() {
        var clock = 0L
        val delegate = FakeDataSource(byteArrayOf(1))
        val source = CvhProgressiveDataSource(delegate, mediaUri, { clock }, idleTimeoutMs = 20_000)
        source.open(spec(mediaUri))
        assertEquals(1, source.read(ByteArray(1), 0, 1))
        clock += 20_001

        assertEquals(C.RESULT_END_OF_INPUT, source.read(ByteArray(1), 0, 1))
    }

    @Test
    fun closeAndOpenClearIdleState() {
        var clock = 0L
        val delegate = FakeDataSource(byteArrayOf(1, 2))
        val source = CvhProgressiveDataSource(delegate, mediaUri, { clock }, idleTimeoutMs = 20_000)
        source.open(spec(mediaUri))
        source.read(ByteArray(1), 0, 1)
        source.close()
        clock += 20_001
        source.open(spec(mediaUri))

        assertEquals(1, source.read(ByteArray(1), 0, 1))
        assertEquals(1, delegate.closeCalls)
    }

    private fun spec(uri: String) = DataSpec(Uri.parse(uri))

    private class FakeDataSource(private val bytes: ByteArray) : DataSource {
        var readCalls = 0
        var closeCalls = 0
        private var position = 0

        override fun addTransferListener(transferListener: TransferListener) = Unit

        override fun open(dataSpec: DataSpec): Long {
            position = dataSpec.position.toInt()
            return (bytes.size - position).toLong()
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            readCalls++
            if (position == bytes.size) return C.RESULT_END_OF_INPUT
            val count = minOf(length, bytes.size - position)
            bytes.copyInto(buffer, offset, position, position + count)
            position += count
            return count
        }

        override fun getUri(): Uri? = null

        override fun getResponseHeaders(): Map<String, List<String>> = emptyMap()

        override fun close() {
            closeCalls++
        }
    }
}
