package me.yummydroid.app.ui

import android.graphics.Color
import android.graphics.Typeface
import android.os.SystemClock
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.media3.exoplayer.DefaultRenderersFactory
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
import me.yummydroid.app.PlaybackRecoveryCandidate
import me.yummydroid.app.PlayerPipController
import me.yummydroid.app.R

@OptIn(UnstableApi::class)
@Composable
internal fun NativeVideoPlayer(
    stream: ResolvedVideoStream,
    animeTitle: String,
    currentVideo: VideoVariant,
    settings: AppSettings,
    startPositionMs: Long,
    playbackPreferredQuality: PreferredQuality,
    pendingPlaybackRecovery: PlaybackRecoveryCandidate?,
    groups: Map<String, List<VideoVariant>>,
    selectedKey: String?,
    previousVideo: VideoVariant?,
    nextVideo: VideoVariant?,
    allowSubscription: Boolean,
    subscriptionActive: Boolean,
    onToggleSubscription: () -> Unit,
    onSelectGroup: (String, VideoVariant?, Long) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    onPlayVideoAt: (VideoVariant, Long) -> Unit,
    onPlayVideoAtQuality: (VideoVariant, Long, PreferredQuality) -> Unit,
    onPlaybackFailed: (VideoVariant, Long) -> Unit,
    onPrepareFallbackSource: (VideoVariant) -> Unit,
    onSwitchToPreparedFallbackSource: (VideoVariant, Long) -> Boolean,
    onRecoveryPrebufferReady: (Long, Long) -> Boolean,
    onRecoveryPrebufferFailed: (Long) -> Unit,
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
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val activity = remember(context) { context.findActivity() }
    val fallbackScope = rememberCoroutineScope()
    val playerControlTexts = rememberPlayerControlTexts()
    val currentSettings by rememberUpdatedState(settings)
    val currentProgressCallback by rememberUpdatedState(onPlaybackProgress)
    val currentProgressVideo by rememberUpdatedState(currentVideo)
    val latestCurrentVideo by rememberUpdatedState(currentVideo)
    val latestPreviousVideo by rememberUpdatedState(previousVideo)
    val latestNextVideo by rememberUpdatedState(nextVideo)
    val latestPlayVideoAt by rememberUpdatedState(onPlayVideoAt)
    val latestPrepareFallbackSource by rememberUpdatedState(onPrepareFallbackSource)
    val latestSwitchToPreparedFallbackSource by rememberUpdatedState(onSwitchToPreparedFallbackSource)
    val latestRecoveryPrebufferReady by rememberUpdatedState(onRecoveryPrebufferReady)
    val latestRecoveryPrebufferFailed by rememberUpdatedState(onRecoveryPrebufferFailed)
    var fallbackSuppressedUntilMs by remember(stream.url, currentVideo.id) {
        mutableLongStateOf(SystemClock.elapsedRealtime() + PLAYBACK_SEEK_BUFFER_GRACE_MS)
    }
    var bufferResetSignal by remember(stream.url, currentVideo.id) { mutableIntStateOf(0) }
    val httpClient = remember { defaultVideoResolveClient() }
    val renderersFactory = remember(context, settings.decoderMode) {
        DefaultRenderersFactory(context)
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
    val streamSubtitleSignature = remember(stream.url, stream.subtitles) {
        stream.subtitles.joinToString("|") { subtitle ->
            listOf(subtitle.uri, subtitle.label, subtitle.language.orEmpty(), subtitle.mimeType.orEmpty()).joinToString(":")
        }
    }
    var appliedSubtitleSignature by remember(player) { mutableStateOf(streamSubtitleSignature) }
    LaunchedEffect(player, stream.url, streamSubtitleSignature) {
        if (appliedSubtitleSignature == streamSubtitleSignature) return@LaunchedEffect
        player.refreshCurrentMediaItem(
            mediaItem = stream.toMediaItem(),
            expectExternalSubtitles = stream.subtitles.isNotEmpty(),
        )
        appliedSubtitleSignature = streamSubtitleSignature
    }
    DisposableEffect(
        pendingPlaybackRecovery?.id,
        pendingPlaybackRecovery?.stream?.url,
        player,
        settings.playerBufferPreset,
        httpClient,
        renderersFactory,
    ) {
        val recovery = pendingPlaybackRecovery
        if (
            recovery == null ||
            recovery.stream.url.isBlank() ||
            recovery.stream.url == stream.url ||
            stream.url.startsWith("file:", ignoreCase = true) ||
            stream.url.startsWith("content:", ignoreCase = true) ||
            recovery.stream.url.startsWith("file:", ignoreCase = true) ||
            recovery.stream.url.startsWith("content:", ignoreCase = true)
        ) {
            onDispose {}
        } else {
            val targetBufferMs = settings.playerBufferPreset.recoveryPrebufferTargetMs()
            val probeStartPositionMs = recovery.positionMs.coerceAtLeast(0L)
            var finished = false
            val probePlayer = runCatching {
                createVideoPlayer(
                    context = context,
                    stream = recovery.stream,
                    startPositionMs = probeStartPositionMs,
                    httpClient = httpClient,
                    renderersFactory = renderersFactory,
                    loadControl = settings.playerBufferPreset.toRecoveryPrebufferLoadControl(),
                ).apply {
                    volume = 0f
                    playWhenReady = false
                }
            }.getOrElse { throwable ->
                AppLog.w("YummyDroidPlayer", "Recovery prebuffer failed to start", throwable)
                latestRecoveryPrebufferFailed(recovery.id)
                null
            }

            if (probePlayer == null) {
                onDispose {}
            } else {
                fun failRecovery(throwable: Throwable? = null) {
                    if (finished) return
                    finished = true
                    if (throwable != null) {
                        AppLog.w("YummyDroidPlayer", "Recovery prebuffer failed", throwable)
                    }
                    latestRecoveryPrebufferFailed(recovery.id)
                }

                fun bufferedAheadMs(): Long {
                    val bufferedPosition = probePlayer.bufferedPosition.takeIf { it != C.TIME_UNSET } ?: 0L
                    return (bufferedPosition - probeStartPositionMs).coerceAtLeast(0L)
                }

                fun maybeSwitchAfterBuffer(): Boolean {
                    if (finished) return true
                    if (probePlayer.playbackState != Player.STATE_READY) return false
                    if (bufferedAheadMs() < targetBufferMs) return false
                    finished = true
                    latestRecoveryPrebufferReady(
                        recovery.id,
                        player.currentPosition.coerceAtLeast(0L),
                    )
                    return true
                }

                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        maybeSwitchAfterBuffer()
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        failRecovery(error)
                    }
                }
                probePlayer.addListener(listener)
                val prebufferJob = fallbackScope.launch {
                    val startedAtMs = SystemClock.elapsedRealtime()
                    while (!finished) {
                        delay(PLAYBACK_RECOVERY_PREBUFFER_POLL_MS)
                        if (maybeSwitchAfterBuffer()) break
                        if (SystemClock.elapsedRealtime() - startedAtMs >= PLAYBACK_RECOVERY_PREBUFFER_TIMEOUT_MS) {
                            failRecovery()
                        }
                    }
                }

                onDispose {
                    prebufferJob.cancel()
                    probePlayer.removeListener(listener)
                    probePlayer.release()
                }
            }
        }
    }
    var tracks by remember(player) { mutableStateOf(player.currentTracks) }
    val onlineQualityOptions = remember(tracks) { tracks.videoQualityOptions() }
    val resolvedSubtitleLabels = remember(stream.subtitles) {
        val labels = stream.subtitles
            .map { subtitle -> subtitleLabelForMedia3(subtitle.label, subtitle.uri) }
            .filter { it.isNotBlank() }
            .toSet()
        labels.takeIf { it.isNotEmpty() }
    }
    val subtitleOptions = remember(tracks, playerControlTexts, resolvedSubtitleLabels) {
        tracks.subtitleOptions(playerControlTexts, resolvedSubtitleLabels)
    }
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
                handle = { event ->
                    val view = playerView
                    if (view == null || isInPictureInPicture) {
                        false
                    } else {
                        view.handleRemoteInputAction(event)
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
                player.play()
            }

            override fun pause() {
                player.pause()
            }

            override fun playPreviousEpisode() {
                latestPreviousVideo?.let { previous ->
                    showVoiceFallbackToast(context, latestCurrentVideo, previous)
                    player.pause()
                    latestPlayVideoAt(previous, 0L)
                }
            }

            override fun playNextEpisode() {
                latestNextVideo?.let { next ->
                    showVoiceFallbackToast(context, latestCurrentVideo, next)
                    player.pause()
                    latestPlayVideoAt(next, 0L)
                }
            }

            override fun setPictureInPictureMode(enabled: Boolean) {
                playerView?.applyPictureInPictureControllerMode(enabled)
            }

            override fun hideAppControls() {
                playerView?.hideController()
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
            playerView?.findViewById<TextView>(R.id.yummy_player_quality)
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
                    playerView?.findViewById<TextView>(R.id.yummy_player_subtitles)
                        ?.setTag(R.id.yummy_player_subtitles, SUBTITLE_OFF_KEY)
                }
                return@LaunchedEffect
            }
            player.selectSubtitle(defaultOption)
            val stableKey = defaultOption.subtitleOptionIdentity()
            selectedSubtitleKey = stableKey
            playerView?.findViewById<TextView>(R.id.yummy_player_subtitles)
                ?.setTag(R.id.yummy_player_subtitles, stableKey)
            return@LaunchedEffect
        }
        if (selectedSubtitleKey == SUBTITLE_OFF_KEY) return@LaunchedEffect
        if (!selectedSubtitleIsAvailable) {
            selectedSubtitleKey = SUBTITLE_OFF_KEY
            player.disableSubtitles()
            playerView?.findViewById<TextView>(R.id.yummy_player_subtitles)
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
            playerView?.hideController()
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

    LaunchedEffect(player, currentVideo.id, stream.url, settings.playerBufferPreset, bufferResetSignal) {
        if (
            stream.url.startsWith("file:", ignoreCase = true) ||
            currentVideo.localPlaybackUrl.isNotBlank()
        ) {
            return@LaunchedEffect
        }

        var lastBufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L)
        var stagnantSinceMs: Long? = null
        var prepareRequested = false
        while (true) {
            delay(PLAYBACK_BUFFER_STALL_POLL_MS)
            val nowMs = SystemClock.elapsedRealtime()
            val positionMs = player.currentPosition.coerceAtLeast(0L)
            val durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0L }
            val bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L)
            val bufferAheadMs = (bufferedPositionMs - positionMs).coerceAtLeast(0L)
            val bufferIsGrowing = bufferedPositionMs > lastBufferedPositionMs + PLAYBACK_BUFFER_GROWTH_EPSILON_MS
            val nearPlaybackEnd = durationMs
                ?.minus(positionMs)
                ?.coerceAtLeast(0L)
                ?.let { remainingMs ->
                    remainingMs <= maxOf(
                        PLAYBACK_BUFFER_END_IGNORE_MS,
                        settings.playerBufferPreset.switchFallbackThresholdMs * 2,
                    )
                } == true
            val bufferedToEnd = durationMs
                ?.let { bufferedPositionMs >= it - PLAYBACK_BUFFER_END_EPSILON_MS } == true
            val canInspectBuffer = nowMs >= fallbackSuppressedUntilMs &&
                player.playbackState == Player.STATE_READY &&
                (player.isPlaying || player.playWhenReady) &&
                !nearPlaybackEnd &&
                !bufferedToEnd

            if (
                canInspectBuffer &&
                !bufferIsGrowing &&
                bufferAheadMs <= settings.playerBufferPreset.prepareFallbackThresholdMs
            ) {
                val stagnantFromMs = stagnantSinceMs ?: nowMs.also { stagnantSinceMs = it }
                val stagnantForMs = nowMs - stagnantFromMs
                if (!prepareRequested && stagnantForMs >= PLAYBACK_BUFFER_STALL_CONFIRM_MS) {
                    prepareRequested = true
                    latestPrepareFallbackSource(currentVideo)
                }
                if (
                    bufferAheadMs <= settings.playerBufferPreset.switchFallbackThresholdMs &&
                    stagnantForMs >= PLAYBACK_BUFFER_STALL_SWITCH_MS &&
                    latestSwitchToPreparedFallbackSource(currentVideo, positionMs)
                ) {
                    return@LaunchedEffect
                }
            } else {
                stagnantSinceMs = null
                if (
                    bufferIsGrowing ||
                    bufferAheadMs > settings.playerBufferPreset.prepareFallbackThresholdMs * 2
                ) {
                    prepareRequested = false
                }
            }
            lastBufferedPositionMs = maxOf(lastBufferedPositionMs, bufferedPositionMs)
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
                playerView?.findViewById<TextView>(R.id.yummy_player_subtitles)
                    ?.setTag(R.id.yummy_player_subtitles, selectedSubtitleKey)
                val resolvedSourceKey = latestStreamSelectedQualityKey
                if (resolvedSourceKey != null && latestQualityOptions.any { it.matchesSelectedQualityKey(resolvedSourceKey) }) {
                    selectedQualityKey = resolvedSourceKey
                    playerView?.findViewById<TextView>(R.id.yummy_player_quality)
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
                    playerView?.findViewById<TextView>(R.id.yummy_player_quality)
                        ?.setTag(R.id.yummy_player_quality, preferredKey)
                    return
                }
                val actualQualityKey = player.currentQualityKey()
                selectedQualityKey = currentTracks.videoQualityOptions()
                    .firstOrNull { it.matchesSelectedQualityKey(actualQualityKey) }
                    ?.qualityOptionIdentity()
                    ?: actualQualityKey?.qualityIdentityFromLabel()
                    ?: actualQualityKey
                playerView?.findViewById<TextView>(R.id.yummy_player_quality)
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
                            !fallbackReported
                        ) {
                            fallbackReported = true
                            onPlaybackFailed(currentVideo, player.currentPosition.coerceAtLeast(0L))
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
                        playerView?.hideController()
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
                bufferResetSignal += 1
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
                    onPlaybackFailed(currentVideo, player.currentPosition.coerceAtLeast(0L))
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
                val parent = FrameLayout(viewContext)
                LayoutInflater.from(viewContext).inflate(R.layout.yummy_player_view, parent, false) as PlayerView
            },
            update = { view ->
                playerView = view
                view.player = player
                view.controllerAutoShow = false
                view.setControllerAnimationEnabled(false)
                view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                view.applyYummySubtitleStyle()
                view.installVideoZoomGestures(token = "${currentVideo.id}:${stream.url}")
                view.keepScreenOn = true
                view.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                view.requestFocus()
                val previousPictureInPictureMode = view.tagValue<Boolean>(R.id.yummy_player_view)
                if (previousPictureInPictureMode != isInPictureInPicture) {
                    view.setTag(R.id.yummy_player_view, isInPictureInPicture)
                    view.applyPictureInPictureControllerMode(isInPictureInPicture)
                }
                if (isInPictureInPicture) {
                    view.hideController()
                } else {
                    view.bindYummyController(
                        player = player,
                        animeTitle = animeTitle,
                        currentVideo = currentVideo,
                        isLocalPlayback = stream.url.startsWith("file:", ignoreCase = true) ||
                            currentVideo.localPlaybackUrl.isNotBlank(),
                        groups = groups,
                        selectedKey = selectedKey,
                        previousVideo = previousVideo,
                        nextVideo = nextVideo,
                        allowSubscription = allowSubscription,
                        subscriptionActive = subscriptionActive,
                        onToggleSubscription = onToggleSubscription,
                        qualityOptions = qualityOptions,
                        selectedQualityKey = selectedQualityKey,
                        onSelectedQualityKeyChange = { selectedQualityKey = it },
                        subtitleOptions = subtitleOptions,
                        selectedSubtitleKey = selectedSubtitleKey,
                        onSelectedSubtitleKeyChange = {
                            subtitleSelectionTouched = true
                            selectedSubtitleKey = it
                        },
                        onSelectLocalQuality = { localFile ->
                            val positionMs = player.currentPosition.coerceAtLeast(0L)
                            player.pause()
                            onPlayVideoAt(currentVideo.withOfflineFile(localFile), positionMs)
                        },
                        onSelectPreferredQuality = { preferredQuality ->
                            val positionMs = player.currentPosition.coerceAtLeast(0L)
                            player.pause()
                            onPlayVideoAtQuality(currentVideo.withoutLocalPlayback(), positionMs, preferredQuality)
                        },
                        onSelectGroup = onSelectGroup,
                        onPlayVideo = onPlayVideo,
                        onPlayVideoAt = onPlayVideoAt,
                        canUsePictureInPicture = canUsePictureInPicture,
                        onEnterPictureInPicture = onEnterPictureInPicture,
                        settings = settings,
                        texts = playerControlTexts,
                        onSettingsChange = onSettingsChange,
                        onBack = onBack,
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

private suspend fun ExoPlayer.refreshCurrentMediaItem(
    mediaItem: MediaItem,
    expectExternalSubtitles: Boolean,
) {
    replaceCurrentMediaItem(mediaItem)
    if (!expectExternalSubtitles || currentTracks.hasSupportedSubtitleTracks()) return

    delay(PLAYBACK_SUBTITLE_TRACK_REFRESH_WAIT_MS)
    if (currentTracks.hasSupportedSubtitleTracks()) return

    AppLog.w(
        "YummyDroidPlayer",
        "Subtitle tracks were not exposed after media item replacement; preparing current item at the active position",
    )
    val positionMs = currentPosition.coerceAtLeast(0L)
    val shouldPlay = playWhenReady
    setMediaItem(mediaItem, positionMs)
    prepare()
    playWhenReady = shouldPlay
}

private fun ExoPlayer.replaceCurrentMediaItem(mediaItem: MediaItem) {
    val currentIndex = currentMediaItemIndex.takeIf { it != C.INDEX_UNSET } ?: 0
    if (mediaItemCount > currentIndex) {
        replaceMediaItem(currentIndex, mediaItem)
        return
    }

    val positionMs = currentPosition.coerceAtLeast(0L)
    val shouldPrepare = playbackState == Player.STATE_IDLE
    setMediaItem(mediaItem, positionMs)
    if (shouldPrepare) {
        prepare()
    }
}

private fun Tracks.hasSupportedSubtitleTracks(): Boolean {
    return groups.any { group ->
        group.type == C.TRACK_TYPE_TEXT &&
            group.isSupported &&
            (0 until group.length).any { trackIndex -> group.isTrackSupported(trackIndex) }
    }
}

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
