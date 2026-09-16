package me.yummydroid.app.ui

import java.io.IOException
import kotlin.test.*
import me.yummydroid.app.data.*
import okhttp3.OkHttpClient

class PlaybackSessionBindingTest {
    @Test
    fun eachOpenUsesFreshTokenFromItsOwnLeaseWithoutRecreatingIt() {
        val descriptor = FakeDescriptor()
        val stream = stream(descriptor)
        val factory = StreamHttpDataSourceFactory(OkHttpClient(), stream)
        assertEquals(0, descriptor.opens)
        assertNull(factory.update(stream))
        val resolve = factory.requestHeadersResolver()
        assertEquals("one", resolve(stream.url, emptyMap())["Accepts-Controls"])
        descriptor.session.token = "two"
        val second = resolve(stream.url, mapOf("Range" to "bytes=1024-2047", "accepts-controls" to "stale"))
        assertEquals("two", second["Accepts-Controls"])
        assertEquals(1, second.keys.count { it.equals("Accepts-Controls", true) })
        assertEquals("bytes=1024-2047", second["Range"])
        assertNull(factory.update(stream.copy(subtitles = listOf(ResolvedSubtitleTrack("file:///subtitle.vtt")))))
        assertEquals(1, descriptor.opens)
        factory.close()
        factory.close()
        assertEquals(1, descriptor.session.closes)
    }

    @Test
    fun oldRequestCannotBorrowNewSourcesCredentialsAndMetadataDoesNotCloseLease() {
        val first = FakeDescriptor()
        val second = FakeDescriptor()
        val factory = StreamHttpDataSourceFactory(OkHttpClient(), stream(first))
        factory.update(stream(first))
        val oldRequest = factory.requestHeadersResolver()
        val retired = factory.update(stream(second))
        second.session.token = "new-source"
        assertSame(first.session, retired)
        assertEquals("one", oldRequest("https://media.test/segment.m4s", emptyMap())["Accepts-Controls"])
        assertEquals("new-source", factory.requestHeadersResolver()("https://media.test/segment.m4s", emptyMap())["Accepts-Controls"])
        retired?.close()
        assertFailsWith<IOException> { oldRequest("https://media.test/segment.m4s", emptyMap()) }
        factory.close()
        assertEquals(1, first.session.closes)
        assertEquals(1, second.session.closes)
    }

    @Test
    fun unrelatedHostDoesNotReceiveCapturedSessionCredentials() {
        val descriptor = FakeDescriptor()
        val factory = StreamHttpDataSourceFactory(OkHttpClient(), stream(descriptor))
        factory.update(stream(descriptor))
        val headers = factory.requestHeadersResolver()("https://unrelated.test/subtitle.vtt", emptyMap())
        assertFalse(headers.keys.any { it.equals("Accepts-Controls", true) || it.equals("Authorizations", true) })
        factory.close()
    }

    private fun stream(descriptor: PlaybackSessionDescriptor) = ResolvedVideoStream(
        "https://media.test/master.m3u8", "application/x-mpegURL",
        mapOf("accepts-controls" to "captured-old", "Authorizations" to "Bearer old"),
        sessionDescriptor = descriptor,
    )
    private class FakeDescriptor : PlaybackSessionDescriptor {
        val session = FakeSession()
        var opens = 0
        override fun open(client: OkHttpClient): PlaybackRuntimeSession { opens++; return session }
    }
    private class FakeSession : PlaybackRuntimeSession {
        var token = "one"
        var closes = 0
        override fun requestHeaders(url: String): Map<String, String> {
            if (closes > 0) throw IOException("closed")
            return if (url.startsWith("https://media.test/")) mapOf("Accepts-Controls" to token) else emptyMap()
        }
        override fun update(state: PlaybackRuntimeState) {}
        override fun seek(positionMs: Long) {}
        override fun ended() {}
        override fun close() { closes++ }
    }
}
