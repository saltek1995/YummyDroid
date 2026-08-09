package me.yummydroid.app.ui

import android.os.SystemClock
import android.view.View
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
    var skipControlsTimelineReady by remember(player) {
        mutableStateOf(player.hasReadyTimeline())
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
                onTracksChanged = { tracks = it },
                onSelectedSubtitleKeyChanged = { selectedSubtitleKey = it },
                onSelectedQualityKeyChanged = { selectedQualityKey = it },
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
        onPlayVideoAt = onPlayVideoAt,
        canUsePictureInPicture = canUsePictureInPicture,
        onEnterPictureInPicture = onEnterPictureInPicture,
        settings = settings,
        skipControlsTimelineReady = skipControlsTimelineReady,
        texts = playerControlTexts,
        onSettingsChange = onSettingsChange,
        onBack = onBack,
        onRequestPlay = ::requestPlaybackStart,
        onPausePlayback = ::pausePlayback,
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
