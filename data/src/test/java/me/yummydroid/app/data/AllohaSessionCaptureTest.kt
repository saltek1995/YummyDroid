package me.yummydroid.app.data

import kotlin.test.*

class AllohaSessionCaptureTest {
    @Test
    fun usesBrowserLanguagesWithoutProlongingDiscoveryForIdenticalReports() {
        val capture = AllohaSessionCapture()
        val report = """{"languages":["en-US"]}"""
        assertTrue(capture.record(report))
        assertFalse(capture.record(report))
        assertEquals("en-US,en;q=0.9", capture.browserAcceptLanguage("151.0.0.0"))
        assertNull(capture.browserAcceptLanguage(null))
    }

    @Test
    fun prefersActualSignedBrowserRequestLanguageOverReconstruction() {
        val capture = AllohaSessionCapture()
        capture.record("""{"languages":["en-US"]}""")
        capture.observeRequest("https://media.test/fragment.m4s", mapOf(
            "Accepts-Controls" to "token", "accept-language" to "en-US",
        ))
        assertEquals("en-US", capture.browserAcceptLanguage("151.0.0.0"))
        capture.observeRequest("https://ads.test/image.png", mapOf("Accept-Language" to "fr"))
        assertEquals("en-US", capture.browserAcceptLanguage(null))
    }

    @Test
    fun capturesInertDescriptorAndDoesNotProlongDiscoveryForUnchangedState() {
        val capture = AllohaSessionCapture()
        assertTrue(capture.record("""{"socketUrl":"wss://socket.test/channel?sid=s&v=2.1&t=1"}"""))
        assertNull(capture.descriptor(stream) { emptyMap() })
        assertTrue(capture.record("""{"startTemplate":"{\"type\":\"playback_start\",\"track_id\":\"t\"}"}"""))
        val update = """{"token":"rotated","query":"old","guard":"guard","expiresAt":1700000000000}"""
        assertTrue(capture.record(update))
        assertFalse(capture.record(update))
        val descriptor = assertNotNull(capture.descriptor(stream) { mapOf("Origin" to "https://alloha.test") })
        assertEquals("rotated", descriptor.initialToken)
        assertEquals("guard", descriptor.guardToken)
        assertEquals(1700000000000, descriptor.expiresAtEpochMs)
        assertEquals(setOf("media.test", "backup.test"), descriptor.mediaHosts)
        assertEquals("https://alloha.test", descriptor.webSocketHeaders["Origin"])
    }

    @Test
    fun newSessionCannotInheritPreviousTemplateOrToken() {
        val capture = AllohaSessionCapture()
        capture.record("""{"socketUrl":"wss://socket.test/channel?sid=one&v=2.1","startTemplate":"{\"type\":\"playback_start\"}","token":"old"}""")
        assertNotNull(capture.descriptor(stream) { emptyMap() })
        capture.record("""{"socketUrl":"wss://socket.test/channel?sid=two&v=2.1"}""")
        assertNull(capture.descriptor(stream) { emptyMap() })
        assertFalse(capture.record("not-json"))
    }

    @Test
    fun partialStartupReportsAccumulateWithoutReplacingEarlierEvents() {
        val capture = AllohaSessionCapture()
        capture.record("""{"socketUrl":"wss://socket.test/channel?sid=one&v=2.1","startTemplate":"{\"type\":\"playback_start\"}","startupEvent":"playback_start"}""")
        capture.record("""{"startupEvent":"init"}""")
        capture.record("""{"token":"token","startupEvent":"resumed"}""")

        assertEquals(
            setOf("playback_start", "init", "resumed"),
            assertNotNull(capture.descriptor(stream) { emptyMap() }).observedStartupEvents,
        )
    }

