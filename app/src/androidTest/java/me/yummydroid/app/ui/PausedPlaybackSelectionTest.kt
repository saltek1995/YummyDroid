package me.yummydroid.app.ui

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.yummydroid.app.InputAction
import me.yummydroid.app.R
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.ui.theme.YummyDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * Covers source and voice changes made from the native popup while local playback is paused.
 * The resolver callbacks below are a fixture seam; this does not cover PlaybackActionRuntime.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(UnstableApi::class)
class PausedPlaybackSelectionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun sourceAndVoiceSelectionsReloadTheSameEpisodeAtThePausedPosition() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val media = File(instrumentation.targetContext.cacheDir, "paused-selection-fixture.mp4")
        instrumentation.context.assets.open("media/realtime-buffer/av-180s.mp4").use { input ->
            media.outputStream().use(input::copyTo)
        }
        val fixture = Fixture(media.toURI().toString())
        val current = mutableStateOf(fixture.binding(fixture.cvhVoiceOne))
        fixture.currentBinding = current
        val visible = mutableStateOf(true)
        var session: NativeVideoPlayerRuntimeSession? = null
        try {
            compose.setContent {
                val navigation = remember { AppNavigationController() }
                CompositionLocalProvider(LocalAppNavigationController provides navigation) {
                    YummyDroidTheme {
                        if (visible.value) {
                            val runtime = rememberNativeVideoPlayerRuntimeSession(current.value)
                            BindNativeVideoPlayerRuntimeEffects(current.value, runtime)
                            AndroidView(
                                factory = { context -> themedPlayerView(context) },
                                update = { playerView ->
                                    playerView.player = runtime.playbackPlayer
                                    playerView.useController = false
                                    playerView.bindYummyController(
                                        createNativeVideoPlayerControllerBinding(
                                            binding = current.value,
                                            session = runtime,
                                            latestBinding = { current.value },
                                            latestSession = { runtime },
                                        ),
                                    )
                                    fixture.playerView = playerView
                                },
                            )
                            SideEffect {
                                session = runtime
                                fixture.session = runtime
                                fixture.latestBinding = current.value
                            }
                        }
                    }
                }
            }

            compose.waitUntil(20_000) { onMain { loadRequests(session?.player) == 1L } }
            compose.waitUntil(20_000) { onMain { session?.player?.playbackState == Player.STATE_READY } }
            assertEquals(View.GONE, onMain { fixture.subtitle.visibility })
            onMain {
                val group = Tracks.Group(
                    TrackGroup(Format.Builder().setSampleMimeType("text/vtt").setLanguage("ru").build()),
                    false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(false),
                )
                fixture.session.selection.onTracksChanged(Tracks(listOf(group)))
            }
            compose.waitUntil { onMain { fixture.subtitle.visibility == View.VISIBLE && fixture.subtitle.isEnabled } }
            onMain { fixture.session.selection.onTracksChanged(Tracks.EMPTY) }
            compose.waitUntil { onMain { fixture.subtitle.visibility == View.GONE } }
            assertSpeedBindingRecoversFromLoadingCarryover(fixture)

            pauseAtThirtySeconds(requireNotNull(session).player)
            selectSourceWithNativePopup(fixture)
            compose.waitUntil { fixture.sourceTarget != null }
            assertEquals(fixture.allohaVoiceOne, fixture.sourceTarget)
            assertNearThirtySeconds(requireNotNull(fixture.sourcePositionMs))
            assertTrue("fixture must hold the source resolution before replacing the binding", fixture.resolving.value)

            releaseResolution(current, fixture, fixture.allohaVoiceOne, requireNotNull(fixture.sourcePositionMs))
            compose.waitUntil(20_000) { onMain { loadRequests(session?.player) == 2L } }
            compose.waitUntil(20_000) { onMain { session?.player?.isPlaying == true } }
            assertNearThirtySeconds(onMain { requireNotNull(session).player.currentPosition })
            assertTrue("source reload failed: ${fixture.failures}", fixture.failures.isEmpty())

            pauseAtThirtySeconds(requireNotNull(session).player)
            selectVoiceWithNativePopup(fixture)
            compose.waitUntil { fixture.voiceTarget != null }
            assertEquals(fixture.allohaVoiceTwo, fixture.voiceTarget)
            assertNearThirtySeconds(requireNotNull(fixture.voicePositionMs))
            assertTrue("fixture must hold the voice resolution before replacing the binding", fixture.resolving.value)

            releaseResolution(current, fixture, fixture.allohaVoiceTwo, requireNotNull(fixture.voicePositionMs))
            compose.waitUntil(20_000) { onMain { loadRequests(session?.player) == 3L } }
            compose.waitUntil(20_000) { onMain { session?.player?.isPlaying == true } }
            assertNearThirtySeconds(onMain { requireNotNull(session).player.currentPosition })
            assertTrue("voice reload failed: ${fixture.failures}", fixture.failures.isEmpty())
        } finally {
            compose.runOnIdle { visible.value = false }
            media.delete()
        }
    }

    private fun assertSpeedBindingRecoversFromLoadingCarryover(fixture: Fixture) {
        onMain {
            fixture.speed.isEnabled = false
            fixture.speed.isFocusable = false
            fixture.playerView.bindYummyController(
                createNativeVideoPlayerControllerBinding(fixture.latestBinding, requireNotNull(fixture.session))
            )
            assertTrue(fixture.speed.isEnabled)
            assertTrue(fixture.speed.isFocusable)
            fixture.speed.performClick()
            assertTrue(fixture.playerView.hasPlayerPopupMenu())
            assertTrue(fixture.playerView.handlePlayerPopupInput(InputAction.Down))
            assertTrue(fixture.playerView.handlePlayerPopupInput(InputAction.Confirm))
        }
        compose.waitUntil { fixture.settings.value.playerSpeed != AppSettings().playerSpeed }
    }

    private fun selectSourceWithNativePopup(fixture: Fixture) = onMain {
        assertTrue(fixture.source.performClick())
        assertTrue(fixture.playerView.hasPlayerPopupMenu())
        assertTrue(fixture.playerView.handlePlayerPopupInput(InputAction.Down))
        assertTrue(fixture.playerView.handlePlayerPopupInput(InputAction.Confirm))
    }

    private fun selectVoiceWithNativePopup(fixture: Fixture) = onMain {
        assertTrue(fixture.voice.performClick())
        assertTrue(fixture.playerView.hasPlayerPopupMenu())
        assertTrue(fixture.playerView.handlePlayerPopupInput(InputAction.Down))
        assertTrue(fixture.playerView.handlePlayerPopupInput(InputAction.Confirm))
    }

    private fun releaseResolution(
        current: androidx.compose.runtime.MutableState<NativeVideoPlayerRuntimeBinding>,
        fixture: Fixture,
        target: VideoVariant,
        positionMs: Long,
    ) = compose.runOnIdle {
        val latest = current.value
        current.value = latest.copy(
            currentVideo = target,
            stream = latest.stream.copy(playbackGeneration = latest.stream.playbackGeneration + 1L),
            startPositionMs = positionMs,
            playWhenReady = true,
            selectedKey = target.groupKey,
            selectedSourceKey = fixture.sourceKey(target),
            playbackSelectionResolving = false,
        )
        fixture.resolving.value = false
    }

    private fun pauseAtThirtySeconds(player: Player) {
        onMain {
            player.pause()
            player.seekTo(30_000L)
        }
        compose.waitUntil(10_000) { onMain { abs(player.currentPosition - 30_000L) <= 1_000L } }
        assertFalse(onMain { player.isPlaying })
    }

    private fun assertNearThirtySeconds(positionMs: Long) {
        assertTrue("expected resumed position near 30 seconds, was $positionMs", abs(positionMs - 30_000L) <= 1_000L)
    }

    private fun themedPlayerView(context: android.content.Context): PlayerView {
        val themed = ContextThemeWrapper(context, R.style.Theme_YummyDroid_Player)
        return LayoutInflater.from(themed).inflate(
            R.layout.yummy_player_view,
            FrameLayout(themed),
            false,
        ) as PlayerView
    }

    private fun loadRequests(player: Player?): Long = Regex("loadRequests=(\\d+)")
        .find(player?.playbackLoadDiagnosticsText().orEmpty())?.groupValues?.get(1)?.toLongOrNull() ?: -1L

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = runCatching(block) }
        return requireNotNull(result).getOrThrow()
    }

    private class Fixture(private val fileUrl: String) {
        val cvhVoiceOne = video(1, "CVH", "Voice one")
        val allohaVoiceOne = video(2, "Alloha", "Voice one")
        val allohaVoiceTwo = video(3, "Alloha", "Voice two")
        val groups = linkedMapOf(
            cvhVoiceOne.groupKey to listOf(cvhVoiceOne),
            allohaVoiceOne.groupKey to listOf(allohaVoiceOne),
            allohaVoiceTwo.groupKey to listOf(allohaVoiceTwo),
        )
        val sourceOptions = listOf(
            SourceOption("cvh", "CVH", cvhVoiceOne),
            SourceOption("alloha", "Alloha", allohaVoiceOne),
        )
        val settings = mutableStateOf(AppSettings(autoplayNextEpisode = false, skipOpeningsAndEndings = false))
        val resolving = mutableStateOf(false)
        val failures = mutableListOf<String>()
        var sourceTarget: VideoVariant? = null
        var sourcePositionMs: Long? = null
        var voiceTarget: VideoVariant? = null
        var voicePositionMs: Long? = null
        lateinit var playerView: PlayerView
        lateinit var session: NativeVideoPlayerRuntimeSession
        lateinit var latestBinding: NativeVideoPlayerRuntimeBinding
        lateinit var currentBinding: androidx.compose.runtime.MutableState<NativeVideoPlayerRuntimeBinding>
        val source: View get() = playerView.findViewById(R.id.yummy_player_source)
        val voice: View get() = playerView.findViewById(R.id.yummy_player_voice)
        val speed: View get() = playerView.findViewById(R.id.yummy_player_speed)
        val subtitle: View get() = playerView.findViewById(R.id.yummy_player_subtitles)

        fun sourceKey(video: VideoVariant): String = sourceOptions.first { it.video.player == video.player }.key

        fun binding(video: VideoVariant): NativeVideoPlayerRuntimeBinding = NativeVideoPlayerRuntimeBinding(
            stream = ResolvedVideoStream(fileUrl, "video/mp4", emptyMap()),
            animeTitle = "Paused selection fixture",
            currentVideo = video,
            interactive = false,
            settings = settings.value,
            startPositionMs = 0L,
            playbackPreferredQuality = PreferredQuality.Auto,
            playbackMetadataLoading = false,
            playbackSelectionResolving = resolving.value,
            playWhenReady = false,
            groups = groups,
            selectedKey = video.groupKey,
            sourceOptions = sourceOptions,
            selectedSourceKey = sourceKey(video),
            previousVideo = null,
            nextVideo = null,
            allowSubscription = false,
            subscriptionActive = false,
            onToggleSubscription = {},
            onSelectGroup = { _, replacement, positionMs ->
                voiceTarget = replacement
                voicePositionMs = positionMs
                resolving.value = true
                currentBinding.value = currentBinding.value.copy(playbackSelectionResolving = true)
            },
            onSelectSource = { target, positionMs ->
                sourceTarget = target
                sourcePositionMs = positionMs
                resolving.value = true
                currentBinding.value = currentBinding.value.copy(playbackSelectionResolving = true)
            },
            onPlayVideoAt = { _, _ -> },
            onPlayVideoAtQuality = { _, _, _ -> },
            onPlaybackFailed = { _, positionMs, failure -> failures += "$failure@$positionMs" },
            onPlaybackStarted = {},
            onPlaybackEnded = {},
            onPlaybackProgress = { _, _, _ -> },
            canUsePictureInPicture = false,
            isInPictureInPicture = false,
            onEnterPictureInPicture = {},
            onSettingsChange = {
                settings.value = it
                currentBinding.value = currentBinding.value.copy(settings = it)
            },
            onBack = {},
            offlineMode = false,
            modifier = Modifier,
            playerControlFocusToRestoreId = null,
            keepControlsVisibleAfterReady = false,
            onRememberPlayerControlFocus = {},
            onPlayerControlFocusRestored = {},
            onKeepControlsVisibleAfterReadyRequested = {},
            onControlsKeptVisibleAfterReady = {},
        ).also { latestBinding = it }

        private fun video(id: Long, player: String, dubbing: String) = VideoVariant(
            id = id.toLong(), animeId = 1L, player = player, dubbing = dubbing, episode = "1",
            url = fileUrl, index = 0, durationSeconds = 180, views = 0L,
        )
    }
}
