package me.yummydroid.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.core.graphics.drawable.toDrawable
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlin.math.ceil
import me.yummydroid.app.R
import me.yummydroid.app.data.OfflineVideoFile
import me.yummydroid.app.data.PlayerSpeed
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.availableVoiceEpisodeCount
import me.yummydroid.app.data.isSameEpisodeAs
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.matchingVoiceTitle
private val PLAYER_POPUP_PANEL_COLOR: Int = 0xF2111B2F.toInt()
private val PLAYER_POPUP_SELECTED_COLOR: Int = 0x33FFB454
private val PLAYER_POPUP_STROKE_COLOR: Int = 0x263A4D67
private const val PLAYER_POPUP_LABEL_TEXT_SIZE_SP = 15f

private class PopupMenu(
    private val context: Context,
    private val anchor: View,
) {
    val menu = PlayerPopupMenu()
    private var itemClickListener: ((PlayerPopupMenuItem) -> Boolean)? = null

    fun setOnMenuItemClickListener(listener: (PlayerPopupMenuItem) -> Boolean) {
        itemClickListener = listener
    }

    fun show() {
        val items = menu.items
        if (items.isEmpty()) return

        val rowHeight = context.playerMenuDp(48)
        val verticalPadding = context.playerMenuDp(10)
        val margin = context.playerMenuDp(14)
        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels
        val maxWidth = (screenWidth - margin * 2).coerceAtLeast(context.playerMenuDp(120))
        val width = context.playerMenuContentWidth(items)
            .coerceAtMost(maxWidth)
        val maxHeight = (screenHeight * 0.62f).toInt().coerceAtLeast(rowHeight + verticalPadding * 2)
        val height = (items.size * rowHeight + verticalPadding * 2).coerceAtMost(maxHeight)

        lateinit var popupWindow: PopupWindow
        val rows = ArrayList<View>(items.size)
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.playerMenuDp(8), context.playerMenuDp(6), context.playerMenuDp(8), context.playerMenuDp(6))
        }

        items.forEachIndexed { index, item ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = rowHeight
                isFocusable = true
                isFocusableInTouchMode = false
                isClickable = true
                isSelected = item.isChecked
                contentDescription = item.title
                background = context.playerMenuRowBackground()
                setPadding(context.playerMenuDp(12), 0, context.playerMenuDp(12), 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    rowHeight,
                )
            }
            val marker = View(context).apply {
                alpha = if (item.isChecked) 1f else 0f
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = context.playerMenuDp(2).toFloat()
                    setColor(PLAYER_ACCENT_COLOR)
                }
                layoutParams = LinearLayout.LayoutParams(
                    context.playerMenuDp(3),
                    context.playerMenuDp(24),
                ).apply {
                    marginEnd = context.playerMenuDp(12)
                }
            }
            val label = TextView(context).apply {
                text = item.title
                setTextColor(playerMenuTextColors())
                textSize = PLAYER_POPUP_LABEL_TEXT_SIZE_SP
                includeFontPadding = false
                isDuplicateParentStateEnabled = true
                isSelected = item.isChecked
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_VERTICAL
                typeface = if (item.isChecked) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1f,
                )
            }
            row.setOnFocusChangeListener { _, focused ->
                marker.alpha = if (focused || item.isChecked) 1f else 0f
                label.typeface = if (focused || item.isChecked) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            row.setOnClickListener {
                if (itemClickListener?.invoke(item) != false) {
                    popupWindow.dismiss()
                }
            }
            row.setOnKeyListener { view, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        view.performClick()
                        true
                    }
                    KeyEvent.KEYCODE_BACK -> {
                        popupWindow.dismiss()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        rows.getOrNull(index - 1)?.requestFocus() ?: true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        rows.getOrNull(index + 1)?.requestFocus() ?: true
                    }
                    else -> false
                }
            }
            row.addView(marker)
            row.addView(label)
            rows += row
            list.addView(row)
        }

        val scrollView = ScrollView(context).apply {
            isFillViewport = false
            isFocusable = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            background = context.playerMenuPanelBackground()
            addView(
                list,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        popupWindow = PopupWindow(scrollView, width, height, true).apply {
            isOutsideTouchable = true
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            setBackgroundDrawable(android.graphics.Color.TRANSPARENT.toDrawable())
            elevation = context.playerMenuDp(12).toFloat()
            setOnDismissListener {
                if (!anchor.isInTouchMode) {
                    anchor.playerFocusableTarget()?.requestFocus()
                }
                (anchor.rootView.findViewById<View>(R.id.yummy_player_view) as? PlayerView)
                    ?.showPlayerControls()
            }
        }

        val anchorLocation = IntArray(2)
        anchor.getLocationOnScreen(anchorLocation)
        val maxX = (screenWidth - width - margin).coerceAtLeast(margin)
        val x = (anchorLocation[0] + anchor.width / 2 - width / 2).coerceIn(margin, maxX)
        val aboveY = anchorLocation[1] - height - context.playerMenuDp(10)
        val belowY = anchorLocation[1] + anchor.height + context.playerMenuDp(10)
        val maxY = (screenHeight - height - margin).coerceAtLeast(margin)
        val y = if (aboveY >= margin) aboveY else belowY.coerceIn(margin, maxY)
        popupWindow.showAtLocation(anchor.rootView, Gravity.NO_GRAVITY, x, y)

        if (!anchor.isInTouchMode) {
            val selectedIndex = items.indexOfFirst { item -> item.isChecked }.takeIf { it >= 0 } ?: 0
            scrollView.post {
                rows.getOrNull(selectedIndex)?.requestFocus()
            }
        }
    }
}

