package me.yummydroid.app.ui

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.DeviceInfo
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.yummydroid.app.AppLog
import me.yummydroid.app.PipPlayerHandle
import me.yummydroid.app.PlaybackFailure
import me.yummydroid.app.PlaybackFailureKind
import me.yummydroid.app.PlayerPipController
import me.yummydroid.app.R
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.OfflineVideoFile
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.defaultVideoResolveClient
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.localizedString
import me.yummydroid.app.sourceSelectionKey

// NativeVideoPlayerControllerBinding
internal fun createNativeVideoPlayerControllerBinding(
    binding: NativeVideoPlayerRuntimeBinding,
    session: NativeVideoPlayerRuntimeSession,
): PlayerControllerBinding {
    val selection = session.selection
    return PlayerControllerBinding(
        player = session.player,
        playbackPlayer = session.playbackPlayer,
        castSession = session.castSession,
        isRemotePlayback = session.castSession.isRemotePlayback.value,
        stream = binding.stream,
        animeTitle = binding.animeTitle,
        currentVideo = binding.currentVideo,
        isLocalPlayback = binding.stream.url.startsWith("file:", ignoreCase = true) ||
            binding.stream.url.startsWith("content:", ignoreCase = true) ||
            binding.currentVideo.localPlaybackUrl.isNotBlank(),
        groups = binding.groups,
        selectedKey = binding.selectedKey,
        sourceOptions = selection.playbackSourceOptions,
        selectedSourceKey = binding.selectedSourceKey,
        previousVideo = binding.previousVideo,
        nextVideo = binding.nextVideo,
        allowSubscription = binding.allowSubscription,
        subscriptionActive = binding.subscriptionActive,
        onToggleSubscription = binding.onToggleSubscription,
        qualityOptions = selection.qualityOptions,
        selectedQualityKey = selection.selectedQualityKey,
        onSelectedQualityKeyChange = selection.onSelectedQualityKeyChanged,
        subtitleOptions = selection.subtitleOptions,
        subtitlesLoading = selection.subtitlesLoading,
        selectedSubtitleKey = selection.selectedSubtitleKey,
        onSelectedSubtitleKeyChange = selection.onControllerSelectedSubtitleKeyChanged,
        onSelectLocalQuality = createLocalQualitySelectionHandler(binding, session),
        onSelectPreferredQuality = createPreferredQualitySelectionHandler(binding, session),
        onSelectGroup = createGroupSelectionHandler(binding),
        onSelectSource = createSourceSelectionHandler(binding),
        onPlayVideoAt = binding.onPlayVideoAt,
        canUsePictureInPicture = binding.canUsePictureInPicture,
        onEnterPictureInPicture = binding.onEnterPictureInPicture,
        settings = binding.settings,
        skipControlsTimelineReady = session.skipControlsTimelineReady.value,
        texts = session.playerControlTexts,
        onSettingsChange = binding.onSettingsChange,
        onBack = binding.onBack,
        onRequestPlay = session.playbackActions::requestStart,
        onPausePlayback = session.playbackActions::pause,
        onRememberPlayerControlFocus = binding.onRememberPlayerControlFocus,
    )
}

private fun createLocalQualitySelectionHandler(
    binding: NativeVideoPlayerRuntimeBinding,
    session: NativeVideoPlayerRuntimeSession,
): (OfflineVideoFile) -> Unit = { localFile ->
    val positionMs = session.playbackPlayer.currentPosition.coerceAtLeast(0L)
    binding.onKeepControlsVisibleAfterReadyRequested()
    session.playbackActions.pause()
    binding.onPlayVideoAt(binding.currentVideo.withOfflineFile(localFile), positionMs)
}

private fun createPreferredQualitySelectionHandler(
    binding: NativeVideoPlayerRuntimeBinding,
    session: NativeVideoPlayerRuntimeSession,
): (PreferredQuality) -> Unit = { preferredQuality ->
    val positionMs = session.playbackPlayer.currentPosition.coerceAtLeast(0L)
    binding.onKeepControlsVisibleAfterReadyRequested()
    session.playbackActions.pause()
    binding.onPlayVideoAtQuality(
        binding.currentVideo.withoutLocalPlayback(),
        positionMs,
        preferredQuality,
    )
}

private fun createGroupSelectionHandler(
    binding: NativeVideoPlayerRuntimeBinding,
): (String, VideoVariant?, Long) -> Unit = { groupKey, replacement, positionMs ->
    if (replacement != null) {
        binding.onKeepControlsVisibleAfterReadyRequested()
    }
    binding.onSelectGroup(groupKey, replacement, positionMs)
}

private fun createSourceSelectionHandler(
    binding: NativeVideoPlayerRuntimeBinding,
): (VideoVariant, Long) -> Unit = { source, positionMs ->
    if (source.sourceSelectionKey != binding.currentVideo.sourceSelectionKey) {
        binding.onKeepControlsVisibleAfterReadyRequested()
    }
    binding.onSelectSource(source, positionMs)
}

// NativeVideoPlayerFacade
@OptIn(UnstableApi::class)
@Composable
internal fun NativeVideoPlayer(
    stream: ResolvedVideoStream,
    animeTitle: String,
    currentVideo: VideoVariant,
    interactive: Boolean,
    settings: AppSettings,
    startPositionMs: Long,
    playbackPreferredQuality: PreferredQuality,
    playbackMetadataLoading: Boolean,
    groups: Map<String, List<VideoVariant>>,
    selectedKey: String?,
    sourceOptions: List<SourceOption>,
    selectedSourceKey: String?,
    previousVideo: VideoVariant?,
    nextVideo: VideoVariant?,
    allowSubscription: Boolean,
    subscriptionActive: Boolean,
    onToggleSubscription: () -> Unit,
    onSelectGroup: (String, VideoVariant?, Long) -> Unit,
    onSelectSource: (VideoVariant, Long) -> Unit,
    onPlayVideoAt: (VideoVariant, Long) -> Unit,
    onPlayVideoAtQuality: (VideoVariant, Long, PreferredQuality) -> Unit,
    onPlaybackFailed: (VideoVariant, Long, PlaybackFailure) -> Unit,
    onPlaybackStarted: (VideoVariant) -> Unit,
    onPlaybackEnded: (VideoVariant) -> Unit,
    onPlaybackProgress: (VideoVariant, Long, Long) -> Unit,
    canUsePictureInPicture: Boolean,
    isInPictureInPicture: Boolean,
    onEnterPictureInPicture: () -> Unit,
    onSettingsChange: (AppSettings) -> Unit,
    onBack: () -> Unit,
    onRegisterPlayerInputActionHandler: ((PlayerInputController?) -> Unit),
    offlineMode: Boolean,
    modifier: Modifier = Modifier,
    playerControlFocusToRestoreId: Int? = null,
    keepControlsVisibleAfterReady: Boolean = false,
    onRememberPlayerControlFocus: (Int) -> Unit = {},
    onPlayerControlFocusRestored: () -> Unit = {},
    onKeepControlsVisibleAfterReadyRequested: () -> Unit = {},
    onControlsKeptVisibleAfterReady: () -> Unit = {},
) {
    NativeVideoPlayerRuntime(
        NativeVideoPlayerRuntimeBinding(
            stream = stream,
            animeTitle = animeTitle,
            currentVideo = currentVideo,
            interactive = interactive,
            settings = settings,
            startPositionMs = startPositionMs,
            playbackPreferredQuality = playbackPreferredQuality,
            playbackMetadataLoading = playbackMetadataLoading,
            groups = groups,
            selectedKey = selectedKey,
            sourceOptions = sourceOptions,
            selectedSourceKey = selectedSourceKey,
            previousVideo = previousVideo,
            nextVideo = nextVideo,
            allowSubscription = allowSubscription,
            subscriptionActive = subscriptionActive,
            onToggleSubscription = onToggleSubscription,
            onSelectGroup = onSelectGroup,
            onSelectSource = onSelectSource,
            onPlayVideoAt = onPlayVideoAt,
            onPlayVideoAtQuality = onPlayVideoAtQuality,
            onPlaybackFailed = onPlaybackFailed,
            onPlaybackStarted = onPlaybackStarted,
            onPlaybackEnded = onPlaybackEnded,
            onPlaybackProgress = onPlaybackProgress,
            canUsePictureInPicture = canUsePictureInPicture,
            isInPictureInPicture = isInPictureInPicture,
            onEnterPictureInPicture = onEnterPictureInPicture,
            onSettingsChange = onSettingsChange,
            onBack = onBack,
            onRegisterPlayerInputActionHandler = onRegisterPlayerInputActionHandler,
            offlineMode = offlineMode,
            modifier = modifier,
            playerControlFocusToRestoreId = playerControlFocusToRestoreId,
            keepControlsVisibleAfterReady = keepControlsVisibleAfterReady,
            onRememberPlayerControlFocus = onRememberPlayerControlFocus,
            onPlayerControlFocusRestored = onPlayerControlFocusRestored,
            onKeepControlsVisibleAfterReadyRequested = onKeepControlsVisibleAfterReadyRequested,
            onControlsKeptVisibleAfterReady = onControlsKeptVisibleAfterReady,
        ),
    )
}

