package me.yummydroid.app.data

import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class AllohaPlaybackSessionTest {
    private class Peer : WebSocketListener() {
        val sockets = LinkedBlockingQueue<WebSocket>()
        val messages = LinkedBlockingQueue<String>()
        override fun onOpen(webSocket: WebSocket, response: Response) { sockets.add(webSocket) }
        override fun onMessage(webSocket: WebSocket, text: String) { messages.add(text) }
        fun message() = Json.parseToJsonElement(requireNotNull(messages.poll(3, TimeUnit.SECONDS))).jsonObject
    }

    private fun descriptor(server: MockWebServer, token: String? = "first", expiry: Long? = null) =
        AllohaSessionDescriptor(
            server.url("/socket?sid=captured&v=2.1&t=original&extra=keep").toString(),
            mapOf("Origin" to "https://player.example"),
            """{"type":"playback_start","resolution":"provider-auto","track_id":"provider-audio","subtitle":-1}""",
            token, "guard", expiry, setOf("media.example"),
        )

    @Test fun rotationIsReadOnEveryOpenAndScopedToMediaHost() {
        MockWebServer().use { server ->
            val peer = Peer()
            server.enqueue(MockResponse().withWebSocketUpgrade(peer))
            AllohaPlaybackSession(descriptor(server), OkHttpClient()).use { session ->
                val socket = requireNotNull(peer.sockets.poll(3, TimeUnit.SECONDS))
                assertEquals("first", session.requestHeaders("https://media.example/a")["Accepts-Controls"])
                socket.send("""{"type":"config_update","edge_hash":"second"}""")
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
                while (session.requestHeaders("https://media.example/a")["Accepts-Controls"] != "second" && System.nanoTime() < deadline) Thread.sleep(5)
                assertEquals(mapOf("Accepts-Controls" to "second", "Authorizations" to "Bearer guard"), session.requestHeaders("https://media.example/a"))
                assertTrue(session.requestHeaders("https://media.example.attacker/a").isEmpty())
                socket.send("""{"type":"config_update","edge_hash":""}""")
                Thread.sleep(30)
                assertEquals("second", session.requestHeaders("https://media.example/a")["Accepts-Controls"])
            }
        }
    }

    @Test fun protocolPreservesProviderIdsAndEmitsIntentHeartbeatSeekAndEnd() {
        MockWebServer().use { server ->
            val peer = Peer()
            server.enqueue(MockResponse().withWebSocketUpgrade(peer))
            AllohaPlaybackSession(descriptor(server), OkHttpClient(), now = { 123456L }, heartbeatIntervalMs = 150).use { session ->
                assertEquals("playback_start", peer.message()["type"]?.jsonPrimitive?.content)
                session.update(PlaybackRuntimeState(12_900, true, 1.5f))
                val resumed = peer.message()
                assertEquals("resumed", resumed["type"]?.jsonPrimitive?.content)
                assertEquals("12", resumed["current_time"]?.jsonPrimitive?.content)
                assertEquals("provider-auto", resumed["resolution"]?.jsonPrimitive?.content)
                assertEquals("provider-audio", resumed["track_id"]?.jsonPrimitive?.content)
                assertEquals("1.5", resumed["speed"]?.jsonPrimitive?.content)
                assertEquals("123456", resumed["ts"]?.jsonPrimitive?.content)
                assertEquals("playing", peer.message()["type"]?.jsonPrimitive?.content)
                session.update(PlaybackRuntimeState(13_000, false))
                assertEquals("paused", peer.message()["type"]?.jsonPrimitive?.content)
                assertEquals(null, peer.messages.poll(220, TimeUnit.MILLISECONDS))
                session.seek(32_900)
                val seeked = peer.message()
                assertEquals("seeked", seeked["type"]?.jsonPrimitive?.content)
                assertEquals("32", seeked["current_time"]?.jsonPrimitive?.content)
                session.update(PlaybackRuntimeState(33_000, false, providerResolution = "720", providerAudioId = "17"))
                val providerChanged = peer.message()
                assertEquals("playback_start", providerChanged["type"]?.jsonPrimitive?.content)
                assertEquals("720", providerChanged["resolution"]?.jsonPrimitive?.content)
                assertEquals("17", providerChanged["track_id"]?.jsonPrimitive?.content)
                session.seek(33_000)
                val explicit = peer.message()
                assertEquals("720", explicit["resolution"]?.jsonPrimitive?.content)
                assertEquals("17", explicit["track_id"]?.jsonPrimitive?.content)
                session.ended()
                assertEquals("ended", peer.message()["type"]?.jsonPrimitive?.content)
                session.update(PlaybackRuntimeState(playWhenReady = false))
                assertEquals(null, peer.messages.poll(220, TimeUnit.MILLISECONDS))
            }
        }
    }

    @Test fun reconnectChangesOnlyTimestampAndSendsPlaybackStart() {
        MockWebServer().use { server ->
            val first = Peer()
            val second = Peer()
            server.enqueue(MockResponse().withWebSocketUpgrade(first))
            server.enqueue(MockResponse().withWebSocketUpgrade(second))
            AllohaPlaybackSession(descriptor(server), OkHttpClient(), reconnectBaseMs = 10).use {
                val socket = requireNotNull(first.sockets.poll(3, TimeUnit.SECONDS))
                val original = requireNotNull(server.takeRequest(3, TimeUnit.SECONDS)).requestUrl!!
                assertNotEquals("original", original.queryParameter("t"))
                socket.close(1000, null)
                val next = requireNotNull(server.takeRequest(3, TimeUnit.SECONDS)).requestUrl!!
                assertEquals(original.queryParameter("sid"), next.queryParameter("sid"))
                assertEquals(original.queryParameter("v"), next.queryParameter("v"))
                assertEquals(original.queryParameter("extra"), next.queryParameter("extra"))
                assertNotEquals(original.queryParameter("t"), next.queryParameter("t"))
                assertEquals("playback_start", second.message()["type"]?.jsonPrimitive?.content)
            }
        }
    }

    @Test fun restrictionStopsReconnectAndExposesRetryAfter() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "12"))
            AllohaPlaybackSession(descriptor(server, token = null), OkHttpClient(), now = { 1000 }, reconnectBaseMs = 10).use { session ->
                val error = assertFailsWith<PlaybackSessionRestrictedException> { session.requestHeaders("https://media.example/a") }
                assertEquals(429, error.statusCode)
                assertEquals(13000, error.retryAtEpochMs)
                server.takeRequest(3, TimeUnit.SECONDS)
                assertEquals(null, server.takeRequest(150, TimeUnit.MILLISECONDS))
            }
        }
    }

    @Test fun ordinaryForbiddenHandshakeReconnectsWithoutDiscardingCurrentToken() {
        MockWebServer().use { server ->
            val first = Peer()
            val next = Peer()
            server.enqueue(MockResponse().withWebSocketUpgrade(first))
            server.enqueue(MockResponse().setResponseCode(403))
            server.enqueue(MockResponse().withWebSocketUpgrade(next))
            AllohaPlaybackSession(descriptor(server, token = null), OkHttpClient(), reconnectBaseMs = 100).use { session ->
                val original = requireNotNull(first.sockets.poll(3, TimeUnit.SECONDS))
                original.send("""{"type":"config_update","edge_hash":"tokenA"}""")
                assertEquals("tokenA", session.requestHeaders("https://media.example/a")["Accepts-Controls"])
                requireNotNull(server.takeRequest(3, TimeUnit.SECONDS))
                original.close(1000, null)
                requireNotNull(server.takeRequest(3, TimeUnit.SECONDS)) // Rejected handshake.
                assertEquals(null, next.sockets.poll(50, TimeUnit.MILLISECONDS))
                assertEquals("tokenA", session.requestHeaders("https://media.example/a")["Accepts-Controls"])
                val renewed = requireNotNull(next.sockets.poll(3, TimeUnit.SECONDS))
                renewed.send("""{"type":"config_update","edge_hash":"tokenB"}""")
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
                while (session.requestHeaders("https://media.example/a")["Accepts-Controls"] != "tokenB" && System.nanoTime() < deadline) Thread.sleep(5)
                assertEquals("tokenB", session.requestHeaders("https://media.example/a")["Accepts-Controls"])
                assertEquals(3, server.requestCount)
            }
        }
    }

    @Test fun persistentForbiddenHandshakeExhaustsExistingReconnectLimit() {
        MockWebServer().use { server ->
            repeat(32) { server.enqueue(MockResponse().setResponseCode(403)) }
            AllohaPlaybackSession(descriptor(server, token = null), OkHttpClient(), reconnectBaseMs = 0).use { session ->
                val error = assertFailsWith<IOException> { session.requestHeaders("https://media.example/a") }
                assertEquals("Playback session reconnection limit reached", error.message)
                assertEquals(31, server.requestCount) // Initial request plus 30 reconnect attempts.
                repeat(31) { requireNotNull(server.takeRequest(3, TimeUnit.SECONDS)) }
                assertEquals(null, server.takeRequest(100, TimeUnit.MILLISECONDS))
            }
        }
    }

    @Test fun forbiddenHandshakeWithFutureRetryAfterRemainsRestricted() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(403).setHeader("Retry-After", "12"))
            AllohaPlaybackSession(descriptor(server, token = null), OkHttpClient(), now = { 1000 }, reconnectBaseMs = 0).use { session ->
                val error = assertFailsWith<PlaybackSessionRestrictedException> { session.requestHeaders("https://media.example/a") }
                assertEquals(403, error.statusCode)
                assertEquals(13000, error.retryAtEpochMs)
                requireNotNull(server.takeRequest(3, TimeUnit.SECONDS))
                assertEquals(null, server.takeRequest(100, TimeUnit.MILLISECONDS))
            }
        }
    }

    @Test fun serviceUnavailableWaitsForRetryAfterBeforeReconnecting() {
        MockWebServer().use { server ->
            val peer = Peer()
            server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "1"))
            server.enqueue(MockResponse().withWebSocketUpgrade(peer))
            AllohaPlaybackSession(descriptor(server), OkHttpClient(), reconnectBaseMs = 10).use {
                requireNotNull(server.takeRequest(3, TimeUnit.SECONDS))
                val receivedAt = System.nanoTime()
                assertEquals(null, server.takeRequest(700, TimeUnit.MILLISECONDS))
                requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))
                assertTrue(System.nanoTime() - receivedAt >= TimeUnit.MILLISECONDS.toNanos(950))
                assertEquals("playback_start", peer.message()["type"]?.jsonPrimitive?.content)
            }
        }
    }

    @Test fun expiredMetadataHintKeepsHealthyLeaseAndCloseIsIdempotent() {
        MockWebServer().use { server ->
            val peer = Peer()
            server.enqueue(MockResponse().withWebSocketUpgrade(peer))
            val session = AllohaPlaybackSession(descriptor(server, expiry = 100), OkHttpClient(), now = { 101 })
            assertEquals("playback_start", peer.message()["type"]?.jsonPrimitive?.content)
            assertEquals("first", session.requestHeaders("https://media.example/a")["Accepts-Controls"])
            session.seek(5_400)
            assertEquals("seeked", peer.message()["type"]?.jsonPrimitive?.content)
            session.close()
            session.close()
            assertFailsWith<IOException> { session.requestHeaders("https://media.example/a") }
            assertEquals(1, server.requestCount)
            session.seek(1_000)
            assertEquals(null, peer.messages.poll(100, TimeUnit.MILLISECONDS))
        }
    }

    @Test fun missingTokenFailsWithinBoundAndCloseWakesWaiter() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().withWebSocketUpgrade(Peer()))
            AllohaPlaybackSession(descriptor(server, token = null), OkHttpClient(), initialTokenWaitMs = 30).use { session ->
                assertFailsWith<IOException> { session.requestHeaders("https://media.example/a") }
            }
            server.enqueue(MockResponse().withWebSocketUpgrade(Peer()))
            val session = AllohaPlaybackSession(descriptor(server, token = null), OkHttpClient())
            val result = LinkedBlockingQueue<Throwable>()
            val waiter = Thread { try { session.requestHeaders("https://media.example/a") } catch (error: Throwable) { result.add(error) } }
            waiter.start()
            session.close()
            assertTrue(result.poll(1, TimeUnit.SECONDS) is IOException)
            waiter.join(1000)
        }
    }

    @Test fun waitingForTokenPreservesInterruption() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().withWebSocketUpgrade(Peer()))
            AllohaPlaybackSession(descriptor(server, token = null), OkHttpClient()).use { session ->
                val result = LinkedBlockingQueue<Pair<Throwable, Boolean>>()
                val waiter = Thread {
                    Thread.currentThread().interrupt()
                    try { session.requestHeaders("https://media.example/a") }
                    catch (error: Throwable) { result.add(error to Thread.currentThread().isInterrupted) }
                }
                waiter.start()
                val failure = requireNotNull(result.poll(1, TimeUnit.SECONDS))
                assertTrue(failure.first is InterruptedIOException)
                assertTrue(failure.second)
                waiter.join(1000)
            }
        }
    }

    @Test fun replayAfterEndedRestartsProtocolAndUsesUpdatedPosition() {
        MockWebServer().use { server ->
            val peer = Peer()
            server.enqueue(MockResponse().withWebSocketUpgrade(peer))
            AllohaPlaybackSession(descriptor(server), OkHttpClient(), heartbeatIntervalMs = 150).use { session ->
                peer.message()
                session.ended()
                assertEquals("ended", peer.message()["type"]?.jsonPrimitive?.content)
                session.update(PlaybackRuntimeState(4_900, true))
                val start = peer.message()
                assertEquals("playback_start", start["type"]?.jsonPrimitive?.content)
                assertEquals("4", start["current_time"]?.jsonPrimitive?.content)
                assertEquals("resumed", peer.message()["type"]?.jsonPrimitive?.content)
                val playing = peer.message()
                assertEquals("playing", playing["type"]?.jsonPrimitive?.content)
                assertEquals("4", playing["current_time"]?.jsonPrimitive?.content)
            }
        }
    }

    @Test fun seekBackAfterEndedReconnectsClosedSocket() {
        MockWebServer().use { server ->
            val first = Peer()
            val next = Peer()
            server.enqueue(MockResponse().withWebSocketUpgrade(first))
            server.enqueue(MockResponse().withWebSocketUpgrade(next))
            AllohaPlaybackSession(descriptor(server), OkHttpClient(), heartbeatIntervalMs = 150).use { session ->
                val socket = requireNotNull(first.sockets.poll(3, TimeUnit.SECONDS))
                first.message()
                session.ended()
                first.message()
                socket.close(1000, null)
                Thread.sleep(100)
                session.seek(2_300)
                val start = next.message()
                assertEquals("playback_start", start["type"]?.jsonPrimitive?.content)
                assertEquals("2", start["current_time"]?.jsonPrimitive?.content)
                assertEquals(null, next.messages.poll(200, TimeUnit.MILLISECONDS))
                session.update(PlaybackRuntimeState(2_300, true))
                assertEquals("resumed", next.message()["type"]?.jsonPrimitive?.content)
                assertEquals("playing", next.message()["type"]?.jsonPrimitive?.content)
            }
        }
    }

    @Test fun rapidPausedAndEndedSeeksSendLatestPositionWithoutResuming() {
        MockWebServer().use { server ->
            val peer = Peer()
            server.enqueue(MockResponse().withWebSocketUpgrade(peer))
            AllohaPlaybackSession(descriptor(server), OkHttpClient(), heartbeatIntervalMs = 100).use { session ->
                peer.message()
                session.update(PlaybackRuntimeState(90_000, false))
                val positions = listOf(40_999L, 70_001L, 2_900L, 13_333L)
                positions.forEach(session::seek)
                positions.forEach { expected ->
                    val event = peer.message()
                    assertEquals("seeked", event["type"]?.jsonPrimitive?.content)
                    assertEquals((expected / 1_000).toString(), event["current_time"]?.jsonPrimitive?.content)
                }
                assertEquals(null, peer.messages.poll(150, TimeUnit.MILLISECONDS))
                session.ended()
                assertEquals("ended", peer.message()["type"]?.jsonPrimitive?.content)
                session.seek(999)
                val afterEnd = peer.message()
                assertEquals("seeked", afterEnd["type"]?.jsonPrimitive?.content)
                assertEquals("0", afterEnd["current_time"]?.jsonPrimitive?.content)
                assertEquals(null, peer.messages.poll(150, TimeUnit.MILLISECONDS))
            }
        }
    }
}
