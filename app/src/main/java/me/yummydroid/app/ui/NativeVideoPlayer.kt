package me.yummydroid.app.ui

import android.graphics.Color
import android.graphics.Typeface
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.layout
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.VideoSize
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.yummydroid.app.AppLog
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.defaultVideoResolveClient
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.PipPlayerHandle
import me.yummydroid.app.PlaybackFailure
import me.yummydroid.app.PlaybackFailureKind
import me.yummydroid.app.PlayerPipController
import me.yummydroid.app.R
import me.yummydroid.app.localizedString
import me.yummydroid.app.sourceSelectionKey

@OptIn(UnstableApi::class)
@Composable
internal fun NativeVideoPlayer(
    stream: ResolvedVideoStream,
    animeTitle: String,
    currentVideo: VideoVariant,
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
    playerControlFocusToRestoreId: Int? = null,
    keepControlsVisibleAfterReady: Boolean = false,
    onRememberPlayerControlFocus: (Int) -> Unit = {},
    onPlayerControlFocusRestored: () -> Unit = {},
    onKeepControlsVisibleAfterReadyRequested: () -> Unit = {},
    onControlsKeptVisibleAfterReady: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val activity = remember(context) { context.findActivity() }
    val fallbackScope = rememberCoroutineScope()
    val playerActionScope = rememberCoroutineScope()
    val playerControlTexts = rememberPlayerControlTexts()
    val sourceSubtitleLabel = uiText(UiStringKey.HasSubtitles)
    val currentSettings by rememberUpdatedState(settings)
    val currentProgressCallback by rememberUpdatedState(onPlaybackProgress)
    val currentProgressVideo by rememberUpdatedState(currentVideo)
    val latestCurrentVideo by rememberUpdatedState(currentVideo)
    val latestPreviousVideo by rememberUpdatedState(previousVideo)
    val latestNextVideo by rememberUpdatedState(nextVideo)
    val latestPlayVideoAt by rememberUpdatedState(onPlayVideoAt)
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
    var pendingPlaybackStartJob by remember(player) { mutableStateOf<Job?>(null) }

    fun pausePlayback() {
        pendingPlaybackStartJob?.cancel()
        pendingPlaybackStartJob = null
        player.pause()
    }

    fun requestPlaybackStart() {
        if (player.isPlaying || pendingPlaybackStartJob?.isActive == true) return
        pendingPlaybackStartJob = playerActionScope.launch {
            try {
                while (
                    player.playbackState != Player.STATE_READY &&
                    player.playbackState != Player.STATE_ENDED &&
                    player.playbackState != Player.STATE_IDLE
                ) {
                    delay(24)
                }
                if (player.playbackState == Player.STATE_IDLE) return@launch
                player.play()
            } finally {
                pendingPlaybackStartJob = null
            }
        }
    }
    val materializedSubtitles = remember(stream.subtitles) {
        stream.subtitles.filter { subtitle -> subtitle.isMaterializedSubtitleTrack() }
    }
    val pendingSubtitleCandidates = remember(stream.subtitles) {
        stream.subtitles.any { subtitle -> !subtitle.isMaterializedSubtitleTrack() }
    }
    val streamSubtitleSignature = remember(stream.url, materializedSubtitles) {
        materializedSubtitles.joinToString("|") { subtitle ->
            listOf(subtitle.uri, subtitle.label, subtitle.language.orEmpty(), subtitle.mimeType.orEmpty()).joinToString(":")
        }
    }
    var appliedSubtitleSignature by remember(player) { mutableStateOf(streamSubtitleSignature) }
    LaunchedEffect(player, stream.url, streamSubtitleSignature) {
        if (appliedSubtitleSignature == streamSubtitleSignature) return@LaunchedEffect
        if (player.prepareCurrentMediaItemIfSameVideo(stream.toMediaItem())) {
            appliedSubtitleSignature = streamSubtitleSignature
        } else {
            AppLog.w("YummyDroidPlayer", "Skipped subtitle media item update because the current video changed")
        }
    }
    var tracks by remember(player) { mutableStateOf(player.currentTracks) }
    val onlineQualityOptions = remember(tracks) { tracks.videoQualityOptions() }
    val resolvedSubtitles = remember(stream.subtitles, stream.embeddedSubtitles) {
        (
            stream.subtitles.mapIndexedNotNull { index, subtitle ->
                subtitle.toSubtitleDisplayReference(index)
            } +
                stream.embeddedSubtitles.mapIndexedNotNull { index, subtitle ->
                    subtitle.toSubtitleDisplayReference(stream.subtitles.size + index)
                }
            )
            .distinctBy { subtitle ->
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
    val playbackSourceOptions = remember(sourceOptions, currentVideo, subtitleOptions, sourceSubtitleLabel) {
        sourceOptions.withCurrentSubtitleMarker(
            currentVideo = currentVideo,
            hasSubtitles = subtitleOptions.isNotEmpty(),
            sourceSubtitleLabel = sourceSubtitleLabel,
        )
    }
    val subtitlesLoading = playbackMetadataLoading && pendingSubtitleCandidates && materializedSubtitles.isEmpty()
    val sourceQualityOptions = remember(
        groups,
        selectedKey,
        currentVideo.matchingEpisodeKey,
        currentVideo.matchingVoiceKey,
    ) {
        val sourceVideos = groups[selectedKey].orEmpty().ifEmpty { groups[currentVideo.matchingVoiceKey].orEmpty() }
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
    val qualityOptions = remember(onlineQualityOptions, sourceQualityOptions, streamQualityOptions, localQualityOptions, offlineMode) {
        mergeVideoQualityOptions(
            onlineOptions = onlineQualityOptions + sourceQualityOptions + streamQualityOptions,
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
    val latestQualityOptions by rememberUpdatedState(qualityOptions)
    val latestPlaybackPreferredQuality by rememberUpdatedState(playbackPreferredQuality)
    val latestStreamSelectedQualityKey by rememberUpdatedState(streamSelectedQualityKey)
    var selectedQualityKey by remember(currentVideo.id, stream.url, stream.selectedVideoHeight) {
        val preferredOption = qualityOptions.preferredOption(
            playbackPreferredQuality.takeUnless { it == PreferredQuality.Auto } ?: settings.defaultQuality,
        )
        mutableStateOf(
            currentVideo.selectedLocalQualityKey(stream.url)
                ?: streamSelectedQualityKey?.takeIf { key -> qualityOptions.any { it.matchesSelectedQualityKey(key) } }
                ?: preferredOption?.qualityOptionIdentity(),
        )
    }
    var selectedSubtitleKey by remember(currentVideo.id, stream.url) {
        mutableStateOf(SUBTITLE_OFF_KEY)
    }
    var subtitleSelectionTouched by remember(currentVideo.id, stream.url) {
        mutableStateOf(false)
    }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    DisposableEffect(player, isInPictureInPicture, onRegisterPlayerInputActionHandler) {
        onRegisterPlayerInputActionHandler(
            PlayerInputController(
                controlsVisible = {
                    !isInPictureInPicture && playerView?.hasVisiblePlayerControls() == true
                },
                hideControls = {
                    val view = playerView
                    if (view == null || isInPictureInPicture) {
                        false
                    } else {
                        view.hidePlayerControls()
                        true
                    }
                },
                handle = { event ->
                    val view = playerView
                    if (view == null || isInPictureInPicture) {
                        false
                    } else {
                        view.handleRemoteInputAction(
                            event = event,
                            onRequestPlay = ::requestPlaybackStart,
                            onPausePlayback = ::pausePlayback,
                        )
                    }
                },
            ),
        )
        onDispose { onRegisterPlayerInputActionHandler(null) }
    }
    val pipPlayerHandle = remember(player) {
        object : PipPlayerHandle {
            override val isPlaying: Boolean
                get() = player.isPlaying

            override val canPlayPreviousEpisode: Boolean
                get() = latestPreviousVideo != null

            override val canPlayNextEpisode: Boolean
                get() = latestNextVideo != null

            override fun play() {
                requestPlaybackStart()
            }

            override fun pause() {
                pausePlayback()
            }

            override fun playPreviousEpisode() {
                latestPreviousVideo?.let { previous ->
                    showVoiceFallbackToast(context, latestCurrentVideo, previous)
                    pausePlayback()
                    latestPlayVideoAt(previous, 0L)
                }
            }

            override fun playNextEpisode() {
                latestNextVideo?.let { next ->
                    showVoiceFallbackToast(context, latestCurrentVideo, next)
                    pausePlayback()
                    latestPlayVideoAt(next, 0L)
                }
            }

            override fun setPictureInPictureMode(enabled: Boolean) {
                playerView?.applyPictureInPictureControllerMode(enabled)
            }

            override fun hideAppControls() {
                playerView?.hidePlayerControls()
            }
        }
    }
    LaunchedEffect(previousVideo?.id, nextVideo?.id, player) {
        PlayerPipController.notifyPlayingChanged()
    }

    LaunchedEffect(qualityOptions) {
        val currentKey = selectedQualityKey
        if (currentKey != null && qualityOptions.none { it.matchesSelectedQualityKey(currentKey) }) {
            val preferredQuality = playbackPreferredQuality.takeUnless { it == PreferredQuality.Auto } ?: settings.defaultQuality
            selectedQualityKey = streamSelectedQualityKey
                ?.takeIf { key -> qualityOptions.any { it.matchesSelectedQualityKey(key) } }
                ?: qualityOptions.preferredOption(preferredQuality)?.qualityOptionIdentity()
        }
    }

    LaunchedEffect(qualityOptions, playbackPreferredQuality, settings.defaultQuality, stream.url, streamSelectedQualityKey) {
        if (selectedQualityKey != null && qualityOptions.any { it.matchesSelectedQualityKey(selectedQualityKey) }) {
            return@LaunchedEffect
        }
        val resolvedSourceKey = streamSelectedQualityKey
            ?.takeIf { key -> qualityOptions.any { it.matchesSelectedQualityKey(key) } }
        val preferredOption = qualityOptions.preferredOption(
            playbackPreferredQuality.takeUnless { it == PreferredQuality.Auto } ?: settings.defaultQuality,
        )
        val preferredKey = resolvedSourceKey ?: preferredOption?.qualityOptionIdentity()
        if (preferredKey != null && selectedQualityKey != preferredKey) {
            preferredOption?.takeIf { it.group != null }?.let(player::selectQuality)
            selectedQualityKey = preferredKey
            playerView?.findViewById<View>(R.id.yummy_player_quality)
                ?.setTag(R.id.yummy_player_quality, preferredKey)
        }
    }

    LaunchedEffect(player, qualityOptions, playbackPreferredQuality, settings.defaultQuality, stream.url) {
        val preferredQuality = playbackPreferredQuality.takeUnless { it == PreferredQuality.Auto }
            ?: settings.defaultQuality.takeUnless { it == PreferredQuality.Auto }
        val preferredOption = preferredQuality?.let { qualityOptions.preferredOption(it) }
        if (preferredOption?.group != null) {
            player.selectQuality(preferredOption)
        }
    }

    LaunchedEffect(player, subtitleOptions, selectedSubtitleKey, subtitleSelectionTouched) {
        val selectedSubtitleIsAvailable = subtitleOptions.any { it.matchesSelectedSubtitleKey(selectedSubtitleKey) }
        if (!subtitleSelectionTouched && (selectedSubtitleKey == SUBTITLE_OFF_KEY || !selectedSubtitleIsAvailable)) {
            val defaultOption = subtitleOptions.defaultSubtitleOption() ?: run {
                if (selectedSubtitleKey != SUBTITLE_OFF_KEY) {
                    selectedSubtitleKey = SUBTITLE_OFF_KEY
                    player.disableSubtitles()
                    playerView?.findViewById<View>(R.id.yummy_player_subtitles)
                        ?.setTag(R.id.yummy_player_subtitles, SUBTITLE_OFF_KEY)
                }
                return@LaunchedEffect
            }
            player.selectSubtitle(defaultOption)
            val stableKey = defaultOption.subtitleOptionIdentity()
            selectedSubtitleKey = stableKey
            playerView?.findViewById<View>(R.id.yummy_player_subtitles)
                ?.setTag(R.id.yummy_player_subtitles, stableKey)
            return@LaunchedEffect
        }
        if (selectedSubtitleKey == SUBTITLE_OFF_KEY) return@LaunchedEffect
        if (!selectedSubtitleIsAvailable) {
            selectedSubtitleKey = SUBTITLE_OFF_KEY
            player.disableSubtitles()
            playerView?.findViewById<View>(R.id.yummy_player_subtitles)
                ?.setTag(R.id.yummy_player_subtitles, SUBTITLE_OFF_KEY)
        }
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

    LaunchedEffect(player, settings.matchDisplayModeToVideo, tracks) {
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

    DisposableEffect(player) {
        var fallbackReported = false
        var autoAdvanceReported = false
        var playbackStartedReported = false
        var playbackEndedReported = false
        var bufferingFallbackJob: Job? = null
        PlayerPipController.registerPlayer(pipPlayerHandle)
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                PlayerPipController.notifyPlayingChanged()
                if (isPlaying && !playbackStartedReported) {
                    playbackStartedReported = true
                    onPlaybackStarted(currentVideo)
                }
            }

            override fun onTracksChanged(currentTracks: Tracks) {
                tracks = currentTracks
                selectedSubtitleKey = currentTracks.currentSubtitleKey() ?: SUBTITLE_OFF_KEY
                playerView?.findViewById<View>(R.id.yummy_player_subtitles)
                    ?.setTag(R.id.yummy_player_subtitles, selectedSubtitleKey)
                val resolvedSourceKey = latestStreamSelectedQualityKey
                if (resolvedSourceKey != null && latestQualityOptions.any { it.matchesSelectedQualityKey(resolvedSourceKey) }) {
                    selectedQualityKey = resolvedSourceKey
                    playerView?.findViewById<View>(R.id.yummy_player_quality)
                        ?.setTag(R.id.yummy_player_quality, resolvedSourceKey)
                    return
                }
                val explicitPreferredQuality = latestPlaybackPreferredQuality.takeUnless { it == PreferredQuality.Auto }
                    ?: currentSettings.defaultQuality.takeUnless { it == PreferredQuality.Auto }
                val preferredOption = explicitPreferredQuality
                    ?.let { latestQualityOptions.preferredOption(it) }
                if (preferredOption != null) {
                    val preferredKey = preferredOption.qualityOptionIdentity()
                    selectedQualityKey = preferredKey
                    playerView?.findViewById<View>(R.id.yummy_player_quality)
                        ?.setTag(R.id.yummy_player_quality, preferredKey)
                    return
                }
                val actualQualityKey = player.currentQualityKey()
                selectedQualityKey = currentTracks.videoQualityOptions()
                    .firstOrNull { it.matchesSelectedQualityKey(actualQualityKey) }
                    ?.qualityOptionIdentity()
                    ?: actualQualityKey?.qualityIdentityFromLabel()
                    ?: actualQualityKey
                playerView?.findViewById<View>(R.id.yummy_player_quality)
                    ?.setTag(R.id.yummy_player_quality, selectedQualityKey)
                activity?.applyVideoDisplayMode(
                    enabled = currentSettings.matchDisplayModeToVideo,
                    video = player.currentVideoDisplayInfo(),
                )
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                activity?.applyVideoDisplayMode(
                    enabled = currentSettings.matchDisplayModeToVideo,
                    video = player.currentVideoDisplayInfo() ?: videoSize.toVideoDisplayInfo(),
                )
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_BUFFERING && playbackStartedReported && !fallbackReported) {
                    bufferingFallbackJob?.cancel()
                    bufferingFallbackJob = fallbackScope.launch {
                        val delayMs = maxOf(
                            PLAYBACK_BUFFERING_FALLBACK_DELAY_MS,
                            fallbackSuppressedUntilMs - SystemClock.elapsedRealtime(),
                        )
                        delay(delayMs.coerceAtLeast(0L))
                        if (
                            SystemClock.elapsedRealtime() >= fallbackSuppressedUntilMs &&
                            player.playbackState == Player.STATE_BUFFERING &&
                            !fallbackReported &&
                            !isPlaybackEndCloseOrBuffered(
                                positionMs = player.currentPosition.coerceAtLeast(0L),
                                bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L),
                                durationMs = resolvedPlaybackDurationMs(
                                    playerDurationMs = player.duration,
                                    contentDurationMs = player.contentDuration,
                                    metadataDurationSeconds = currentVideo.durationSeconds,
                                ),
                                switchFallbackThresholdMs = currentSettings.playerBufferPreset.switchFallbackThresholdMs,
                            )
                        ) {
                            fallbackReported = true
                            onPlaybackFailed(
                                currentVideo,
                                player.currentPosition.coerceAtLeast(0L),
                                PlaybackFailure(
                                    kind = PlaybackFailureKind.BufferingTimeout,
                                    message = context.localizedString(
                                        R.string.ui_playback_buffer_not_filling,
                                        currentSettings.contentLanguage,
                                    ),
                                ),
                            )
                        }
                    }
                } else if (playbackState != Player.STATE_BUFFERING) {
                    bufferingFallbackJob?.cancel()
                    bufferingFallbackJob = null
                }

                if (playbackState == Player.STATE_ENDED && !playbackEndedReported) {
                    playbackEndedReported = true
                    onPlaybackEnded(currentVideo)
                }
                if (
                    playbackState == Player.STATE_ENDED &&
                    currentSettings.autoplayNextEpisode &&
                    !autoAdvanceReported
                ) {
                    autoAdvanceReported = true
                    nextVideo?.let { next ->
                        showVoiceFallbackToast(context, currentVideo, next)
                        currentProgressCallback(next, 1_000L, 0L)
                        playerView?.hidePlayerControls()
                        onPlayVideoAt(next, 0L)
                    }
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                fallbackSuppressedUntilMs = SystemClock.elapsedRealtime() + PLAYBACK_SEEK_BUFFER_GRACE_MS
            }

            override fun onPlayerError(error: PlaybackException) {
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
                if (!fallbackReported) {
                    bufferingFallbackJob?.cancel()
                    bufferingFallbackJob = null
                    fallbackReported = true
                    onPlaybackFailed(
                        currentVideo,
                        player.currentPosition.coerceAtLeast(0L),
                        PlaybackFailure(
                            kind = PlaybackFailureKind.PlayerError,
                            message = error.playbackFailureMessage(),
                        ),
                    )
                }
            }
        }
        player.addListener(listener)
        onDispose {
            bufferingFallbackJob?.cancel()
            currentProgressCallback(
                currentProgressVideo,
                player.currentPosition.coerceAtLeast(0L),
                player.duration.normalizedDurationMs(),
            )
            player.removeListener(listener)
            PlayerPipController.unregisterPlayer(pipPlayerHandle)
            playerView?.clearTimelineScrubState()
            playerView?.unbindSkipControls()
            activity?.clearPreferredDisplayMode()
            player.release()
        }
    }

    key(
        configuration.orientation,
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        configuration.smallestScreenWidthDp,
    ) {
        AndroidView(
            factory = { viewContext ->
                val playerContext = ContextThemeWrapper(viewContext, R.style.Theme_YummyDroid_Player)
                val parent = FrameLayout(playerContext)
                LayoutInflater.from(playerContext).inflate(R.layout.yummy_player_view, parent, false) as PlayerView
            },
            update = { view ->
                playerView = view
                view.player = player
                view.controllerAutoShow = false
                view.setControllerAnimationEnabled(false)
                view.setControllerShowTimeoutMs(0)
                view.installPlayerControlsVisibilitySync()
                view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                view.applyYummySubtitleStyle()
                view.installVideoZoomGestures(token = "${currentVideo.id}:${stream.url}")
                view.keepScreenOn = true
                view.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                if (
                    !view.isInTouchMode &&
                    playerControlFocusToRestoreId == null &&
                    !view.hasFocusedPlayerControl()
                ) {
                    view.requestFocus()
                }
                val previousPictureInPictureMode = view.tagValue<Boolean>(R.id.yummy_player_view)
                if (previousPictureInPictureMode != isInPictureInPicture) {
                    view.setTag(R.id.yummy_player_view, isInPictureInPicture)
                    view.applyPictureInPictureControllerMode(isInPictureInPicture)
                }
                if (isInPictureInPicture) {
                    view.hidePlayerControls()
                } else {
                    view.bindYummyController(
                        player = player,
                        animeTitle = animeTitle,
                        currentVideo = currentVideo,
                        isLocalPlayback = stream.url.startsWith("file:", ignoreCase = true) ||
                            currentVideo.localPlaybackUrl.isNotBlank(),
                        groups = groups,
                        selectedKey = selectedKey,
                        sourceOptions = playbackSourceOptions,
                        selectedSourceKey = selectedSourceKey,
                        previousVideo = previousVideo,
                        nextVideo = nextVideo,
                        allowSubscription = allowSubscription,
                        subscriptionActive = subscriptionActive,
                        onToggleSubscription = onToggleSubscription,
                        qualityOptions = qualityOptions,
                        selectedQualityKey = selectedQualityKey,
                        onSelectedQualityKeyChange = { selectedQualityKey = it },
                        subtitleOptions = subtitleOptions,
                        subtitlesLoading = subtitlesLoading,
                        selectedSubtitleKey = selectedSubtitleKey,
                        onSelectedSubtitleKeyChange = {
                            subtitleSelectionTouched = true
                            selectedSubtitleKey = it
                        },
                        onSelectLocalQuality = { localFile ->
                            val positionMs = player.currentPosition.coerceAtLeast(0L)
                            onKeepControlsVisibleAfterReadyRequested()
                            pausePlayback()
                            onPlayVideoAt(currentVideo.withOfflineFile(localFile), positionMs)
                        },
                        onSelectPreferredQuality = { preferredQuality ->
                            val positionMs = player.currentPosition.coerceAtLeast(0L)
                            onKeepControlsVisibleAfterReadyRequested()
                            pausePlayback()
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
                        onPlayVideo = onPlayVideo,
                        onPlayVideoAt = onPlayVideoAt,
                        canUsePictureInPicture = canUsePictureInPicture,
                        onEnterPictureInPicture = onEnterPictureInPicture,
                        settings = settings,
                        texts = playerControlTexts,
                        onSettingsChange = onSettingsChange,
                        onBack = onBack,
                        onRequestPlay = ::requestPlaybackStart,
                        onPausePlayback = ::pausePlayback,
                        onRememberPlayerControlFocus = onRememberPlayerControlFocus,
                    )
                    view.restorePlayerControlFocusWhenReady(
                        controlId = playerControlFocusToRestoreId,
                        onRestored = onPlayerControlFocusRestored,
                    )
                    if (previousPictureInPictureMode != false) {
                        view.restoreControllerAfterPictureInPicture()
                    }
                }
            },
            modifier = modifier,
        )
    }
}

private fun ExoPlayer.prepareCurrentMediaItemIfSameVideo(mediaItem: MediaItem): Boolean {
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

internal fun resolvedPlaybackDurationMs(
    playerDurationMs: Long,
    contentDurationMs: Long,
    metadataDurationSeconds: Int?,
): Long? {
    return playerDurationMs.takeIf { it != C.TIME_UNSET && it > 0L }
        ?: contentDurationMs.takeIf { it != C.TIME_UNSET && it > 0L }
        ?: metadataDurationSeconds
            ?.takeIf { it > 0 }
            ?.toLong()
            ?.times(1_000L)
}

internal fun isPlaybackEndCloseOrBuffered(
    positionMs: Long,
    bufferedPositionMs: Long,
    durationMs: Long?,
    switchFallbackThresholdMs: Long,
): Boolean {
    val duration = durationMs?.takeIf { it > 0L } ?: return false
    val safePositionMs = positionMs.coerceAtLeast(0L)
    val safeBufferedPositionMs = bufferedPositionMs.coerceAtLeast(0L)
    val endIgnoreWindowMs = maxOf(PLAYBACK_BUFFER_END_IGNORE_MS, switchFallbackThresholdMs * 2)
    val remainingMs = (duration - safePositionMs).coerceAtLeast(0L)
    return remainingMs <= endIgnoreWindowMs ||
        safeBufferedPositionMs >= duration - PLAYBACK_BUFFER_END_EPSILON_MS
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

private fun PlaybackException.playbackFailureMessage(): String {
    val httpError = cause as? HttpDataSource.InvalidResponseCodeException
    if (httpError != null) return "HTTP ${httpError.responseCode}"
    return errorCodeName.takeIf { it.isNotBlank() }
        ?: localizedMessage?.takeIf { it.isNotBlank() }
        ?: message?.takeIf { it.isNotBlank() }
        ?: "playback error"
}
