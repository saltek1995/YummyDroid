package me.yummydroid.app.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.VideoVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exercises the runtime-owned load effect with a local MP4; it performs no provider traffic. */
@RunWith(AndroidJUnit4::class)
class NativeRuntimeLoadTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun stableRuntimeUpdatesKeepOneLoadAndGenerationReloadsAtTheObservedPosition() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fixture = File(instrumentation.targetContext.cacheDir, "native-runtime-load-fixture.mp4")
        instrumentation.context.assets.open("media/realtime-buffer/av-180s.mp4").use { input ->
            fixture.outputStream().use(input::copyTo)
        }
        val fileUrl = fixture.toURI().toString()
        val video = VideoVariant(1L, 1L, "CVH", dubbing = "fixture", episode = "1", url = fileUrl,
            index = 0, durationSeconds = 180, views = 0L)
        val progress = mutableListOf<Long>()
        val failures = mutableListOf<String>()
        val current = mutableStateOf(binding(video, fileUrl, progress, failures))
        current.value = current.value.copy(onPlaybackProgress = { _, positionMs, _ ->
            synchronized(progress) { progress += positionMs }
            current.value = current.value.copy(startPositionMs = positionMs)
        })
        val visible = mutableStateOf(true)
        var session: NativeVideoPlayerRuntimeSession? = null
        try {
            compose.setContent {
                val navigation = remember { AppNavigationController() }
                CompositionLocalProvider(LocalAppNavigationController provides navigation) {
                    if (visible.value) {
                        val runtimeSession = rememberNativeVideoPlayerRuntimeSession(current.value)
                        BindNativeVideoPlayerRuntimeEffects(current.value, runtimeSession)
                        AndroidView(factory = { context -> PlayerView(context).apply {
                            player = runtimeSession.playbackPlayer
                            useController = false
                        } })
                        SideEffect { session = runtimeSession }
                    }
                }
            }
            compose.waitUntil(20_000) { onMain { loadRequests(session?.player) == 1L } }
            val initialPlayer = onMain { requireNotNull(session).player }
            assertSame(initialPlayer, onMain { requireNotNull(session).playbackPlayer })

            compose.runOnIdle { current.value = current.value.copy(playWhenReady = true) }
            compose.waitUntil(40_000) { onMain { initialPlayer.isPlaying } &&
                synchronized(progress) { progress.any { it >= 10_000L } } }
            val resumeAtMs = synchronized(progress) { progress.last() }.coerceAtLeast(1L)
            assertTrue("runtime load failed: $failures", failures.isEmpty())

            repeat(100) { index ->
                compose.runOnIdle {
                    val latest = current.value
                    current.value = latest.copy(
                        startPositionMs = resumeAtMs,
                        stream = latest.stream.copy(runtimeMetadataResolved = index % 2 == 0),
                        currentVideo = latest.currentVideo.copy(durationSeconds = 180 + index % 2),
                        onPlaybackProgress = { _, positionMs, _ -> synchronized(progress) { progress += positionMs } },
                    )
                }
            }
            compose.waitForIdle()
            assertSame(initialPlayer, onMain { requireNotNull(session).player })
            assertEquals(1L, onMain { loadRequests(initialPlayer) })

            compose.runOnIdle {
                val latest = current.value
                current.value = latest.copy(
                    startPositionMs = resumeAtMs,
                    playWhenReady = false,
                    stream = latest.stream.copy(playbackGeneration = 1L),
                )
            }
            compose.waitUntil(20_000) { onMain { loadRequests(initialPlayer) == 2L } }
            compose.waitUntil(20_000) { onMain { initialPlayer.playbackState == Player.STATE_READY } }
            assertTrue(onMain { kotlin.math.abs(initialPlayer.currentPosition - resumeAtMs) <= 1_000L })
            assertTrue("runtime reload failed: $failures", failures.isEmpty())
        } finally {
            compose.runOnIdle { visible.value = false }
            fixture.delete()
        }
    }

    private fun binding(
        video: VideoVariant,
        fileUrl: String,
        progress: MutableList<Long>,
        failures: MutableList<String>,
    ) =
        NativeVideoPlayerRuntimeBinding(
            stream = ResolvedVideoStream(fileUrl, "video/mp4", emptyMap()),
            animeTitle = "Fixture",
            currentVideo = video,
            interactive = false,
            settings = AppSettings(autoplayNextEpisode = false, skipOpeningsAndEndings = false),
            startPositionMs = 0L,
            playbackPreferredQuality = PreferredQuality.Auto,
            playbackMetadataLoading = false,
            playbackSelectionResolving = false,
            playWhenReady = false,
            groups = mapOf(video.groupKey to listOf(video)),
            selectedKey = video.groupKey,
            sourceOptions = emptyList(),
            selectedSourceKey = null,
            previousVideo = null,
            nextVideo = null,
            allowSubscription = false,
            subscriptionActive = false,
            onToggleSubscription = {},
            onSelectGroup = { _, _, _ -> },
            onSelectSource = { _, _ -> },
            onPlayVideoAt = { _, _ -> },
            onPlayVideoAtQuality = { _, _, _ -> },
            onPlaybackFailed = { _, positionMs, failure -> synchronized(failures) { failures += "$failure@$positionMs" } },
            onPlaybackStarted = {},
            onPlaybackEnded = {},
            onPlaybackProgress = { _, positionMs, _ -> synchronized(progress) { progress += positionMs } },
            canUsePictureInPicture = false,
            isInPictureInPicture = false,
            onEnterPictureInPicture = {},
            onSettingsChange = {},
            onBack = {},
            offlineMode = false,
            modifier = Modifier,
            playerControlFocusToRestoreId = null,
            keepControlsVisibleAfterReady = false,
            onRememberPlayerControlFocus = {},
            onPlayerControlFocusRestored = {},
            onKeepControlsVisibleAfterReadyRequested = {},
            onControlsKeptVisibleAfterReady = {},
        )

    private fun loadRequests(player: Player?): Long {
        val text = player?.playbackLoadDiagnosticsText().orEmpty()
        return Regex("loadRequests=(\\d+)").find(text)?.groupValues?.get(1)?.toLongOrNull() ?: -1L
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = runCatching(block) }
        return requireNotNull(result).getOrThrow()
    }
}
