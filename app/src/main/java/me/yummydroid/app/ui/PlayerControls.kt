package me.yummydroid.app.ui

import android.content.res.ColorStateList
import android.os.SystemClock
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R as Media3R
import androidx.media3.ui.TimeBar
import java.util.Locale
import me.yummydroid.app.BuildConfig
import me.yummydroid.app.R
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.OfflineVideoFile
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.VideoSkipSegment
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.normalizedSkipSegments
import me.yummydroid.app.formatPlaybackTime
import me.yummydroid.app.playbackSourceKey
import android.content.Context
import android.util.AttributeSet
import androidx.media3.ui.DefaultTimeBar
import kotlin.math.ceil

// PlayerAuxiliaryControls
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
        visibility = if (binding.canUsePictureInPicture && !binding.isRemotePlayback) {
            View.VISIBLE
        } else {
            View.GONE
        }
        setOnClickListener {
            hidePlayerControls()
            postDelayed({ binding.onEnterPictureInPicture() }, PIP_ENTER_DELAY_MS)
        }
    }
}

internal fun PlayerView.bindPlayerCastControl(binding: PlayerControllerBinding) {
    findViewById<androidx.mediarouter.app.MediaRouteButton>(R.id.yummy_player_cast)
        ?.let { button -> binding.castSession.bind(button, binding.toCastControllerBinding(context)) }
}

internal fun PlayerView.bindPlayerSkipControls(binding: PlayerControllerBinding) {
    if (!binding.settings.skipOpeningsAndEndings || binding.currentVideo.skipSegments.isEmpty()) {
        unbindSkipControls()
    } else if (binding.skipControlsTimelineReady) {
        bindSkipControls(
            player = binding.playbackPlayer,
            currentVideo = binding.currentVideo,
            texts = binding.texts,
        )
    }
}

// PlayerControllerBinder
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
    bindPlayerCastControl(binding)
    bindPlayerPictureInPictureControl(binding)
    bindPlayerSkipControls(binding)
    bindSkipTimelineMarkers(player = binding.playbackPlayer, currentVideo = binding.currentVideo)
    bindPlayerDebugOverlay(binding)
    configurePlayerFocusNavigation()
}

// PlayerControllerBinding
internal class PlayerControllerBinding(
    val player: ExoPlayer,
    val playbackPlayer: Player,
    val castSession: PlayerCastSession,
    val isRemotePlayback: Boolean,
    val stream: ResolvedVideoStream,
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
    val skipControlsTimelineReady: Boolean,
    val texts: PlayerControlTexts,
    val onSettingsChange: (AppSettings) -> Unit,
    val onBack: () -> Unit,
    val onRequestPlay: () -> Unit,
    val onPausePlayback: () -> Unit,
    val onPlaybackSelectionStarted: () -> Unit = {},
    val onRememberPlayerControlFocus: (Int) -> Unit,
)

// PlayerControlRegistry
internal val playerControlIds = intArrayOf(
    R.id.yummy_player_back,
    R.id.yummy_episode_previous,
    Media3R.id.exo_play_pause,
    R.id.yummy_episode_next,
    Media3R.id.exo_progress,
    R.id.yummy_skip_skip,
    R.id.yummy_skip_watch,
    R.id.yummy_player_quality,
    R.id.yummy_player_source,
    R.id.yummy_player_voice,
    R.id.yummy_player_subtitles,
    R.id.yummy_player_subscription,
    R.id.yummy_player_speed,
    R.id.yummy_player_cast,
    R.id.yummy_player_pip,
)

internal val playerChromeIds = intArrayOf(
    Media3R.id.exo_controls_background,
    R.id.yummy_player_top_bar,
    R.id.yummy_player_episode_controls,
    R.id.yummy_skip_controls,
    Media3R.id.exo_bottom_bar,
)

internal val playerPrimaryIconIds = intArrayOf(
    R.id.yummy_player_back,
    R.id.yummy_episode_previous,
    Media3R.id.exo_play_pause,
    R.id.yummy_episode_next,
)

// PlayerControlStyle
internal fun PlayerView.applyPlayerControlIconColors() {
    playerPrimaryIconIds.forEach { id ->
        findViewById<ImageButton>(id)?.imageTintList = playerControlContentColors(active = false)
    }
}

internal fun TextView.applyPlayerSubscriptionState(active: Boolean) {
    applyPlayerToggleState(active)
}

internal fun ImageButton.applyPlayerIconControl(
    @DrawableRes iconResId: Int,
    label: CharSequence,
    active: Boolean = false,
) {
    contentDescription = label
    backgroundTintList = null
    setBackgroundResource(R.drawable.player_center_control_background)
    setImageResource(iconResId)
    imageTintList = playerControlContentColors(active)
}

internal fun TextView.applyPlayerToggleState(active: Boolean) {
    backgroundTintList = null
    setBackgroundResource(R.drawable.player_center_control_background)
    val colors = playerControlContentColors(active)
    setTextColor(colors)
    TextViewCompat.setCompoundDrawableTintList(this, colors)
}

internal fun TextView.applyPlayerQualityControl(
    title: String,
    label: CharSequence,
) {
    text = title
    contentDescription = label
    applyPlayerToggleState(active = false)
}

internal fun playerControlContentColors(active: Boolean): ColorStateList {
    return ColorStateList(
        arrayOf(
            intArrayOf(android.R.attr.state_enabled, android.R.attr.state_focused),
            intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed),
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(),
        ),
        intArrayOf(
            PLAYER_ACCENT_CONTENT_COLOR,
            PLAYER_ACCENT_CONTENT_COLOR,
            0x66F3F6FA,
            if (active) PLAYER_ACCENT_COLOR else PLAYER_CONTROL_CONTENT_COLOR,
        ),
    )
}

