package me.yummydroid.app.ui

import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import me.yummydroid.app.R
import me.yummydroid.app.data.VideoSkipSegment
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.normalizedSkipSegments
import me.yummydroid.app.playbackSourceKey

@OptIn(UnstableApi::class)
internal fun PlayerView.bindSkipControls(
    player: ExoPlayer,
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

    val container = findViewById<View>(R.id.yummy_skip_controls) ?: return
    val skipButton = findViewById<TextView>(R.id.yummy_skip_skip) ?: return
    val watchButton = findViewById<TextView>(R.id.yummy_skip_watch) ?: return
    setTag(R.id.yummy_player_skip_text_tag, texts.skip)
    watchButton.text = texts.watch
    if (alreadyBound) return
    if (currentVideo.skipSegments.isEmpty()) {
        clearActiveSkipPrompt(markDismissed = false)
        setSkipControlsActive(false)
        return
    }

    fun dismissActivePrompt() {
        hidePlayerControls()
    }

    fun skipActivePrompt() {
        val prompt = tagValue<ActiveSkipPrompt>(R.id.yummy_player_active_skip_segment) ?: return
        val targetEndMs = prompt.targetEndMs
        clearActiveSkipPrompt(markDismissed = true)
        hidePlayerControls()
        if (player.currentPosition.coerceAtLeast(0L) < targetEndMs) {
            player.seekTo(targetEndMs)
        }
    }

    fun updateSkipButtonText(state: SkipCountdownState, nowMs: Long = SystemClock.elapsedRealtime()) {
        val remainingSeconds = (((state.deadlineMs - nowMs).coerceAtLeast(0L) + 999L) / 1_000L)
            .toInt()
            .coerceIn(0, SKIP_PROMPT_COUNTDOWN_SECONDS)
        skipButton.text = if (state.autoSkipEnabled) {
            context.getString(R.string.player_skip_countdown, texts.skip, remainingSeconds)
        } else {
            texts.skip
        }
    }

    fun scheduleCountdown(prompt: ActiveSkipPrompt) {
        val startedAtMs = SystemClock.elapsedRealtime()
        val state = SkipCountdownState(
            startedAtMs = startedAtMs,
            deadlineMs = startedAtMs + SKIP_PROMPT_COUNTDOWN_SECONDS * 1_000L,
            autoSkipEnabled = true,
        )
        setTag(R.id.yummy_player_skip_auto_cancelled, state)
        updateSkipButtonText(state)

        fun tick() {
            val activeKey = tagValue<String>(R.id.yummy_player_active_skip_key)
            if (activeKey != prompt.key || !state.autoSkipEnabled) return
            val playerPositionMs = player.currentPosition.coerceAtLeast(0L)
            if (!prompt.hasUsefulSkipAt(playerPositionMs)) {
                clearActiveSkipPrompt(markDismissed = true)
                return
            }
            val nowMs = SystemClock.elapsedRealtime()
            val remainingMs = state.deadlineMs - nowMs
            if (remainingMs <= 0L) {
                updateSkipButtonText(state, state.deadlineMs)
                val finishCountdown = Runnable {
                    val currentKey = tagValue<String>(R.id.yummy_player_active_skip_key)
                    if (currentKey == prompt.key && state.autoSkipEnabled) {
                        skipActivePrompt()
                    }
                }
                setTag(R.id.yummy_player_skip_countdown_runnable, finishCountdown)
                postDelayed(finishCountdown, SKIP_PROMPT_ZERO_DISPLAY_MS)
            } else {
                updateSkipButtonText(state, nowMs)
                val nextTick = Runnable { tick() }
                setTag(R.id.yummy_player_skip_countdown_runnable, nextTick)
                val elapsedMs = (nowMs - state.startedAtMs).coerceAtLeast(0L)
                val nextSecondMs = ((elapsedMs / 1_000L) + 1L) * 1_000L
                val delayMs = (nextSecondMs - elapsedMs).coerceIn(16L, remainingMs)
                postDelayed(nextTick, delayMs)
            }
        }

        val firstTick = Runnable { tick() }
        setTag(R.id.yummy_player_skip_countdown_runnable, firstTick)
        postDelayed(firstTick, 1_000L)
    }

    fun showPrompt(segment: VideoSkipSegment) {
        val key = segment.key
        if (tagValue<String>(R.id.yummy_player_active_skip_key) == key) return
        val cluster = currentVideo.skipSegments.skipPromptCluster(segment)
        val prompt = ActiveSkipPrompt(
            key = key,
            segment = segment,
            dismissKeys = cluster.mapTo(mutableSetOf()) { clusterSegment -> clusterSegment.key },
            activeStartMs = cluster.minOfOrNull { clusterSegment -> clusterSegment.startMs } ?: segment.startMs,
            targetEndMs = cluster.maxOfOrNull { clusterSegment -> clusterSegment.endMs } ?: segment.endMs,
        )
        setTag(R.id.yummy_player_active_skip_key, key)
        setTag(R.id.yummy_player_active_skip_segment, prompt)
        setSkipControlsActive(true)
        showPlayerControls()
        setSkipOnlyControllerMode(true)
        skipButton.setOnClickListener { skipActivePrompt() }
        watchButton.setOnClickListener { dismissActivePrompt() }
        configureSkipFocusNavigation(active = true)
        scheduleCountdown(prompt)
        if (isInTouchMode) {
            clearPlayerControlFocusAfterTouch()
        } else {
            post { skipButton.requestFocus() }
        }
    }

    val pollRunnable = object : Runnable {
        override fun run() {
            val position = player.currentPosition.coerceAtLeast(0L)
            val activePrompt = tagValue<ActiveSkipPrompt>(R.id.yummy_player_active_skip_segment)
            val countdownState = tagValue<SkipCountdownState>(R.id.yummy_player_skip_auto_cancelled)
            if (
                activePrompt != null &&
                countdownState?.autoSkipEnabled != true &&
                !activePrompt.hasUsefulSkipAt(position)
            ) {
                clearActiveSkipPrompt(markDismissed = true)
            }
            if (container.visibility != View.VISIBLE) {
                val segment = currentVideo.skipSegments.firstOrNull { segment ->
                    segment.key !in dismissedSkipKeys() &&
                        segment.hasUsefulSkipAt(position)
                }
                if (segment != null) {
                    showPrompt(segment)
                }
            }
            postDelayed(this, SKIP_PROMPT_POLL_MS)
        }
    }

    setTag(R.id.yummy_player_skip_poll_runnable, pollRunnable)
    post(pollRunnable)
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
