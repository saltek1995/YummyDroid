package me.yummydroid.app.ui

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.defaultVideoResolveClient
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.PlaybackFailure
import me.yummydroid.app.PlaybackFailureKind
import me.yummydroid.app.PlayerPipController
import me.yummydroid.app.R
import me.yummydroid.app.localizedString
import me.yummydroid.app.sourceSelectionKey

@OptIn(UnstableApi::class)
@Composable
internal fun NativeVideoPlayerRuntime(
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
    onPlayVideo: (VideoVariant) -> Unit,
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
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val playerActionScope = rememberCoroutineScope()
    val playerControlTexts = rememberPlayerControlTexts()
    val sourceSubtitleLabel = uiText(UiStringKey.HasSubtitles)
    val currentSettings by rememberUpdatedState(settings)
    val currentProgressCallback by rememberUpdatedState(onPlaybackProgress)
    val currentProgressVideo by rememberUpdatedState(currentVideo)
    var fallbackSuppressedUntilMs by remember(stream.url, currentVideo.id) {
        mutableLongStateOf(SystemClock.elapsedRealtime() + PLAYBACK_SEEK_BUFFER_GRACE_MS)
    }
    val httpClient = remember { defaultVideoResolveClient() }
    val renderersFactory = remember(context, settings.decoderMode) {
        YummyRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector(settings.decoderMode.mediaCodecSelector())
    }
    val player = remember(
        stream.url,
        stream.headers,
        startPositionMs,
        httpClient,
        renderersFactory,
        settings.playerBufferPreset,
    ) {
        createVideoPlayer(
            context = context,
            stream = stream,
            startPositionMs = startPositionMs,
            httpClient = httpClient,
            renderersFactory = renderersFactory,
            loadControl = settings.playerBufferPreset.toLoadControl(),
        )
    }
    var skipControlsTimelineReady by remember(player) {
        mutableStateOf(player.hasReadyTimeline())
    }
    val playbackActions = rememberNativePlayerPlaybackActions(player, playerActionScope)
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    val selection = rememberNativePlayerSelection(
        stream = stream,
        currentVideo = currentVideo,
        player = player,
        playerView = { playerView },
        playerControlTexts = playerControlTexts,
        sourceSubtitleLabel = sourceSubtitleLabel,
        playbackMetadataLoading = playbackMetadataLoading,
        groups = groups,
        selectedKey = selectedKey,
        sourceOptions = sourceOptions,
        playbackPreferredQuality = playbackPreferredQuality,
        settings = settings,
        offlineMode = offlineMode,
    )
    val latestQualityOptions by rememberUpdatedState(selection.qualityOptions)
    val latestPlaybackPreferredQuality by rememberUpdatedState(playbackPreferredQuality)
    val latestStreamSelectedQualityKey by rememberUpdatedState(selection.streamSelectedQualityKey)
    RegisterNativePlayerInputController(
        player = player,
        playerView = { playerView },
        isInPictureInPicture = isInPictureInPicture,
        playbackActions = playbackActions,
        onRegisterPlayerInputActionHandler = onRegisterPlayerInputActionHandler,
    )
    val pipPlayerHandle = rememberNativePipPlayerHandle(
        context = context,
        player = player,
        playerView = { playerView },
        playbackActions = playbackActions,
        currentVideo = currentVideo,
        previousVideo = previousVideo,
        nextVideo = nextVideo,
        onPlayVideoAt = onPlayVideoAt,
    )
    LaunchedEffect(previousVideo?.id, nextVideo?.id, player) {
        PlayerPipController.notifyPlayingChanged()
    }

    LaunchedEffect(player, settings.playerSpeed) {
        player.setPlaybackSpeed(settings.playerSpeed.value)
    }

    LaunchedEffect(player) {
        while (player.playbackState != Player.STATE_READY && player.playbackState != Player.STATE_ENDED) {
            delay(24)
        }
        if (player.playbackState == Player.STATE_READY) {
            if (keepControlsVisibleAfterReady) {
                playerView?.showPlayerControls()
            } else {
                playerView?.hidePlayerControls()
            }
            if (keepControlsVisibleAfterReady) {
                onControlsKeptVisibleAfterReady()
            }
            player.play()
        }
    }

    LaunchedEffect(player, settings.matchDisplayModeToVideo, selection.tracks) {
        activity?.applyVideoDisplayMode(
            enabled = settings.matchDisplayModeToVideo,
            video = player.currentVideoDisplayInfo(),
        )
    }

    LaunchedEffect(player, currentVideo.id) {
        while (true) {
            delay(PLAYBACK_PROGRESS_SAVE_INTERVAL_MS)
            if (player.playbackState != Player.STATE_IDLE) {
                currentProgressCallback(
                    currentProgressVideo,
                    player.currentPosition.coerceAtLeast(0L),
                    player.duration.normalizedDurationMs(),
                )
            }
        }
    }

    NativePlayerLifecycle(
        binding = NativePlayerLifecycleBinding(
            player = player,
            pipPlayerHandle = pipPlayerHandle,
            metadataDurationSeconds = currentVideo.durationSeconds,
            state = NativePlayerEventState(
                playerView = { playerView },
                settings = { currentSettings },
                qualityOptions = { latestQualityOptions },
                playbackPreferredQuality = { latestPlaybackPreferredQuality },
                streamSelectedQualityKey = { latestStreamSelectedQualityKey },
                fallbackSuppressedUntilMs = { fallbackSuppressedUntilMs },
                onFallbackSuppressedUntilChanged = { fallbackSuppressedUntilMs = it },
                skipControlsTimelineReady = { skipControlsTimelineReady },
                onSkipControlsTimelineReady = { skipControlsTimelineReady = true },
                onTracksChanged = selection.onTracksChanged,
                onSelectedSubtitleKeyChanged = selection.onSelectedSubtitleKeyChanged,
                onSelectedQualityKeyChanged = selection.onSelectedQualityKeyChanged,
            ),
            callbacks = NativePlayerEventCallbacks(
                onPlaybackStarted = { onPlaybackStarted(currentVideo) },
                onPlaybackEnded = { onPlaybackEnded(currentVideo) },
                onBufferingTimeout = { positionMs ->
                    onPlaybackFailed(
                        currentVideo,
                        positionMs,
                        PlaybackFailure(
                            kind = PlaybackFailureKind.BufferingTimeout,
                            message = context.localizedString(
                                R.string.ui_playback_buffer_not_filling,
                                currentSettings.contentLanguage,
                            ),
                        ),
                    )
                },
                onAutoAdvance = {
                    nextVideo?.let { next ->
                        showVoiceFallbackToast(context, currentVideo, next)
                        currentProgressCallback(next, 1_000L, 0L)
                        playerView?.hidePlayerControls()
                        onPlayVideoAt(next, 0L)
                    }
                },
                onPlaybackError = { positionMs, error ->
                    onPlaybackFailed(
                        currentVideo,
                        positionMs,
                        PlaybackFailure(
                            kind = PlaybackFailureKind.PlayerError,
                            message = error.playbackFailureMessage(),
                        ),
                    )
                },
                onProgressSnapshot = { positionMs, durationMs ->
                    currentProgressCallback(currentProgressVideo, positionMs, durationMs)
                },
                onDisplayModeUpdate = { videoSize ->
                    activity?.applyVideoDisplayMode(
                        enabled = currentSettings.matchDisplayModeToVideo,
                        video = player.currentVideoDisplayInfo() ?: videoSize?.toVideoDisplayInfo(),
                    )
                },
                onDispose = {
                    playerView?.clearTimelineScrubState()
                    playerView?.unbindSkipControls()
                    activity?.clearPreferredDisplayMode()
                },
            ),
        ),
    )

    val controllerBinding = PlayerControllerBinding(
        player = player,
        animeTitle = animeTitle,
        currentVideo = currentVideo,
        isLocalPlayback = stream.url.startsWith("file:", ignoreCase = true) ||
            currentVideo.localPlaybackUrl.isNotBlank(),
        groups = groups,
        selectedKey = selectedKey,
        sourceOptions = selection.playbackSourceOptions,
        selectedSourceKey = selectedSourceKey,
        previousVideo = previousVideo,
        nextVideo = nextVideo,
        allowSubscription = allowSubscription,
        subscriptionActive = subscriptionActive,
        onToggleSubscription = onToggleSubscription,
        qualityOptions = selection.qualityOptions,
        selectedQualityKey = selection.selectedQualityKey,
        onSelectedQualityKeyChange = selection.onSelectedQualityKeyChanged,
        subtitleOptions = selection.subtitleOptions,
        subtitlesLoading = selection.subtitlesLoading,
        selectedSubtitleKey = selection.selectedSubtitleKey,
        onSelectedSubtitleKeyChange = selection.onControllerSelectedSubtitleKeyChanged,
        onSelectLocalQuality = { localFile ->
            val positionMs = player.currentPosition.coerceAtLeast(0L)
            onKeepControlsVisibleAfterReadyRequested()
            playbackActions.pause()
            onPlayVideoAt(currentVideo.withOfflineFile(localFile), positionMs)
        },
        onSelectPreferredQuality = { preferredQuality ->
            val positionMs = player.currentPosition.coerceAtLeast(0L)
            onKeepControlsVisibleAfterReadyRequested()
            playbackActions.pause()
            onPlayVideoAtQuality(currentVideo.withoutLocalPlayback(), positionMs, preferredQuality)
        },
        onSelectGroup = { groupKey, replacement, positionMs ->
            if (replacement != null) {
                onKeepControlsVisibleAfterReadyRequested()
            }
            onSelectGroup(groupKey, replacement, positionMs)
        },
        onSelectSource = { source, positionMs ->
            if (source.sourceSelectionKey != currentVideo.sourceSelectionKey) {
                onKeepControlsVisibleAfterReadyRequested()
            }
            onSelectSource(source, positionMs)
        },
        onPlayVideoAt = onPlayVideoAt,
        canUsePictureInPicture = canUsePictureInPicture,
        onEnterPictureInPicture = onEnterPictureInPicture,
        settings = settings,
        skipControlsTimelineReady = skipControlsTimelineReady,
        texts = playerControlTexts,
        onSettingsChange = onSettingsChange,
        onBack = onBack,
        onRequestPlay = playbackActions::requestStart,
        onPausePlayback = playbackActions::pause,
        onRememberPlayerControlFocus = onRememberPlayerControlFocus,
    )
    NativePlayerView(
        player = player,
        videoToken = "${currentVideo.id}:${stream.url}",
        interactive = interactive,
        isInPictureInPicture = isInPictureInPicture,
        controllerBinding = controllerBinding,
        playerControlFocusToRestoreId = playerControlFocusToRestoreId,
        onPlayerViewChanged = { playerView = it },
        onPlayerControlFocusRestored = onPlayerControlFocusRestored,
        modifier = modifier,
    )
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