private class PlayerPopupMenu {
    private val mutableItems = mutableListOf<PlayerPopupMenuItem>()
    val items: List<PlayerPopupMenuItem>
        get() = mutableItems.sortedBy { item -> item.order }

    fun add(groupId: Int, itemId: Int, order: Int, title: CharSequence): PlayerPopupMenuItem {
        return PlayerPopupMenuItem(
            groupId = groupId,
            itemId = itemId,
            order = order,
            title = title,
        ).also(mutableItems::add)
    }

    fun setGroupCheckable(groupId: Int, checkable: Boolean, exclusive: Boolean) {
        mutableItems
            .filter { item -> item.groupId == groupId }
            .forEach { item -> item.isCheckable = checkable }
    }
}

private class PlayerPopupMenuItem(
    val groupId: Int,
    val itemId: Int,
    val order: Int,
    val title: CharSequence,
) {
    var isCheckable: Boolean = false
    var isChecked: Boolean = false
}

private fun <T> PopupMenu.addCheckableItems(
    groupId: Int,
    entries: List<T>,
    itemIdOffset: Int = 0,
    orderOffset: Int = itemIdOffset,
    title: (index: Int, entry: T) -> CharSequence,
    selected: (entry: T) -> Boolean,
) {
    entries.forEachIndexed { index, entry ->
        menu.add(groupId, index + itemIdOffset, index + orderOffset, title(index, entry)).apply {
            isCheckable = true
            isChecked = selected(entry)
        }
    }
    menu.setGroupCheckable(groupId, true, true)
}

private fun Context.playerMenuDp(value: Int): Int {
    return (value * resources.displayMetrics.density).toInt()
}

private fun Context.playerMenuContentWidth(
    items: List<PlayerPopupMenuItem>,
): Int {
    val probe = TextView(this).apply {
        textSize = PLAYER_POPUP_LABEL_TEXT_SIZE_SP
        typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
    }
    val longestLabelWidth = items
        .maxOfOrNull { item -> ceil(probe.paint.measureText(item.title.toString())).toInt() }
        ?: 0
    val listHorizontalPadding = playerMenuDp(16)
    val rowHorizontalPadding = playerMenuDp(24)
    val markerWidthWithMargin = playerMenuDp(15)
    return playerMenuContentWidthPx(
        longestLabelWidth = longestLabelWidth,
        listHorizontalPadding = listHorizontalPadding,
        rowHorizontalPadding = rowHorizontalPadding,
        markerWidthWithMargin = markerWidthWithMargin,
    )
}

internal fun playerMenuContentWidthPx(
    longestLabelWidth: Int,
    listHorizontalPadding: Int,
    rowHorizontalPadding: Int,
    markerWidthWithMargin: Int,
): Int = longestLabelWidth + listHorizontalPadding + rowHorizontalPadding + markerWidthWithMargin

private fun Context.playerMenuPanelBackground(): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = playerMenuDp(18).toFloat()
        setColor(PLAYER_POPUP_PANEL_COLOR)
        setStroke(playerMenuDp(1), PLAYER_POPUP_STROKE_COLOR)
    }
}

private fun Context.playerMenuRowBackground(): StateListDrawable {
    return StateListDrawable().apply {
        addState(
            intArrayOf(android.R.attr.state_pressed),
            GradientDrawable().apply {
                cornerRadius = playerMenuDp(12).toFloat()
                setColor(PLAYER_ACCENT_COLOR)
            },
        )
        addState(
            intArrayOf(android.R.attr.state_focused),
            GradientDrawable().apply {
                cornerRadius = playerMenuDp(12).toFloat()
                setColor(PLAYER_ACCENT_COLOR)
            },
        )
        addState(
            intArrayOf(android.R.attr.state_selected),
            GradientDrawable().apply {
                cornerRadius = playerMenuDp(12).toFloat()
                setColor(PLAYER_POPUP_SELECTED_COLOR)
            },
        )
        addState(
            intArrayOf(),
            GradientDrawable().apply {
                cornerRadius = playerMenuDp(12).toFloat()
                setColor(android.graphics.Color.TRANSPARENT)
            },
        )
    }
}

