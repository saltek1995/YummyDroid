package me.yummydroid.app.ui

import androidx.media3.common.DeviceInfo
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import android.view.View
import me.yummydroid.app.R
import me.yummydroid.app.data.SourceQuality
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class NativeVideoPlayerTest {
    @Test
    fun adaptiveSelectionDoesNotGuessFirstSelectedTrackBeforeFormatArrives() {
        val probe = QualityPlayerProbe()
        assertEquals(null, probe.player.currentQualityKey())
        probe.videoFormat = probe.formats[0]
        assertEquals("144:-1:144p", probe.player.currentQualityKey())
        probe.videoFormat = probe.formats[1]
        assertEquals("720:-1:720p", probe.player.currentQualityKey())
    }

    @Test
    fun explicitCurrentAdaptiveHeightInstallsOverrideAndRepeatedRequestIsIdempotent() {
        val probe = QualityPlayerProbe()
        probe.videoFormat = probe.formats[0]
        val options = probe.tracks.videoQualityOptions()
        val requested = options.single { it.height == 144 }
        assertFalse(probe.player.hasQualityOverride(requested))
        probe.player.applyPlaybackQualityPreference(PreferredQuality.P144, options)
        assertTrue(probe.player.hasQualityOverride(requested))
        assertEquals(1, probe.parameterUpdates)
        probe.player.applyPlaybackQualityPreference(PreferredQuality.P144, options)
        assertEquals(1, probe.parameterUpdates)
    }

    @Test
    fun fixedQualityUsesPlayableTracksWhileStreamMetadataKeepsItsAuthoritativeMenu() {
        val probe = QualityPlayerProbe()
        val trackOptions = probe.tracks.videoQualityOptions()
        val streamOptions = listOf(720, 360, 144).map { SourceQuality(height = it) }.sourceQualityOptions()
        val menuOptions = resolvedOnlineQualityOptions(streamOptions, trackOptions, emptyList())
        assertEquals(streamOptions, menuOptions)
        assertTrue(menuOptions.all { it.group == null })
        probe.player.applyPlaybackQualityPreference(PreferredQuality.P720, trackOptions)
        assertTrue(probe.player.hasQualityOverride(trackOptions.single { it.height == 720 }))
    }

    @Test
    fun autoAfterFixedQualityClearsOnlyVideoOverride() {
        val probe = QualityPlayerProbe()
        val audioGroup = TrackGroup(Format.Builder().setSampleMimeType("audio/mp4a-latm").build())
        val audioOverride = TrackSelectionOverride(audioGroup, 0)
        probe.parameters = probe.parameters.buildUpon().addOverride(audioOverride).build()
        probe.player.applyPlaybackQualityPreference(PreferredQuality.P144, probe.tracks.videoQualityOptions())
        assertEquals(2, probe.parameters.overrides.size)
        probe.player.applyPlaybackQualityPreference(PreferredQuality.Auto, probe.tracks.videoQualityOptions())
        assertEquals(listOf(audioOverride), probe.parameters.overrides.values.toList())
        val updates = probe.parameterUpdates
        probe.player.applyPlaybackQualityPreference(PreferredQuality.Auto, probe.tracks.videoQualityOptions())
        assertEquals(updates, probe.parameterUpdates)
    }

    @Test
    fun clickingCurrentAutomaticHeightStillRequestsFixedQuality() {
        val probe = QualityPlayerProbe()
        probe.videoFormat = probe.formats[0]
        assertEquals(PreferredQuality.P144, clickQualityOption(probe, 144))
    }

    @Test
    fun sourceOnlyQualityStillRoutesThroughPreferredQualityResolution() {
        val probe = QualityPlayerProbe()
        assertEquals(PreferredQuality.P360, clickQualityOption(probe, 360))
        assertEquals(0, probe.parameterUpdates)
    }

    private fun clickQualityOption(probe: QualityPlayerProbe, height: Int): PreferredQuality? {
        val anchor = View(RuntimeEnvironment.getApplication())
        anchor.id = R.id.yummy_player_quality
        anchor.setTag(R.id.yummy_player_quality, "height:144")
        var requested: PreferredQuality? = null
        val popup = prepareQualityPopup(
            anchor = anchor,
            player = probe.player,
            options = listOf(SourceQuality(height = height)).sourceQualityOptions(),
            selectedQualityKey = "height:144",
            onSelectedQualityKeyChange = {},
            onSelectLocalQuality = { error("Unexpected offline route") },
            onSelectPreferredQuality = { requested = it },
        )
        // Invoke the real popup callback without showing a window or preparing media.
        val callbackField = PopupMenu::class.java.getDeclaredField("itemClickListener").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val callback = callbackField.get(popup) as (PlayerPopupMenuItem) -> Boolean
        assertTrue(callback(popup.menu.items.single()))
        return requested
    }

    private class QualityPlayerProbe {
        val formats = listOf(144, 720).map { height ->
            Format.Builder().setSampleMimeType("video/avc").setHeight(height).build()
        }
        val tracks = Tracks(listOf(Tracks.Group(
            TrackGroup(*formats.toTypedArray()),
            true,
            intArrayOf(C.FORMAT_HANDLED, C.FORMAT_HANDLED),
            booleanArrayOf(true, true),
        )))
        var videoFormat: Format? = null
        var parameters = TrackSelectionParameters.Builder(RuntimeEnvironment.getApplication()).build()
        var parameterUpdates = 0
        val player = Proxy.newProxyInstance(
            ExoPlayer::class.java.classLoader,
            arrayOf(ExoPlayer::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getCurrentTracks" -> tracks
                "getVideoFormat" -> videoFormat
                "getTrackSelectionParameters" -> parameters
                "setTrackSelectionParameters" -> {
                    parameters = args!![0] as TrackSelectionParameters
                    parameterUpdates++
                    null
                }
                else -> error("Unexpected player call: ${method.name}")
            }
        } as ExoPlayer
    }

    @Test
    fun activeTransferGetsTimeForInPlaceRecoveryWithoutDisablingStallDetection() {
        assertEquals(60_000L, playbackNetworkStallTimeoutMs(10_000, isLoading = true))
        assertEquals(10_000L, playbackNetworkStallTimeoutMs(10_000, isLoading = false))
        val tracker = PlaybackStallTracker(0, 1000, 1000)
        assertFalse(tracker.isStalled(30_000, 1000, 1000, true, 60_000, receivedBytes = 2048))
        assertFalse(tracker.isStalled(89_999, 1000, 1000, true, 60_000, receivedBytes = 2048))
        assertTrue(tracker.isStalled(90_000, 1000, 1000, true, 60_000, receivedBytes = 2048))
    }

    @Test
    fun audioFocusSuppressionDoesNotCountAsStalledPlayback() {
        val tracker = PlaybackStallTracker(0, 1000, 1000)
        val suppressed = playbackStallMonitoringEnabled(true, Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS)
        assertFalse(suppressed)
        assertFalse(tracker.isStalled(120_000, 1000, 1000, suppressed, 10_000))
        val enabled = playbackStallMonitoringEnabled(true, Player.PLAYBACK_SUPPRESSION_REASON_NONE)
        assertTrue(enabled)
        assertFalse(tracker.isStalled(129_999, 1000, 1000, enabled, 10_000))
        assertTrue(tracker.isStalled(130_000, 1000, 1000, enabled, 10_000))
    }

    @Test
    fun stallDetectionWaitsForInactivityAfterBufferGrowthPauseAndLatestSeek() {
        val tracker = PlaybackStallTracker(0, 60_000, 60_000)
        assertFalse(tracker.isStalled(10_000, 60_000, 65_000, true, 10_000))
        assertFalse(tracker.isStalled(20_000, 60_000, 65_000, false, 10_000))
        assertFalse(tracker.isStalled(30_000, 0, 0, true, 10_000))
        assertFalse(tracker.isStalled(39_000, 45_000, 45_000, true, 10_000))
        assertFalse(tracker.isStalled(48_999, 45_000, 45_000, true, 10_000))
        assertTrue(tracker.isStalled(49_000, 45_000, 45_000, true, 10_000))
    }

    @Test
    fun refreshedIdenticalUrlRestartsLoadingAndErrorReportingButMetadataDoesNot() {
        val stream = ResolvedVideoStream("https://stream.test/video.m3u8", "application/x-mpegURL", emptyMap(),
            playbackGeneration = 10L)
        val refreshed = stream.copy(playbackGeneration = 11L)
        assertNotEquals(stream.playbackLoadIdentity(), refreshed.playbackLoadIdentity())
        assertNotEquals(stream.playbackEventIdentity(), refreshed.playbackEventIdentity())
        val metadata = stream.copy(subtitles = listOf(ResolvedSubtitleTrack(uri = "https://stream.test/sub.vtt")))
        assertEquals(stream.playbackLoadIdentity(), metadata.playbackLoadIdentity())
        assertEquals(stream.playbackEventIdentity(), metadata.playbackEventIdentity())
    }

    @Test
    fun typedFallbackPreservesItsFormatHeadersAndHeightWhenMetadataArrives() {
        val alternative = me.yummydroid.app.data.PlaybackStreamAlternative(
            "https://backup.test/video.mp4", "video/mp4", mapOf("Referer" to "https://provider.test"), 720)
        val primary = ResolvedVideoStream("https://stream.test/video.mpd", "application/dash+xml", emptyMap(),
            selectedVideoHeight = 1080, alternatives = listOf(alternative), fallbackUrls = listOf(alternative.url),
            playbackGeneration = 2L)
        assertEquals(listOf(alternative), limitedPlaybackAlternatives(primary))
        val active = primary.copy(url = alternative.url, mimeType = alternative.mimeType,
            headers = alternative.headers, selectedVideoHeight = alternative.videoHeight)
        val enriched = active.withLatestPlaybackMetadata(primary.copy(
            subtitles = listOf(ResolvedSubtitleTrack(uri = "https://stream.test/sub.vtt"))))
        assertEquals(active.playbackLoadIdentity(), enriched.playbackLoadIdentity())
        assertEquals(720, enriched.selectedVideoHeight)
        assertEquals("video/mp4", enriched.mimeType)
        assertEquals(1, enriched.subtitles.size)
    }

    @Test
    fun malformedMediaCanUseSourceFallbackButDecoderFailuresCannot() {
        for (code in listOf(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED)) {
            assertTrue(isRecoverableSourceErrorCode(code))
        }
        assertFalse(isRecoverableSourceErrorCode(PlaybackException.ERROR_CODE_DECODING_FAILED))
        assertFalse(isRecoverableSourceErrorCode(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED))
    }

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
    fun subtitleMediaItemUpdatesAreSkippedDuringActivePlayback() {
        assertFalse(
            shouldUpdateCurrentMediaItemForResolvedSubtitles(
                isPlaying = true,
                playWhenReady = true,
                playbackState = Player.STATE_READY,
            ),
        )
        assertFalse(
            shouldUpdateCurrentMediaItemForResolvedSubtitles(
                isPlaying = false,
                playWhenReady = true,
                playbackState = Player.STATE_BUFFERING,
            ),
        )
        assertTrue(
            shouldUpdateCurrentMediaItemForResolvedSubtitles(
                isPlaying = false,
                playWhenReady = false,
                playbackState = Player.STATE_READY,
            ),
        )
    }

    @Test
    fun automaticQualityDisplayFollowsAdaptationInsteadOfRetainingInitialHeight() {
        val options = listOf(qualityOption(144), qualityOption(720))
        val initial = resolvePlaybackQualitySelection(
            resolvedSourceKey = null,
            qualityOptions = options,
            trackOptions = options,
            playbackPreferredQuality = PreferredQuality.Auto,
            actualQualityKey = "144p",
        )
        assertEquals("height:144", initial.key)
        val adapted = resolvePlaybackQualitySelection(
            resolvedSourceKey = "height:144",
            selectedQualityKey = initial.key,
            qualityOptions = options,
            trackOptions = options,
            playbackPreferredQuality = PreferredQuality.Auto,
            actualQualityKey = "720p",
        )
        assertEquals("height:720", adapted.key)
        assertTrue(adapted.shouldUpdateDisplayMode)
    }

    @Test
    fun nativeTracksStateIgnoresEventsWithSameOptionsIdentity() {
        assertFalse(shouldUpdateNativeTracksState(currentOptionsIdentity = 42, nextOptionsIdentity = 42))
        assertTrue(shouldUpdateNativeTracksState(currentOptionsIdentity = 42, nextOptionsIdentity = 43))
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
    fun loadingIndicatorAnchorUsesPlayPauseButtonCenterInsidePlayerView() {
        assertEquals(
            NativePlayerLoadingIndicatorAnchor(centerX = 125, centerY = 74, diameter = 50),
            nativePlayerLoadingIndicatorAnchorPx(
                playerLeftOnScreen = 20,
                playerTopOnScreen = 10,
                playPauseLeftOnScreen = 120,
                playPauseTopOnScreen = 59,
                playPauseWidth = 50,
                playPauseHeight = 50,
            ),
        )
        assertEquals(
            null,
            nativePlayerLoadingIndicatorAnchorPx(
                playerLeftOnScreen = 0,
                playerTopOnScreen = 0,
                playPauseLeftOnScreen = 0,
                playPauseTopOnScreen = 0,
                playPauseWidth = 0,
                playPauseHeight = 50,
            ),
        )
    }

    @Test
    fun subtitleAvailabilityUsesOnlyOpenableSubtitleOptionsForCurrentSourceMarker() {
        assertEquals(
            NativePlayerSubtitleAvailability(
                currentSourceHasUsableSubtitles = false,
                subtitlesLoading = true,
            ),
            nativePlayerSubtitleAvailability(
                subtitleOptionCount = 0,
                playbackMetadataLoading = true,
                hasPendingSubtitleCandidates = true,
            ),
        )
        assertEquals(
            NativePlayerSubtitleAvailability(
                currentSourceHasUsableSubtitles = true,
                subtitlesLoading = false,
            ),
            nativePlayerSubtitleAvailability(
                subtitleOptionCount = 1,
                playbackMetadataLoading = true,
                hasPendingSubtitleCandidates = true,
            ),
        )
    }

    @Test
    fun resolvedStreamQualityTakesPriority() {
        val selection = resolvePlaybackQualitySelection(
            resolvedSourceKey = "height:720",
            qualityOptions = listOf(qualityOption(1080), qualityOption(720)),
            trackOptions = emptyList(),
            playbackPreferredQuality = PreferredQuality.P1080,
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
            actualQualityKey = "height:1080",
        )

        assertEquals("height:480", selection.key)
        assertFalse(selection.shouldUpdateDisplayMode)
    }

    @Test
    fun playbackPreferenceSelectsTrackBeforeActualObservedQuality() {
        val selection = resolvePlaybackQualitySelection(
            resolvedSourceKey = null,
            qualityOptions = listOf(qualityOption(1080), qualityOption(720)),
            trackOptions = emptyList(),
            playbackPreferredQuality = PreferredQuality.P720,
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
            actualQualityKey = "720p",
        )

        assertEquals("height:720", selection.key)
        assertTrue(selection.shouldUpdateDisplayMode)
    }

    @Test
    fun automaticPlaybackDoesNotSelectAFixedInitialTrack() {
        assertEquals(
            null,
            resolveInitialNativeQualityKey(
                selectedLocalQualityKey = null,
                streamSelectedQualityKey = null,
                qualityOptions = listOf(qualityOption(1080), qualityOption(720)),
                playbackPreferredQuality = PreferredQuality.Auto,
            ),
        )
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
