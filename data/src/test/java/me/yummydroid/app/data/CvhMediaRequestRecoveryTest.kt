package me.yummydroid.app.data

import java.io.IOException
import java.net.InetAddress
import java.net.ProtocolException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import okio.GzipSink
import okio.buffer

class CvhMediaRequestRecoveryTest {
    @Test
    fun failedIpRouteRecoversOnOriginalHostBeforeProviderFailover() {
        MockWebServer().use { server ->
            server.start(InetAddress.getByName("127.0.0.1"), 0)
            server.enqueue(MockResponse().setBody("media"))
            server.enqueue(MockResponse().setBody("next"))
            val lookedUpHosts = mutableListOf<String>()
            val baseClient = OkHttpClient.Builder()
                .dns(object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> {
                        lookedUpHosts += hostname
                        return if (hostname == "primary.test") listOf(
                            InetAddress.getByName("127.0.0.2"),
                            InetAddress.getByName("127.0.0.1"),
                        ) else listOf(InetAddress.getByName("127.0.0.1"))
                    }
                })
                .connectTimeout(1, TimeUnit.SECONDS)
                .readTimeout(1, TimeUnit.SECONDS)
                .build()
            val client = CvhMediaRequestRecovery("primary.test", "fallback.test").createClient(baseClient)
            client.newCall(request(server)).execute().use { assertEquals("media", it.body!!.string()) }
            client.newCall(request(server, "/next")).execute().use { assertEquals("next", it.body!!.string()) }
            assertEquals(listOf("primary.test"), lookedUpHosts)
            assertEquals(2, server.requestCount)
            repeat(2) { assertEquals("primary.test:${server.port}", server.takeRequest().getHeader("Host")) }
        }
    }

    @Test
    fun advertisedHostRecoveryPreservesRangeSignedUrlAndHeadersAndStaysSticky() {
        for (status in listOf(403, 404, 408, 500, 502, 503, 504)) {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setResponseCode(status).setBody("failed"))
                server.enqueue(MockResponse().setBody("first"))
                server.enqueue(MockResponse().setBody("audio"))
                val client = client(CvhMediaRequestRecovery("primary.test", "fallback.test"))
                val request = request(server, "/video/a%2Fb.m4s?token=x%2By&track=video").newBuilder()
                    .header("Range", "bytes=123-456").header("Referer", "https://player.test/")
                    .header("X-Playback", "preserved").build()
                client.newCall(request).execute().use { assertEquals("first", it.body!!.string()) }
                client.newCall(request(server, "/audio/segment.m4s?track=audio")).execute().close()
                val primary = server.takeRequest()
                val fallback = server.takeRequest()
                assertEquals("primary.test:${server.port}", primary.getHeader("Host"))
                assertEquals("fallback.test:${server.port}", fallback.getHeader("Host"))
                assertEquals(primary.path, fallback.path)
                assertEquals("bytes=123-456", fallback.getHeader("Range"))
                assertEquals("https://player.test/", fallback.getHeader("Referer"))
                assertEquals("preserved", fallback.getHeader("X-Playback"))
                assertEquals("fallback.test:${server.port}", server.takeRequest().getHeader("Host"))
                assertEquals(3, server.requestCount)
            }
        }
    }

    @Test
    fun restrictionsNeverVisitFailover() {
        for ((status, retryAfter) in listOf(429 to null, 503 to "120", 403 to "120", 200 to "120")) {
            MockWebServer().use { server ->
                val response = MockResponse().setResponseCode(status).setBody("restricted")
                if (retryAfter != null) response.setHeader("Retry-After", retryAfter)
                server.enqueue(response)
                val client = client(CvhMediaRequestRecovery("primary.test", "fallback.test"))
                client.newCall(request(server)).execute().use { assertEquals(status, it.code) }
                assertEquals(1, server.requestCount)
            }
        }
    }

    @Test
    fun networkFailureRetriesOnceButNeverCyclesFromFallback() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            server.enqueue(MockResponse().setResponseCode(502).setBody("fallback failed"))
            server.enqueue(MockResponse().setBody("next request"))
            val client = client(CvhMediaRequestRecovery("primary.test", "fallback.test"))
            client.newCall(request(server)).execute().use { assertEquals(502, it.code) }
            client.newCall(request(server, "/next")).execute().use { assertEquals("next request", it.body!!.string()) }
            server.takeRequest()
            assertEquals("fallback.test:${server.port}", server.takeRequest().getHeader("Host"))
            assertEquals("fallback.test:${server.port}", server.takeRequest().getHeader("Host"))
            assertEquals(3, server.requestCount)
        }
    }

    @Test
    fun partialBodyFailureOnlyChangesNextRequestAndPreservesItsResumeRange() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("abcdef").setHeader("Content-Length", "20")
                .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END))
            server.enqueue(MockResponse().setResponseCode(206).setBody("remaining"))
            val client = client(CvhMediaRequestRecovery("primary.test", "fallback.test"))
            val received = Buffer()
            client.newCall(request(server)).execute().use { response ->
                assertFailsWith<IOException> {
                    while (response.body!!.source().read(received, 3L) != -1L) Unit
                }
            }
            assertEquals(6L, received.size)
            assertEquals(1, server.requestCount)
            val resumed = request(server).newBuilder().header("Range", "bytes=${received.size}-").build()
            client.newCall(resumed).execute().use { assertEquals(206, it.code) }
            server.takeRequest()
            val fallback = server.takeRequest()
            assertEquals("fallback.test:${server.port}", fallback.getHeader("Host"))
            assertEquals("bytes=6-", fallback.getHeader("Range"))
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun cancellationTlsAndProtocolFailuresAreNeverRetried() {
        for (kind in listOf("cancel", "wrapped cancellation", "tls", "protocol")) {
            var attempts = 0
            val client = client(CvhMediaRequestRecovery("primary.test", "fallback.test")).newBuilder()
                .addInterceptor { chain ->
                    attempts++
                    when (kind) {
                        "cancel" -> { chain.call().cancel(); throw IOException("Canceled") }
                        "wrapped cancellation" -> throw IOException(java.util.concurrent.CancellationException())
                        "tls" -> throw SSLHandshakeException("certificate failure")
                        else -> throw ProtocolException("invalid protocol")
                    }
                }.build()
            assertFailsWith<IOException> {
                client.newCall(Request.Builder().url("http://primary.test/media").build()).execute()
            }
            assertEquals(1, attempts)
        }
    }

    @Test
    fun unrelatedHostsNonGetAndIdenticalHostsDoNotRecover() {
        MockWebServer().use { server ->
            val client = client(CvhMediaRequestRecovery("primary.test", "fallback.test"))
            server.enqueue(MockResponse().setResponseCode(503))
            client.newCall(request(server).newBuilder().url(server.url("/other")).build()).execute().close()
            server.enqueue(MockResponse().setResponseCode(503))
            client.newCall(request(server).newBuilder().head().build()).execute().close()
            server.enqueue(MockResponse().setResponseCode(503))
            client(CvhMediaRequestRecovery("primary.test", "PRIMARY.TEST"))
                .newCall(request(server)).execute().close()
            assertEquals(3, server.requestCount)
        }
    }

    @Test
    fun sharedInterceptorCarriesRouteAcrossClientsButNewGenerationStartsOnPrimary() {
        MockWebServer().use { server ->
            val descriptor = CvhMediaRequestRecovery("primary.test", "fallback.test")
            val first = client(descriptor)
            server.enqueue(MockResponse().setResponseCode(403))
            server.enqueue(MockResponse().setBody("recovered"))
            first.newCall(request(server)).execute().close()
            server.enqueue(MockResponse().setBody("shared"))
            first.newBuilder().build().newCall(request(server)).execute().close()
            server.enqueue(MockResponse().setBody("new generation"))
            client(descriptor).newCall(request(server)).execute().close()
            val hosts = (1..4).map { server.takeRequest().getHeader("Host") }
            assertEquals(listOf("primary.test", "fallback.test", "fallback.test", "primary.test"),
                hosts.map { it!!.substringBefore(':') })
        }
    }

    @Test
    fun monitoredBodyPreservesTransparentGzipDecoding() {
        MockWebServer().use { server ->
            val encoded = Buffer()
            GzipSink(encoded).buffer().use { it.writeUtf8("decoded media bytes") }
            server.enqueue(MockResponse().setBody(encoded).setHeader("Content-Encoding", "gzip"))
            client(CvhMediaRequestRecovery("primary.test", "fallback.test"))
                .newCall(request(server)).execute().use {
                    assertEquals("decoded media bytes", it.body!!.string())
                }
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun signedAccessCodesStopOnPrimaryAndPreserveActualStatusAndReason() {
        for (reason in listOf(1, 8, 10, 11, 18, 21)) {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setResponseCode(403).setBody(" $reason\n")
                    .setHeader("Retry-After", "120"))
                val startedAt = System.currentTimeMillis()
                val client = client(CvhMediaRequestRecovery("primary.test", "fallback.test"))
                val failure = assertFailsWith<CvhMediaAccessException> {
                    client.newCall(request(server)).execute()
                }
                assertEquals(403, failure.responseCode)
                assertEquals(reason, failure.reasonCode)
                assertEquals(if (reason == 8) 429 else 403, failure.statusCode)
                kotlin.test.assertTrue(failure.retryAtEpochMs!! >= startedAt + 120_000L)
                assertEquals(1, server.requestCount)
                assertEquals("primary.test:${server.port}", server.takeRequest().getHeader("Host"))
            }
        }
    }

    @Test
    fun floodCodeIsRecognizedOnImmediateFallbackAndLaterStickyRequests() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(502).setBody("upstream unavailable"))
            server.enqueue(MockResponse().setResponseCode(403).setBody("8"))
            server.enqueue(MockResponse().setResponseCode(503).setBody("8"))
            val client = client(CvhMediaRequestRecovery("primary.test", "fallback.test"))
            val first = assertFailsWith<CvhMediaAccessException> {
                client.newCall(request(server)).execute()
            }
            assertEquals(403, first.responseCode)
            assertEquals(8, first.reasonCode)
            assertEquals(429, first.statusCode)
            assertEquals(2, server.requestCount)
            // The controller normally enforces cooldown. Exercise a direct later transport call to
            // ensure its already-sticky branch still classifies the response.
            val later = assertFailsWith<CvhMediaAccessException> {
                client.newCall(request(server, "/audio/next")).execute()
            }
            assertEquals(503, later.responseCode)
            assertEquals(8, later.reasonCode)
            assertEquals(429, later.statusCode)
            assertEquals(3, server.requestCount)
            assertEquals(listOf("primary.test", "fallback.test", "fallback.test"),
                (1..3).map { server.takeRequest().getHeader("Host")!!.substringBefore(':') })
        }
    }

    @Test
    fun directAdvertisedFallbackClassifiesAccessErrorsWithoutRetry() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(403).setBody("8"))
            val client = client(CvhMediaRequestRecovery("primary.test", "fallback.test"))
            val direct = request(server).newBuilder().url(server.url("/media")
                .newBuilder().host("fallback.test").build()).build()
            val failure = assertFailsWith<CvhMediaAccessException> { client.newCall(direct).execute() }
            assertEquals(8, failure.reasonCode)
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun htmlJsonAndLongNumericLookingPrefixesAreNotAccessCodes() {
        for (body in listOf("<html>8</html>", "{\"code\":8}", "\"8\"", "8.0", "80", "+8", "08",
            "8" + " ".repeat(63) + "<html>unrelated upstream failure</html>")) {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setResponseCode(403).setBody(body))
                server.enqueue(MockResponse().setBody("recovered"))
                val client = client(CvhMediaRequestRecovery("primary.test", "fallback.test"))
                client.newCall(request(server)).execute().use { assertEquals("recovered", it.body!!.string()) }
                assertEquals(2, server.requestCount)
            }
        }
    }

    private fun client(descriptor: CvhMediaRequestRecovery) = OkHttpClient.Builder()
        .dns(object : Dns {
            override fun lookup(hostname: String) = listOf(InetAddress.getByName("127.0.0.1"))
        })
        .retryOnConnectionFailure(false)
        .readTimeout(1, TimeUnit.SECONDS)
        .addInterceptor(descriptor.createInterceptor()).build()

    private fun request(server: MockWebServer, path: String = "/media?token=signed") = Request.Builder()
        .url(server.url(path).newBuilder().host("primary.test").build()).build()
}