internal fun List<QualityOption>.selectedQualityControlText(selectedQualityKey: String?): String {
    val selected = firstOrNull { it.matchesSelectedQualityKey(selectedQualityKey) }
    val height = selected?.height?.takeIf { it > 0 }
    if (height != null) return "${height}p"
    return selected?.label?.compactQualityControlText()
        ?: selectedQualityKey?.compactQualityControlText()
        ?: PLAYER_AUTO_QUALITY_LABEL
}

private fun String.compactQualityControlText(): String? {
    val explicitHeight = compactQualityHeightPattern
        .find(this)
        ?.groupValues
        ?.getOrNull(1)
    if (explicitHeight != null) return "${explicitHeight}p"
    if (contains("auto", ignoreCase = true)) return PLAYER_AUTO_QUALITY_LABEL
    if (contains("adaptive", ignoreCase = true)) return PLAYER_AUTO_QUALITY_LABEL
    return null
}

private val compactQualityHeightPattern =
    Regex("""(?i)(2160|1440|1080|720|576|540|480|360|240|144)\s*p""")

internal val PLAYER_ACCENT_COLOR: Int = 0xFFFFB454.toInt()
internal val PLAYER_ACCENT_CONTENT_COLOR: Int = 0xFF1B1305.toInt()
internal val PLAYER_CONTROL_CONTENT_COLOR: Int = 0xFFF3F6FA.toInt()
internal const val PLAYER_AUTO_QUALITY_LABEL = "AUTO"

internal fun View.setPlayerControlEnabled(enabled: Boolean) {
    isEnabled = enabled
    isFocusable = enabled
    alpha = if (enabled) 1f else 0.45f
}

// PlayerControlsVisibility
@OptIn(UnstableApi::class)
internal fun PlayerView.applyPictureInPictureControllerMode(enabled: Boolean) {
    useController = !enabled
    controllerAutoShow = false
    if (enabled) {
        hidePlayerControls()
    }
    requestLayout()
    invalidate()
}

@OptIn(UnstableApi::class)
internal fun PlayerView.restoreControllerAfterPictureInPicture() {
    useController = true
    controllerAutoShow = false
    hidePlayerControls()
    requestLayout()
    post {
        requestLayout()
        invalidate()
        postDelayed({ showPlayerControls() }, 220L)
    }
}

@OptIn(UnstableApi::class)
internal fun PlayerView.showPlayerControls() {
    if (!useController) return
    removeTaggedRunnable(R.id.yummy_player_controls_hide_runnable)
    removeTaggedRunnable(R.id.yummy_player_controls_auto_hide_runnable)
    val hadDisplayedChrome = hasDisplayedPlayerControlChrome()
    val showPlan = playerControlsShowPlan(
        controllerFullyVisible = isControllerFullyVisible,
        chromeDisplayed = hadDisplayedChrome,
    )
    setTag(R.id.yummy_player_controls_visible, true)
    setControllerShowTimeoutMs(0)
    if (showPlan.showController) {
        showController()
    }
    if (showPlan.animateChrome) {
        fadePlayerControlChrome(visible = true, fromHidden = !hadDisplayedChrome)
    } else {
        setPlayerControlChromeAlpha(1f)
    }
    schedulePlayerControlsAutoHide()
}

internal data class PlayerControlsShowPlan(
    val showController: Boolean,
    val animateChrome: Boolean,
)

internal fun playerControlsShowPlan(
    controllerFullyVisible: Boolean,
    chromeDisplayed: Boolean,
): PlayerControlsShowPlan {
    return PlayerControlsShowPlan(
        showController = !controllerFullyVisible,
        animateChrome = !chromeDisplayed,
    )
}

@OptIn(UnstableApi::class)
internal fun PlayerView.keepVisiblePlayerControlsAwake() {
    if (hasVisiblePlayerControls()) {
        showPlayerControls()
    }
}

@OptIn(UnstableApi::class)
internal fun PlayerView.hasVisiblePlayerControls(): Boolean {
    if (isControllerFullyVisible || hasDisplayedPlayerControlChrome()) return true
    tagValue<Boolean>(R.id.yummy_player_controls_visible)?.let { knownVisible ->
        return knownVisible
    }
    return playerChromeIds.any { id ->
        findViewById<View>(id)?.let { view ->
            view.isVisible && view.isShown
        } == true
    }
}

@OptIn(UnstableApi::class)
internal fun PlayerView.hidePlayerControls() {
    cancelSkipAutoCountdown()
    clearActiveSkipPrompt(markDismissed = true)
    setTag(R.id.yummy_player_controls_visible, false)
    removeTaggedRunnable(R.id.yummy_player_controls_hide_runnable)
    removeTaggedRunnable(R.id.yummy_player_controls_auto_hide_runnable)
    if (!useController) {
        hideController()
        setPlayerControlChromeAlpha(0f)
        return
    }
    fadePlayerControlChrome(visible = false, fromHidden = false)
    val hideRunnable = Runnable {
        clearTagValue(R.id.yummy_player_controls_hide_runnable)
        hideController()
        setPlayerControlChromeAlpha(0f)
    }
    setTag(R.id.yummy_player_controls_hide_runnable, hideRunnable)
    postDelayed(hideRunnable, PLAYER_CONTROLS_FADE_OUT_MS)
}

@OptIn(UnstableApi::class)
internal fun PlayerView.hideVisiblePlayerControls(): Boolean {
    if (!hasVisiblePlayerControls()) return false
    hidePlayerControls()
    return true
}

private val PlayerControlChromeInterpolator = LinearInterpolator()
private const val PLAYER_CONTROLS_FADE_IN_MS = 340L
private const val PLAYER_CONTROLS_FADE_OUT_MS = 340L
private const val PLAYER_CONTROLS_DEBUG_AUTO_HIDE_MS = 120_000L