    @Test
    fun newSidClearsPreviouslyObservedStartupEvents() {
        val capture = AllohaSessionCapture()
        capture.record("""{"socketUrl":"wss://socket.test/channel?sid=one&v=2.1","startTemplate":"{\"type\":\"playback_start\"}","startupEvent":"init"}""")
        capture.record("""{"startupEvent":"resumed"}""")
        capture.record("""{"socketUrl":"wss://socket.test/channel?sid=two&v=2.1","startTemplate":"{\"type\":\"playback_start\"}"}""")

        assertEquals(emptySet(), assertNotNull(capture.descriptor(stream) { emptyMap() }).observedStartupEvents)
    }

    @Test
    fun advertisedMetadataRequiresSessionBeforeSocketAndPersistsAcrossPartialReports() {
        val capture = AllohaSessionCapture()
        assertFalse(capture.requiresSession)
        capture.record("""{"pnr":"route","pnk":"key","expiresAt":100}""")
        assertTrue(capture.requiresSession)
        assertFalse(capture.hasObservedSocket)
        assertNull(capture.descriptor(stream) { emptyMap() })
        capture.record("""{"token":"token"}""")
        capture.record("""{"pnr":null,"pnk":""}""")
        assertTrue(capture.requiresSession)
        assertNull(capture.descriptor(stream) { emptyMap() })
    }

    @Test
    fun additionalHostsRequireMatchingControlsAndAnyProvidedGuard() {
        val capture = readyCapture()
        assertTrue(capture.observeRequest("https://segments.test/chunk.ts", mapOf("accepts-controls" to "token", "authorizations" to "Bearer guard")))
        assertTrue(capture.observeRequest("https://keys.test/key", mapOf("Accepts-Controls" to "query")))
        assertFalse(capture.observeRequest("https://wrong.test/chunk.ts", mapOf("Accepts-Controls" to "wrong", "Authorizations" to "Bearer guard")))
        assertFalse(capture.observeRequest("https://guard-only.test/chunk.ts", mapOf("Authorizations" to "Bearer guard")))
        assertFalse(capture.observeRequest("https://wrong-guard.test/chunk.ts", mapOf("Accepts-Controls" to "token", "Authorizations" to "Bearer wrong")))
        val hosts = assertNotNull(capture.descriptor(stream) { emptyMap() }).mediaHosts
        assertEquals(setOf("media.test", "backup.test", "segments.test", "keys.test"), hosts)
    }

    @Test
    fun requestsBeforeCredentialsAreValidatedWhenTokenAndGuardArrive() {
        val capture = AllohaSessionCapture()
        capture.record("""{"socketUrl":"wss://socket.test/channel?sid=one&v=2.1","startTemplate":"{\"type\":\"playback_start\"}"}""")
        assertFalse(capture.observeRequest("https://early.test/segment", mapOf("Accepts-Controls" to "later", "Authorizations" to "Bearer guard")))
        capture.record("""{"token":"later"}""")
        assertFalse("early.test" in assertNotNull(capture.descriptor(stream) { emptyMap() }).mediaHosts)
        capture.record("""{"guard":"guard"}""")
        assertTrue("early.test" in assertNotNull(capture.descriptor(stream) { emptyMap() }).mediaHosts)
    }

    @Test
    fun newSidClearsAcceptedAndPendingHostsAndOldCredentials() {
        val capture = readyCapture()
        capture.observeRequest("https://old.test/chunk", mapOf("Accepts-Controls" to "token"))
        capture.observeRequest("https://pending.test/chunk", mapOf("Accepts-Controls" to "next"))
        capture.record("""{"socketUrl":"wss://socket.test/channel?sid=two&v=2.1","startTemplate":"{\"type\":\"playback_start\"}","token":"next"}""")
        assertFalse(capture.observeRequest("https://stale-query.test/chunk", mapOf("Accepts-Controls" to "query")))
        val descriptor = assertNotNull(capture.descriptor(stream) { emptyMap() })
        assertEquals(setOf("media.test", "backup.test"), descriptor.mediaHosts)
        assertNull(descriptor.guardToken)
        assertEquals("next", descriptor.initialToken)
    }

