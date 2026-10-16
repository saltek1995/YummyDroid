package me.yummydroid.app.ui

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.media3.ui.R as Media3R
import kotlin.math.roundToInt
import me.yummydroid.app.R

/** Edge panels and transport keep their dp sizes regardless of the window's aspect ratio. */
internal class PlayerControlsViewport @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        // Keep the floating Watch slot stable when the ordinary panels are hidden.
        for (id in intArrayOf(R.id.yummy_player_top_bar, Media3R.id.exo_bottom_bar)) {
            findViewById<View>(id)?.takeIf { it.visibility == GONE }?.let {
                measureChild(it, widthMeasureSpec, heightMeasureSpec)
            }
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val transport = findViewById<View>(R.id.yummy_player_episode_controls) ?: return
        val playPause = findViewById<View>(Media3R.id.exo_play_pause) ?: return
        val prompt = findViewById<View>(R.id.yummy_skip_controls) ?: return
        val skip = findViewById<View>(R.id.yummy_skip_skip) ?: return
        val headerHeight = findViewById<View>(R.id.yummy_player_top_bar).measuredHeight
        val footerHeight = findViewById<View>(Media3R.id.exo_bottom_bar).measuredHeight
        val halfTouchHeight = playPause.layoutParams.height / 2f
        val minimumCenter = headerHeight + dp(1) + halfTouchHeight
        val maximumCenter = height - footerHeight - dp(1) - halfTouchHeight
        var centerY = height / 2f
        if (minimumCenter <= maximumCenter) centerY = centerY.coerceIn(minimumCenter, maximumCenter)

        if (prompt.visibility != GONE) {
            val edge = (prompt.layoutParams as LayoutParams).marginEnd
            val skipLeft = width - edge - skip.measuredWidth
            val transportRight = (width + transport.layoutParams.width) / 2f
            var lastRowTop = height - footerHeight - dp(8) - skip.measuredHeight
            // Reserve the same right-hand slot in floating and full-controller modes.
            // On short, narrow windows use spare space above the footer before moving transport.
            if (skipLeft < transportRight + dp(4)) {
                val lowestRowTop = height - footerHeight - dp(1) - skip.measuredHeight
                lastRowTop = maxOf(lastRowTop, minOf(
                    (centerY + halfTouchHeight + dp(4)).roundToInt(), lowestRowTop,
                ))
                centerY = minOf(centerY, lastRowTop - dp(4) - halfTouchHeight)
                    .coerceAtLeast(minimumCenter)
            }
            val highestRowTop = headerHeight + dp(1)
            val lowestRowTop = height - footerHeight - skip.measuredHeight - dp(1)
            if (highestRowTop <= lowestRowTop) lastRowTop = lastRowTop.coerceIn(highestRowTop, lowestRowTop)
            val promptBottom = (lastRowTop + skip.measuredHeight)
                .coerceAtLeast(minOf(prompt.measuredHeight, height))
            prompt.layout(prompt.left, promptBottom - prompt.measuredHeight, prompt.right, promptBottom)
        }

        if (transport.visibility != GONE) {
            val transportTop = (centerY - transport.measuredHeight / 2f).roundToInt()
            transport.layout(transport.left, transportTop, transport.right, transportTop + transport.measuredHeight)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}

/** Keeps a balanced action grid centered beneath the timeline at fixed touch-target sizes. */
internal class PlayerBottomControlsLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    private var rows = emptyList<List<View>>()
    private var actionHeight = 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight).coerceAtLeast(1)
        val free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        val actions = playerControlRows.last().asList().mapNotNull { findViewById<View>(it) }.filter { it.visibility != GONE }
        actions.forEach { measureChild(it, free, free) }
        val gap = dp(4)
        actionHeight = actions.maxOfOrNull { it.measuredHeight } ?: 0
        val columns = ((available + gap) / ((actions.maxOfOrNull { it.measuredWidth } ?: 1) + gap)).coerceAtLeast(1)
        val rowCount = (actions.size + columns - 1) / columns
        val balancedColumns = if (rowCount == 0) 1 else (actions.size + rowCount - 1) / rowCount
        rows = actions.chunked(balancedColumns)
        val gridHeight = rows.size * actionHeight + gap * (rows.size - 1).coerceAtLeast(0)
        val height = maxOf(minimumHeight, gridHeight) + paddingTop + paddingBottom
        setMeasuredDimension(resolveSize(MeasureSpec.getSize(widthMeasureSpec), widthMeasureSpec),
            resolveSize(height, heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val gap = dp(4)
        val contentHeight = height - paddingTop - paddingBottom
        val gridHeight = rows.size * actionHeight + gap * (rows.size - 1).coerceAtLeast(0)
        var y = paddingTop + (contentHeight - gridHeight) / 2
        for (row in rows) {
            val rowWidth = row.sumOf { it.measuredWidth } + gap * (row.size - 1)
            var x = paddingLeft + (width - paddingLeft - paddingRight - rowWidth) / 2
            for (action in row) {
                val actionTop = y + (actionHeight - action.measuredHeight) / 2
                action.layout(x, actionTop, x + action.measuredWidth, actionTop + action.measuredHeight)
                x += action.measuredWidth + gap
            }
            y += actionHeight + gap
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}

/** Floating actions wrap into a column only when their natural widths cannot fit. */
internal class PlayerSkipControlsLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val skip = findViewById<View>(R.id.yummy_skip_skip)
        val watch = findViewById<View>(R.id.yummy_skip_watch)
        val showWatch = watch.visibility != GONE
        val gap = (8 * resources.displayMetrics.density).roundToInt()
        val unconstrainedWidth = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        measureChild(skip, unconstrainedWidth, heightMeasureSpec)
        if (showWatch) measureChild(watch, unconstrainedWidth, heightMeasureSpec)
        val naturalWidth = paddingLeft + paddingRight + skip.measuredWidth +
            if (showWatch) gap + watch.measuredWidth else 0
        val stack = showWatch && MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED &&
            naturalWidth > MeasureSpec.getSize(widthMeasureSpec)
        orientation = if (stack) VERTICAL else HORIZONTAL
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
        (skip.layoutParams as LayoutParams).apply {
            marginEnd = if (showWatch && !stack) gap else 0
            bottomMargin = if (stack) gap else 0
            resolveLayoutDirection(layoutDirection)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
