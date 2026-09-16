package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class PlaybackResolveRequestPolicyTest {
    @Test
    fun retryAfterStopsDiscoveryForServiceUnavailableAndForbiddenEvenWhenOrdinaryForbiddenIsAllowed() {
        for (status in listOf(403, 503)) {
            val policy = PlaybackResolveRequestPolicy(stopOnForbidden = false, clockMs = { 1_000L })
            val failure = assertFailsWith<SourceHttpRestricted> {
                policy.onResponse(status, mapOf("Retry-After" to listOf("120")))
            }
            assertEquals(status, failure.statusCode)
            assertEquals(121_000L, failure.retryAtEpochMs)
            assertSame(failure, assertFailsWith<SourceHttpRestricted> { policy.beforeRequest() })
            assertSame(failure, assertFailsWith<SourceHttpRestricted> { policy.subtitlePolicy().beforeRequest() })
        }
    }

    @Test
    fun ordinaryOrExpiredResponsesKeepExistingProviderBehavior() {
        for (status in listOf(403, 503)) {
            val policy = PlaybackResolveRequestPolicy(stopOnForbidden = false, clockMs = { 1_000L })
            policy.onResponse(status)
            policy.onResponse(status, mapOf("Retry-After" to listOf("0")))
            policy.beforeRequest()
        }
    }
}
