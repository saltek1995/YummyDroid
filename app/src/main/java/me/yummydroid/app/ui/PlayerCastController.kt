package me.yummydroid.app.ui

import android.app.Dialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.SoundEffectConstants
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.mediarouter.app.MediaRouteButton
import java.util.Locale
import kotlin.math.min
import me.yummydroid.app.R

class YummyCastRouteButton : MediaRouteButton {
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
        super(context, attrs, defStyleAttr)

    internal var onConnectedClick: (() -> Boolean)? = null

    override fun performClick(): Boolean {
        if (onConnectedClick?.invoke() == true) {
            playSoundEffect(SoundEffectConstants.CLICK)
            sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED)
            return true
        }
        return super.performClick()
    }
}

internal data class PlayerCastControllerBinding(
    val title: String,
    val subtitle: String,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
)

internal fun PlayerControllerBinding.toCastControllerBinding(context: Context): PlayerCastControllerBinding {
    val episodeTitle = currentVideo.episode
        .takeIf(String::isNotBlank)
        ?.let { context.getString(R.string.player_cast_episode_format, it) }
        .orEmpty()
    return PlayerCastControllerBinding(
        title = animeTitle,
        subtitle = listOf(currentVideo.groupTitle, episodeTitle)
            .filter(String::isNotBlank)
            .joinToString("  |  "),
        hasPrevious = previousVideo != null,
        hasNext = nextVideo != null,
        onPrevious = {
            previousVideo?.let { video ->
                showVoiceFallbackToast(context, currentVideo, video)
                onPlaybackSelectionStarted()
                onPausePlayback()
                onPlayVideoAt(video, 0L)
            }
        },
        onNext = {
            nextVideo?.let { video ->
                showVoiceFallbackToast(context, currentVideo, video)
                onPlaybackSelectionStarted()
                onPausePlayback()
                onPlayVideoAt(video, 0L)
            }
        },
    )
}

internal class PlayerCastController(
    private val context: Context,
    private val player: Player,
    private val deviceName: String,
    private val binding: PlayerCastControllerBinding,
    private val onStopCasting: () -> Unit,
    private val onDismissed: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var dialog: Dialog? = null
    private var seeking = false
    private var cleanedUp = false

    private lateinit var positionText: TextView
    private lateinit var durationText: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var playPauseButton: ImageButton

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            updatePlaybackState()
        }
    }

    private val progressUpdate = object : Runnable {
        override fun run() {
            updatePlaybackState()
            handler.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MS)
        }
    }

    fun show() {
        if (dialog != null) return
        val content = LayoutInflater.from(context).inflate(
            R.layout.player_cast_controller_dialog,
            FrameLayout(context),
            false,
        )
        bindContent(content)
        dialog = Dialog(context, R.style.Theme_YummyDroid_MediaRouteDialog).apply {
            setContentView(content)
            setCanceledOnTouchOutside(true)
            setOnDismissListener { cleanup() }
            show()
            window?.setLayout(dialogWidth(), ViewGroup.LayoutParams.WRAP_CONTENT)
            window?.setGravity(Gravity.CENTER)
        }
        player.addListener(playerListener)
        updatePlaybackState()
        handler.postDelayed(progressUpdate, PROGRESS_UPDATE_INTERVAL_MS)
    }

    fun dismiss() {
        dialog?.dismiss() ?: cleanup()
    }

    private fun bindContent(content: View) {
        content.findViewById<TextView>(R.id.cast_controller_device).text =
            deviceName.ifBlank { context.getString(R.string.player_cast) }
        content.findViewById<TextView>(R.id.cast_controller_title).text = binding.title
        content.findViewById<TextView>(R.id.cast_controller_subtitle).apply {
            text = binding.subtitle
            visibility = if (binding.subtitle.isBlank()) View.GONE else View.VISIBLE
        }
        positionText = content.findViewById(R.id.cast_controller_position)
        durationText = content.findViewById(R.id.cast_controller_duration)
        seekBar = content.findViewById<SeekBar>(R.id.cast_controller_seek).apply {
            max = SEEK_BAR_MAX
            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                        if (fromUser) positionText.text = formatDuration(progressPosition(progress))
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar) {
                        seeking = true
                    }

                    override fun onStopTrackingTouch(seekBar: SeekBar) {
                        player.seekTo(progressPosition(seekBar.progress))
                        seeking = false
                    }
                },
            )
        }
        playPauseButton = content.findViewById<ImageButton>(R.id.cast_controller_play_pause).apply {
            setOnClickListener {
                if (player.playWhenReady) player.pause() else player.play()
            }
        }
        content.findViewById<ImageButton>(R.id.cast_controller_previous).apply {
            visibility = if (binding.hasPrevious) View.VISIBLE else View.INVISIBLE
            isEnabled = binding.hasPrevious
            setOnClickListener {
                binding.onPrevious()
                dismiss()
            }
        }
        content.findViewById<ImageButton>(R.id.cast_controller_next).apply {
            visibility = if (binding.hasNext) View.VISIBLE else View.INVISIBLE
            isEnabled = binding.hasNext
            setOnClickListener {
                binding.onNext()
                dismiss()
            }
        }
        content.findViewById<View>(R.id.cast_controller_stop).setOnClickListener {
            onStopCasting()
            dismiss()
        }
    }

    private fun updatePlaybackState() {
        if (!this::seekBar.isInitialized) return
        val duration = player.duration.validDuration()
        val position = player.currentPosition.coerceAtLeast(0L).coerceAtMost(duration.coerceAtLeast(0L))
        if (!seeking) {
            seekBar.isEnabled = duration > 0L
            seekBar.progress = if (duration > 0L) {
                ((position.toDouble() / duration.toDouble()) * SEEK_BAR_MAX).toInt()
            } else {
                0
            }
            positionText.text = formatDuration(position)
        }
        durationText.text = formatDuration(duration)
        playPauseButton.setImageResource(
            if (player.playWhenReady) R.drawable.ic_pip_pause else R.drawable.ic_pip_play,
        )
    }

    private fun progressPosition(progress: Int): Long {
        val duration = player.duration.validDuration()
        if (duration <= 0L) return 0L
        return (duration.toDouble() * progress.toDouble() / SEEK_BAR_MAX.toDouble()).toLong()
    }

    private fun cleanup() {
        if (cleanedUp) return
        cleanedUp = true
        handler.removeCallbacks(progressUpdate)
        player.removeListener(playerListener)
        dialog = null
        onDismissed()
    }

    private fun dialogWidth(): Int {
        val displayWidth = context.resources.displayMetrics.widthPixels
        return min(displayWidth - context.dp(32), context.dp(380)).coerceAtLeast(context.dp(280))
    }
}

private fun Long.validDuration(): Long {
    return takeUnless { it == C.TIME_UNSET || it < 0L } ?: 0L
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

private fun Context.dp(value: Int): Int {
    return (value * resources.displayMetrics.density).toInt()
}

private const val SEEK_BAR_MAX = 1_000
private const val PROGRESS_UPDATE_INTERVAL_MS = 500L
