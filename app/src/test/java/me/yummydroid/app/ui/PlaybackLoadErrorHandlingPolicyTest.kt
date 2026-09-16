package me.yummydroid.app.ui

import androidx.media3.common.C
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.FallbackOptions
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import me.yummydroid.app.data.PlaybackProvider
import me.yummydroid.app.data.PlaybackHttpException
import me.yummydroid.app.data.PlaybackSessionExpiredException
import me.yummydroid.app.data.PlaybackSessionRestrictedException

class PlaybackLoadErrorHandlingPolicyTest {
    private val policy = PlaybackLoadErrorHandlingPolicy()
    private val alternatives = FallbackOptions(2, 0, 2, 0)

    @Test
    fun restrictionsNeverRetryOrSwitchTracksAndLocations() {
        for (code in listOf(429)) {
            for (error in listOf(httpError(code), IOException(httpError(code)))) {
                assertTrue(error.isPlaybackHttpRestricted())
                for (attempt in 1..4) {
                    val info = errorInfo(error, attempt)
                    assertEquals(C.TIME_UNSET, policy.getRetryDelayMsFor(info))
                    assertNull(policy.getFallbackSelectionFor(alternatives, info))
                }
            }
        }
    }

    @Test
    fun forbiddenCdnRequestsHaveBoundedDelayedRecovery() {
        val policy = PlaybackLoadErrorHandlingPolicy(PlaybackProvider.Alloha)
        for (error in listOf(httpError(403), IOException(httpError(403)))) {
            assertFalse(error.isPlaybackHttpRestricted())
            assertEquals(2_000L, policy.getRetryDelayMsFor(errorInfo(error, 1)))
            assertEquals(5_000L, policy.getRetryDelayMsFor(errorInfo(error, 2)))
            for (attempt in 3..8) {
                assertEquals(C.TIME_UNSET, policy.getRetryDelayMsFor(errorInfo(error, attempt)))
            }
        }
        assertNull(policy.getFallbackSelectionFor(alternatives, errorInfo(httpError(403), 1)))
    }

    @Test
    fun providersKeepSeparateForbiddenRecoveryStrategies() {
        for (provider in listOf(PlaybackProvider.Cvh, PlaybackProvider.Kodik, PlaybackProvider.Sibnet)) {
            assertEquals(C.TIME_UNSET, PlaybackLoadErrorHandlingPolicy(provider).getRetryDelayMsFor(errorInfo(httpError(403), 1)))
        }
        assertEquals(2_000L, PlaybackLoadErrorHandlingPolicy(PlaybackProvider.Aksor).getRetryDelayMsFor(errorInfo(httpError(403), 1)))
        assertNotNull(PlaybackLoadErrorHandlingPolicy().getFallbackSelectionFor(alternatives, errorInfo(httpError(403), 1)))
    }

    @Test
    fun transientErrorsKeepNormalRecovery() {
        val missing = errorInfo(httpError(404), 2)
        assertFalse(missing.exception.isPlaybackHttpRestricted())
        assertNotNull(policy.getFallbackSelectionFor(alternatives, missing))
        assertEquals(1_000L, policy.getRetryDelayMsFor(errorInfo(IOException("timeout"), 2)))
    }

    @Test
    fun serverDeadlinePrecedesEveryProviderMirrorAndRetryStrategyAndRetriesAreBounded() {
        for (provider in PlaybackProvider.entries) {
            val policy = PlaybackLoadErrorHandlingPolicy(provider, clockMs = { 1_000L })
            for (status in listOf(403, 503)) {
                val error = IOException(PlaybackHttpException(status, 12_000L))
                assertTrue(error.isPlaybackHttpRestricted(1_000L))
                for (attempt in 1..2) {
                    assertEquals(11_000L, policy.getRetryDelayMsFor(errorInfo(error, attempt)))
                    assertNull(policy.getFallbackSelectionFor(alternatives, errorInfo(error, attempt)))
                }
                for (attempt in listOf(0, 3, Int.MAX_VALUE)) {
                    assertEquals(C.TIME_UNSET, policy.getRetryDelayMsFor(errorInfo(error, attempt)))
                    assertNull(policy.getFallbackSelectionFor(alternatives, errorInfo(error, attempt)))
                }
            }
        }
    }

    @Test
    fun veryLargeDeadlineCannotOverflowIntoImmediateRetry() {
        val policy = PlaybackLoadErrorHandlingPolicy(PlaybackProvider.Alloha, clockMs = { Long.MIN_VALUE })
        val error = PlaybackHttpException(403, Long.MAX_VALUE)
        assertEquals(C.TIME_UNSET, policy.getRetryDelayMsFor(errorInfo(error, 1)))
        assertNull(policy.getFallbackSelectionFor(alternatives, errorInfo(error, 1)))
    }

    @Test
    fun leaseFailuresRemainTerminalWithoutHttpStatus() {
        for (error in listOf(PlaybackSessionExpiredException(), PlaybackSessionRestrictedException(403, null))) {
            assertTrue(IOException(error).isTerminalPlaybackSessionFailure())
            assertEquals(C.TIME_UNSET, policy.getRetryDelayMsFor(errorInfo(error, 1)))
            assertNull(policy.getFallbackSelectionFor(alternatives, errorInfo(error, 1)))
        }
        assertFalse(IOException("timeout").isTerminalPlaybackSessionFailure())
    }

    @Test
    fun wrapperDeadlineIsNotDiscardedForNestedOrdinaryHttpFailure() {
        val wrapper = PlaybackHttpException(503, 12_000L).apply { initCause(PlaybackHttpException(403)) }
        assertEquals(12_000L, wrapper.playbackHttpDetails(1_000L)?.retryAtEpochMs)
        assertTrue(wrapper.isPlaybackHttpRestricted(1_000L))
        val rateLimited = PlaybackHttpException(429).apply { initCause(PlaybackHttpException(503, 120_000L)) }
        assertEquals(429, rateLimited.playbackHttpDetails(1_000L)?.statusCode)
        assertEquals(120_000L, rateLimited.playbackHttpDetails(1_000L)?.retryAtEpochMs)
    }

    // These policies only inspect exception/errorCount, not Android-backed event metadata.
    private fun errorInfo(error: IOException, attempt: Int) = PlaybackErrorFixtures.info(error, attempt)

    private fun httpError(code: Int) = PlaybackErrorFixtures.httpError(code)
}
