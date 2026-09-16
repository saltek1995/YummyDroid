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

class PlaybackLoadErrorHandlingPolicyTest {
    private val policy = PlaybackLoadErrorHandlingPolicy()
    private val alternatives = FallbackOptions(2, 0, 2, 0)

    @Test
    fun restrictionsNeverRetryOrSwitchTracksAndLocations() {
        for (code in listOf(403, 429)) {
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
    fun transientErrorsKeepNormalRecovery() {
        val missing = errorInfo(httpError(404), 2)
        assertFalse(missing.exception.isPlaybackHttpRestricted())
        assertNotNull(policy.getFallbackSelectionFor(alternatives, missing))
        assertEquals(1_000L, policy.getRetryDelayMsFor(errorInfo(IOException("timeout"), 2)))
    }

    // These policies only inspect exception/errorCount, not Android-backed event metadata.
    private fun errorInfo(error: IOException, attempt: Int) = PlaybackErrorFixtures.info(error, attempt)

    private fun httpError(code: Int) = PlaybackErrorFixtures.httpError(code)
}
