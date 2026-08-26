package me.yummydroid.app.ui

import androidx.media3.common.DeviceInfo
import androidx.media3.common.Player
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.ResolvedSubtitleTrack
import me.yummydroid.app.data.ResolvedVideoStream

class NativeVideoPlayerTest {
    @Test
    fun controllerRestoresOnlyWhenActuallyLeavingPictureInPicture() {
        assertFalse(shouldRestoreControllerAfterPictureInPicture(previous = null, current = false))
        assertFalse(shouldRestoreControllerAfterPictureInPicture(previous = false, current = false))
        assertFalse(shouldRestoreControllerAfterPictureInPicture(previous = false, current = true))
        assertFalse(shouldRestoreControllerAfterPictureInPicture(previous = true, current = true))
        assertTrue(shouldRestoreControllerAfterPictureInPicture(previous = true, current = false))
    }

    @Test
    fun playerViewReattachesToLocalPlayerAfterRemotePlaybackEnds() {
        val localPlayer = playerStub()
        val playbackPlayer = playerStub()

        assertSame(
            playbackPlayer,
            selectNativePlayerViewPlayer(localPlayer, playbackPlayer, isRemotePlayback = true),
        )
        assertSame(
            localPlayer,
            selectNativePlayerViewPlayer(localPlayer, playbackPlayer, isRemotePlayback = false),
        )
    }

    @Test
    fun playbackIntentDoesNotReplayAlreadyRequestedPlayback() {
        assertEquals(
            PlayerPlaybackIntentAction.Play,
            playerPlaybackIntentAction(
                requestedPlayWhenReady = true,
                playerPlayWhenReady = false,
                playbackState = Player.STATE_READY,
            ),
        )
        assertEquals(
            PlayerPlaybackIntentAction.None,
            playerPlaybackIntentAction(
                requestedPlayWhenReady = true,
                playerPlayWhenReady = true,
                playbackState = Player.STATE_READY,
            ),
        )
        assertEquals(
            PlayerPlaybackIntentAction.Pause,
            playerPlaybackIntentAction(
                requestedPlayWhenReady = false,
                playerPlayWhenReady = true,
                playbackState = Player.STATE_READY,
            ),
        )
        assertEquals(
            PlayerPlaybackIntentAction.None,
            playerPlaybackIntentAction(
                requestedPlayWhenReady = true,
                playerPlayWhenReady = false,
                playbackState = Player.STATE_ENDED,
            ),
        )
    }

    @Test
    fun nativePlaybackReadinessWaitStopsForTerminalStates() {
        assertFalse(shouldWaitForNativePlaybackReady(Player.STATE_READY))
        assertFalse(shouldWaitForNativePlaybackReady(Player.STATE_ENDED))
        assertFalse(shouldWaitForNativePlaybackReady(Player.STATE_IDLE))
        assertTrue(shouldWaitForNativePlaybackReady(Player.STATE_BUFFERING))
    }

    @Test
    fun nativePlaybackLoadIdentityIgnoresSubtitleOnlyStreamChanges() {
        val stream = ResolvedVideoStream(
            url = "https://stream.test/video.m3u8",
            mimeType = "application/x-mpegURL",
            headers = mapOf("Referer" to "https://site.test"),
        )

        assertEquals(
            stream.playbackLoadIdentity(),
            stream.copy(
                subtitles = listOf(ResolvedSubtitleTrack(uri = "https://stream.test/sub.vtt")),
                hasEmbeddedSubtitles = true,
                sourceSubtitleSourceKeys = setOf("cvh"),
            ).playbackLoadIdentity(),
        )
        assertNotEquals(
            stream.playbackLoadIdentity(),
            stream.copy(headers = mapOf("Referer" to "https://other.test")).playbackLoadIdentity(),
        )
    }

    @Test
    fun castReceiverOwnsAutomaticEpisodeAdvanceForRemotePlayback() {
        assertFalse(
            shouldAutoAdvanceEpisode(
                playbackState = Player.STATE_ENDED,
                autoplayNextEpisode = true,
                playbackType = DeviceInfo.PLAYBACK_TYPE_REMOTE,
                alreadyReported = false,
            ),
        )
        assertTrue(
            shouldAutoAdvanceEpisode(
                playbackState = Player.STATE_ENDED,
                autoplayNextEpisode = true,
                playbackType = DeviceInfo.PLAYBACK_TYPE_LOCAL,
                alreadyReported = false,
            ),
        )
    }

    @Test
    fun loadingIsVisibleImmediatelyForResolvingAndPendingSelection() {
        assertTrue(
            shouldShowNativePlayerLoading(
                resolving = true,
                selectionPending = false,
                buffering = false,
            ),
        )
        assertTrue(
            shouldShowNativePlayerLoading(
                resolving = false,
                selectionPending = true,
                buffering = false,
            ),
        )
        assertTrue(
            shouldShowNativePlayerLoading(
                resolving = false,
                selectionPending = false,
                buffering = false,
                castConnectionPending = true,
            ),
        )
        assertFalse(
            shouldShowNativePlayerLoading(
                resolving = false,
                selectionPending = false,
                buffering = false,
            ),
        )
    }

    @Test
    fun remotePlaybackDoesNotKeepLocalBufferingIndicatorVisible() {
        assertFalse(
            shouldShowNativePlayerLoading(
                resolving = false,
                selectionPending = false,
                buffering = true,
                isRemotePlayback = true,
            ),
        )
        assertTrue(
            shouldShowNativePlayerLoading(
                resolving = false,
                selectionPending = true,
                buffering = true,
                isRemotePlayback = true,
            ),
        )
        assertTrue(
            shouldShowNativePlayerLoading(
                resolving = false,
                selectionPending = false,
                buffering = true,
                castConnectionPending = true,
                isRemotePlayback = true,
            ),
        )
    }

