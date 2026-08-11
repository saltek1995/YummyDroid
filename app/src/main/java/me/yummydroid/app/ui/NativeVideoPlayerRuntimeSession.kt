package me.yummydroid.app.ui

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import me.yummydroid.app.PipPlayerHandle
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.defaultVideoResolveClient

internal class NativeVideoPlayerRuntimeSession(
    val context: Context,
    val activity: Activity?,
    val player: ExoPlayer,
    val playbackActions: NativePlayerPlaybackActions,
    val playerView: MutableState<PlayerView?>,
    val playerControlTexts: PlayerControlTexts,
    val selection: NativePlayerSelectionSnapshot,
    val pipPlayerHandle: PipPlayerHandle,
    val currentSettings: State<AppSettings>,
    val currentProgressCallback: State<(VideoVariant, Long, Long) -> Unit>,
    val currentProgressVideo: State<VideoVariant>,
    val latestQualityOptions: State<List<QualityOption>>,
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
    val skipControlsTimelineReady = remember(player) {
        mutableStateOf(player.hasReadyTimeline())
    }
    val playbackActions = rememberNativePlayerPlaybackActions(player, playerActionScope)
    val playerView = remember { mutableStateOf<PlayerView?>(null) }
    val controls = rememberNativeRuntimeControlSelection(binding, player, playerView)
    val latestQualityOptions = rememberUpdatedState(controls.selection.qualityOptions)
    val latestPlaybackPreferredQuality = rememberUpdatedState(binding.playbackPreferredQuality)
    val latestStreamSelectedQualityKey = rememberUpdatedState(
        controls.selection.streamSelectedQualityKey,
    )

    RegisterNativePlayerInputController(
        player = player,
        playerView = { playerView.value },
        isInPictureInPicture = binding.isInPictureInPicture,
        playbackActions = playbackActions,
        onRegisterPlayerInputActionHandler = binding.onRegisterPlayerInputActionHandler,
    )
    val pipPlayerHandle = rememberNativePipPlayerHandle(
        context = context,
        player = player,
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
        playbackActions = playbackActions,
        playerView = playerView,
        playerControlTexts = controls.texts,
        selection = controls.selection,
        pipPlayerHandle = pipPlayerHandle,
        currentSettings = currentSettings,
        currentProgressCallback = currentProgressCallback,
        currentProgressVideo = currentProgressVideo,
        latestQualityOptions = latestQualityOptions,
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
    return remember(
        binding.stream.url,
        binding.stream.headers,
        binding.startPositionMs,
        httpClient,
        renderersFactory,
        binding.settings.playerBufferPreset,
    ) {
        createVideoPlayer(
            context = context,
            stream = binding.stream,
            startPositionMs = binding.startPositionMs,
            httpClient = httpClient,
            renderersFactory = renderersFactory,
            loadControl = binding.settings.playerBufferPreset.toLoadControl(),
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
