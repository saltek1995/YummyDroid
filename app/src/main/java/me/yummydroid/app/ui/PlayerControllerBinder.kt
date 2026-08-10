package me.yummydroid.app.ui

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
internal fun PlayerView.bindYummyController(binding: PlayerControllerBinding) {
    bindPlayerMetadata(binding)
    bindPlayerEpisodeControls(binding)
    bindPlayerVoiceControl(binding)
    bindPlayerSourceControl(binding)
    bindPlayerQualityControl(binding)
    bindPlayerSubtitleControl(binding)
    bindPlayerSubscriptionControl(binding)
    bindPlayerSpeedControl(binding)
    bindPlayerPictureInPictureControl(binding)
    bindPlayerSkipControls(binding)
    bindSkipTimelineMarkers(player = binding.player, currentVideo = binding.currentVideo)
    configurePlayerFocusNavigation()
}