private fun PlayerView.playerControlChromeViews(): List<View> {
    val views = ArrayList<View>(playerChromeIds.size)
    playerChromeIds.forEach { id ->
        findViewById<View>(id)?.let(views::add)
    }
    return views.distinctBy { view -> view.id }
}

private fun PlayerView.hasDisplayedPlayerControlChrome(): Boolean {
    return playerControlChromeViews().any { control ->
        control.isVisible && control.isShown && control.alpha > 0.01f
    }
}

private fun PlayerView.fadePlayerControlChrome(
    visible: Boolean,
    fromHidden: Boolean,
) {
    val targetAlpha = if (visible) 1f else 0f
    playerControlChromeViews().forEach { control ->
        control.animate().cancel()
        if (visible && fromHidden) {
            control.alpha = 0f
        }
        control.animate()
            .alpha(targetAlpha)
            .setDuration(if (visible) PLAYER_CONTROLS_FADE_IN_MS else PLAYER_CONTROLS_FADE_OUT_MS)
            .setInterpolator(PlayerControlChromeInterpolator)
            .withLayer()
            .start()
    }
}

internal fun PlayerView.setPlayerControlChromeAlpha(alpha: Float) {
    playerControlChromeViews().forEach { control ->
        control.animate().cancel()
        control.alpha = alpha
    }
}

internal fun PlayerView.resumePlayerControlsAutoHide() {
    if (hasVisiblePlayerControls()) {
        schedulePlayerControlsAutoHide()
    }
}

private fun PlayerView.schedulePlayerControlsAutoHide() {
    removeTaggedRunnable(R.id.yummy_player_controls_auto_hide_runnable)
    if (player == null || isSkipOnlyControllerMode() || hasPlayerPopupMenu()) return
    val hideRunnable = Runnable {
        clearTagValue(R.id.yummy_player_controls_auto_hide_runnable)
        hidePlayerControls()
    }
    setTag(R.id.yummy_player_controls_auto_hide_runnable, hideRunnable)
    postDelayed(hideRunnable, playerControlsAutoHideMs())
}

private fun playerControlsAutoHideMs(): Long {
    return if (BuildConfig.DEBUG) {
        PLAYER_CONTROLS_DEBUG_AUTO_HIDE_MS
    } else {
        PLAYER_CONTROLS_AUTO_HIDE_MS
    }
}

@OptIn(UnstableApi::class)
internal fun PlayerView.requestDefaultPlayerControlFocus(): Boolean {
    val timeBar = findViewById<View>(Media3R.id.exo_progress)?.apply {
        isFocusable = true
        isFocusableInTouchMode = false
    }
    return timeBar.playerFocusableTarget()?.requestFocus() == true ||
        findViewById<View>(Media3R.id.exo_play_pause).playerFocusableTarget()?.requestFocus() == true ||
        playerControlIds
            .asSequence()
            .mapNotNull { id -> findViewById<View>(id).playerFocusableTarget() }
            .firstOrNull()
            ?.requestFocus() == true ||
        requestFocus()
}

@OptIn(UnstableApi::class)
internal fun PlayerView.hasFocusedPlayerControl(): Boolean {
    return playerControlIds.any { id -> findViewById<View>(id)?.hasFocus() == true }
}

@OptIn(UnstableApi::class)
internal fun PlayerView.clearPlayerControlFocus() {
    playerControlIds.forEach { id ->
        findViewById<View>(id)?.clearFocus()
    }
    clearFocus()
}

@OptIn(UnstableApi::class)
internal fun PlayerView.clearPlayerControlFocusAfterTouch() {
    post {
        if (isInTouchMode) {
            clearPlayerControlFocus()
        }
    }
    postDelayed(
        {
            if (isInTouchMode) {
                clearPlayerControlFocus()
            }
        },
        PLAYER_TOUCH_FOCUS_CLEAR_DELAY_MS,
    )
}

internal fun PlayerView.hasRecentPlayerTouch(): Boolean {
    val lastTouchAt = tagValue<Long>(R.id.yummy_player_last_touch_down_at) ?: return false
    return SystemClock.uptimeMillis() - lastTouchAt <= PLAYER_TOUCH_FOCUS_CLEAR_WINDOW_MS
}

@OptIn(UnstableApi::class)
internal fun PlayerView.installPlayerControlsVisibilitySync() {
    setControllerVisibilityListener(
        PlayerView.ControllerVisibilityListener { visibility ->
            val visible = visibility == View.VISIBLE
            val wasVisible = tagValue<Boolean>(R.id.yummy_player_controls_visible) == true
            setTag(R.id.yummy_player_controls_visible, visible)
            if (visible && hasRecentPlayerTouch()) {
                clearPlayerControlFocusAfterTouch()
            }
            if (visible && !wasVisible) {
                removeTaggedRunnable(R.id.yummy_player_controls_hide_runnable)
                fadePlayerControlChrome(visible = true, fromHidden = !hasDisplayedPlayerControlChrome())
                schedulePlayerControlsAutoHide()
            }
            if (!visible) {
                removeTaggedRunnable(R.id.yummy_player_controls_hide_runnable)
                removeTaggedRunnable(R.id.yummy_player_controls_auto_hide_runnable)
                cancelSkipAutoCountdown()
                if (isSkipOnlyControllerMode()) {
                    setSkipOnlyControllerMode(false)
                }
                setPlayerControlChromeAlpha(0f)
            }
        },
    )
}

internal fun PlayerView.isSkipOnlyControllerMode(): Boolean {
    return tagValue<Boolean>(R.id.yummy_player_skip_only_mode) == true
}