// NativeVideoPlayerLifecycle
internal data class PlaybackQualitySelection(
    val key: String?,
    val shouldUpdateDisplayMode: Boolean,
)

internal fun resolvePlaybackQualitySelection(
    resolvedSourceKey: String?,
    selectedQualityKey: String? = null,
    qualityOptions: List<QualityOption>,
    trackOptions: List<QualityOption>,
    playbackPreferredQuality: PreferredQuality,
    defaultQuality: PreferredQuality,
    actualQualityKey: String?,
): PlaybackQualitySelection {
    if (selectedQualityKey != null && qualityOptions.any { it.matchesSelectedQualityKey(selectedQualityKey) }) {
        return PlaybackQualitySelection(selectedQualityKey, shouldUpdateDisplayMode = false)
    }
    if (resolvedSourceKey != null && qualityOptions.any { it.matchesSelectedQualityKey(resolvedSourceKey) }) {
        return PlaybackQualitySelection(resolvedSourceKey, shouldUpdateDisplayMode = false)
    }
    val explicitPreferredQuality = playbackPreferredQuality.takeUnless { it == PreferredQuality.Auto }
        ?: defaultQuality.takeUnless { it == PreferredQuality.Auto }
    explicitPreferredQuality
        ?.let(qualityOptions::preferredOption)
        ?.let { preferredOption ->
            return PlaybackQualitySelection(preferredOption.qualityOptionIdentity(), shouldUpdateDisplayMode = false)
        }
    val selectedKey = trackOptions
        .firstOrNull { it.matchesSelectedQualityKey(actualQualityKey) }
        ?.qualityOptionIdentity()
        ?: actualQualityKey?.qualityIdentityFromLabel()
        ?: actualQualityKey
    return PlaybackQualitySelection(selectedKey, shouldUpdateDisplayMode = true)
}

internal class NativePlayerEventState(
    val playerView: () -> PlayerView?,
    val settings: () -> AppSettings,
    val qualityOptions: () -> List<QualityOption>,
    val selectedQualityKey: () -> String?,
    val playbackPreferredQuality: () -> PreferredQuality,
    val streamSelectedQualityKey: () -> String?,
    val fallbackSuppressedUntilMs: () -> Long,
    val onFallbackSuppressedUntilChanged: (Long) -> Unit,
    val skipControlsTimelineReady: () -> Boolean,
    val onSkipControlsTimelineReady: () -> Unit,
    val onTracksChanged: (Tracks) -> Unit,
    val onSelectedSubtitleKeyChanged: (String) -> Unit,
    val onSelectedQualityKeyChanged: (String?) -> Unit,
)

internal class NativePlayerEventCallbacks(
    val onPlaybackStarted: () -> Unit,
    val onPlaybackEnded: () -> Unit,
    val onBufferingTimeout: (Long) -> Unit,
    val onAutoAdvance: () -> Unit,
    val onPlaybackError: (Long, PlaybackException) -> Unit,
    val onProgressSnapshot: (Long, Long) -> Unit,
    val onDisplayModeUpdate: (VideoSize?) -> Unit,
    val onDispose: () -> Unit,
)

internal class NativePlayerLifecycleBinding(
    val player: Player,
    val localPlayer: ExoPlayer,
    val castSession: PlayerCastSession,
    val stream: ResolvedVideoStream,
    val pipPlayerHandle: PipPlayerHandle,
    val metadataDurationSeconds: Int?,
    val state: NativePlayerEventState,
    val callbacks: NativePlayerEventCallbacks,
)

@Composable
internal fun NativePlayerLifecycle(binding: NativePlayerLifecycleBinding) {
    val fallbackScope = rememberCoroutineScope()
    DisposableEffect(binding.player) {
        val listener = NativePlayerEventListener(binding, fallbackScope)
        PlayerPipController.registerPlayer(binding.pipPlayerHandle)
        binding.player.addListener(listener)
        listener.start()
        onDispose {
            listener.dispose()
            binding.callbacks.onProgressSnapshot(
                binding.player.currentPosition.coerceAtLeast(0L),
                binding.player.duration.normalizedDurationMs(),
            )
            binding.player.removeListener(listener)
            PlayerPipController.unregisterPlayer(binding.pipPlayerHandle)
            binding.callbacks.onDispose()
            binding.castSession.release()
        }
    }
}

@OptIn(UnstableApi::class)
private class NativePlayerEventListener(
    private val binding: NativePlayerLifecycleBinding,
    private val fallbackScope: CoroutineScope,
) : Player.Listener {
    private var fallbackReported = false
    private var autoAdvanceReported = false
    private var playbackStartedReported = false
    private var playbackEndedReported = false
    private var startupFallbackJob: Job? = null
    private var bufferingFallbackJob: Job? = null
    private val attemptedPlaybackUrlIdentities = linkedSetOf(playbackFallbackUrlIdentity(binding.stream.url))
    private val remainingPlaybackFallbackUrls = limitedPlaybackFallbackUrls(
        primaryUrl = binding.stream.url,
        fallbackUrls = binding.stream.fallbackUrls,
    ).toMutableList()

    override fun onEvents(player: Player, events: Player.Events) {
        if (!binding.state.skipControlsTimelineReady() && player.hasReadyTimeline()) {
            binding.state.onSkipControlsTimelineReady()
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        PlayerPipController.notifyPlayingChanged()
        if (isPlaying) reportPlaybackStarted()
        updateStartupFallback(binding.player.playbackState)
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        updateStartupFallback(binding.player.playbackState)
    }

    override fun onRenderedFirstFrame() {
        reportPlaybackStarted()
    }

    override fun onTracksChanged(currentTracks: Tracks) {
        if (binding.player.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE) return
        binding.state.onTracksChanged(currentTracks)
        val subtitleKey = currentTracks.currentSubtitleKey() ?: SUBTITLE_OFF_KEY
        binding.state.onSelectedSubtitleKeyChanged(subtitleKey)
        binding.state.playerView()
            ?.findViewById<android.view.View>(me.yummydroid.app.R.id.yummy_player_subtitles)
            ?.setTag(me.yummydroid.app.R.id.yummy_player_subtitles, subtitleKey)

        val selection = resolvePlaybackQualitySelection(
            resolvedSourceKey = binding.state.streamSelectedQualityKey(),
            selectedQualityKey = binding.state.selectedQualityKey(),
            qualityOptions = binding.state.qualityOptions(),
            trackOptions = currentTracks.videoQualityOptions(),
            playbackPreferredQuality = binding.state.playbackPreferredQuality(),
            defaultQuality = binding.state.settings().defaultQuality,
            actualQualityKey = binding.localPlayer.currentQualityKey(),
        )
        binding.state.onSelectedQualityKeyChanged(selection.key)
        binding.state.playerView()
            ?.findViewById<android.view.View>(me.yummydroid.app.R.id.yummy_player_quality)
            ?.setTag(me.yummydroid.app.R.id.yummy_player_quality, selection.key)
        if (selection.shouldUpdateDisplayMode) {
            binding.callbacks.onDisplayModeUpdate(null)
        }
    }

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        if (binding.player.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE) return
        binding.callbacks.onDisplayModeUpdate(videoSize)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        updateStartupFallback(playbackState)
        updateBufferingFallback(playbackState)
        if (playbackState == Player.STATE_ENDED && !playbackEndedReported) {
            playbackEndedReported = true
            binding.callbacks.onPlaybackEnded()
        }
        if (
            playbackState == Player.STATE_ENDED &&
            binding.state.settings().autoplayNextEpisode &&
            !autoAdvanceReported
        ) {
            autoAdvanceReported = true
            binding.callbacks.onAutoAdvance()
        }
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        binding.state.onFallbackSuppressedUntilChanged(
            SystemClock.elapsedRealtime() + PLAYBACK_SEEK_BUFFER_GRACE_MS,
        )
    }

    override fun onPlayerError(error: PlaybackException) {
        logPlaybackError(error)
        if (tryPlayNextStreamFallback(error)) {
            return
        }
        if (!fallbackReported) {
            startupFallbackJob?.cancel()
            startupFallbackJob = null
            bufferingFallbackJob?.cancel()
            bufferingFallbackJob = null
            fallbackReported = true
            binding.callbacks.onPlaybackError(
                binding.player.currentPosition.coerceAtLeast(0L),
                error,
            )
        }
    }

    private fun tryPlayNextStreamFallback(error: PlaybackException): Boolean {
        if (!error.isSourcePlaybackFailure()) return false
        while (remainingPlaybackFallbackUrls.isNotEmpty()) {
            val fallbackUrl = remainingPlaybackFallbackUrls.removeAt(0)
            if (!attemptedPlaybackUrlIdentities.add(playbackFallbackUrlIdentity(fallbackUrl))) continue
            if (fallbackUrl == binding.player.currentMediaItemUrl()) continue
            val shouldPlay = binding.player.playWhenReady || !playbackStartedReported
            val positionMs = binding.player.currentPosition.coerceAtLeast(0L)
            val fallbackStream = binding.stream.copy(
                url = fallbackUrl,
                fallbackUrls = remainingPlaybackFallbackUrls.toList(),
            )
            AppLog.w("YummyDroidPlayer", "Retrying playback fallback URL: ${fallbackUrl.safePlaybackLogUrl()}")
            bufferingFallbackJob?.cancel()
            bufferingFallbackJob = null
            binding.player.setMediaItem(
                fallbackStream.toMediaItem(binding.player.currentMediaItem?.mediaMetadata ?: MediaMetadata.EMPTY),
                positionMs,
            )
            binding.player.prepare()
            binding.player.playWhenReady = shouldPlay
            return true
        }
        if (binding.stream.fallbackUrls.isNotEmpty()) {
            AppLog.w(
                "YummyDroidPlayer",
                "Playback fallback URLs exhausted: attempted=${attemptedPlaybackUrlIdentities.size}",
            )
        }
        return false
    }

    fun dispose() {
        startupFallbackJob?.cancel()
        bufferingFallbackJob?.cancel()
    }

    private fun updateStartupFallback(playbackState: Int) {
        if (
            shouldSchedulePlaybackStartupFallback(
                playbackState = playbackState,
                playbackStartedReported = playbackStartedReported,
                fallbackReported = fallbackReported,
            )
        ) {
            if (startupFallbackJob == null) {
                startupFallbackJob = fallbackScope.launch {
                    reportStartupTimeoutIfNeeded()
                }
            }
        } else {
            startupFallbackJob?.cancel()
            startupFallbackJob = null
        }
    }

    private fun updateBufferingFallback(playbackState: Int) {
        if (shouldSchedulePlaybackBufferingFallback(playbackState, fallbackReported)) {
            bufferingFallbackJob?.cancel()
            bufferingFallbackJob = fallbackScope.launch {
                reportBufferingTimeoutIfNeeded()
            }
        } else if (playbackState != Player.STATE_BUFFERING) {
            bufferingFallbackJob?.cancel()
            bufferingFallbackJob = null
        }
    }

    fun start() {
        updateStartupFallback(binding.player.playbackState)
        updateBufferingFallback(binding.player.playbackState)
    }

    private suspend fun reportStartupTimeoutIfNeeded() {
        val settings = binding.state.settings()
        delay(playbackStartupFallbackDelayMs(settings.playerBufferPreset))
        if (
            !shouldReportPlaybackStartupFallback(
                playbackState = binding.player.playbackState,
                playbackStartedReported = playbackStartedReported,
                fallbackReported = fallbackReported,
                playWhenReady = binding.player.playWhenReady,
            )
        ) {
            return
        }
        startupFallbackJob = null
        fallbackReported = true
        bufferingFallbackJob?.cancel()
        bufferingFallbackJob = null
        AppLog.w(
            "YummyDroidPlayer",
            "Startup fallback timeout: state=${binding.player.playbackState} playWhenReady=${binding.player.playWhenReady}",
        )
        binding.callbacks.onBufferingTimeout(binding.player.currentPosition.coerceAtLeast(0L))
    }

    private fun reportPlaybackStarted() {
        if (playbackStartedReported) return
        playbackStartedReported = true
        startupFallbackJob?.cancel()
        startupFallbackJob = null
        binding.callbacks.onPlaybackStarted()
    }

    private suspend fun reportBufferingTimeoutIfNeeded() {
        val settings = binding.state.settings()
        val delayMs = playbackBufferingFallbackDelayMs(
            playbackStartedReported = playbackStartedReported,
            playerBufferPreset = settings.playerBufferPreset,
            fallbackSuppressedUntilMs = binding.state.fallbackSuppressedUntilMs(),
            nowMs = SystemClock.elapsedRealtime(),
        )
        delay(delayMs.coerceAtLeast(0L))
        if (SystemClock.elapsedRealtime() < binding.state.fallbackSuppressedUntilMs()) return
        if (binding.player.playbackState != Player.STATE_BUFFERING || fallbackReported) return
        if (
            isPlaybackEndCloseOrBuffered(
                positionMs = binding.player.currentPosition.coerceAtLeast(0L),
                bufferedPositionMs = binding.player.bufferedPosition.coerceAtLeast(0L),
                durationMs = resolvedPlaybackDurationMs(
                    playerDurationMs = binding.player.duration,
                    contentDurationMs = binding.player.contentDuration,
                    metadataDurationSeconds = binding.metadataDurationSeconds,
                ),
                switchFallbackThresholdMs = settings.playerBufferPreset.switchFallbackThresholdMs,
            )
        ) {
            return
        }
        fallbackReported = true
        binding.callbacks.onBufferingTimeout(binding.player.currentPosition.coerceAtLeast(0L))
    }

    private fun logPlaybackError(error: PlaybackException) {
        val httpError = error.cause as? HttpDataSource.InvalidResponseCodeException
        if (httpError != null) {
            val uri = httpError.dataSpec.uri
            AppLog.w(
                "YummyDroidPlayer",
                "Playback HTTP ${httpError.responseCode}: host=${uri.host}, file=${uri.lastPathSegment}, headers=${httpError.headerFields.keys}",
            )
        } else {
            AppLog.w("YummyDroidPlayer", "Playback failed: ${error.errorCodeName}", error)
        }
    }
}

