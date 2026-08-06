package me.yummydroid.app.ui

import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R as Media3R
import me.yummydroid.app.R
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.OfflineVideoFile
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.VideoVariant

internal class PlayerControllerBinding(
    val player: ExoPlayer,
    val animeTitle: String,
    val currentVideo: VideoVariant,
    val isLocalPlayback: Boolean,
    val groups: Map<String, List<VideoVariant>>,
    val selectedKey: String?,
    val sourceOptions: List<SourceOption>,
    val selectedSourceKey: String?,
    val previousVideo: VideoVariant?,
    val nextVideo: VideoVariant?,
    val allowSubscription: Boolean,
    val subscriptionActive: Boolean,
    val onToggleSubscription: () -> Unit,
    val qualityOptions: List<QualityOption>,
    val selectedQualityKey: String?,
    val onSelectedQualityKeyChange: (String) -> Unit,
    val subtitleOptions: List<SubtitleOption>,
    val subtitlesLoading: Boolean,
    val selectedSubtitleKey: String,
    val onSelectedSubtitleKeyChange: (String) -> Unit,
    val onSelectLocalQuality: (OfflineVideoFile) -> Unit,
    val onSelectPreferredQuality: (PreferredQuality) -> Unit,
    val onSelectGroup: (String, VideoVariant?, Long) -> Unit,
    val onSelectSource: (VideoVariant, Long) -> Unit,
    val onPlayVideoAt: (VideoVariant, Long) -> Unit,
    val canUsePictureInPicture: Boolean,
    val onEnterPictureInPicture: () -> Unit,
    val settings: AppSettings,
    val texts: PlayerControlTexts,
    val onSettingsChange: (AppSettings) -> Unit,
    val onBack: () -> Unit,
    val onRequestPlay: () -> Unit,
    val onPausePlayback: () -> Unit,
    val onRememberPlayerControlFocus: (Int) -> Unit,
)

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

private fun PlayerView.bindPlayerMetadata(binding: PlayerControllerBinding) {
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

private fun PlayerView.bindPlayerEpisodeControls(binding: PlayerControllerBinding) {
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

private fun PlayerView.bindPlayerVoiceControl(binding: PlayerControllerBinding) {
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

private fun PlayerView.bindPlayerSourceControl(binding: PlayerControllerBinding) {
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

private fun PlayerView.bindPlayerQualityControl(binding: PlayerControllerBinding) {
    findViewById<TextView>(R.id.yummy_player_quality)?.apply {
        val qualityTitle = binding.qualityOptions.selectedQualityControlText(binding.selectedQualityKey)
        applyPlayerQualityControl(qualityTitle, "${binding.texts.quality}: $qualityTitle")
        visibility = View.VISIBLE
        setPlayerControlEnabled(binding.qualityOptions.isNotEmpty())
        setOnClickListener {
            if (binding.qualityOptions.isEmpty()) return@setOnClickListener
            showPlayerControls()
            showQualityPopup(
                anchor = this,
                player = binding.player,
                options = binding.qualityOptions,
                selectedQualityKey = binding.selectedQualityKey,
                onSelectedQualityKeyChange = binding.onSelectedQualityKeyChange,
                onSelectLocalQuality = binding.onSelectLocalQuality,
                onSelectPreferredQuality = binding.onSelectPreferredQuality,
                onRememberPlayerControlFocus = binding.onRememberPlayerControlFocus,
            )
        }
    }
}

private fun PlayerView.bindPlayerSubtitleControl(binding: PlayerControllerBinding) {
    findViewById<ImageButton>(R.id.yummy_player_subtitles)?.apply {
        val label = if (binding.subtitlesLoading && binding.subtitleOptions.isEmpty()) {
            "${binding.texts.subtitles}..."
        } else {
            binding.texts.subtitles
        }
        applyPlayerIconControl(
            iconResId = R.drawable.ic_player_subtitles,
            label = label,
            active = binding.selectedSubtitleKey != SUBTITLE_OFF_KEY && binding.subtitleOptions.isNotEmpty(),
        )
        visibility = View.VISIBLE
        setPlayerControlEnabled(binding.subtitleOptions.isNotEmpty())
        setOnClickListener {
            if (binding.subtitleOptions.isEmpty()) return@setOnClickListener
            showPlayerControls()
            showSubtitlePopup(
                anchor = this,
                player = binding.player,
                options = binding.subtitleOptions,
                selectedSubtitleKey = binding.selectedSubtitleKey,
                texts = binding.texts,
                onRememberPlayerControlFocus = binding.onRememberPlayerControlFocus,
                onSelectedSubtitleKeyChange = binding.onSelectedSubtitleKeyChange,
            )
        }
    }
}

private fun PlayerView.bindPlayerSubscriptionControl(binding: PlayerControllerBinding) {
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

private fun PlayerView.bindPlayerSpeedControl(binding: PlayerControllerBinding) {
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

private fun PlayerView.bindPlayerPictureInPictureControl(binding: PlayerControllerBinding) {
    findViewById<ImageButton>(R.id.yummy_player_pip)?.apply {
        applyPlayerIconControl(R.drawable.ic_player_pip, context.getString(R.string.player_pip))
        visibility = if (binding.canUsePictureInPicture) View.VISIBLE else View.GONE
        setOnClickListener {
            hidePlayerControls()
            postDelayed({ binding.onEnterPictureInPicture() }, PIP_ENTER_DELAY_MS)
        }
    }
}

private fun PlayerView.bindPlayerSkipControls(binding: PlayerControllerBinding) {
    if (binding.settings.skipOpeningsAndEndings) {
        bindSkipControls(
            player = binding.player,
            currentVideo = binding.currentVideo,
            texts = binding.texts,
        )
    } else {
        unbindSkipControls()
    }
}
