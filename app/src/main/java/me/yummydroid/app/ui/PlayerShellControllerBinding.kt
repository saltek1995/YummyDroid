package me.yummydroid.app.ui

import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R as Media3R
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.R

internal inline fun <reified T> View.tagValue(tagId: Int): T? {
    return getTag(tagId) as? T
}

internal fun View.clearTagValue(tagId: Int) {
    setTag(tagId, null)
}

internal fun View.removeTaggedRunnable(tagId: Int) {
    tagValue<Runnable>(tagId)?.let(::removeCallbacks)
    clearTagValue(tagId)
}

@OptIn(UnstableApi::class)
internal fun PlayerView.bindYummyShellController(
    animeTitle: String,
    currentVideo: VideoVariant,
    settings: AppSettings,
    groups: Map<String, List<VideoVariant>>,
    selectedKey: String?,
    sourceOptions: List<SourceOption>,
    selectedSourceKey: String?,
    previousVideo: VideoVariant?,
    nextVideo: VideoVariant?,
    allowSubscription: Boolean,
    subscriptionActive: Boolean,
    canUsePictureInPicture: Boolean,
    showCenterControls: Boolean,
    texts: PlayerControlTexts,
    onToggleSubscription: () -> Unit,
    onSelectGroup: (String, VideoVariant?) -> Unit,
    onSelectSource: (VideoVariant) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    onBack: () -> Unit,
    onRememberPlayerControlFocus: (Int) -> Unit = {},
) {
    applyPlayerControlIconColors()
    bindShellHeader(
        animeTitle = animeTitle,
        currentVideo = currentVideo,
        videos = groups.values.flatten(),
        texts = texts,
    )
    bindShellTransport(showCenterControls, previousVideo, nextVideo, onPlayVideo, onBack)
    bindVoiceSelector(
        currentVideo = currentVideo,
        groups = groups,
        selectedKey = selectedKey,
        texts = texts,
        onRememberPlayerControlFocus = onRememberPlayerControlFocus,
        onSelectGroup = onSelectGroup,
    )
    bindSourceSelector(
        sourceOptions = sourceOptions,
        selectedSourceKey = selectedSourceKey,
        texts = texts,
        onRememberPlayerControlFocus = onRememberPlayerControlFocus,
        onSelectSource = onSelectSource,
    )
    bindStaticShellControls(
        settings = settings,
        allowSubscription = allowSubscription,
        subscriptionActive = subscriptionActive,
        canUsePictureInPicture = canUsePictureInPicture,
        texts = texts,
        onToggleSubscription = onToggleSubscription,
    )
}

@OptIn(UnstableApi::class)
private fun PlayerView.bindShellHeader(
    animeTitle: String,
    currentVideo: VideoVariant,
    videos: List<VideoVariant>,
    texts: PlayerControlTexts,
) {
    findViewById<TextView>(R.id.yummy_player_title)?.text = animeTitle.ifBlank { texts.title }
    findViewById<TextView>(R.id.yummy_player_subtitle)?.text = currentVideo.playbackSubtitle(texts, videos)
    findViewById<TextView>(R.id.yummy_player_info)?.text = currentVideo.playbackSourceLabel(false)
    findViewById<TextView>(Media3R.id.exo_position)?.text = context.getString(R.string.player_zero_time)
    findViewById<TextView>(Media3R.id.exo_duration)?.text = context.getString(R.string.player_zero_time)
}

@OptIn(UnstableApi::class)
private fun PlayerView.bindShellTransport(
    showCenterControls: Boolean,
    previousVideo: VideoVariant?,
    nextVideo: VideoVariant?,
    onPlayVideo: (VideoVariant) -> Unit,
    onBack: () -> Unit,
) {
    findViewById<View>(Media3R.id.exo_settings)?.visibility = View.GONE
    setSkipControlsActive(false)
    findViewById<View>(Media3R.id.exo_play_pause)?.visibility = View.GONE
    findViewById<View>(R.id.yummy_player_back)?.setOnClickListener { onBack() }
    findViewById<View>(R.id.yummy_player_episode_controls)?.visibility = if (showCenterControls) {
        View.VISIBLE
    } else {
        View.GONE
    }

    findViewById<View>(R.id.yummy_episode_previous)?.apply {
        visibility = if (showCenterControls && previousVideo != null) View.VISIBLE else View.GONE
        setOnClickListener { previousVideo?.let(onPlayVideo) }
    }
    findViewById<View>(R.id.yummy_episode_next)?.apply {
        visibility = if (showCenterControls && nextVideo != null) View.VISIBLE else View.GONE
        setOnClickListener { nextVideo?.let(onPlayVideo) }
    }
}