@OptIn(UnstableApi::class)
internal fun PlayerView.setSkipOnlyControllerMode(enabled: Boolean) {
    setTag(R.id.yummy_player_skip_only_mode, enabled)
    setControllerShowTimeoutMs(0)
    if (enabled) {
        removeTaggedRunnable(R.id.yummy_player_controls_auto_hide_runnable)
    } else if (hasVisiblePlayerControls()) {
        schedulePlayerControlsAutoHide()
    }
    findViewById<View>(Media3R.id.exo_controls_background)?.visibility = if (enabled) View.GONE else View.VISIBLE
    findViewById<View>(R.id.yummy_player_top_bar)?.visibility = if (enabled) View.GONE else View.VISIBLE
    findViewById<View>(R.id.yummy_player_episode_controls)?.visibility = if (enabled) View.GONE else View.VISIBLE
    findViewById<View>(Media3R.id.exo_bottom_bar)?.visibility = if (enabled) View.GONE else View.VISIBLE
}

// PlayerControlTexts
internal data class PlayerControlTexts(
    val title: String,
    val watch: String,
    val voice: String,
    val source: String,
    val quality: String,
    val subtitles: String,
    val subtitlesOff: String,
    val subscription: String,
    val subscribed: String,
    val skip: String,
    val episode: String,
    val episodeFallback: String,
    val of: String,
    val downloaded: String,
)

internal val defaultPlayerControlTexts = PlayerControlTexts(
    title = "Watch",
    watch = "Watch",
    voice = "Voice",
    source = "Source",
    quality = "Quality",
    subtitles = "Subtitles",
    subtitlesOff = "Off",
    subscription = "Subscription",
    subscribed = "Subscribed",
    skip = "Skip",
    episode = "Episode",
    episodeFallback = "Episode",
    of = "of",
    downloaded = "downloaded",
)

@Composable
internal fun rememberPlayerControlTexts(): PlayerControlTexts {
    return PlayerControlTexts(
        title = uiText(UiStringKey.Watch),
        watch = uiText(UiStringKey.Watch5af041),
        voice = uiText(UiStringKey.Voice),
        source = uiText(UiStringKey.Source),
        quality = uiText(UiStringKey.Quality),
        subtitles = uiText(UiStringKey.Subtitles),
        subtitlesOff = uiText(UiStringKey.Off),
        subscription = uiText(UiStringKey.Subscription),
        subscribed = uiText(UiStringKey.Subscribed),
        skip = uiText(UiStringKey.Skip),
        episode = uiText(UiStringKey.Episode),
        episodeFallback = uiText(UiStringKey.Episode4da919),
        of = uiText(UiStringKey.Of),
        downloaded = uiText(UiStringKey.DownloadedBc4f6a).lowercase(Locale.ROOT),
    )
}