private fun playerMenuTextColors(): ColorStateList {
    return ColorStateList(
        arrayOf(
            intArrayOf(android.R.attr.state_focused),
            intArrayOf(android.R.attr.state_pressed),
            intArrayOf(android.R.attr.state_selected),
            intArrayOf(),
        ),
        intArrayOf(
            PLAYER_ACCENT_CONTENT_COLOR,
            PLAYER_ACCENT_CONTENT_COLOR,
            PLAYER_ACCENT_COLOR,
            PLAYER_CONTROL_CONTENT_COLOR,
        ),
    )
}

internal fun showVoicePopup(
    anchor: View,
    groups: Map<String, List<VideoVariant>>,
    selectedKey: String?,
    preferredGroupKey: String?,
    currentVideo: VideoVariant,
    texts: PlayerControlTexts,
    onRememberPlayerControlFocus: (Int) -> Unit = {},
    onSelectGroup: (String, VideoVariant?) -> Unit,
) {
    val entries = groups.entries.toList()
    anchor.rememberPlayerControlFocus(onRememberPlayerControlFocus)
    PopupMenu(anchor.context, anchor).apply {
        entries.forEachIndexed { index, entry ->
            val voiceTitle = entry.value.firstOrNull()?.matchingVoiceTitle.orEmpty().ifBlank { "${texts.voice} ${index + 1}" }
            val availableEpisodes = entry.value.availableVoiceEpisodeCount()
            val downloadedEpisodes = entry.value
                .asSequence()
                .filter { it.isOfflineAvailable }
                .map { it.matchingEpisodeKey }
                .distinct()
                .count()
            val downloadedSuffix = if (downloadedEpisodes > 0) " \u2022 ${texts.downloaded}: $downloadedEpisodes" else ""
            val title = "$voiceTitle ($availableEpisodes)$downloadedSuffix"
            menu.add(VOICE_MENU_GROUP_ID, index, index, title).apply {
                isCheckable = true
                isChecked = entry.key == selectedKey
            }
        }
        menu.setGroupCheckable(VOICE_MENU_GROUP_ID, true, true)
        setOnMenuItemClickListener { item ->
            val entry = entries.getOrNull(item.itemId) ?: return@setOnMenuItemClickListener false
            val sortedVideos = entry.value.sortedForPlayer(preferredGroupKey, entry.key)
            val replacement = sortedVideos.firstOrNull { it.isSameEpisodeAs(currentVideo) }
                ?: sortedVideos.firstOrNull()
            val groupKey = replacement?.groupKey ?: entry.value.firstOrNull()?.groupKey ?: entry.key
            anchor.rememberPlayerControlFocus(onRememberPlayerControlFocus)
            anchor.post { onSelectGroup(groupKey, replacement) }
            true
        }
        show()
    }
}

internal fun showSourcePopup(
    anchor: View,
    options: List<SourceOption>,
    selectedSourceKey: String?,
    onRememberPlayerControlFocus: (Int) -> Unit = {},
    onSelectSource: (VideoVariant) -> Unit,
) {
    anchor.rememberPlayerControlFocus(onRememberPlayerControlFocus)
    PopupMenu(anchor.context, anchor).apply {
        addCheckableItems(
            groupId = SOURCE_MENU_GROUP_ID,
            entries = options,
            title = { _, option -> option.label },
            selected = { option -> option.key == selectedSourceKey },
        )
        setOnMenuItemClickListener { item ->
            val option = options.getOrNull(item.itemId) ?: return@setOnMenuItemClickListener false
            anchor.rememberPlayerControlFocus(onRememberPlayerControlFocus)
            anchor.post { onSelectSource(option.video) }
            true
        }
        show()
    }
}

