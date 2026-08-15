package me.yummydroid.app.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.media3.ui.R as Media3UiR
import kotlin.math.min
import kotlin.math.roundToInt
import me.yummydroid.app.R

private const val PLAY_PAUSE_VIEWPORT_FRACTION = 0.10f
private const val MIN_PLAY_PAUSE_DP = 48f
private const val MAX_PLAY_PAUSE_DP = 64f
private const val CONTROL_SPACING_DP = 8f
private const val EPISODE_BUTTON_WIDTH_FRACTION = 0.94f
private const val EPISODE_BUTTON_HEIGHT_FRACTION = 0.80f
private const val EPISODE_BUTTON_OFFSET_FRACTION = 1.35f

internal data class PlayerEpisodeControlDimensions(
    val playPauseSize: Int,
    val playPauseContainerSize: Int,
    val episodeButtonWidth: Int,
    val episodeButtonHeight: Int,
    val episodeButtonOffset: Float,
    val controlsHeight: Int,
)

internal fun resolvePlayerEpisodeControlDimensions(
    viewportWidth: Int,
    viewportHeight: Int,
    density: Float,
): PlayerEpisodeControlDimensions {
    val safeDensity = density.coerceAtLeast(1f)
    val shortSide = min(viewportWidth, viewportHeight).coerceAtLeast(1)
    val playPauseSize = (shortSide * PLAY_PAUSE_VIEWPORT_FRACTION)
        .roundToInt()
        .coerceIn(
            (MIN_PLAY_PAUSE_DP * safeDensity).roundToInt(),
            (MAX_PLAY_PAUSE_DP * safeDensity).roundToInt(),
        )
    val spacing = (CONTROL_SPACING_DP * safeDensity).roundToInt()
    return PlayerEpisodeControlDimensions(
        playPauseSize = playPauseSize,
        playPauseContainerSize = playPauseSize + spacing,
        episodeButtonWidth = (playPauseSize * EPISODE_BUTTON_WIDTH_FRACTION).roundToInt(),
        episodeButtonHeight = (playPauseSize * EPISODE_BUTTON_HEIGHT_FRACTION).roundToInt(),
        episodeButtonOffset = playPauseSize * EPISODE_BUTTON_OFFSET_FRACTION,
        controlsHeight = playPauseSize + (spacing * 2),
    )
}

internal class AdaptivePlayerEpisodeControls @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    private var appliedDimensions: PlayerEpisodeControlDimensions? = null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val dimensions = resolvePlayerEpisodeControlDimensions(
            viewportWidth = MeasureSpec.getSize(widthMeasureSpec),
            viewportHeight = MeasureSpec.getSize(heightMeasureSpec),
            density = resources.displayMetrics.density,
        )
        applyDimensions(dimensions)
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(dimensions.controlsHeight, MeasureSpec.EXACTLY),
        )
    }

    private fun applyDimensions(dimensions: PlayerEpisodeControlDimensions) {
        if (appliedDimensions == dimensions) return
        appliedDimensions = dimensions
        resize(R.id.yummy_play_pause_container, dimensions.playPauseContainerSize, dimensions.playPauseContainerSize)
        resize(Media3UiR.id.exo_play_pause, dimensions.playPauseSize, dimensions.playPauseSize)
        resize(R.id.yummy_episode_previous, dimensions.episodeButtonWidth, dimensions.episodeButtonHeight)
        resize(R.id.yummy_episode_next, dimensions.episodeButtonWidth, dimensions.episodeButtonHeight)
        findViewById<View>(R.id.yummy_episode_previous).translationX = -dimensions.episodeButtonOffset
        findViewById<View>(R.id.yummy_episode_next).translationX = dimensions.episodeButtonOffset
    }

    private fun resize(viewId: Int, width: Int, height: Int) {
        val view = findViewById<View>(viewId)
        view.layoutParams = view.layoutParams.apply {
            this.width = width
            this.height = height
        }
    }
}