// PlayerPrimaryControls
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
        recordPlayerDebugOverlayPlayPauseHit(binding)
        if (binding.playbackPlayer.isPlaying) {
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
                binding.onPlaybackSelectionStarted()
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
                binding.onPlaybackSelectionStarted()
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
            showVoicePopup(
                anchor = this,
                groups = binding.groups,
                selectedKey = binding.selectedKey,
                preferredGroupKey = binding.currentVideo.groupKey,
                currentVideo = binding.currentVideo,
                texts = binding.texts,
                onPlaybackSelectionStarted = binding.onPlaybackSelectionStarted,
                onRememberPlayerControlFocus = binding.onRememberPlayerControlFocus,
                onSelectGroup = { groupKey, replacement ->
                    binding.onPausePlayback()
                    binding.onSelectGroup(
                        groupKey,
                        replacement,
                        binding.playbackPlayer.currentPosition.coerceAtLeast(0L),
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
            showSourcePopup(
                anchor = this,
                options = binding.sourceOptions,
                selectedSourceKey = binding.selectedSourceKey,
                onPlaybackSelectionStarted = binding.onPlaybackSelectionStarted,
                onRememberPlayerControlFocus = binding.onRememberPlayerControlFocus,
                onSelectSource = { source ->
                    binding.onPausePlayback()
                    binding.onSelectSource(
                        source,
                        binding.playbackPlayer.currentPosition.coerceAtLeast(0L),
                    )
                },
            )
        }
    }
}

// PlayerSkipControls
private data class PlayerSkipControlViews(
    val container: View,
    val skipButton: TextView,
    val watchButton: TextView,
)

@OptIn(UnstableApi::class)
internal fun PlayerView.bindSkipControls(
    player: Player,
    currentVideo: VideoVariant,
    texts: PlayerControlTexts,
) {
    val bindingKey = currentVideo.skipPromptBindingKey()
    val alreadyBound = tagValue<String>(R.id.yummy_player_skip_binding_key) == bindingKey
    if (!alreadyBound) {
        unbindSkipControls()
        setTag(R.id.yummy_player_skip_binding_key, bindingKey)
        setTag(R.id.yummy_player_skip_dismissed_keys, mutableSetOf<String>())
    }
    val views = skipControlViews() ?: return
    setTag(R.id.yummy_player_skip_text_tag, texts.skip)
    views.watchButton.text = texts.watch
    if (alreadyBound) return
    if (currentVideo.skipSegments.isEmpty()) {
        clearActiveSkipPrompt(markDismissed = false)
        setSkipControlsActive(false)
        return
    }
    PlayerSkipControlSession(
        playerView = this,
        player = player,
        currentVideo = currentVideo,
        texts = texts,
        views = views,
    ).start()
}

private fun PlayerView.skipControlViews(): PlayerSkipControlViews? {
    val container = findViewById<View>(R.id.yummy_skip_controls) ?: return null
    val skipButton = findViewById<TextView>(R.id.yummy_skip_skip) ?: return null
    val watchButton = findViewById<TextView>(R.id.yummy_skip_watch) ?: return null
    return PlayerSkipControlViews(container, skipButton, watchButton)
}

@OptIn(UnstableApi::class)
private class PlayerSkipControlSession(
    private val playerView: PlayerView,
    private val player: Player,
    private val currentVideo: VideoVariant,
    private val texts: PlayerControlTexts,
    private val views: PlayerSkipControlViews,
) {
    fun start() {
        val pollRunnable = createPollRunnable()
        playerView.setTag(R.id.yummy_player_skip_poll_runnable, pollRunnable)
        playerView.post(pollRunnable)
    }

    private fun dismissActivePrompt() {
        playerView.hidePlayerControls()
    }

    private fun skipActivePrompt() {
        val prompt = playerView.tagValue<ActiveSkipPrompt>(R.id.yummy_player_active_skip_segment) ?: return
        val targetEndMs = prompt.targetEndMs
        playerView.clearActiveSkipPrompt(markDismissed = true)
        playerView.hidePlayerControls()
        if (player.currentPosition.coerceAtLeast(0L) < targetEndMs) {
            player.seekTo(targetEndMs)
        }
    }

    private fun updateSkipButtonText(state: SkipCountdownState, nowMs: Long = SystemClock.elapsedRealtime()) {
        val remainingSeconds = (((state.deadlineMs - nowMs).coerceAtLeast(0L) + 999L) / 1_000L)
            .toInt()
            .coerceIn(0, SKIP_PROMPT_COUNTDOWN_SECONDS)
        views.skipButton.text = if (state.autoSkipEnabled) {
            playerView.context.getString(R.string.player_skip_countdown, texts.skip, remainingSeconds)
        } else {
            texts.skip
        }
    }

    private fun scheduleCountdown(prompt: ActiveSkipPrompt) {
        val startedAtMs = SystemClock.elapsedRealtime()
        val state = SkipCountdownState(
            startedAtMs = startedAtMs,
            deadlineMs = startedAtMs + SKIP_PROMPT_COUNTDOWN_SECONDS * 1_000L,
            autoSkipEnabled = true,
        )
        playerView.setTag(R.id.yummy_player_skip_auto_cancelled, state)
        updateSkipButtonText(state)
        postCountdownTick(prompt, state, 1_000L)
    }

    private fun countdownTick(prompt: ActiveSkipPrompt, state: SkipCountdownState) {
        val activeKey = playerView.tagValue<String>(R.id.yummy_player_active_skip_key)
        if (activeKey != prompt.key || !state.autoSkipEnabled) return
        val playerPositionMs = player.currentPosition.coerceAtLeast(0L)
        if (!prompt.hasUsefulSkipAt(playerPositionMs)) {
            playerView.clearActiveSkipPrompt(markDismissed = true)
            return
        }
        val nowMs = SystemClock.elapsedRealtime()
        val remainingMs = state.deadlineMs - nowMs
        if (remainingMs <= 0L) {
            finishCountdown(prompt, state)
        } else {
            updateSkipButtonText(state, nowMs)
            postCountdownTick(prompt, state, nextCountdownDelayMs(state, nowMs, remainingMs))
        }
    }

    private fun finishCountdown(prompt: ActiveSkipPrompt, state: SkipCountdownState) {
        updateSkipButtonText(state, state.deadlineMs)
        val finishCountdown = Runnable {
            val currentKey = playerView.tagValue<String>(R.id.yummy_player_active_skip_key)
            if (currentKey == prompt.key && state.autoSkipEnabled) skipActivePrompt()
        }
        playerView.setTag(R.id.yummy_player_skip_countdown_runnable, finishCountdown)
        playerView.postDelayed(finishCountdown, SKIP_PROMPT_ZERO_DISPLAY_MS)
    }

    private fun postCountdownTick(
        prompt: ActiveSkipPrompt,
        state: SkipCountdownState,
        delayMs: Long,
    ) {
        val tick = Runnable { countdownTick(prompt, state) }
        playerView.setTag(R.id.yummy_player_skip_countdown_runnable, tick)
        playerView.postDelayed(tick, delayMs)
    }

    private fun nextCountdownDelayMs(
        state: SkipCountdownState,
        nowMs: Long,
        remainingMs: Long,
    ): Long {
        val elapsedMs = (nowMs - state.startedAtMs).coerceAtLeast(0L)
        val nextSecondMs = ((elapsedMs / 1_000L) + 1L) * 1_000L
        return (nextSecondMs - elapsedMs).coerceIn(16L, remainingMs)
    }

    private fun showPrompt(segment: VideoSkipSegment) {
        val key = segment.key
        if (playerView.tagValue<String>(R.id.yummy_player_active_skip_key) == key) return
        val cluster = currentVideo.skipSegments.skipPromptCluster(segment)
        val prompt = ActiveSkipPrompt(
            key = key,
            segment = segment,
            dismissKeys = cluster.mapTo(mutableSetOf()) { clusterSegment -> clusterSegment.key },
            activeStartMs = cluster.minOfOrNull { clusterSegment -> clusterSegment.startMs } ?: segment.startMs,
            targetEndMs = cluster.maxOfOrNull { clusterSegment -> clusterSegment.endMs } ?: segment.endMs,
        )
        playerView.setTag(R.id.yummy_player_active_skip_key, key)
        playerView.setTag(R.id.yummy_player_active_skip_segment, prompt)
        playerView.setSkipControlsActive(true)
        playerView.showPlayerControls()
        playerView.setSkipOnlyControllerMode(true)
        views.skipButton.setOnClickListener { skipActivePrompt() }
        views.watchButton.setOnClickListener { dismissActivePrompt() }
        playerView.configureSkipFocusNavigation(active = true)
        scheduleCountdown(prompt)
        if (playerView.isInTouchMode) {
            playerView.clearPlayerControlFocusAfterTouch()
        } else {
            playerView.post {
                val promptStillActive = playerView.tagValue<String>(R.id.yummy_player_active_skip_key) == key
                if (promptStillActive && !playerView.isInTouchMode) {
                    views.skipButton.playerFocusableTarget()?.requestFocus()
                }
            }
        }
    }

    private fun createPollRunnable(): Runnable = object : Runnable {
        override fun run() {
            pollSkipPrompt()
            playerView.postDelayed(this, SKIP_PROMPT_POLL_MS)
        }
    }

    private fun pollSkipPrompt() {
        val position = player.currentPosition.coerceAtLeast(0L)
        clearExpiredManualPrompt(position)
        if (views.container.visibility == View.VISIBLE) return
        val segment = currentVideo.skipSegments.firstOrNull { segment ->
            segment.key !in playerView.dismissedSkipKeys() && segment.hasUsefulSkipAt(position)
        }
        if (segment != null) showPrompt(segment)
    }

    private fun clearExpiredManualPrompt(position: Long) {
        val activePrompt = playerView.tagValue<ActiveSkipPrompt>(R.id.yummy_player_active_skip_segment)
        val countdownState = playerView.tagValue<SkipCountdownState>(R.id.yummy_player_skip_auto_cancelled)
        if (activePrompt == null || countdownState?.autoSkipEnabled == true) return
        if (!activePrompt.hasUsefulSkipAt(position)) {
            playerView.clearActiveSkipPrompt(markDismissed = true)
        }
    }
}

internal fun PlayerView.cancelSkipAutoCountdown() {
    val state = tagValue<SkipCountdownState>(R.id.yummy_player_skip_auto_cancelled) ?: return
    if (!state.autoSkipEnabled) return
    state.autoSkipEnabled = false
    val skipText = tagValue<String>(R.id.yummy_player_skip_text_tag) ?: defaultPlayerControlTexts.skip
    findViewById<TextView>(R.id.yummy_skip_skip)?.text = skipText
    removeTaggedRunnable(R.id.yummy_player_skip_countdown_runnable)
}

internal fun PlayerView.unbindSkipControls() {
    removeTaggedRunnable(R.id.yummy_player_skip_poll_runnable)
    clearActiveSkipPrompt(markDismissed = false)
    clearTagValue(R.id.yummy_player_skip_binding_key)
    clearTagValue(R.id.yummy_player_skip_dismissed_keys)
}

internal fun Player.hasReadyTimeline(): Boolean {
    return hasReadyPlaybackTimeline(playbackState, duration)
}

internal fun hasReadyPlaybackTimeline(playbackState: Int, durationMs: Long): Boolean {
    return playbackState == Player.STATE_READY && durationMs.normalizedDurationMs() > 0L
}

internal fun VideoVariant.skipPromptBindingKey(): String {
    return listOf(
        animeId.toString(),
        matchingEpisodeKey,
        matchingVoiceKey,
        playbackSourceKey,
        skipSegments.skipPromptSignature(),
    ).joinToString("|")
}

internal fun List<VideoSkipSegment>.skipPromptSignature(): String {
    return normalizedSkipSegments()
        .joinToString(";") { segment -> segment.key }
}

internal fun Long.normalizedDurationMs(): Long {
    return takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
}

// PlayerSkipTimelineBinder
@OptIn(UnstableApi::class)
internal fun PlayerView.bindSkipTimelineMarkers(
    player: Player,
    currentVideo: VideoVariant,
) {
    val timeBar = findViewById<YummyPlayerTimeBar>(Media3R.id.exo_progress) ?: return
    val durationMs = resolvedPlaybackDurationMs(
        playerDurationMs = player.duration,
        contentDurationMs = player.contentDuration,
        metadataDurationSeconds = currentVideo.durationSeconds,
    )
    val segments = currentVideo.skipSegments.timelineMarkerSegments(durationMs)
    if (segments.isNotEmpty() && timeBar.width <= 0) {
        timeBar.post { bindSkipTimelineMarkers(player, currentVideo) }
    }
    timeBar.setYummySkipMarkerTimes(
        markerTimesMs = segments.timelineAdMarkerTimes(
            durationMs = durationMs,
            timelineWidthPx = timeBar.width,
            density = resources.displayMetrics.density,
        ),
    )
}

// PlayerTimelineScrub
@OptIn(UnstableApi::class)
internal fun PlayerView.seekTimelineIfFocused(
    forward: Boolean,
    repeatedInput: Boolean,
): Boolean {
    val timeBarView = findViewById<View>(Media3R.id.exo_progress) ?: return false
    if (!timeBarView.hasFocus()) return false
    val currentPlayer = player ?: return false
    val duration = currentPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: return false
    val direction = if (forward) 1 else -1
    val state = updateTimelineScrubState(
        currentPositionMs = currentPlayer.currentPosition,
        durationMs = duration,
        direction = direction,
        repeatedInput = repeatedInput,
    )
    state.commitRunnable?.let(::removeCallbacks)
    val commitGeneration = state.generation
    val commitRunnable = createTimelineCommitRunnable(
        currentPlayer = currentPlayer,
        durationMs = duration,
        commitGeneration = commitGeneration,
    )
    state.commitRunnable = commitRunnable
    setTag(R.id.yummy_player_timeline_scrub_state, state)
    renderTimelineScrubPosition(state)
    postTimelineScrubRender(commitGeneration)
    holdTimelineScrubPosition()
    postDelayed(commitRunnable, PLAYER_TIMELINE_SCRUB_COMMIT_DELAY_MS)
    return true
}

private fun PlayerView.updateTimelineScrubState(
    currentPositionMs: Long,
    durationMs: Long,
    direction: Int,
    repeatedInput: Boolean,
): TimelineScrubState {
    val now = SystemClock.uptimeMillis()
    val state = tagValue<TimelineScrubState>(R.id.yummy_player_timeline_scrub_state)
        ?: TimelineScrubState(pendingPositionMs = currentPositionMs.coerceIn(0L, durationMs))
    state.clearRunnable?.let(::removeCallbacks)
    state.clearRunnable = null
    val keepsHoldingSameDirection = repeatedInput && state.lastDirection == direction
    state.repeatedInputCount = if (keepsHoldingSameDirection) state.repeatedInputCount + 1 else 1
    state.lastDirection = direction
    state.lastInputAtMs = now
    state.generation += 1
    state.pendingPositionMs = (
        state.pendingPositionMs + direction.toLong() * state.stepMs(durationMs)
        ).coerceIn(0L, durationMs)
    setTag(R.id.yummy_player_timeline_manual_until, now + PLAYER_TIMELINE_MANUAL_FREEZE_MS)
    return state
}

private fun PlayerView.createTimelineCommitRunnable(
    currentPlayer: Player,
    durationMs: Long,
    commitGeneration: Int,
): Runnable {
    return object : Runnable {
        override fun run() {
            val latestState = tagValue<TimelineScrubState>(R.id.yummy_player_timeline_scrub_state)
                ?: return
            if (latestState.generation != commitGeneration) return
            val elapsedSinceInputMs = SystemClock.uptimeMillis() - latestState.lastInputAtMs
            if (elapsedSinceInputMs < PLAYER_TIMELINE_SCRUB_COMMIT_DELAY_MS) {
                postDelayed(this, PLAYER_TIMELINE_SCRUB_COMMIT_DELAY_MS - elapsedSinceInputMs)
                return
            }
            commitTimelinePosition(currentPlayer, durationMs, latestState)
        }
    }
}

private fun PlayerView.commitTimelinePosition(
    currentPlayer: Player,
    durationMs: Long,
    state: TimelineScrubState,
) {
    val targetPositionMs = state.pendingPositionMs.coerceIn(0L, durationMs)
    currentPlayer.seekTo(targetPositionMs)
    state.pendingPositionMs = targetPositionMs
    state.repeatedInputCount = 0
    state.commitRunnable = null
    renderTimelineScrubPosition(state)
    val freezeUntil = SystemClock.uptimeMillis() + PLAYER_TIMELINE_MANUAL_FREEZE_MS
    setTag(R.id.yummy_player_timeline_manual_until, freezeUntil)
    val clearRunnable = createTimelineClearRunnable(state)
    state.clearRunnable = clearRunnable
    postDelayed(clearRunnable, PLAYER_TIMELINE_MANUAL_FREEZE_MS)
}

private fun PlayerView.createTimelineClearRunnable(expectedState: TimelineScrubState): Runnable {
    return object : Runnable {
        override fun run() {
            val currentState = tagValue<TimelineScrubState>(R.id.yummy_player_timeline_scrub_state)
            if (currentState !== expectedState) return
            if (isTimelineManuallyControlled()) {
                postDelayed(this, 50L)
                return
            }
            clearTimelineScrubState()
        }
    }
}

private fun PlayerView.postTimelineScrubRender(commitGeneration: Int) {
    post {
        val latestState = tagValue<TimelineScrubState>(R.id.yummy_player_timeline_scrub_state)
        if (latestState?.generation == commitGeneration) {
            renderTimelineScrubPosition(latestState)
        }
    }
}

@OptIn(UnstableApi::class)
internal fun PlayerView.confirmTimelineScrubOrTogglePlayback(
    onRequestPlay: (() -> Unit)? = null,
    onPausePlayback: (() -> Unit)? = null,
): Boolean {
    val requestPlay = onRequestPlay ?: requestPlayCallback()
    val pausePlayback = onPausePlayback ?: pausePlaybackCallback()
    val currentPlayer = player
    if (currentPlayer != null && commitPendingTimelineScrub(currentPlayer)) return true
    return togglePlayerPlayback(requestPlay, pausePlayback)
}

private fun PlayerView.commitPendingTimelineScrub(currentPlayer: Player): Boolean {
    val state = tagValue<TimelineScrubState>(R.id.yummy_player_timeline_scrub_state) ?: return false
    if (!isTimelineManuallyControlled()) return false
    state.commitRunnable?.let(::removeCallbacks)
    state.clearRunnable?.let(::removeCallbacks)
    state.commitRunnable = null
    state.clearRunnable = null
    val duration = currentPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0L }
        ?: currentPlayer.contentDuration.takeIf { it != C.TIME_UNSET && it > 0L }
    val targetPositionMs = duration?.let { state.pendingPositionMs.coerceIn(0L, it) }
        ?: state.pendingPositionMs.coerceAtLeast(0L)
    state.pendingPositionMs = targetPositionMs
    currentPlayer.seekTo(targetPositionMs)
    renderTimelineScrubPosition(state)
    clearTimelineScrubState()
    return true
}

