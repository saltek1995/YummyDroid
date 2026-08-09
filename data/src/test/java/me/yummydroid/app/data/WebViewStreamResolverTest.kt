package me.yummydroid.app.data

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebViewStreamResolverTest {
    @Test
    fun sessionAllowsExactlyOneTerminalTransition() {
        val termination = WebViewSessionTermination()

        assertFalse(termination.isTerminated)
        assertTrue(termination.tryTerminate())
        assertTrue(termination.isTerminated)
        assertFalse(termination.tryTerminate())
    }

    @Test
    fun playbackOnlyResolutionUsesShortIdleWindow() {
        assertEquals(
            250L,
            webViewDiscoveryIdleMs(
                waitForRuntimeSubtitles = false,
                hasCapturedSubtitles = false,
                requiresRuntimePlayerDiscovery = true,
            ),
        )
    }

    @Test
    fun runtimeProviderWaitsForLateSubtitleDiscovery() {
        assertEquals(
            4_000L,
            webViewDiscoveryIdleMs(
                waitForRuntimeSubtitles = true,
                hasCapturedSubtitles = false,
                requiresRuntimePlayerDiscovery = true,
            ),
        )
    }

    @Test
    fun capturedOrStaticSubtitlesUseNormalIdleWindow() {
        assertEquals(
            1_200L,
            webViewDiscoveryIdleMs(
                waitForRuntimeSubtitles = true,
                hasCapturedSubtitles = true,
                requiresRuntimePlayerDiscovery = true,
            ),
        )
        assertEquals(
            1_200L,
            webViewDiscoveryIdleMs(
                waitForRuntimeSubtitles = true,
                hasCapturedSubtitles = false,
                requiresRuntimePlayerDiscovery = false,
            ),
        )
    }

    @Test
    fun documentStartScriptUsesExactRuntimePlayerOrigin() {
        assertEquals(
            "https://player.allohastream.example:8443",
            runtimeDocumentStartOriginRule("https://player.allohastream.example:8443/embed/14?episode=2"),
        )
        assertFailsWith<IOException> {
            runtimeDocumentStartOriginRule("not a URL")
        }
    }
}