@OptIn(UnstableApi::class)
internal fun showQualityPopup(
    anchor: View,
    player: ExoPlayer,
    options: List<QualityOption>,
    selectedQualityKey: String?,
    onSelectedQualityKeyChange: (String) -> Unit,
    onSelectLocalQuality: (OfflineVideoFile) -> Unit,
    onSelectPreferredQuality: (PreferredQuality) -> Unit,
    onRememberPlayerControlFocus: (Int) -> Unit = {},
) {
    anchor.rememberPlayerControlFocus(onRememberPlayerControlFocus)
    PopupMenu(anchor.context, anchor).apply {
        val effectiveSelectedQualityKey = anchor.tagValue<String>(R.id.yummy_player_quality)
            ?: selectedQualityKey
            ?: player.currentQualityKey()
        addCheckableItems(
            groupId = QUALITY_MENU_GROUP_ID,
            entries = options,
            title = { _, option -> option.label },
            selected = { option -> option.matchesSelectedQualityKey(effectiveSelectedQualityKey) },
        )
        setOnMenuItemClickListener { item ->
            val option = options.getOrNull(item.itemId) ?: return@setOnMenuItemClickListener false
            anchor.rememberPlayerControlFocus(onRememberPlayerControlFocus)
            option.localFile?.let { localFile ->
                anchor.post { onSelectLocalQuality(localFile) }
            } ?: option.preferredQuality?.let { preferredQuality ->
                anchor.post { onSelectPreferredQuality(preferredQuality) }
            } ?: player.selectQuality(option)
            val stableKey = option.qualityOptionIdentity()
            anchor.setTag(R.id.yummy_player_quality, stableKey)
            onSelectedQualityKeyChange(stableKey)
            true
        }
        show()
    }
}

@OptIn(UnstableApi::class)
internal fun showSubtitlePopup(
    anchor: View,
    player: ExoPlayer,
    options: List<SubtitleOption>,
    selectedSubtitleKey: String,
    texts: PlayerControlTexts,
    onRememberPlayerControlFocus: (Int) -> Unit = {},
    onSelectedSubtitleKeyChange: (String) -> Unit,
) {
    anchor.rememberPlayerControlFocus(onRememberPlayerControlFocus)
    PopupMenu(anchor.context, anchor).apply {
        val effectiveSelectedSubtitleKey = anchor.tagValue<String>(R.id.yummy_player_subtitles)
            ?: selectedSubtitleKey
        menu.add(SUBTITLE_MENU_GROUP_ID, 0, 0, texts.subtitlesOff).apply {
            isCheckable = true
            isChecked = effectiveSelectedSubtitleKey == SUBTITLE_OFF_KEY
        }
        addCheckableItems(
            groupId = SUBTITLE_MENU_GROUP_ID,
            entries = options,
            itemIdOffset = 1,
            title = { _, option -> option.label },
            selected = { option -> option.matchesSelectedSubtitleKey(effectiveSelectedSubtitleKey) },
        )
        menu.setGroupCheckable(SUBTITLE_MENU_GROUP_ID, true, true)
        setOnMenuItemClickListener { item ->
            anchor.rememberPlayerControlFocus(onRememberPlayerControlFocus)
            if (item.itemId == 0) {
                player.disableSubtitles()
                anchor.setTag(R.id.yummy_player_subtitles, SUBTITLE_OFF_KEY)
                onSelectedSubtitleKeyChange(SUBTITLE_OFF_KEY)
                return@setOnMenuItemClickListener true
            }
            val option = options.getOrNull(item.itemId - 1) ?: return@setOnMenuItemClickListener false
            player.selectSubtitle(option)
            val stableKey = option.subtitleOptionIdentity()
            anchor.setTag(R.id.yummy_player_subtitles, stableKey)
            onSelectedSubtitleKeyChange(stableKey)
            true
        }
        show()
    }
}

internal fun showSpeedPopup(
    anchor: View,
    selected: PlayerSpeed,
    onRememberPlayerControlFocus: (Int) -> Unit = {},
    onSelected: (PlayerSpeed) -> Unit,
) {
    anchor.rememberPlayerControlFocus(onRememberPlayerControlFocus)
    PopupMenu(anchor.context, anchor).apply {
        addCheckableItems(
            groupId = SPEED_MENU_GROUP_ID,
            entries = PlayerSpeed.entries,
            title = { _, speed -> speed.title },
            selected = { speed -> speed == selected },
        )
        setOnMenuItemClickListener { item ->
            val speed = PlayerSpeed.entries.getOrNull(item.itemId) ?: return@setOnMenuItemClickListener false
            anchor.rememberPlayerControlFocus(onRememberPlayerControlFocus)
            onSelected(speed)
            true
        }
        show()
    }
}

private fun View.rememberPlayerControlFocus(onRememberPlayerControlFocus: (Int) -> Unit) {
    if (!isInTouchMode && id != View.NO_ID) {
        onRememberPlayerControlFocus(id)
    }
    (rootView.findViewById<View>(R.id.yummy_player_view) as? PlayerView)?.showPlayerControls()
}