@OptIn(UnstableApi::class)
internal fun PlayerView.renderTimelineScrubPosition(state: TimelineScrubState) {
    (findViewById<View>(Media3R.id.exo_progress) as? TimeBar)?.setPosition(state.pendingPositionMs)
    findViewById<TextView>(Media3R.id.exo_position)?.text = formatPlaybackTime(state.pendingPositionMs)
}

internal fun PlayerView.isTimelineManuallyControlled(): Boolean {
    val until = tagValue<Long>(R.id.yummy_player_timeline_manual_until) ?: return false
    return SystemClock.uptimeMillis() < until
}

@OptIn(UnstableApi::class)
internal fun PlayerView.holdTimelineScrubPosition() {
    if (tagValue<Runnable>(R.id.yummy_player_timeline_hold_runnable) != null) return
    val runnable = object : Runnable {
        override fun run() {
            val latestState = tagValue<TimelineScrubState>(R.id.yummy_player_timeline_scrub_state)
            if (latestState == null || !isTimelineManuallyControlled()) {
                clearTagValue(R.id.yummy_player_timeline_hold_runnable)
                return
            }
            renderTimelineScrubPosition(latestState)
            postOnAnimation(this)
        }
    }
    setTag(R.id.yummy_player_timeline_hold_runnable, runnable)
    postOnAnimation(runnable)
}