    @Test
    fun pendingRequestsAreBounded() {
        val capture = readyCapture()
        repeat(65) { capture.observeRequest("https://candidate$it.test/chunk", mapOf("Accepts-Controls" to "next")) }
        capture.record("""{"token":"next"}""")
        val hosts = assertNotNull(capture.descriptor(stream) { emptyMap() }).mediaHosts
        assertFalse("candidate0.test" in hosts)
        assertTrue("candidate64.test" in hosts)
        assertEquals(66, hosts.size)
    }

    @Test
    fun telemetryRequiresProviderPageAndCompleteSeedsAndPreservesSentState() {
        val page = "https://alloha.test/player?episode=synthetic"
        val capture = AllohaSessionCapture(page)
        val report = """{"pageUrl":"$page","telemetry":{"token":"http-token","domain":"site.test","fileId":"file","statType":"mgpo","pageEpochMs":1234,"initialStatSent":true,"statInfo":{"wasm":true},"percents":[0,10],"events":[{"event_type":"view_start","at":5678}],"envelope":{"token":"http-token","domain":"site.test","clientSessionId":"view","clientRequestId":"request","env":{"pixelRatio":1},"events":[]}}}"""
        assertNull(capture.telemetryDescriptor { _, _ -> emptyMap() })
        assertFalse(capture.record(report.replace(page, "https://advert.test/frame")))
        assertTrue(capture.record(report))
        assertFalse(capture.record(report))
        capture.observeProviderRequest("https://alloha.test/events", mapOf("User-Agent" to "actual-browser"))
        capture.observeProviderRequest("https://media.test/events", mapOf("Cookie" to "cdn-only"))
        val descriptor = assertNotNull(capture.telemetryDescriptor { url, observed ->
            assertTrue(url in setOf("https://alloha.test/events", "https://alloha.test/stat"))
            if (url.endsWith("/events")) assertEquals(mapOf("User-Agent" to "actual-browser"), observed)
            else assertTrue(observed.isEmpty())
            observed + ("Cookie" to if (url.endsWith("/events")) "events-only" else "stat-only")
        })
        assertEquals("http-token", descriptor.token)
        assertEquals("file", descriptor.fileId)
        assertEquals(setOf(0, 10), descriptor.observedStatPercents)
        assertTrue(descriptor.initialStatSent)
        assertEquals("events-only", descriptor.endpointHeaders["/events"]?.get("Cookie"))
        assertEquals("stat-only", descriptor.endpointHeaders["/stat"]?.get("Cookie"))
        assertTrue(descriptor.headers.isEmpty())
        assertEquals(1, descriptor.observedEvents.size)
        assertEquals(1234L, descriptor.pageEpochMs)
        assertFalse(capture.record("""{"languages":["unscoped"]}"""))
        assertTrue(capture.record(report.replace("\"initialStatSent\":true", "\"initialStatSent\":true,\"deliveryFailed\":true")))
        assertNotNull(capture.telemetryDescriptor { _, _ -> emptyMap() })
        assertTrue(capture.record(report.replace("\"statInfo\":{\"wasm\":true},", "")))
        assertNull(capture.telemetryDescriptor { _, _ -> emptyMap() })
    }

    @Test
    fun untrustedFramesCannotForgeProviderPageReports() {
        val capture = AllohaSessionCapture("https://alloha.test/player", "private-proof")
        assertFalse(capture.record("""{"pageUrl":"https://alloha.test/player","languages":["en-US"]}"""))
        assertFalse(capture.record("""{"captureNonce":"wrong","pageUrl":"https://alloha.test/player","languages":["en-US"]}"""))
        assertTrue(capture.record("""{"captureNonce":"private-proof","pageUrl":"https://alloha.test/player","languages":["en-US"]}"""))
    }

    private fun readyCapture() = AllohaSessionCapture().apply {
        record("""{"socketUrl":"wss://socket.test/channel?sid=one&v=2.1","startTemplate":"{\"type\":\"playback_start\"}","token":"token","query":"query","guard":"guard"}""")
    }

    private val stream = ResolvedVideoStream("https://media.test/master.m3u8", "application/x-mpegURL",
        emptyMap(), fallbackUrls = listOf("https://backup.test/master.m3u8"))
}
