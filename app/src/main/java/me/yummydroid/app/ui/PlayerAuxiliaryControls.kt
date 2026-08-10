package me.yummydroid.app.ui

import android.view.View
import android.widget.ImageButton
import androidx.media3.ui.PlayerView
import me.yummydroid.app.R

internal fun PlayerView.bindPlayerSubscriptionControl(binding: PlayerControllerBinding) {
    findViewById<ImageButton>(R.id.yummy_player_subscription)?.apply {
        applyPlayerIconControl(
            iconResId = R.drawable.ic_player_subscription,
            label = if (binding.subscriptionActive) binding.texts.subscribed else binding.texts.subscription,
            active = binding.subscriptionActive,
        )
        visibility = View.VISIBLE
        setPlayerControlEnabled(binding.allowSubscription)
        setOnClickListener {
            if (!binding.allowSubscription) return@setOnClickListener
            showPlayerControls()
            binding.onToggleSubscription()
        }
    }
}

internal fun PlayerView.bindPlayerSpeedControl(binding: PlayerControllerBinding) {
    findViewById<ImageButton>(R.id.yummy_player_speed)?.apply {
        applyPlayerIconControl(
            iconResId = R.drawable.ic_player_speed,
            label = "${context.getString(R.string.player_speed)}: ${binding.settings.playerSpeed.title}",
        )
        visibility = View.VISIBLE
        setOnClickListener {
            showPlayerControls()
            showSpeedPopup(
                anchor = this,
                selected = binding.settings.playerSpeed,
                onRememberPlayerControlFocus = binding.onRememberPlayerControlFocus,
                onSelected = { speed ->
                    binding.onSettingsChange(binding.settings.copy(playerSpeed = speed))
                },
            )
        }
    }
}

internal fun PlayerView.bindPlayerPictureInPictureControl(binding: PlayerControllerBinding) {
    findViewById<ImageButton>(R.id.yummy_player_pip)?.apply {
        applyPlayerIconControl(R.drawable.ic_player_pip, context.getString(R.string.player_pip))
        visibility = if (binding.canUsePictureInPicture) View.VISIBLE else View.GONE
        setOnClickListener {
            hidePlayerControls()
            postDelayed({ binding.onEnterPictureInPicture() }, PIP_ENTER_DELAY_MS)
        }
    }
}

internal fun PlayerView.bindPlayerSkipControls(binding: PlayerControllerBinding) {
    if (!binding.settings.skipOpeningsAndEndings || binding.currentVideo.skipSegments.isEmpty()) {
        unbindSkipControls()
    } else if (binding.skipControlsTimelineReady) {
        bindSkipControls(
            player = binding.player,
            currentVideo = binding.currentVideo,
            texts = binding.texts,
        )
    }
}