internal fun PlayerView.clearTimelineScrubState() {
    tagValue<TimelineScrubState>(R.id.yummy_player_timeline_scrub_state)?.commitRunnable?.let(::removeCallbacks)
    tagValue<TimelineScrubState>(R.id.yummy_player_timeline_scrub_state)?.clearRunnable?.let(::removeCallbacks)
    removeTaggedRunnable(R.id.yummy_player_timeline_hold_runnable)
    clearTagValue(R.id.yummy_player_timeline_scrub_state)
    clearTagValue(R.id.yummy_player_timeline_manual_until)
}

internal data class TimelineScrubState(
    var pendingPositionMs: Long,
    var repeatedInputCount: Int = 0,
    var lastDirection: Int = 0,
    var generation: Int = 0,
    var lastInputAtMs: Long = 0L,
    var commitRunnable: Runnable? = null,
    var clearRunnable: Runnable? = null,
) {
    fun stepMs(durationMs: Long): Long {
        val requestedStep = when {
            repeatedInputCount <= 3 -> PLAYER_TIMELINE_BASE_STEP_MS
            repeatedInputCount <= 7 -> 10_000L
            repeatedInputCount <= 13 -> 30_000L
            else -> 60_000L
        }
        val maxStep = (durationMs / PLAYER_TIMELINE_MAX_STEP_DIVISOR).coerceAtLeast(1_000L)
        return requestedStep.coerceAtMost(maxStep)
    }
}