private fun PlaybackException.isSourcePlaybackFailure(): Boolean {
    return errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
        errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
        errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
        errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
        errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
        cause is HttpDataSource.InvalidResponseCodeException
}

private fun Player.currentMediaItemUrl(): String? {
    return currentMediaItem?.localConfiguration?.uri?.toString()
}

private fun String.safePlaybackLogUrl(): String {
    val normalized = substringBefore('?')
    return runCatching {
        val uri = android.net.Uri.parse(normalized)
        listOfNotNull(uri.host, uri.lastPathSegment).joinToString("/")
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: normalized.take(96)
}

internal fun PlaybackException.playbackFailureMessage(): String {
    val httpError = cause as? HttpDataSource.InvalidResponseCodeException
    if (httpError != null) return "HTTP ${httpError.responseCode}"
    return errorCodeName.takeIf { it.isNotBlank() }
        ?: localizedMessage?.takeIf { it.isNotBlank() }
        ?: message?.takeIf { it.isNotBlank() }
        ?: "playback error"
}

private fun PlaybackException.playbackFailureKind(): PlaybackFailureKind {
    return if (isSourcePlaybackFailure()) {
        PlaybackFailureKind.SourceUnavailable
    } else {
        PlaybackFailureKind.PlayerError
    }
}

internal fun limitedPlaybackFallbackUrls(
    primaryUrl: String,
    fallbackUrls: List<String>,
    limit: Int = PLAYBACK_STREAM_FALLBACK_URL_LIMIT,
): List<String> {
    val seen = linkedSetOf(playbackFallbackUrlIdentity(primaryUrl))
    return fallbackUrls.asSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .filter { candidate -> seen.add(playbackFallbackUrlIdentity(candidate)) }
        .take(limit)
        .toList()
}

internal fun playbackFallbackUrlIdentity(url: String): String {
    val value = url.trim()
    val queryIndex = value.indexOf('?').takeIf { it >= 0 } ?: value.length
    val fragmentIndex = value.indexOf('#').takeIf { it >= 0 } ?: value.length
    return value.take(minOf(queryIndex, fragmentIndex))
}

internal const val PLAYBACK_STREAM_FALLBACK_URL_LIMIT = 3

// NativeVideoPlayerQualitySelection
internal data class NativePlayerQualitySelection(
    val options: List<QualityOption>,
    val selectedKey: String?,
    val streamSelectedKey: String?,
    val onSelectedKeyChanged: (String?) -> Unit,
)

@Composable
internal fun rememberNativePlayerQualitySelection(
    stream: ResolvedVideoStream,
    currentVideo: VideoVariant,
    player: ExoPlayer,
    playerView: () -> PlayerView?,
    playerControlTexts: PlayerControlTexts,
    groups: Map<String, List<VideoVariant>>,
    selectedKey: String?,
    playbackPreferredQuality: PreferredQuality,
    defaultQuality: PreferredQuality,
    offlineMode: Boolean,
    tracks: Tracks,
): NativePlayerQualitySelection {
    val onlineQualityOptions = remember(tracks) { tracks.videoQualityOptions() }
    val sourceQualityOptions = remember(
        groups,
        selectedKey,
        currentVideo.matchingEpisodeKey,
        currentVideo.matchingVoiceKey,
    ) {
        val sourceVideos = groups[selectedKey].orEmpty()
            .ifEmpty { groups[currentVideo.matchingVoiceKey].orEmpty() }
        sourceVideos.sourceQualityOptionsFor(currentVideo)
    }
    val streamQualityOptions = remember(stream.availableQualities) {
        stream.availableQualities.sourceQualityOptions()
    }
    val localQualityOptions = remember(
        currentVideo.matchingEpisodeKey,
        currentVideo.matchingVoiceKey,
        currentVideo.localPlaybackUrl,
        currentVideo.localFiles,
    ) {
        currentVideo.localQualityOptions()
    }
    val qualityOptions = remember(
        onlineQualityOptions,
        sourceQualityOptions,
        streamQualityOptions,
        localQualityOptions,
        offlineMode,
    ) {
        mergeVideoQualityOptions(
            onlineOptions = resolvedOnlineQualityOptions(
                streamOptions = streamQualityOptions,
                trackOptions = onlineQualityOptions,
                sourceOptions = sourceQualityOptions,
            ),
            localOptions = localQualityOptions,
            offlineMode = offlineMode,
            downloadedLabel = playerControlTexts.downloaded,
        )
    }
    val streamSelectedQualityKey = remember(currentVideo.id, stream.url, stream.selectedVideoHeight) {
        stream.selectedVideoHeight
            ?.takeIf { it > 0 }
            ?.let { "height:$it" }
    }
    var selectedQualityKey by remember(currentVideo.id, stream.url, stream.selectedVideoHeight) {
        mutableStateOf(
            resolveInitialNativeQualityKey(
                selectedLocalQualityKey = currentVideo.selectedLocalQualityKey(stream.url),
                streamSelectedQualityKey = streamSelectedQualityKey,
                qualityOptions = qualityOptions,
                playbackPreferredQuality = playbackPreferredQuality,
                defaultQuality = defaultQuality,
            ),
        )
    }
    NativePlayerQualitySelectionEffects(
        player = player,
        playerView = playerView,
        streamUrl = stream.url,
        qualityOptions = qualityOptions,
        streamSelectedQualityKey = streamSelectedQualityKey,
        playbackPreferredQuality = playbackPreferredQuality,
        defaultQuality = defaultQuality,
        selectedQualityKey = selectedQualityKey,
        onSelectedQualityKeyChanged = { selectedQualityKey = it },
    )
    return NativePlayerQualitySelection(
        options = qualityOptions,
        selectedKey = selectedQualityKey,
        streamSelectedKey = streamSelectedQualityKey,
        onSelectedKeyChanged = { selectedQualityKey = it },
    )
}

@Composable
private fun NativePlayerQualitySelectionEffects(
    player: ExoPlayer,
    playerView: () -> PlayerView?,
    streamUrl: String,
    qualityOptions: List<QualityOption>,
    streamSelectedQualityKey: String?,
    playbackPreferredQuality: PreferredQuality,
    defaultQuality: PreferredQuality,
    selectedQualityKey: String?,
    onSelectedQualityKeyChanged: (String?) -> Unit,
) {
    val currentSelectedQualityKey = rememberUpdatedState(selectedQualityKey)
    RefreshMissingQualitySelectionEffect(
        qualityOptions,
        streamSelectedQualityKey,
        playbackPreferredQuality,
        defaultQuality,
        currentSelectedQualityKey,
        onSelectedQualityKeyChanged,
    )
    ApplyInitialQualitySelectionEffect(
        player,
        playerView,
        streamUrl,
        qualityOptions,
        streamSelectedQualityKey,
        playbackPreferredQuality,
        defaultQuality,
        currentSelectedQualityKey,
        onSelectedQualityKeyChanged,
    )
    ApplySelectedTrackQualityEffect(
        player,
        streamUrl,
        qualityOptions,
        currentSelectedQualityKey,
    )
    ApplyPreferredTrackQualityEffect(
        player,
        streamUrl,
        qualityOptions,
        currentSelectedQualityKey,
        playbackPreferredQuality,
        defaultQuality,
    )
}

@Composable
private fun RefreshMissingQualitySelectionEffect(
    qualityOptions: List<QualityOption>,
    streamSelectedQualityKey: String?,
    playbackPreferredQuality: PreferredQuality,
    defaultQuality: PreferredQuality,
    selectedQualityKey: State<String?>,
    onSelectedQualityKeyChanged: (String?) -> Unit,
) {
    LaunchedEffect(qualityOptions) {
        val currentKey = selectedQualityKey.value
        if (currentKey != null && qualityOptions.none { it.matchesSelectedQualityKey(currentKey) }) {
            val preferredQuality = playbackPreferredQuality.takeUnless { it == PreferredQuality.Auto }
                ?: defaultQuality
            onSelectedQualityKeyChanged(
                streamSelectedQualityKey
                    ?.takeIf { key -> qualityOptions.any { it.matchesSelectedQualityKey(key) } }
                    ?: qualityOptions.preferredOption(preferredQuality)?.qualityOptionIdentity(),
            )
        }
    }
}

@Composable
private fun ApplyInitialQualitySelectionEffect(
    player: ExoPlayer,
    playerView: () -> PlayerView?,
    streamUrl: String,
    qualityOptions: List<QualityOption>,
    streamSelectedQualityKey: String?,
    playbackPreferredQuality: PreferredQuality,
    defaultQuality: PreferredQuality,
    selectedQualityKey: State<String?>,
    onSelectedQualityKeyChanged: (String?) -> Unit,
) {
    LaunchedEffect(
        qualityOptions,
        playbackPreferredQuality,
        defaultQuality,
        streamUrl,
        streamSelectedQualityKey,
    ) {
        val currentKey = selectedQualityKey.value
        if (currentKey != null && qualityOptions.any { it.matchesSelectedQualityKey(currentKey) }) {
            return@LaunchedEffect
        }
        val resolvedSourceKey = streamSelectedQualityKey
            ?.takeIf { key -> qualityOptions.any { it.matchesSelectedQualityKey(key) } }
        val preferredOption = qualityOptions.preferredOption(
            playbackPreferredQuality.takeUnless { it == PreferredQuality.Auto } ?: defaultQuality,
        )
        val preferredKey = resolvedSourceKey ?: preferredOption?.qualityOptionIdentity()
        if (preferredKey != null && currentKey != preferredKey) {
            preferredOption?.takeIf { it.group != null }?.let(player::selectQuality)
            onSelectedQualityKeyChanged(preferredKey)
            playerView()?.setSelectedQualityTag(preferredKey)
        }
    }
}

@Composable
private fun ApplySelectedTrackQualityEffect(
    player: ExoPlayer,
    streamUrl: String,
    qualityOptions: List<QualityOption>,
    selectedQualityKey: State<String?>,
) {
    LaunchedEffect(player, qualityOptions, selectedQualityKey.value, streamUrl) {
        val selectedKey = selectedQualityKey.value ?: return@LaunchedEffect
        qualityOptions
            .firstOrNull { option ->
                option.matchesSelectedQualityKey(selectedKey) && option.hasPlayableQualityConstraint()
            }
            ?.let(player::selectQuality)
    }
}

@Composable
private fun ApplyPreferredTrackQualityEffect(
    player: ExoPlayer,
    streamUrl: String,
    qualityOptions: List<QualityOption>,
    selectedQualityKey: State<String?>,
    playbackPreferredQuality: PreferredQuality,
    defaultQuality: PreferredQuality,
) {
    LaunchedEffect(player, qualityOptions, selectedQualityKey.value, playbackPreferredQuality, defaultQuality, streamUrl) {
        val selectedKey = selectedQualityKey.value
        if (selectedKey != null && qualityOptions.any { it.matchesSelectedQualityKey(selectedKey) }) {
            return@LaunchedEffect
        }
        val preferredQuality = playbackPreferredQuality.takeUnless { it == PreferredQuality.Auto }
            ?: defaultQuality.takeUnless { it == PreferredQuality.Auto }
        val preferredOption = preferredQuality?.let { qualityOptions.preferredOption(it) }
        if (preferredOption?.group != null) {
            player.selectQuality(preferredOption)
        }
    }
}

private fun PlayerView.setSelectedQualityTag(key: String) {
    findViewById<View>(R.id.yummy_player_quality)
        ?.setTag(R.id.yummy_player_quality, key)
}

// NativeVideoPlayerRuntime
@OptIn(UnstableApi::class)
@Composable
internal fun NativeVideoPlayerRuntime(binding: NativeVideoPlayerRuntimeBinding) {
    val session = rememberNativeVideoPlayerRuntimeSession(binding)
    val bufferingVisible = rememberNativePlayerBufferingVisible(session.playbackPlayer)
    BindNativeVideoPlayerRuntimeEffects(binding, session)
    Box(modifier = binding.modifier) {
        NativePlayerView(
            player = session.playbackPlayer,
            videoToken = "${binding.currentVideo.id}:${binding.stream.url}",
            interactive = binding.interactive,
            isInPictureInPicture = binding.isInPictureInPicture,
            controllerBinding = createNativeVideoPlayerControllerBinding(binding, session),
            playerControlFocusToRestoreId = binding.playerControlFocusToRestoreId,
            onPlayerViewChanged = { session.playerView.value = it },
            onPlayerControlFocusRestored = binding.onPlayerControlFocusRestored,
            modifier = Modifier.fillMaxSize(),
        )
        NativePlayerBufferingOverlay(visible = bufferingVisible)
    }
}

@Composable
private fun rememberNativePlayerBufferingVisible(player: Player): Boolean {
    var visible by remember(player) { mutableStateOf(player.shouldShowNativePlayerBuffering()) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                visible = player.shouldShowNativePlayerBuffering()
            }
        }
        player.addListener(listener)
        visible = player.shouldShowNativePlayerBuffering()
        onDispose { player.removeListener(listener) }
    }
    return visible
}

