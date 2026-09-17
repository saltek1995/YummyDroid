package me.yummydroid.app.ui

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.extractor.DefaultExtractorInput
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@OptIn(UnstableApi::class)
class CvhProgressiveExtractorResumeTest {
    private val uri = "https://cvh.test/episode.mp4?token=signed"
    private val bytes = byteArrayOf(0, 0, 0, 3, 0x65, 0x21, 0x43)

    @Test
    fun partialNalPrefixTimeoutLeavesExtractorPositionForCleanResume() {
        var clockReads = 0
        val delegate = OneByteDataSource(bytes)
        val source = CvhProgressiveDataSource(
            delegate,
            uri,
            elapsedRealtime = { if (clockReads++ == 0) 0L else 20_001L },
            idleTimeoutMs = 20_000L,
        )
        source.open(spec(0))
        val input = DefaultExtractorInput(source, 0, bytes.size.toLong())
        val prefix = byteArrayOf(-1, -1, -1, -1)

        assertFailsWith<HttpDataSource.HttpDataSourceException> { input.readFully(prefix, 0, prefix.size) }
        assertEquals(0, input.position)
        assertEquals(1, delegate.position)
        assertContentEquals(byteArrayOf(0, -1, -1, -1), prefix)

        source.close()
        source.open(spec(input.position))
        val resumed = DefaultExtractorInput(source, input.position, bytes.size.toLong())
        val actual = ByteArray(bytes.size)
        resumed.readFully(actual, 0, actual.size)

        assertContentEquals(bytes, actual)
        assertEquals(bytes.size.toLong(), resumed.position)
    }

    @Test
    fun timeoutAfterPeekedPrefixLeavesLogicalPositionForCleanResume() {
        var clock = 0L
        val delegate = OneByteDataSource(bytes)
        val source = CvhProgressiveDataSource(delegate, uri, { clock }, idleTimeoutMs = 20_000L)
        source.open(spec(0))
        val input = DefaultExtractorInput(source, 0, bytes.size.toLong())
        val peeked = ByteArray(2)
        input.peekFully(peeked, 0, peeked.size)
        assertContentEquals(byteArrayOf(0, 0), peeked)
        assertEquals(0, input.position)
        assertEquals(2, input.peekPosition)
        assertEquals(2, delegate.position)
        clock = 20_001L
        val prefix = byteArrayOf(-1, -1, -1, -1)

        assertFailsWith<HttpDataSource.HttpDataSourceException> { input.readFully(prefix, 0, prefix.size) }
        assertEquals(0, input.position)
        assertEquals(2, delegate.position)
        assertContentEquals(byteArrayOf(0, 0, -1, -1), prefix)

        source.close()
        source.open(spec(input.position))
        val resumed = DefaultExtractorInput(source, input.position, bytes.size.toLong())
        val actual = ByteArray(bytes.size)
        resumed.readFully(actual, 0, actual.size)

        assertContentEquals(bytes, actual)
        assertEquals(bytes.size.toLong(), resumed.position)
    }

    private fun spec(position: Long) = DataSpec.Builder().setUri(Uri.parse(uri)).setPosition(position).build()

    private class OneByteDataSource(private val bytes: ByteArray) : DataSource {
        var position = 0

        override fun addTransferListener(transferListener: TransferListener) = Unit

        override fun open(dataSpec: DataSpec): Long {
            position = dataSpec.position.toInt()
            return (bytes.size - position).toLong()
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position == bytes.size) return C.RESULT_END_OF_INPUT
            buffer[offset] = bytes[position++]
            return 1
        }

        override fun getUri(): Uri? = null

        override fun getResponseHeaders(): Map<String, List<String>> = emptyMap()

        override fun close() = Unit
    }
}