// SkipTimelineMarkerSegment
internal data class SkipTimelineMarkerSegment(
    val startMs: Long,
    val endMs: Long,
)

// SkipTimelineMarkerTimes
private const val MarkerStrideDp = 3f

internal fun List<SkipTimelineMarkerSegment>.timelineAdMarkerTimes(
    durationMs: Long?,
    timelineWidthPx: Int,
    density: Float,
): LongArray {
    val duration = durationMs?.takeIf { it > 0L } ?: return LongArray(0)
    if (isEmpty()) return LongArray(0)
    val effectiveWidthPx = maxOf(1, timelineWidthPx)
    val stridePx = maxOf(1f, MarkerStrideDp * density)
    val strideMs = maxOf(1L, ceil(duration.toDouble() * stridePx / effectiveWidthPx.toDouble()).toLong())
    return flatMap { segment ->
        buildList {
            var timeMs = segment.startMs
            add(timeMs)
            while (timeMs + strideMs < segment.endMs) {
                timeMs += strideMs
                add(timeMs)
            }
            add(segment.endMs)
        }
    }
        .distinct()
        .sorted()
        .toLongArray()
}

// SkipTimelineSegments
internal fun List<VideoSkipSegment>.timelineMarkerSegments(durationMs: Long?): List<SkipTimelineMarkerSegment> {
    val duration = durationMs?.takeIf { it > 0L } ?: return emptyList()
    return normalizedSkipSegments()
        .mapNotNull { segment ->
            val startMs = segment.startMs.coerceIn(0L, duration)
            val endMs = segment.endMs.coerceIn(0L, duration)
            if (endMs <= startMs) return@mapNotNull null
            SkipTimelineMarkerSegment(
                startMs = startMs,
                endMs = endMs,
            )
        }
}

// YummyPlayerTimeBar
@UnstableApi
class YummyPlayerTimeBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : DefaultTimeBar(context, attrs, defStyleAttr) {
    private var playerMarkerTimesMs = LongArray(0)
    private var playerPlayedMarkers = BooleanArray(0)
    private var playerMarkerCount = 0
    private var skipMarkerTimesMs = LongArray(0)

    override fun setAdGroupTimesMs(
        adGroupTimesMs: LongArray?,
        playedAdGroups: BooleanArray?,
        adGroupCount: Int,
    ) {
        playerMarkerCount = adGroupCount
            .coerceAtLeast(0)
            .coerceAtMost(adGroupTimesMs?.size ?: 0)
        playerMarkerTimesMs = adGroupTimesMs?.copyOf(playerMarkerCount) ?: LongArray(0)
        playerPlayedMarkers = playedAdGroups?.copyOf(playerMarkerCount) ?: BooleanArray(playerMarkerCount)
        applyMergedMarkers()
    }

    fun setYummySkipMarkerTimes(markerTimesMs: LongArray) {
        if (skipMarkerTimesMs.contentEquals(markerTimesMs)) return
        skipMarkerTimesMs = markerTimesMs
        applyMergedMarkers()
    }

    private fun applyMergedMarkers() {
        if (skipMarkerTimesMs.isEmpty()) {
            super.setAdGroupTimesMs(playerMarkerTimesMs, playerPlayedMarkers, playerMarkerCount)
            return
        }

        val markers = buildList {
            repeat(playerMarkerCount) { index ->
                add(TimelineMarker(playerMarkerTimesMs[index], playerPlayedMarkers.getOrElse(index) { false }))
            }
            skipMarkerTimesMs.forEach { timeMs ->
                add(TimelineMarker(timeMs, false))
            }
        }
            .filter { marker -> marker.timeMs != C.TIME_UNSET }
            .groupBy { marker -> marker.timeMs }
            .map { (timeMs, sameTimeMarkers) ->
                TimelineMarker(timeMs, sameTimeMarkers.any { marker -> marker.played })
            }
            .sortedBy { marker -> marker.timeMs }

        super.setAdGroupTimesMs(
            markers.map { marker -> marker.timeMs }.toLongArray(),
            markers.map { marker -> marker.played }.toBooleanArray(),
            markers.size,
        )
    }
}

private data class TimelineMarker(
    val timeMs: Long,
    val played: Boolean,
)