@OptIn(UnstableApi::class)
private fun PlayerView.bindVoiceSelector(
    currentVideo: VideoVariant,
    groups: Map<String, List<VideoVariant>>,
    selectedKey: String?,
    texts: PlayerControlTexts,
    onRememberPlayerControlFocus: (Int) -> Unit,
    onSelectGroup: (String, VideoVariant?) -> Unit,
) {
    findViewById<ImageButton>(R.id.yummy_player_voice)?.apply {
        applyPlayerIconControl(R.drawable.ic_player_voice, texts.voice)
        visibility = View.VISIBLE
        setPlayerControlEnabled(groups.size > 1)
        setOnClickListener {
            if (groups.size <= 1) return@setOnClickListener
            showPlayerControls()
            showVoicePopup(
                anchor = this,
                groups = groups,
                selectedKey = selectedKey,
                preferredGroupKey = currentVideo.groupKey,
                currentVideo = currentVideo,
                texts = texts,
                onRememberPlayerControlFocus = onRememberPlayerControlFocus,
                onSelectGroup = onSelectGroup,
            )
        }
    }
}

@OptIn(UnstableApi::class)
private fun PlayerView.bindSourceSelector(
    sourceOptions: List<SourceOption>,
    selectedSourceKey: String?,
    texts: PlayerControlTexts,
    onRememberPlayerControlFocus: (Int) -> Unit,
    onSelectSource: (VideoVariant) -> Unit,
) {
    findViewById<ImageButton>(R.id.yummy_player_source)?.apply {
        applyPlayerIconControl(R.drawable.ic_player_source, texts.source)
        visibility = View.VISIBLE
        setPlayerControlEnabled(sourceOptions.size > 1)
        setOnClickListener {
            if (sourceOptions.size <= 1) return@setOnClickListener
            showPlayerControls()
            showSourcePopup(
                anchor = this,
                options = sourceOptions,
                selectedSourceKey = selectedSourceKey,
                onRememberPlayerControlFocus = onRememberPlayerControlFocus,
                onSelectSource = onSelectSource,
            )
        }
    }
}

@OptIn(UnstableApi::class)
private fun PlayerView.bindStaticShellControls(
    settings: AppSettings,
    allowSubscription: Boolean,
    subscriptionActive: Boolean,
    canUsePictureInPicture: Boolean,
    texts: PlayerControlTexts,
    onToggleSubscription: () -> Unit,
) {
    findViewById<TextView>(R.id.yummy_player_quality)?.apply {
        applyPlayerQualityControl(PLAYER_AUTO_QUALITY_LABEL, texts.quality)
        visibility = View.VISIBLE
        setPlayerControlEnabled(false)
    }
    findViewById<ImageButton>(R.id.yummy_player_subtitles)?.apply {
        applyPlayerIconControl(R.drawable.ic_player_subtitles, texts.subtitles)
        visibility = View.VISIBLE
        setPlayerControlEnabled(false)
    }
    findViewById<ImageButton>(R.id.yummy_player_subscription)?.apply {
        applyPlayerIconControl(
            iconResId = R.drawable.ic_player_subscription,
            label = if (subscriptionActive) texts.subscribed else texts.subscription,
            active = subscriptionActive,
        )
        visibility = View.VISIBLE
        setPlayerControlEnabled(allowSubscription)
        setOnClickListener {
            if (!allowSubscription) return@setOnClickListener
            showPlayerControls()
            onToggleSubscription()
        }
    }
    findViewById<ImageButton>(R.id.yummy_player_speed)?.apply {
        applyPlayerIconControl(
            iconResId = R.drawable.ic_player_speed,
            label = "${context.getString(R.string.player_speed)}: ${settings.playerSpeed.title}",
        )
        visibility = View.VISIBLE
        setPlayerControlEnabled(false)
    }
    findViewById<ImageButton>(R.id.yummy_player_pip)?.apply {
        applyPlayerIconControl(R.drawable.ic_player_pip, context.getString(R.string.player_pip))
        visibility = if (canUsePictureInPicture) View.VISIBLE else View.GONE
        setPlayerControlEnabled(false)
    }

    findViewById<View>(Media3R.id.exo_progress)?.apply {
        isEnabled = false
        isFocusable = false
    }
}