private fun Player.shouldShowNativePlayerBuffering(): Boolean {
    return playbackState == Player.STATE_BUFFERING && !isPlaying
}

@Composable
private fun NativePlayerBufferingOverlay(visible: Boolean) {
    if (!visible) return
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(44.dp),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

internal fun ExoPlayer.prepareCurrentMediaItemIfSameVideo(mediaItem: MediaItem): Boolean {
    val currentUri = currentMediaItem?.localConfiguration?.uri ?: return false
    val replacementUri = mediaItem.localConfiguration?.uri ?: return false
    if (currentUri != replacementUri) return false
    val positionMs = currentPosition.coerceAtLeast(0L)
    val shouldPlay = playWhenReady
    setMediaItem(mediaItem, positionMs)
    prepare()
    playWhenReady = shouldPlay
    return true
}

// NativeVideoPlayerRuntimeBinding
internal class NativeVideoPlayerRuntimeBinding(
    val stream: ResolvedVideoStream,
    val animeTitle: String,
    val currentVideo: VideoVariant,
    val interactive: Boolean,
    val settings: AppSettings,
    val startPositionMs: Long,
    val playbackPreferredQuality: PreferredQuality,
    val playbackMetadataLoading: Boolean,
    val groups: Map<String, List<VideoVariant>>,
    val selectedKey: String?,
    val sourceOptions: List<SourceOption>,
    val selectedSourceKey: String?,
    val previousVideo: VideoVariant?,
    val nextVideo: VideoVariant?,
    val allowSubscription: Boolean,
    val subscriptionActive: Boolean,
    val onToggleSubscription: () -> Unit,
    val onSelectGroup: (String, VideoVariant?, Long) -> Unit,
    val onSelectSource: (VideoVariant, Long) -> Unit,
    val onPlayVideoAt: (VideoVariant, Long) -> Unit,
    val onPlayVideoAtQuality: (VideoVariant, Long, PreferredQuality) -> Unit,
    val onPlaybackFailed: (VideoVariant, Long, PlaybackFailure) -> Unit,
    val onPlaybackStarted: (VideoVariant) -> Unit,
    val onPlaybackEnded: (VideoVariant) -> Unit,
    val onPlaybackProgress: (VideoVariant, Long, Long) -> Unit,
    val canUsePictureInPicture: Boolean,
    val isInPictureInPicture: Boolean,
    val onEnterPictureInPicture: () -> Unit,
    val onSettingsChange: (AppSettings) -> Unit,
    val onBack: () -> Unit,
    val onRegisterPlayerInputActionHandler: (PlayerInputController?) -> Unit,
    val offlineMode: Boolean,
    val modifier: Modifier,
    val playerControlFocusToRestoreId: Int?,
    val keepControlsVisibleAfterReady: Boolean,
    val onRememberPlayerControlFocus: (Int) -> Unit,
    val onPlayerControlFocusRestored: () -> Unit,
    val onKeepControlsVisibleAfterReadyRequested: () -> Unit,
    val onControlsKeptVisibleAfterReady: () -> Unit,
)

// NativeVideoPlayerRuntimeEffects
@Composable
internal fun BindNativeVideoPlayerRuntimeEffects(
    binding: NativeVideoPlayerRuntimeBinding,
    session: NativeVideoPlayerRuntimeSession,
) {
    val player = session.playbackPlayer
    LaunchedEffect(binding.previousVideo?.id, binding.nextVideo?.id, player) {
        PlayerPipController.notifyPlayingChanged()
    }
    LaunchedEffect(player, binding.settings.playerSpeed) {
        player.setPlaybackSpeed(binding.settings.playerSpeed.value)
    }
    LaunchedEffect(player) {
        if (player.playbackState != Player.STATE_ENDED) {
            player.play()
        }
        while (player.playbackState != Player.STATE_READY && player.playbackState != Player.STATE_ENDED) {
            delay(24)
        }
        if (player.playbackState == Player.STATE_READY) {
            if (binding.keepControlsVisibleAfterReady) {
                session.playerView.value?.showPlayerControls()
            } else {
                session.playerView.value?.hidePlayerControls()
            }
            if (binding.keepControlsVisibleAfterReady) {
                binding.onControlsKeptVisibleAfterReady()
            }
        }
    }
    LaunchedEffect(player, binding.settings.matchDisplayModeToVideo, session.selection.tracks) {
        session.activity?.applyVideoDisplayMode(
            enabled = binding.settings.matchDisplayModeToVideo,
            video = player.currentVideoDisplayInfo(),
        )
    }
    LaunchedEffect(player, binding.currentVideo.id) {
        while (true) {
            delay(PLAYBACK_PROGRESS_SAVE_INTERVAL_MS)
            if (player.playbackState != Player.STATE_IDLE) {
                session.currentProgressCallback.value(
                    session.currentProgressVideo.value,
                    player.currentPosition.coerceAtLeast(0L),
                    player.duration.normalizedDurationMs(),
                )
            }
        }
    }
    NativePlayerLifecycle(createNativePlayerLifecycleBinding(binding, session))
}

private fun createNativePlayerLifecycleBinding(
    binding: NativeVideoPlayerRuntimeBinding,
    session: NativeVideoPlayerRuntimeSession,
): NativePlayerLifecycleBinding {
    return NativePlayerLifecycleBinding(
        player = session.playbackPlayer,
        localPlayer = session.player,
        castSession = session.castSession,
        stream = binding.stream,
        pipPlayerHandle = session.pipPlayerHandle,
        metadataDurationSeconds = binding.currentVideo.durationSeconds,
        state = createNativePlayerEventState(session),
        callbacks = createNativePlayerEventCallbacks(binding, session),
    )
}

private fun createNativePlayerEventState(
    session: NativeVideoPlayerRuntimeSession,
): NativePlayerEventState {
    return NativePlayerEventState(
        playerView = { session.playerView.value },
        settings = { session.currentSettings.value },
        qualityOptions = { session.latestQualityOptions.value },
        selectedQualityKey = { session.latestSelectedQualityKey.value },
        playbackPreferredQuality = { session.latestPlaybackPreferredQuality.value },
        streamSelectedQualityKey = { session.latestStreamSelectedQualityKey.value },
        fallbackSuppressedUntilMs = { session.fallbackSuppressedUntilMs.longValue },
        onFallbackSuppressedUntilChanged = { session.fallbackSuppressedUntilMs.longValue = it },
        skipControlsTimelineReady = { session.skipControlsTimelineReady.value },
        onSkipControlsTimelineReady = { session.skipControlsTimelineReady.value = true },
        onTracksChanged = session.selection.onTracksChanged,
        onSelectedSubtitleKeyChanged = session.selection.onSelectedSubtitleKeyChanged,
        onSelectedQualityKeyChanged = session.selection.onSelectedQualityKeyChanged,
    )
}

private fun createNativePlayerEventCallbacks(
    binding: NativeVideoPlayerRuntimeBinding,
    session: NativeVideoPlayerRuntimeSession,
): NativePlayerEventCallbacks {
    return NativePlayerEventCallbacks(
        onPlaybackStarted = { binding.onPlaybackStarted(binding.currentVideo) },
        onPlaybackEnded = { binding.onPlaybackEnded(binding.currentVideo) },
        onBufferingTimeout = { positionMs ->
            binding.onPlaybackFailed(
                binding.currentVideo,
                positionMs,
                PlaybackFailure(
                    kind = PlaybackFailureKind.BufferingTimeout,
                    message = session.context.localizedString(
                        R.string.ui_playback_buffer_not_filling,
                        session.currentSettings.value.contentLanguage,
                    ),
                ),
            )
        },
        onAutoAdvance = {
            binding.nextVideo?.let { next ->
                showVoiceFallbackToast(session.context, binding.currentVideo, next)
                session.currentProgressCallback.value(next, 1_000L, 0L)
                session.playerView.value?.hidePlayerControls()
                binding.onPlayVideoAt(next, 0L)
            }
        },
        onPlaybackError = { positionMs, error ->
            binding.onPlaybackFailed(
                binding.currentVideo,
                positionMs,
                PlaybackFailure(
                    kind = error.playbackFailureKind(),
                    message = error.playbackFailureMessage(),
                ),
            )
        },
        onProgressSnapshot = { positionMs, durationMs ->
            session.currentProgressCallback.value(
                session.currentProgressVideo.value,
                positionMs,
                durationMs,
            )
        },
        onDisplayModeUpdate = { videoSize ->
            (session.playerView.value as? YummyPlayerView)?.updateControllerViewport()
            session.activity?.applyVideoDisplayMode(
                enabled = session.currentSettings.value.matchDisplayModeToVideo,
                video = session.player.currentVideoDisplayInfo() ?: videoSize?.toVideoDisplayInfo(),
            )
        },
        onDispose = {
            session.playerView.value?.clearTimelineScrubState()
            session.playerView.value?.unbindSkipControls()
            session.activity?.clearPreferredDisplayMode()
        },
    )
}

// NativeVideoPlayerRuntimeSession
internal class NativeVideoPlayerRuntimeSession(
    val context: Context,
    val activity: Activity?,
    val player: ExoPlayer,
    val playbackPlayer: Player,
    val castSession: PlayerCastSession,
    val playbackActions: NativePlayerPlaybackActions,
    val playerView: MutableState<PlayerView?>,
    val playerControlTexts: PlayerControlTexts,
    val selection: NativePlayerSelectionSnapshot,
    val pipPlayerHandle: PipPlayerHandle,
    val currentSettings: State<AppSettings>,
    val currentProgressCallback: State<(VideoVariant, Long, Long) -> Unit>,
    val currentProgressVideo: State<VideoVariant>,
    val latestQualityOptions: State<List<QualityOption>>,
    val latestSelectedQualityKey: State<String?>,
    val latestPlaybackPreferredQuality: State<PreferredQuality>,
    val latestStreamSelectedQualityKey: State<String?>,
    val fallbackSuppressedUntilMs: MutableLongState,
    val skipControlsTimelineReady: MutableState<Boolean>,
)

private data class NativePlayerControlSelection(
    val texts: PlayerControlTexts,
    val selection: NativePlayerSelectionSnapshot,
)

@Composable
internal fun rememberNativeVideoPlayerRuntimeSession(
    binding: NativeVideoPlayerRuntimeBinding,
): NativeVideoPlayerRuntimeSession {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val playerActionScope = rememberCoroutineScope()
    val currentSettings = rememberUpdatedState(binding.settings)
    val currentProgressCallback = rememberUpdatedState(binding.onPlaybackProgress)
    val currentProgressVideo = rememberUpdatedState(binding.currentVideo)
    val fallbackSuppressedUntilMs = remember(binding.stream.url, binding.currentVideo.id) {
        mutableLongStateOf(SystemClock.elapsedRealtime() + PLAYBACK_SEEK_BUFFER_GRACE_MS)
    }
    val player = rememberNativeRuntimePlayer(binding, context)
    val castSession = rememberPlayerCastSession(context, player)
    val playbackPlayer = castSession.playbackPlayer
    val skipControlsTimelineReady = remember(playbackPlayer) {
        mutableStateOf(playbackPlayer.hasReadyTimeline())
    }
    val playbackActions = rememberNativePlayerPlaybackActions(playbackPlayer, playerActionScope)
    val playerView = remember { mutableStateOf<PlayerView?>(null) }
    val controls = rememberNativeRuntimeControlSelection(binding, player, playerView)
    val latestQualityOptions = rememberUpdatedState(controls.selection.qualityOptions)
    val latestSelectedQualityKey = rememberUpdatedState(controls.selection.selectedQualityKey)
    val latestPlaybackPreferredQuality = rememberUpdatedState(binding.playbackPreferredQuality)
    val latestStreamSelectedQualityKey = rememberUpdatedState(
        controls.selection.streamSelectedQualityKey,
    )

    RegisterNativePlayerInputController(
        player = playbackPlayer,
        playerView = { playerView.value },
        isInPictureInPicture = binding.isInPictureInPicture,
        playbackActions = playbackActions,
        onRegisterPlayerInputActionHandler = binding.onRegisterPlayerInputActionHandler,
    )
    val pipPlayerHandle = rememberNativePipPlayerHandle(
        context = context,
        player = playbackPlayer,
        playerView = { playerView.value },
        playbackActions = playbackActions,
        currentVideo = binding.currentVideo,
        previousVideo = binding.previousVideo,
        nextVideo = binding.nextVideo,
        onPlayVideoAt = binding.onPlayVideoAt,
    )
    return NativeVideoPlayerRuntimeSession(
        context = context,
        activity = activity,
        player = player,
        playbackPlayer = playbackPlayer,
        castSession = castSession,
        playbackActions = playbackActions,
        playerView = playerView,
        playerControlTexts = controls.texts,
        selection = controls.selection,
        pipPlayerHandle = pipPlayerHandle,
        currentSettings = currentSettings,
        currentProgressCallback = currentProgressCallback,
        currentProgressVideo = currentProgressVideo,
        latestQualityOptions = latestQualityOptions,
        latestSelectedQualityKey = latestSelectedQualityKey,
        latestPlaybackPreferredQuality = latestPlaybackPreferredQuality,
        latestStreamSelectedQualityKey = latestStreamSelectedQualityKey,
        fallbackSuppressedUntilMs = fallbackSuppressedUntilMs,
        skipControlsTimelineReady = skipControlsTimelineReady,
    )
}

@OptIn(UnstableApi::class)
@Composable
private fun rememberNativeRuntimePlayer(
    binding: NativeVideoPlayerRuntimeBinding,
    context: Context,
): ExoPlayer {
    val httpClient = remember { defaultVideoResolveClient() }
    val renderersFactory = remember(context, binding.settings.decoderMode) {
        YummyRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector(binding.settings.decoderMode.mediaCodecSelector())
    }
    val mediaMetadata = remember(binding.animeTitle, binding.currentVideo.id) {
        val subtitle = listOf(binding.currentVideo.dubbing, binding.currentVideo.episode)
            .map(String::trim)
            .filter(String::isNotBlank)
            .joinToString(" • ")
        MediaMetadata.Builder()
            .setMediaType(MediaMetadata.MEDIA_TYPE_TV_SHOW)
            .setTitle(binding.animeTitle)
            .setSubtitle(subtitle.takeIf(String::isNotBlank))
            .build()
    }
    return remember(
        binding.stream.url,
        binding.stream.headers,
        binding.startPositionMs,
        httpClient,
        renderersFactory,
        binding.settings.playerBufferPreset,
        mediaMetadata,
    ) {
        createVideoPlayer(
            context = context,
            stream = binding.stream,
            startPositionMs = binding.startPositionMs,
            httpClient = httpClient,
            renderersFactory = renderersFactory,
            loadControl = binding.settings.playerBufferPreset.toLoadControl(),
            mediaMetadata = mediaMetadata,
        )
    }
}

@Composable
private fun rememberNativeRuntimeControlSelection(
    binding: NativeVideoPlayerRuntimeBinding,
    player: ExoPlayer,
    playerView: State<PlayerView?>,
): NativePlayerControlSelection {
    val texts = rememberPlayerControlTexts()
    val selection = rememberNativePlayerSelection(
        stream = binding.stream,
        currentVideo = binding.currentVideo,
        player = player,
        playerView = { playerView.value },
        playerControlTexts = texts,
        sourceSubtitleLabel = uiText(UiStringKey.HasSubtitles),
        playbackMetadataLoading = binding.playbackMetadataLoading,
        groups = binding.groups,
        selectedKey = binding.selectedKey,
        sourceOptions = binding.sourceOptions,
        playbackPreferredQuality = binding.playbackPreferredQuality,
        settings = binding.settings,
        offlineMode = binding.offlineMode,
    )
    return NativePlayerControlSelection(texts, selection)
}

// NativeVideoPlayerSelection
internal data class NativePlayerSelectionSnapshot(
    val tracks: Tracks,
    val playbackSourceOptions: List<SourceOption>,
    val subtitleOptions: List<SubtitleOption>,
    val subtitlesLoading: Boolean,
    val qualityOptions: List<QualityOption>,
    val selectedQualityKey: String?,
    val selectedSubtitleKey: String,
    val streamSelectedQualityKey: String?,
    val onTracksChanged: (Tracks) -> Unit,
    val onSelectedQualityKeyChanged: (String?) -> Unit,
    val onSelectedSubtitleKeyChanged: (String) -> Unit,
    val onControllerSelectedSubtitleKeyChanged: (String) -> Unit,
)

internal fun resolveInitialNativeQualityKey(
    selectedLocalQualityKey: String?,
    streamSelectedQualityKey: String?,
    qualityOptions: List<QualityOption>,
    playbackPreferredQuality: PreferredQuality,
    defaultQuality: PreferredQuality,
): String? {
    val preferredOption = qualityOptions.preferredOption(
        playbackPreferredQuality.takeUnless { it == PreferredQuality.Auto } ?: defaultQuality,
    )
    return selectedLocalQualityKey
        ?: streamSelectedQualityKey?.takeIf { key ->
            qualityOptions.any { it.matchesSelectedQualityKey(key) }
        }
        ?: preferredOption?.qualityOptionIdentity()
}

@Composable
internal fun rememberNativePlayerSelection(
    stream: ResolvedVideoStream,
    currentVideo: VideoVariant,
    player: ExoPlayer,
    playerView: () -> PlayerView?,
    playerControlTexts: PlayerControlTexts,
    sourceSubtitleLabel: String,
    playbackMetadataLoading: Boolean,
    groups: Map<String, List<VideoVariant>>,
    selectedKey: String?,
    sourceOptions: List<SourceOption>,
    playbackPreferredQuality: PreferredQuality,
    settings: AppSettings,
    offlineMode: Boolean,
): NativePlayerSelectionSnapshot {
    var tracks by remember(player) { mutableStateOf(player.currentTracks) }
    val subtitles = rememberNativePlayerSubtitlePresentation(
        stream = stream,
        currentVideo = currentVideo,
        player = player,
        playerControlTexts = playerControlTexts,
        sourceSubtitleLabel = sourceSubtitleLabel,
        playbackMetadataLoading = playbackMetadataLoading,
        sourceOptions = sourceOptions,
        tracks = tracks,
    )
    val quality = rememberNativePlayerQualitySelection(
        stream = stream,
        currentVideo = currentVideo,
        player = player,
        playerView = playerView,
        playerControlTexts = playerControlTexts,
        groups = groups,
        selectedKey = selectedKey,
        playbackPreferredQuality = playbackPreferredQuality,
        defaultQuality = settings.defaultQuality,
        offlineMode = offlineMode,
        tracks = tracks,
    )
    val subtitleSelection = rememberNativePlayerSubtitleSelection(
        currentVideo = currentVideo,
        streamUrl = stream.url,
        player = player,
        playerView = playerView,
        subtitleOptions = subtitles.options,
    )
    return NativePlayerSelectionSnapshot(
        tracks = tracks,
        playbackSourceOptions = subtitles.playbackSourceOptions,
        subtitleOptions = subtitles.options,
        subtitlesLoading = subtitles.loading,
        qualityOptions = quality.options,
        selectedQualityKey = quality.selectedKey,
        selectedSubtitleKey = subtitleSelection.selectedKey,
        streamSelectedQualityKey = quality.streamSelectedKey,
        onTracksChanged = { tracks = it },
        onSelectedQualityKeyChanged = quality.onSelectedKeyChanged,
        onSelectedSubtitleKeyChanged = subtitleSelection.onSelectedKeyChanged,
        onControllerSelectedSubtitleKeyChanged = subtitleSelection.onControllerSelectedKeyChanged,
    )
}

// NativeVideoPlayerSession
internal class NativePlayerPlaybackActions(
    private val player: Player,
    private val scope: CoroutineScope,
    private val uiControls: UiControlCoordinator,
) {
    fun pause() {
        uiControls.cancel(this, UiControlOperation.PlaybackLatest)
        player.pause()
    }

    fun requestStart() {
        if (player.isPlaying || uiControls.isActive(UiControlOperation.PlaybackLatest)) return
        uiControls.launch(scope, this, UiControlOperation.PlaybackLatest) {
            while (
                player.playbackState != Player.STATE_READY &&
                player.playbackState != Player.STATE_ENDED &&
                player.playbackState != Player.STATE_IDLE
            ) {
                delay(24)
            }
            if (player.playbackState != Player.STATE_IDLE) player.play()
        }
    }
}

@Composable
internal fun rememberNativePlayerPlaybackActions(
    player: Player,
    scope: CoroutineScope,
): NativePlayerPlaybackActions {
    val uiControls = LocalUiControlCoordinator.current
    return remember(player, scope, uiControls) { NativePlayerPlaybackActions(player, scope, uiControls) }
}

@Composable
internal fun RegisterNativePlayerInputController(
    player: Player,
    playerView: () -> PlayerView?,
    isInPictureInPicture: Boolean,
    playbackActions: NativePlayerPlaybackActions,
    onRegisterPlayerInputActionHandler: (PlayerInputController?) -> Unit,
) {
    DisposableEffect(player, isInPictureInPicture, onRegisterPlayerInputActionHandler) {
        onRegisterPlayerInputActionHandler(
            PlayerInputController(
                controlsVisible = {
                    !isInPictureInPicture && playerView()?.hasVisiblePlayerControls() == true
                },
                hideControls = {
                    val view = playerView()
                    if (view == null || isInPictureInPicture) {
                        false
                    } else {
                        view.hidePlayerControls()
                        true
                    }
                },
                handle = { event ->
                    val view = playerView()
                    if (view == null || isInPictureInPicture) {
                        false
                    } else {
                        view.handleRemoteInputAction(
                            event = event,
                            onRequestPlay = playbackActions::requestStart,
                            onPausePlayback = playbackActions::pause,
                        )
                    }
                },
            ),
        )
        onDispose { onRegisterPlayerInputActionHandler(null) }
    }
}

@Composable
internal fun rememberNativePipPlayerHandle(
    context: Context,
    player: Player,
    playerView: () -> PlayerView?,
    playbackActions: NativePlayerPlaybackActions,
    currentVideo: VideoVariant,
    previousVideo: VideoVariant?,
    nextVideo: VideoVariant?,
    onPlayVideoAt: (VideoVariant, Long) -> Unit,
): PipPlayerHandle {
    val latestCurrentVideo = rememberUpdatedState(currentVideo)
    val latestPreviousVideo = rememberUpdatedState(previousVideo)
    val latestNextVideo = rememberUpdatedState(nextVideo)
    val latestPlayVideoAt = rememberUpdatedState(onPlayVideoAt)
    return remember(player) {
        object : PipPlayerHandle {
            override val isPlaying: Boolean
                get() = player.isPlaying

            override val canPlayPreviousEpisode: Boolean
                get() = latestPreviousVideo.value != null

            override val canPlayNextEpisode: Boolean
                get() = latestNextVideo.value != null

            override fun play() = playbackActions.requestStart()

            override fun pause() = playbackActions.pause()

            override fun playPreviousEpisode() {
                latestPreviousVideo.value?.let { previous ->
                    showVoiceFallbackToast(context, latestCurrentVideo.value, previous)
                    playbackActions.pause()
                    latestPlayVideoAt.value(previous, 0L)
                }
            }

            override fun playNextEpisode() {
                latestNextVideo.value?.let { next ->
                    showVoiceFallbackToast(context, latestCurrentVideo.value, next)
                    playbackActions.pause()
                    latestPlayVideoAt.value(next, 0L)
                }
            }

            override fun setPictureInPictureMode(enabled: Boolean) {
                playerView()?.applyPictureInPictureControllerMode(enabled)
            }

            override fun hideAppControls() {
                playerView()?.hidePlayerControls()
            }
        }
    }
}

// NativeVideoPlayerSubtitleSelection
internal data class NativePlayerSubtitlePresentation(
    val options: List<SubtitleOption>,
    val playbackSourceOptions: List<SourceOption>,
    val loading: Boolean,
)

internal data class NativePlayerSubtitleSelection(
    val selectedKey: String,
    val onSelectedKeyChanged: (String) -> Unit,
    val onControllerSelectedKeyChanged: (String) -> Unit,
)

private data class MaterializedStreamSubtitles(
    val tracks: List<me.yummydroid.app.data.ResolvedSubtitleTrack>,
    val hasPendingCandidates: Boolean,
)

@Composable
internal fun rememberNativePlayerSubtitlePresentation(
    stream: ResolvedVideoStream,
    currentVideo: VideoVariant,
    player: ExoPlayer,
    playerControlTexts: PlayerControlTexts,
    sourceSubtitleLabel: String,
    playbackMetadataLoading: Boolean,
    sourceOptions: List<SourceOption>,
    tracks: Tracks,
): NativePlayerSubtitlePresentation {
    val materialized = rememberMaterializedStreamSubtitles(stream, player)
    val resolvedSubtitles = remember(stream.subtitles, stream.embeddedSubtitles) {
        (
            stream.subtitles.mapIndexedNotNull { index, subtitle ->
                subtitle.toSubtitleDisplayReference(index)
            } +
                stream.embeddedSubtitles.mapIndexedNotNull { index, subtitle ->
                    subtitle.toSubtitleDisplayReference(stream.subtitles.size + index)
                }
            ).distinctBy { subtitle ->
                listOf(
                    subtitle.media3Id,
                    subtitle.sourceIndex?.toString().orEmpty(),
                    subtitle.label,
                ).joinToString(":")
            }
    }
    val subtitleOptions = remember(tracks, playerControlTexts, resolvedSubtitles) {
        tracks.subtitleOptions(playerControlTexts, resolvedSubtitles)
    }
    val hasVerifiedSubtitles = resolvedSubtitles.isNotEmpty()
    val playbackSourceOptions = remember(sourceOptions, currentVideo, hasVerifiedSubtitles, sourceSubtitleLabel) {
        sourceOptions.withCurrentSubtitleMarker(
            currentVideo = currentVideo,
            hasSubtitles = hasVerifiedSubtitles,
            sourceSubtitleLabel = sourceSubtitleLabel,
        )
    }
    return NativePlayerSubtitlePresentation(
        options = subtitleOptions,
        playbackSourceOptions = playbackSourceOptions,
        loading = playbackMetadataLoading &&
            materialized.hasPendingCandidates &&
            materialized.tracks.isEmpty(),
    )
}

@Composable
private fun rememberMaterializedStreamSubtitles(
    stream: ResolvedVideoStream,
    player: ExoPlayer,
): MaterializedStreamSubtitles {
    val materializedSubtitles = remember(stream.subtitles) {
        stream.subtitles.filter { subtitle -> subtitle.isMaterializedSubtitleTrack() }
    }
    val pendingSubtitleCandidates = remember(stream.subtitles) {
        stream.subtitles.any { subtitle -> !subtitle.isMaterializedSubtitleTrack() }
    }
    val streamSubtitleSignature = remember(stream.url, materializedSubtitles) {
        materializedSubtitles.joinToString("|") { subtitle ->
            listOf(
                subtitle.uri,
                subtitle.label,
                subtitle.language.orEmpty(),
                subtitle.mimeType.orEmpty(),
            ).joinToString(":")
        }
    }
    var appliedSubtitleSignature by remember(player) { mutableStateOf(streamSubtitleSignature) }
    LaunchedEffect(player, stream.url, streamSubtitleSignature) {
        if (appliedSubtitleSignature == streamSubtitleSignature) return@LaunchedEffect
        if (
            player.prepareCurrentMediaItemIfSameVideo(
                stream.toMediaItem(player.currentMediaItem?.mediaMetadata ?: MediaMetadata.EMPTY),
            )
        ) {
            appliedSubtitleSignature = streamSubtitleSignature
        } else {
            AppLog.w("YummyDroidPlayer", "Skipped subtitle media item update because the current video changed")
        }
    }
    return MaterializedStreamSubtitles(materializedSubtitles, pendingSubtitleCandidates)
}

@Composable
internal fun rememberNativePlayerSubtitleSelection(
    currentVideo: VideoVariant,
    streamUrl: String,
    player: ExoPlayer,
    playerView: () -> PlayerView?,
    subtitleOptions: List<SubtitleOption>,
): NativePlayerSubtitleSelection {
    var selectedSubtitleKey by remember(currentVideo.id, streamUrl) {
        mutableStateOf(SUBTITLE_OFF_KEY)
    }
    var subtitleSelectionTouched by remember(currentVideo.id, streamUrl) {
        mutableStateOf(false)
    }
    NativePlayerSubtitleSelectionEffect(
        player = player,
        playerView = playerView,
        subtitleOptions = subtitleOptions,
        selectedSubtitleKey = selectedSubtitleKey,
        subtitleSelectionTouched = subtitleSelectionTouched,
        onSelectedSubtitleKeyChanged = { selectedSubtitleKey = it },
    )
    return NativePlayerSubtitleSelection(
        selectedKey = selectedSubtitleKey,
        onSelectedKeyChanged = { selectedSubtitleKey = it },
        onControllerSelectedKeyChanged = {
            subtitleSelectionTouched = true
            selectedSubtitleKey = it
        },
    )
}

@Composable
private fun NativePlayerSubtitleSelectionEffect(
    player: ExoPlayer,
    playerView: () -> PlayerView?,
    subtitleOptions: List<SubtitleOption>,
    selectedSubtitleKey: String,
    subtitleSelectionTouched: Boolean,
    onSelectedSubtitleKeyChanged: (String) -> Unit,
) {
    LaunchedEffect(player, subtitleOptions, selectedSubtitleKey, subtitleSelectionTouched) {
        val selectedSubtitleIsAvailable = subtitleOptions.any {
            it.matchesSelectedSubtitleKey(selectedSubtitleKey)
        }
        if (!subtitleSelectionTouched && (selectedSubtitleKey == SUBTITLE_OFF_KEY || !selectedSubtitleIsAvailable)) {
            val defaultOption = subtitleOptions.defaultSubtitleOption() ?: run {
                if (selectedSubtitleKey != SUBTITLE_OFF_KEY) {
                    onSelectedSubtitleKeyChanged(SUBTITLE_OFF_KEY)
                    player.disableSubtitles()
                    playerView()?.setSelectedSubtitleTag(SUBTITLE_OFF_KEY)
                }
                return@LaunchedEffect
            }
            player.selectSubtitle(defaultOption)
            val stableKey = defaultOption.subtitleOptionIdentity()
            onSelectedSubtitleKeyChanged(stableKey)
            playerView()?.setSelectedSubtitleTag(stableKey)
            return@LaunchedEffect
        }
        if (selectedSubtitleKey == SUBTITLE_OFF_KEY) return@LaunchedEffect
        if (!selectedSubtitleIsAvailable) {
            onSelectedSubtitleKeyChanged(SUBTITLE_OFF_KEY)
            player.disableSubtitles()
            playerView()?.setSelectedSubtitleTag(SUBTITLE_OFF_KEY)
        }
    }
}

private fun PlayerView.setSelectedSubtitleTag(key: String) {
    findViewById<View>(R.id.yummy_player_subtitles)
        ?.setTag(R.id.yummy_player_subtitles, key)
}

// NativeVideoPlayerView
@OptIn(UnstableApi::class)
@Composable
internal fun NativePlayerView(
    player: Player,
    videoToken: String,
    interactive: Boolean,
    isInPictureInPicture: Boolean,
    controllerBinding: PlayerControllerBinding,
    playerControlFocusToRestoreId: Int?,
    onPlayerViewChanged: (PlayerView) -> Unit,
    onPlayerControlFocusRestored: () -> Unit,
    modifier: Modifier,
) {
    val configuration = LocalConfiguration.current
    val windowSize = currentWindowSizeDp()
    key(
        configuration.orientation,
        windowSize.width,
        windowSize.height,
        configuration.smallestScreenWidthDp,
    ) {
        AndroidView(
            factory = { viewContext ->
                val playerContext = ContextThemeWrapper(viewContext, R.style.Theme_YummyDroid_Player)
                val parent = FrameLayout(playerContext)
                LayoutInflater.from(playerContext).inflate(R.layout.yummy_player_view, parent, false) as PlayerView
            },
            update = { view ->
                onPlayerViewChanged(view)
                view.bindPlayer(
                    player = player,
                    videoToken = videoToken,
                    interactive = interactive,
                    isInPictureInPicture = isInPictureInPicture,
                    controllerBinding = controllerBinding,
                    playerControlFocusToRestoreId = playerControlFocusToRestoreId,
                    onPlayerControlFocusRestored = onPlayerControlFocusRestored,
                )
            },
            modifier = modifier,
        )
    }
}

@OptIn(UnstableApi::class)
private fun PlayerView.bindPlayer(
    player: Player,
    videoToken: String,
    interactive: Boolean,
    isInPictureInPicture: Boolean,
    controllerBinding: PlayerControllerBinding,
    playerControlFocusToRestoreId: Int?,
    onPlayerControlFocusRestored: () -> Unit,
) {
    attachPlayer(player)
    configurePlayerView(videoToken)
    requestInitialPlayerFocus(interactive, playerControlFocusToRestoreId)
    val restoreAfterPictureInPicture = updatePictureInPictureMode(isInPictureInPicture)
    bindPlayerInteraction(
        interactive = interactive,
        isInPictureInPicture = isInPictureInPicture,
        controllerBinding = controllerBinding,
        playerControlFocusToRestoreId = playerControlFocusToRestoreId,
        onPlayerControlFocusRestored = onPlayerControlFocusRestored,
        restoreAfterPictureInPicture = restoreAfterPictureInPicture,
    )
}

private fun PlayerView.attachPlayer(player: Player) {
    if (this.player === player) return
    unbindSkipControls()
    this.player = player
}

@OptIn(UnstableApi::class)
private fun PlayerView.configurePlayerView(videoToken: String) {
    controllerAutoShow = false
    setControllerAnimationEnabled(false)
    setControllerShowTimeoutMs(0)
    installPlayerControlsVisibilitySync()
    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
    (this as? YummyPlayerView)?.updateControllerViewport()
    applyYummySubtitleStyle()
    installVideoZoomGestures(token = videoToken)
    keepScreenOn = true
    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
}

private fun PlayerView.requestInitialPlayerFocus(
    interactive: Boolean,
    playerControlFocusToRestoreId: Int?,
) {
    if (!interactive) return
    if (isInTouchMode) return
    if (playerControlFocusToRestoreId != null) return
    if (hasFocusedPlayerControl()) return
    requestFocus()
}

private fun PlayerView.updatePictureInPictureMode(isInPictureInPicture: Boolean): Boolean {
    val previousPictureInPictureMode = tagValue<Boolean>(R.id.yummy_player_view)
    if (previousPictureInPictureMode != isInPictureInPicture) {
        setTag(R.id.yummy_player_view, isInPictureInPicture)
        applyPictureInPictureControllerMode(isInPictureInPicture)
    }
    return previousPictureInPictureMode != false
}

private fun PlayerView.bindPlayerInteraction(
    interactive: Boolean,
    isInPictureInPicture: Boolean,
    controllerBinding: PlayerControllerBinding,
    playerControlFocusToRestoreId: Int?,
    onPlayerControlFocusRestored: () -> Unit,
    restoreAfterPictureInPicture: Boolean,
) {
    when {
        !interactive -> {
            dismissPlayerPopupMenu()
            unbindSkipControls()
            hidePlayerControls()
            clearFocus()
        }

        isInPictureInPicture -> hidePlayerControls()
        else -> bindInteractiveController(
            controllerBinding = controllerBinding,
            playerControlFocusToRestoreId = playerControlFocusToRestoreId,
            onPlayerControlFocusRestored = onPlayerControlFocusRestored,
            restoreAfterPictureInPicture = restoreAfterPictureInPicture,
        )
    }
}

private fun PlayerView.bindInteractiveController(
    controllerBinding: PlayerControllerBinding,
    playerControlFocusToRestoreId: Int?,
    onPlayerControlFocusRestored: () -> Unit,
    restoreAfterPictureInPicture: Boolean,
) {
    bindYummyController(binding = controllerBinding)
    restorePlayerControlFocusWhenReady(
        controlId = playerControlFocusToRestoreId,
        onRestored = onPlayerControlFocusRestored,
    )
    if (restoreAfterPictureInPicture) {
        restoreControllerAfterPictureInPicture()
    }
}

@OptIn(UnstableApi::class)
private fun PlayerView.applyYummySubtitleStyle() {
    subtitleView?.apply {
        setApplyEmbeddedStyles(true)
        setApplyEmbeddedFontSizes(true)
        setStyle(
            CaptionStyleCompat(
                Color.WHITE,
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                Color.BLACK,
                Typeface.DEFAULT_BOLD,
            ),
        )
    }
}
