package me.yummydroid.app.data

import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy

class AllohaHttpTelemetryTest {
    private class Clock(var at: Long = 1_000_000) {
        fun advance(ms: Long) { at += ms }
    }

    private fun json(text: String) = Json.parseToJsonElement(text).jsonObject
    private fun server(code: Int = 200) = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(code).setBody("{}")
        }
        start()
    }

    private fun descriptor(server: MockWebServer, captured: Boolean = true) = AllohaHttpTelemetryDescriptor(
        providerPageUrl = server.url("/embed/episode?session=private-referrer").toString(),
        headers = mapOf("User-Agent" to "captured-browser", "Cookie" to "SID=private-cookie",
            "Sec-Fetch-Mode" to "cors", "Content-Type" to "obsolete-content-type"),
        token = "private-http-token", domain = "captured-domain", fileId = "file-42",
        environment = json("""{"connectionType":"4g","downlink":9.1,"pixelRatio":2,"screenW":960,"screenH":540}"""),
        statInfo = json("""{"wasm":true,"sw":false,"resolution":{"screenWidth":960,"screenHeight":540,"windowWidth":1280,"windowHeight":720,"devicePixelRatio":2},"webSocket":true,"platform":"Linux x86_64"}"""),
        initialEnvelope = if (captured) json("""{"playerInitAt":1000000,"clientSessionId":"captured-session-id","clientRequestId":"captured-request-id"}""") else null,
        observedEvents = if (captured) listOf(
            json("""{"event_type":"view_start","at":999000,"paused":false}"""),
            json("""{"event_type":"first_frame","at":999100,"paused":false}"""),
            json("""{"event_type":"playback_ready","at":999100,"paused":false}"""),
            json("""{"event_type":"heartbeat","at":999200,"playedSecondsDelta":5.0,"bytesLoadedTotal":1000,"droppedFrames":3,"rebufferCountSession":2,"rebufferTotalMsSession":400,"paused":false}"""),
            json("""{"event_type":"view_percent","percent":0,"watchTimeSec":0,"paused":false}"""),
            json("""{"event_type":"view_percent","percent":10,"watchTimeSec":10,"paused":false}"""),
        ) else emptyList(),
        observedStatPercents = if (captured) setOf(0, 10) else emptySet(),
        initialStatSent = captured, pageEpochMs = 990000, adBlock = false,
    )

    private fun playing(position: Long = 10000) = PlaybackRuntimeState(
        positionMs = position, durationMs = 100000, bufferedDurationMs = 12500,
        playWhenReady = true, isPlaying = true, renderedFirstFrame = true,
        audioTrackLabel = "Original audio", providerAudioId = "provider-id", qualityHeight = 1080,
        manifestUrl = "https://media.invalid/captured.m3u8?private=manifest", completedMediaBytes = 100,
        droppedVideoFrames = 2, bandwidthEstimate = 5000000, videoWidth = 1920,
        videoHeight = 1080, videoBitrate = 5254742, videoFrameRate = 23.974f,
    )

    private fun form(request: RecordedRequest): Map<String, String> {
        val body = request.body.clone().readUtf8()
        if (request.getHeader("Content-Type")?.startsWith("multipart/form-data") == true) {
            return Regex("name=\"([^\"]+)\".*?\\r\\n\\r\\n(.*?)\\r\\n--", RegexOption.DOT_MATCHES_ALL)
                .findAll(body).associate { it.groupValues[1] to it.groupValues[2] }
        }
        return body.split('&').filter { it.isNotBlank() }.associate { pair ->
            val parts = pair.split('=', limit = 2)
            URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts.getOrElse(1) { "" }, "UTF-8")
        }
    }

    private fun payload(request: RecordedRequest) = json(requireNotNull(form(request)["payload"]))
    private fun events(envelope: JsonObject) = envelope["events"]!!.jsonArray.map { it.jsonObject }
    private fun type(event: JsonObject) = event["event_type"]!!.jsonPrimitive.content
    private fun take(server: MockWebServer) = requireNotNull(server.takeRequest(3, TimeUnit.SECONDS))
    private fun waitUntil(check: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        while (!check() && System.nanoTime() < deadline) Thread.sleep(5)
        assertTrue(check(), "Asynchronous operation did not complete")
    }

    @Test fun continuesCapturedIdentityAndCountersWithTenSecondSamplesThirtySecondBatches() {
        server().use { server ->
            val clock = Clock()
            val engine = AllohaHttpTelemetry(descriptor(server), OkHttpClient(), { clock.at }, autoSchedule = false)
            try {
                engine.update(playing())
                clock.advance(500); engine.update(playing(10500))
                clock.advance(500); engine.update(playing(11000))
                clock.at = 1010000; engine.tick()
                clock.at = 1020000; engine.tick()
                assertNull(server.takeRequest(75, TimeUnit.MILLISECONDS))
                clock.at = 1030000; engine.tick()
                val request = take(server)
                assertEquals("/events", request.path)
                assertEquals("application/x-www-form-urlencoded;charset=UTF-8", request.getHeader("Content-Type"))
                assertEquals("captured-browser", request.getHeader("User-Agent"))
                assertEquals("SID=private-cookie", request.getHeader("Cookie"))
                assertEquals(descriptor(server).providerPageUrl, request.getHeader("Referer"))
                assertEquals(server.url("/").toString().removeSuffix("/"), request.getHeader("Origin"))
                val envelope = payload(request)
                assertEquals("private-http-token", form(request)["token"])
                assertEquals("private-http-token", envelope["token"]!!.jsonPrimitive.content)
                assertEquals("captured-session-id", envelope["clientSessionId"]!!.jsonPrimitive.content)
                assertEquals("captured-request-id", envelope["clientRequestId"]!!.jsonPrimitive.content)
                val samples = events(envelope)
                assertEquals(listOf("heartbeat", "heartbeat", "heartbeat"), samples.map(::type))
                val first = samples.first()
                assertEquals(11.0, first["currentTime"]!!.jsonPrimitive.double)
                assertEquals(12.5, first["bufferLength"]!!.jsonPrimitive.double)
                assertFalse(first["quality"]!!.jsonPrimitive.isString)
                assertTrue(first["audioTrack"]!!.jsonPrimitive.isString)
                assertEquals(JsonNull, first["subtitle"])
                assertEquals(1100, first["bytesLoadedTotal"]!!.jsonPrimitive.int)
                assertEquals(100, first["bytesLoadedDelta"]!!.jsonPrimitive.int)
                assertEquals(5, first["droppedFrames"]!!.jsonPrimitive.int)
                assertEquals(2, first["droppedFramesDelta"]!!.jsonPrimitive.int)
                assertEquals(6.0, first["playedSecondsDelta"]!!.jsonPrimitive.double)
                assertEquals(0.0, samples[1]["playedSecondsDelta"]!!.jsonPrimitive.double)
                assertEquals(20000, first["since"]!!.jsonPrimitive.int)
                assertEquals(2, first["rebufferCountSession"]!!.jsonPrimitive.int)
                assertFalse(engine.diagnostics().contains("private"))
            } finally { engine.close() }
        }
    }

    @Test fun statUsesCapturedNestedFormAndRoundWhileViewMilestonesUseFloor() {
        server().use { server ->
            val clock = Clock()
            val engine = AllohaHttpTelemetry(descriptor(server, captured = false), OkHttpClient(), { clock.at }, autoSchedule = false)
            try {
                engine.update(playing(0).copy(isPlaying = false, renderedFirstFrame = false))
                val initial = take(server)
                assertEquals("/stat", initial.path)
                assertEquals("application/x-www-form-urlencoded;charset=UTF-8", initial.getHeader("Content-Type"))
                val fields = form(initial)
                assertFalse(fields.containsKey("percent"))
                assertEquals("false", fields["ab"])
                assertEquals("file-42", fields["id"])
                assertEquals("mgpo", fields["type"])
                assertEquals("true", fields["info[wasm]"])
                assertEquals("1280", fields["info[resolution][windowWidth]"])
                clock.advance(250); engine.update(playing(250))
                assertEquals("0", form(take(server))["percent"])
                clock.advance(250); engine.update(playing(9500))
                assertEquals("10", form(take(server))["percent"])
                engine.flush()
                val before = events(payload(take(server)))
                assertEquals(listOf(0), before.filter { type(it) == "view_percent" }.map { it["percent"]!!.jsonPrimitive.int })
                clock.advance(500); engine.update(playing(10000))
                engine.flush()
                val after = events(payload(take(server)))
                assertEquals(listOf(10), after.filter { type(it) == "view_percent" }.map { it["percent"]!!.jsonPrimitive.int })
                assertFalse((before + after).any { type(it).startsWith("ad_") })
            } finally { engine.close() }
        }
    }

    @Test fun rebufferLifecycleUsesMeasuredTimeAndDoesNotCountStalledMediaAsWatched() {
        server().use { server ->
            val clock = Clock()
            val engine = AllohaHttpTelemetry(descriptor(server), OkHttpClient(), { clock.at }, autoSchedule = false)
            try {
                engine.update(playing())
                clock.advance(250)
                engine.update(playing().copy(isPlaying = false, isBuffering = true, bufferedDurationMs = 0))
                clock.advance(1000)
                engine.update(playing())
                clock.at = 1010000; engine.tick()
                engine.flush()
                val result = events(payload(take(server)))
                assertEquals(1, result.count { type(it) == "rebuffer_start" })
                val end = result.single { type(it) == "rebuffer_end" }
                assertEquals(1000, end["rebufferDurationMs"]!!.jsonPrimitive.int)
                assertEquals(3, end["rebufferCountTotal"]!!.jsonPrimitive.int)
                val heartbeat = result.single { type(it) == "heartbeat" }
                assertEquals(1400, heartbeat["rebufferTotalMsSession"]!!.jsonPrimitive.int)
                assertEquals(5.0, heartbeat["playedSecondsDelta"]!!.jsonPrimitive.double)
            } finally { engine.close() }
        }
    }

    @Test fun realPauseSeekAndQualityChangesExcludeSeekDistanceAndFinishOnlyOnce() {
        server().use { server ->
            val clock = Clock()
            val engine = AllohaHttpTelemetry(descriptor(server), OkHttpClient(), { clock.at }, autoSchedule = false)
            engine.update(playing())
            clock.advance(500); engine.update(playing(10500))
            clock.advance(250); engine.update(playing(10750).copy(playWhenReady = false, isPlaying = false))
            clock.advance(10000); engine.tick()
            engine.seek(70000)
            engine.update(playing(70000).copy(isSeeking = true, isPlaying = false))
            engine.update(playing(70000))
            clock.advance(250); engine.update(playing(70250))
            clock.advance(250); engine.update(playing(70500).copy(qualityHeight = 720, subtitleLanguage = "en", speed = 1.5f))
            val beforeSwitch = payload(take(server))
            clock.advance(250); engine.update(playing(70750).copy(qualityHeight = 720, subtitleLanguage = "en", speed = 1.5f))
            engine.ended()
            engine.ended()
            engine.close()
            val finalRequest = take(server)
            assertTrue(finalRequest.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
            val afterSwitch = payload(finalRequest)
            assertEquals(beforeSwitch["clientSessionId"], afterSwitch["clientSessionId"])
            assertNotEquals(beforeSwitch["clientRequestId"], afterSwitch["clientRequestId"])
            val all = events(beforeSwitch) + events(afterSwitch)
            assertEquals(1, all.count { type(it) == "seek_start" })
            val seek = all.single { type(it) == "seek_end" }
            assertEquals(59.25, seek["seekDistance"]!!.jsonPrimitive.double)
            assertEquals(1, all.count { type(it) == "quality_switch" })
            assertEquals(1, all.count { type(it) == "switch_complete" })
            assertEquals(1, all.count { type(it) == "subtitle_change" })
            assertEquals(1, all.count { type(it) == "rate_change" })
            val finish = all.single { type(it) == "view_finish" }
            assertTrue(finish["completed"]!!.jsonPrimitive.boolean)
            assertEquals(12, finish["watchTimeSec"]!!.jsonPrimitive.int)
            assertFalse(all.any { type(it) in setOf("view_start", "startup", "ad_block_start", "first_frame") })
            assertNull(server.takeRequest(75, TimeUnit.MILLISECONDS))
        }
    }

    @Test fun earlyCloseSendsIncompleteFinalViewAndBoundsEventQueue() {
        server().use { server ->
            val clock = Clock()
            val engine = AllohaHttpTelemetry(descriptor(server), OkHttpClient(), { clock.at }, maxEventQueue = 3, autoSchedule = false)
            engine.update(playing())
            repeat(8) { index ->
                clock.advance(250)
                engine.update(playing(10000 + index * 250L).copy(speed = if (index % 2 == 0) 1.5f else 1f))
            }
            engine.close()
            val result = payload(take(server))
            assertEquals(3, events(result).size)
            assertTrue(result["dropped"]!!.jsonPrimitive.int > 0)
            val finish = events(result).single { type(it) == "view_finish" }
            assertFalse(finish["completed"]!!.jsonPrimitive.boolean)
            engine.close()
            assertNull(server.takeRequest(75, TimeUnit.MILLISECONDS))
        }
    }

    @Test fun replayStartsNewViewWithoutRepeatingOldFinishOrCountingOldMediaBytes() {
        server().use { server ->
            val clock = Clock()
            val engine = AllohaHttpTelemetry(descriptor(server), OkHttpClient(), { clock.at }, autoSchedule = false)
            try {
                engine.update(playing(100000))
                // This position crosses the remaining HTTP milestones, but not a legacy /stat equality.
                engine.ended()
                val firstView = payload(take(server))
                engine.update(playing(100000).copy(isPlaying = false))
                assertNull(server.takeRequest(50, TimeUnit.MILLISECONDS))
                engine.seek(0)
                engine.update(playing(0).copy(isSeeking = true, isPlaying = false))
                assertEquals("/stat", take(server).path)
                clock.advance(250); engine.update(playing(250))
                assertEquals("0", form(take(server))["percent"])
                clock.at = 1010000; engine.tick()
                engine.flush()
                val replay = payload(take(server))
                assertNotEquals(firstView["clientSessionId"], replay["clientSessionId"])
                assertEquals(1, events(firstView).count { type(it) == "view_finish" })
                assertFalse(events(replay).any { type(it) == "view_finish" })
                assertEquals(1, events(replay).count { type(it) == "view_start" })
                assertEquals(1, events(replay).count { type(it) == "seek_start" })
                assertEquals(1, events(replay).count { type(it) == "seek_end" })
                assertEquals(0, events(replay).single { type(it) == "heartbeat" }["bytesLoadedTotal"]!!.jsonPrimitive.int)
            } finally { engine.close() }
        }
    }

    @Test fun sessionKeepsCapturedTranslationLabelOnlyForTheSameProviderAudioId() {
        server().use { http ->
            MockWebServer().use { websocket ->
                websocket.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {}))
                val capture = descriptor(http).let { original ->
                    original.copy(observedEvents = original.observedEvents +
                        json("""{"event_type":"play","audioTrack":"(Russian) AniBaza","paused":false}"""))
                }
                val captured = AllohaSessionDescriptor(
                    observedWebSocketUrl = websocket.url("/socket").toString(), webSocketHeaders = emptyMap(),
                    playbackStartTemplate = """{"track_id":"original-provider-track"}""",
                    initialToken = "edge", guardToken = "guard", expiresAtEpochMs = null,
                    mediaHosts = setOf("media.invalid"), telemetry = capture,
                )
                AllohaPlaybackSession(captured, OkHttpClient()).use { session ->
                    session.update(playing().copy(providerAudioId = "original-provider-track", audioTrackLabel = "rus"))
                    session.playbackError("TEST_FLUSH")
                    val sameTrack = events(payload(take(http)))
                    assertFalse(sameTrack.any { type(it) == "audio_switch" })
                    assertTrue(sameTrack.all { it["audioTrack"]?.jsonPrimitive?.content == "(Russian) AniBaza" })

                    session.update(playing().copy(providerAudioId = null, audioTrackLabel = "Russian"))
                    session.playbackError("TEST_FLUSH")
                    val fallbackTrack = events(payload(take(http)))
                    assertFalse(fallbackTrack.any { type(it) == "audio_switch" })
                    assertTrue(fallbackTrack.all { it["audioTrack"]?.jsonPrimitive?.content == "(Russian) AniBaza" })

                    session.update(playing().copy(providerAudioId = "different-provider-track", audioTrackLabel = "jpn"))
                    session.playbackError("TEST_FLUSH")
                    val changedTrack = events(payload(take(http)))
                    val changed = changedTrack.single { type(it) == "audio_switch" }
                    assertEquals("(Russian) AniBaza", changed["audioBefore"]!!.jsonPrimitive.content)
                    assertEquals("jpn", changed["audioAfter"]!!.jsonPrimitive.content)
                    assertTrue(changedTrack.all { it["audioTrack"]?.jsonPrimitive?.content == "jpn" })
                }
            }
        }
    }

    @Test fun telemetryRestrictionStopsBothChannelsWithoutFailingMediaHeaders() {
        for (code in listOf(403, 429)) {
            server(code).use { http ->
                MockWebServer().use { websocket ->
                    websocket.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {}))
                    val descriptor = AllohaSessionDescriptor(
                        observedWebSocketUrl = websocket.url("/socket").toString(), webSocketHeaders = emptyMap(),
                        playbackStartTemplate = "{}", initialToken = "valid-edge", guardToken = "valid-guard",
                        expiresAtEpochMs = null, mediaHosts = setOf("media.invalid"),
                        telemetry = descriptor(http, captured = false),
                    )
                    AllohaPlaybackSession(descriptor, OkHttpClient()).use { session ->
                        session.update(playing())
                        take(http)
                        waitUntil { session.diagnostics().contains("httpRestricted=$code") }
                        assertEquals("valid-edge", session.requestHeaders("https://media.invalid/segment.ts")["Accepts-Controls"])
                        session.update(playing(97000))
                        session.seek(98000)
                        session.playbackError("ERROR_CODE_IO_BAD_HTTP_STATUS")
                        session.ended()
                        assertEquals(1, http.requestCount)
                    }
                }
            }
        }
    }

    @Test fun bufferedReadinessPrecedesFirstRendererFrameAndCompletesActualNativeStartup() {
        server().use { server ->
            val clock = Clock()
            val engine = AllohaHttpTelemetry(descriptor(server, captured = false), OkHttpClient(), { clock.at }, autoSchedule = false)
            try {
                engine.update(playing(0).copy(isPlaying = false, renderedFirstFrame = false, bufferedDurationMs = 0))
                take(server) // Initial /stat only.
                clock.advance(100)
                engine.update(playing(0).copy(isPlaying = false, renderedFirstFrame = false, bufferedDurationMs = 6000))
                engine.flush()
                val readyEvents = events(payload(take(server)))
                assertEquals(1, readyEvents.count { type(it) == "playback_ready" })
                assertFalse(readyEvents.any { type(it) in setOf("first_frame", "file_load_complete") })
                clock.advance(100)
                engine.update(playing(250))
                take(server) // Legacy zero-percent /stat.
                engine.flush()
                val rendered = events(payload(take(server)))
                assertEquals(1, rendered.count { type(it) == "first_frame" })
                val complete = rendered.single { type(it) == "file_load_complete" }
                assertEquals(200, complete["playToFirstFrameMs"]!!.jsonPrimitive.int)
                assertEquals(JsonNull, complete["manifestLoadMs"])
                assertEquals(JsonNull, complete["backendMs"])
            } finally { engine.close() }
        }
    }

    @Test fun sourceReloadPreservesKnownAudioAndSubtitleAcrossUnknownPreparationState() {
        server().use { server ->
            val clock = Clock()
            val engine = AllohaHttpTelemetry(descriptor(server), OkHttpClient(), { clock.at }, autoSchedule = false)
            try {
                engine.update(playing().copy(subtitleLanguage = "en"))
                engine.flush()
                take(server)
                clock.advance(250)
                engine.update(playing().copy(audioTrackLabel = null, qualityHeight = null,
                    subtitleLanguage = null, isPlaying = false, renderedFirstFrame = false))
                clock.advance(250)
                engine.update(playing(10250).copy(audioTrackLabel = "New audio", subtitleLanguage = "en"))
                engine.flush()
                val replay = events(payload(take(server)))
                assertEquals(1, replay.count { type(it) == "audio_switch" })
                assertFalse(replay.any { type(it) == "subtitle_change" })
                assertFalse(replay.any { type(it) == "quality_switch" })
            } finally { engine.close() }
        }
    }

    @Test fun endpointSpecificCookiesDoNotBorrowAnotherPathsCapturedCookie() {
        server().use { server ->
            val captured = descriptor(server, captured = false).copy(endpointHeaders = mapOf(
                "/stat" to mapOf("Cookie" to "stat=only", "User-Agent" to "browser"),
                "/events" to mapOf("User-Agent" to "browser"),
            ))
            AllohaHttpTelemetry(captured, OkHttpClient(), autoSchedule = false).use { engine ->
                engine.update(playing(0).copy(isPlaying = false, renderedFirstFrame = false))
                val stat = take(server)
                assertEquals("/stat", stat.path)
                assertEquals("stat=only", stat.getHeader("Cookie"))
                engine.flush()
                val events = take(server)
                assertEquals("/events", events.path)
                assertNull(events.getHeader("Cookie"))
            }
        }
    }

    @Test fun redirectCannotSendCapturedIdentityToAnotherOrigin() {
        server().use { target ->
            MockWebServer().use { origin ->
                origin.dispatcher = object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(307)
                        .setHeader("Location", target.url("/collect"))
                }
                origin.start()
                AllohaHttpTelemetry(descriptor(origin), OkHttpClient(), autoSchedule = false).use { engine ->
                    engine.update(playing())
                    engine.playbackError("ERROR_CODE_DECODING_FAILED")
                    take(origin)
                    waitUntil { engine.diagnostics().contains("httpFailed=1") }
                    assertEquals(0, target.requestCount)
                }
            }
        }
    }

    @Test fun closingCancelsUnresponsiveInFlightCallWithinGracePeriod() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val engine = AllohaHttpTelemetry(descriptor(server, captured = false), OkHttpClient(),
                closeGraceMs = 100, autoSchedule = false)
            engine.update(playing())
            take(server)
            engine.close()
            waitUntil { engine.diagnostics().contains("httpActive=false") && engine.diagnostics().contains("httpPending=0") }
            val completedRequests = server.requestCount
            engine.update(playing(97000))
            assertEquals(completedRequests, server.requestCount)
        }
    }
}
