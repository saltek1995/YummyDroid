package me.yummydroid.app.ui

import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R as Media3R
import me.yummydroid.app.R

internal fun PlayerView.bindPlayerMetadata(binding: PlayerControllerBinding) {
    setTag(R.id.yummy_player_request_play_callback, binding.onRequestPlay)
    setTag(R.id.yummy_player_pause_callback, binding.onPausePlayback)
    applyPlayerControlIconColors()
    findViewById<TextView>(R.id.yummy_player_title)?.text =
        binding.animeTitle.ifBlank { binding.texts.title }
    findViewById<TextView>(R.id.yummy_player_subtitle)?.text =
        binding.currentVideo.playbackSubtitle(binding.texts, binding.groups.values.flatten())
    findViewById<TextView>(R.id.yummy_player_info)?.text =
        binding.currentVideo.playbackSourceLabel(binding.isLocalPlayback)

    findViewById<View>(Media3R.id.exo_settings)?.visibility = View.GONE
    findViewById<View>(R.id.yummy_player_back)?.setOnClickListener { binding.onBack() }
    findViewById<View>(Media3R.id.exo_play_pause)?.setOnClickListener {
        if (binding.player.isPlaying) {
            binding.onPausePlayback()
        } else {
            binding.onRequestPlay()
        }
    }
}

internal fun PlayerView.bindPlayerEpisodeControls(binding: PlayerControllerBinding) {
    findViewById<View>(R.id.yummy_episode_previous)?.apply {
        visibility = if (binding.previousVideo != null) View.VISIBLE else View.GONE
        setOnClickListener {
            binding.previousVideo?.let { previousVideo ->
                showVoiceFallbackToast(context, binding.currentVideo, previousVideo)
                binding.onPausePlayback()
                binding.onPlayVideoAt(previousVideo, 0L)
            }
        }
    }

    findViewById<View>(R.id.yummy_episode_next)?.apply {
        visibility = if (binding.nextVideo != null) View.VISIBLE else View.GONE
        setOnClickListener {
            binding.nextVideo?.let { nextVideo ->
                showVoiceFallbackToast(context, binding.currentVideo, nextVideo)
                binding.onPausePlayback()
                binding.onPlayVideoAt(nextVideo, 0L)
            }
        }
    }
}

internal fun PlayerView.bindPlayerVoiceControl(binding: PlayerControllerBinding) {
    findViewById<ImageButton>(R.id.yummy_player_voice)?.apply {
        applyPlayerIconControl(R.drawable.ic_player_voice, binding.texts.voice)
        visibility = View.VISIBLE
        setPlayerControlEnabled(binding.groups.size > 1)
        setOnClickListener {
            if (binding.groups.size <= 1) return@setOnClickListener
            showPlayerControls()
            showVoicePopup(
                anchor = this,
                groups = binding.groups,
                selectedKey = binding.selectedKey,
                preferredGroupKey = binding.currentVideo.groupKey,
                currentVideo = binding.currentVideo,
                texts = binding.texts,
                onRememberPlayerControlFocus = binding.onRememberPlayerControlFocus,
                onSelectGroup = { groupKey, replacement ->
                    binding.onPausePlayback()
                    binding.onSelectGroup(
                        groupKey,
                        replacement,
                        binding.player.currentPosition.coerceAtLeast(0L),
                    )
                },
            )
        }
    }
}

internal fun PlayerView.bindPlayerSourceControl(binding: PlayerControllerBinding) {
    findViewById<ImageButton>(R.id.yummy_player_source)?.apply {
        applyPlayerIconControl(R.drawable.ic_player_source, binding.texts.source)
        visibility = View.VISIBLE
        setPlayerControlEnabled(binding.sourceOptions.size > 1)
        setOnClickListener {
            if (binding.sourceOptions.size <= 1) return@setOnClickListener
            showPlayerControls()
            showSourcePopup(
                anchor = this,
                options = binding.sourceOptions,
                selectedSourceKey = binding.selectedSourceKey,
                onRememberPlayerControlFocus = binding.onRememberPlayerControlFocus,
                onSelectSource = { source ->
                    binding.onPausePlayback()
                    binding.onSelectSource(
                        source,
                        binding.player.currentPosition.coerceAtLeast(0L),
                    )
                },
            )
        }
    }
}