    @Test
    fun loadingIndicatorUsesActualPlayPauseButtonSize() {
        assertEquals(48, nativePlayerLoadingIndicatorSizePx(width = 48, height = 48))
        assertEquals(50, nativePlayerLoadingIndicatorSizePx(width = 50, height = 50))
        assertEquals(50, nativePlayerLoadingIndicatorSizePx(width = 48, height = 50))
        assertEquals(0, nativePlayerLoadingIndicatorSizePx(width = 0, height = 0))
    }

    @Test
    fun resolvedStreamQualityTakesPriority() {
        val selection = resolvePlaybackQualitySelection(
            resolvedSourceKey = "height:720",
            qualityOptions = listOf(qualityOption(1080), qualityOption(720)),
            trackOptions = emptyList(),
            playbackPreferredQuality = PreferredQuality.P1080,
            defaultQuality = PreferredQuality.Auto,
            actualQualityKey = "height:1080",
        )

        assertEquals("height:720", selection.key)
        assertFalse(selection.shouldUpdateDisplayMode)
    }

    @Test
    fun currentManualQualityTakesPriorityOverResolvedStreamQuality() {
        val selection = resolvePlaybackQualitySelection(
            resolvedSourceKey = "height:1080",
            selectedQualityKey = "height:480",
            qualityOptions = listOf(qualityOption(1080), qualityOption(720), qualityOption(480)),
            trackOptions = listOf(qualityOption(1080), qualityOption(720), qualityOption(480)),
            playbackPreferredQuality = PreferredQuality.P1080,
            defaultQuality = PreferredQuality.Auto,
            actualQualityKey = "height:1080",
        )

        assertEquals("height:480", selection.key)
        assertFalse(selection.shouldUpdateDisplayMode)
    }

    @Test
    fun explicitPlaybackPreferenceTakesPriorityOverDefault() {
        val selection = resolvePlaybackQualitySelection(
            resolvedSourceKey = null,
            qualityOptions = listOf(qualityOption(1080), qualityOption(720)),
            trackOptions = emptyList(),
            playbackPreferredQuality = PreferredQuality.P720,
            defaultQuality = PreferredQuality.P1080,
            actualQualityKey = "height:1080",
        )

        assertEquals("height:720", selection.key)
        assertFalse(selection.shouldUpdateDisplayMode)
    }

    private fun playerStub(): Player {
        return Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
        ) { _, method, _ ->
            when (method.returnType) {
                Boolean::class.javaPrimitiveType -> false
                Int::class.javaPrimitiveType -> 0
                Long::class.javaPrimitiveType -> 0L
                Float::class.javaPrimitiveType -> 0f
                Double::class.javaPrimitiveType -> 0.0
                else -> null
            }
        } as Player
    }

    @Test
    fun actualTrackQualityIsUsedInAutomaticMode() {
        val selection = resolvePlaybackQualitySelection(
            resolvedSourceKey = null,
            qualityOptions = listOf(qualityOption(1080), qualityOption(720)),
            trackOptions = listOf(qualityOption(720)),
            playbackPreferredQuality = PreferredQuality.Auto,
            defaultQuality = PreferredQuality.Auto,
            actualQualityKey = "720p",
        )

        assertEquals("height:720", selection.key)
        assertTrue(selection.shouldUpdateDisplayMode)
    }

    @Test
    fun initialQualityKeepsLocalSelectionAheadOfResolvedAndPreferredQuality() {
        assertEquals(
            "local:1080",
            resolveInitialNativeQualityKey(
                selectedLocalQualityKey = "local:1080",
                streamSelectedQualityKey = "height:720",
                qualityOptions = listOf(qualityOption(1080), qualityOption(720)),
                playbackPreferredQuality = PreferredQuality.P1080,
                defaultQuality = PreferredQuality.Auto,
            ),
        )
    }

    @Test
    fun initialQualityIgnoresUnavailableResolvedHeight() {
        assertEquals(
            "height:1080",
            resolveInitialNativeQualityKey(
                selectedLocalQualityKey = null,
                streamSelectedQualityKey = "height:480",
                qualityOptions = listOf(qualityOption(1080), qualityOption(720)),
                playbackPreferredQuality = PreferredQuality.P1080,
                defaultQuality = PreferredQuality.Auto,
            ),
        )
    }

    @Test
    fun playbackFallbackUrlsAreDeduplicatedByMediaPathAndLimited() {
        val fallbackUrls = limitedPlaybackFallbackUrls(
            primaryUrl = "https://cdn.example/video/master.m3u8?token=primary",
            fallbackUrls = listOf(
                " https://cdn.example/video/master.m3u8?token=duplicate ",
                "https://cdn-a.example/video/master.m3u8?token=1",
                "https://cdn-b.example/video/master.m3u8?token=2",
                "https://cdn-c.example/video/master.m3u8?token=3",
                "https://cdn-d.example/video/master.m3u8?token=4",
            ),
        )

        assertEquals(
            listOf(
                "https://cdn-a.example/video/master.m3u8?token=1",
                "https://cdn-b.example/video/master.m3u8?token=2",
                "https://cdn-c.example/video/master.m3u8?token=3",
            ),
            fallbackUrls,
        )
    }

    private fun qualityOption(height: Int): QualityOption {
        return QualityOption(
            group = null,
            trackIndex = 0,
            label = "${height}p",
            height = height,
            bitrate = 0,
            key = "height:$height",
            preferredQuality = PreferredQuality.fromHeight(height),
        )
    }
}
