package me.yummydroid.app.ui

import me.yummydroid.app.data.OfflineVideoFile
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.sourceSelectionKey

internal fun createNativeVideoPlayerControllerBinding(
    binding: NativeVideoPlayerRuntimeBinding,
    session: NativeVideoPlayerRuntimeSession,
): PlayerControllerBinding {
    val selection = session.selection
    return PlayerControllerBinding(
        player = session.player,
        animeTitle = binding.animeTitle,
        currentVideo = binding.currentVideo,
        isLocalPlayback = binding.stream.url.startsWith("file:", ignoreCase = true) ||
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
    val positionMs = session.player.currentPosition.coerceAtLeast(0L)
    binding.onKeepControlsVisibleAfterReadyRequested()
    session.playbackActions.pause()
    binding.onPlayVideoAt(binding.currentVideo.withOfflineFile(localFile), positionMs)
}

private fun createPreferredQualitySelectionHandler(
    binding: NativeVideoPlayerRuntimeBinding,
    session: NativeVideoPlayerRuntimeSession,
): (PreferredQuality) -> Unit = { preferredQuality ->
    val positionMs = session.player.currentPosition.coerceAtLeast(0L)
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
