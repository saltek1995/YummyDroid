package me.yummydroid.app.data

import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class DirectDownloadIdentityTest {
    @get:Rule val folder = TemporaryFolder()
    private val client = OkHttpClient()
    private val limiter = object : DownloadBandwidthLimiter {
        override suspend fun throttle(bytes: Long) = Unit
    }

    @Test fun sameResourceResumesWithIfRangeAndRemovesIdentity() = runBlocking {
        MockWebServer().use { server ->
            val session = session()
            val stream = stream(server)
            interrupt(server, session, stream)
            server.enqueue(partial("\"v1\""))
            attempt(session, stream)
            val request = server.takeRequest()
            assertEquals("bytes=3-", request.getHeader("Range"))
            assertEquals("\"v1\"", request.getHeader("If-Range"))
            assertEquals("abcDEF", session.target.readText())
            assertFalse(identity(session).exists())
        }
    }

    @Test fun changedUrlOrHeadersRestartsWithoutRange() = runBlocking {
        for (changeHeaders in listOf(false, true)) {
            MockWebServer().use { server ->
                val session = session()
                val original = stream(server)
                interrupt(server, session, original)
                assertFalse(identity(session).readText().contains("secret"))
                val changed = if (changeHeaders) original.copy(headers = mapOf("Authorization" to "new-secret"))
                    else original.copy(url = server.url("/new.mp4").toString())
                server.enqueue(MockResponse().setBody("new-file").setHeader("ETag", "\"v2\""))
                attempt(session, changed)
                assertNull(server.takeRequest().getHeader("Range"))
                assertEquals("new-file", session.target.readText())
            }
        }
    }

    @Test fun changedOrMissingValidatorRejectsPartialAndStale416() = runBlocking {
        for (code in listOf(206, 416)) for (etag in listOf("\"v2\"", "W/\"v1\"", null)) {
            MockWebServer().use { server ->
                val session = session()
                val stream = stream(server)
                interrupt(server, session, stream)
                val response = if (code == 206) partial(etag) else MockResponse()
                    .setResponseCode(416).setHeader("Content-Range", "bytes */3")
                    .apply { etag?.let { setHeader("ETag", it) } }
                server.enqueue(response)
                expectFailure { attempt(session, stream) }
                server.takeRequest()
                assertFalse(session.target.exists())
                assertFalse(session.temp.exists())
                assertFalse(identity(session).exists())
                server.enqueue(MockResponse().setBody("replacement"))
                attempt(session, stream)
                assertNull(server.takeRequest().getHeader("Range"))
                assertEquals("replacement", session.target.readText())
            }
        }
    }

    @Test fun fullResponseToIfRangeReplacesPartial() = runBlocking {
        MockWebServer().use { server ->
            val session = session()
            val stream = stream(server)
            interrupt(server, session, stream)
            server.enqueue(MockResponse().setBody("replacement").setHeader("ETag", "\"v2\""))
            attempt(session, stream)
            assertEquals("\"v1\"", server.takeRequest().getHeader("If-Range"))
            assertEquals("replacement", session.target.readText())
        }
    }

    @Test fun matchingValidatorAllowsAlreadyComplete416() = runBlocking {
        MockWebServer().use { server ->
            val session = session()
            val stream = stream(server)
            interrupt(server, session, stream)
            server.enqueue(MockResponse().setResponseCode(416)
                .setHeader("Content-Range", "bytes */3").setHeader("ETag", "\"v1\""))
            attempt(session, stream)
            assertEquals("abc", session.target.readText())
            assertFalse(identity(session).exists())
        }
    }

    @Test fun changedRedirectDestinationRejectsSameEtag() = runBlocking {
        MockWebServer().use { server ->
            val session = session()
            val stream = stream(server)
            interrupt(server, session, stream)
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/different.mp4"))
            server.enqueue(partial("\"v1\""))
            expectFailure { attempt(session, stream) }
            assertFalse(session.target.exists())
            assertFalse(session.temp.exists())
        }
    }

    @Test fun missingIdentityOrWeakEtagRestartsPartial() = runBlocking {
        for (etag in listOf(null, "W/\"v1\"")) {
            MockWebServer().use { server ->
                val session = session()
                val stream = stream(server)
                interrupt(server, session, stream, etag)
                server.enqueue(MockResponse().setBody("replacement"))
                attempt(session, stream)
                assertNull(server.takeRequest().getHeader("Range"))
                assertEquals("replacement", session.target.readText())
            }
        }
    }

    private fun session() = DirectDownloadSession(File(folder.newFolder(), "video.mp4"), "", "")
    private fun stream(server: MockWebServer) = ResolvedVideoStream(
        server.url("/video.mp4").toString(), "video/mp4", mapOf("Authorization" to "secret"),
    )
    private fun identity(session: DirectDownloadSession) = File(session.temp.parentFile, "${session.temp.name}.identity")
    private fun partial(etag: String?) = MockResponse().setResponseCode(206)
        .setHeader("Content-Range", "bytes 3-5/6").setBody("DEF")
        .apply { etag?.let { setHeader("ETag", it) } }

    private suspend fun attempt(session: DirectDownloadSession, stream: ResolvedVideoStream) {
        client.downloadDirectVideoAttempt(session, stream, {}, { false }, limiter)
    }

    private suspend fun interrupt(
        server: MockWebServer, session: DirectDownloadSession, stream: ResolvedVideoStream, etag: String? = "\"v1\"",
    ) {
        server.enqueue(MockResponse().setChunkedBody("abc", 3).apply { etag?.let { setHeader("ETag", it) } })
        expectFailure {
            client.downloadDirectVideoAttempt(session, stream, { throw IOException("Interrupted") }, { false }, limiter)
        }
        server.takeRequest()
        assertEquals("abc", session.temp.readText())
    }

    private suspend fun expectFailure(block: suspend () -> Unit) {
        var failed = false
        try { block() } catch (_: IOException) { failed = true }
        assertTrue(failed, "Expected failed download")
    }
}
