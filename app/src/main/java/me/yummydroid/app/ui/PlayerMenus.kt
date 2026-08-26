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
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.core.graphics.drawable.toDrawable
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.util.WeakHashMap
import kotlin.math.ceil
import me.yummydroid.app.R
import me.yummydroid.app.data.OfflineVideoFile
import me.yummydroid.app.data.PlayerSpeed
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.episodeOrderValue
import me.yummydroid.app.data.isSameEpisodeAs
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.matchingVoiceTitle
import me.yummydroid.app.data.sourceProviderRank

// PlayerPopupMenus
internal fun <T> PopupMenu.addCheckableItems(
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

internal fun View.rememberPlayerControlFocus(onRememberPlayerControlFocus: (Int) -> Unit) {
    if (!isInTouchMode && id != View.NO_ID) {
        onRememberPlayerControlFocus(id)
    }
}

// PlayerPopupMenuView
private val PLAYER_POPUP_PANEL_COLOR: Int = 0xF2111B2F.toInt()
private val PLAYER_POPUP_SELECTED_COLOR: Int = 0x33FFB454
private val PLAYER_POPUP_STROKE_COLOR: Int = 0x263A4D67
private const val PLAYER_POPUP_LABEL_TEXT_SIZE_SP = 15f

private data class PlayerPopupLayout(
    val rowHeight: Int,
    val margin: Int,
    val screenWidth: Int,
    val screenHeight: Int,
    val width: Int,
    val height: Int,
)

internal data class PlayerPopupPlacement(
    val x: Int,
    val y: Int,
)

internal enum class PlayerPopupKeyAction {
    Click,
    Dismiss,
    Previous,
    Next,
    Ignore,
}

internal class PopupMenu(
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

        val layout = context.playerPopupLayout(items)
        val playerView = anchor.rootView.findViewById<PlayerView>(R.id.yummy_player_view) ?: return
        playerView.dismissPlayerPopupMenu()
        lateinit var overlay: PlayerPopupOverlay
        val adapter = PlayerPopupMenuAdapter(items, layout.rowHeight)
        val listView = createListView(adapter) { overlay.dismiss() }
        overlay = createOverlay(playerView, listView, layout)
        ActivePlayerPopupOverlays[playerView] = overlay
        playerView.addView(overlay.container)
        playerView.removeTaggedRunnable(R.id.yummy_player_controls_auto_hide_runnable)
        requestInitialFocus(items, adapter, listView)
    }

    private fun createRow(rowHeight: Int): LinearLayout {
        val marker = context.createPlayerPopupMarker(checked = false)
        val label = context.createPlayerPopupLabel()
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = rowHeight
            isClickable = true
            background = context.playerMenuRowBackground()
            setPadding(context.playerMenuDp(12), 0, context.playerMenuDp(12), 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, rowHeight)
            tag = PlayerPopupRowViews(marker, label)
            addView(marker)
            addView(label)
        }
    }

    private fun LinearLayout.bindRow(item: PlayerPopupMenuItem, active: Boolean) {
        val views = tag as? PlayerPopupRowViews ?: return
        val highlighted = active || item.isChecked
        isActivated = active
        isSelected = item.isChecked
        contentDescription = item.title
        views.marker.alpha = if (highlighted) 1f else 0f
        views.label.apply {
            text = item.title
            typeface = if (highlighted) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    private fun createListView(
        adapter: PlayerPopupMenuAdapter,
        dismiss: () -> Unit,
    ): ListView {
        return ListView(context).apply {
            this.adapter = adapter
            divider = null
            selector = android.graphics.Color.TRANSPARENT.toDrawable()
            cacheColorHint = android.graphics.Color.TRANSPARENT
            isFocusable = true
            isFocusableInTouchMode = true
            itemsCanFocus = false
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            choiceMode = ListView.CHOICE_MODE_SINGLE
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            background = context.playerMenuPanelBackground()
            setPadding(context.playerMenuDp(8), context.playerMenuDp(6), context.playerMenuDp(8), context.playerMenuDp(6))
            clipToPadding = false
            onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
                val item = adapter.itemAt(position) ?: return@OnItemClickListener
                if (itemClickListener?.invoke(item) != false) {
                    dismiss()
                }
            }
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    adapter.selectedIndex = position
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
            setOnKeyListener { _, keyCode, event ->
                when (playerPopupKeyAction(keyCode, event.action)) {
                    PlayerPopupKeyAction.Click -> {
                        val position = selectedItemPosition
                            .takeIf { it != AdapterView.INVALID_POSITION }
                            ?: adapter.selectedIndex
                        val child = getChildAt(position - firstVisiblePosition)
                        performItemClick(child ?: this, position, adapter.getItemId(position))
                        true
                    }
                    PlayerPopupKeyAction.Dismiss -> {
                        dismiss()
                        true
                    }
                    PlayerPopupKeyAction.Previous,
                    PlayerPopupKeyAction.Next,
                    PlayerPopupKeyAction.Ignore -> false
                }
            }
        }
    }

    private fun createOverlay(
        playerView: PlayerView,
        listView: ListView,
        layout: PlayerPopupLayout,
    ): PlayerPopupOverlay {
        val overlayContainer = FrameLayout(context).apply {
            isClickable = true
            isFocusable = false
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            setOnClickListener {
                ActivePlayerPopupOverlays[playerView]?.dismiss()
            }
            addView(listView, playerPopupListLayoutParams(playerView, layout))
        }
        return PlayerPopupOverlay(playerView, anchor, overlayContainer)
    }

    private fun playerPopupListLayoutParams(
        playerView: PlayerView,
        layout: PlayerPopupLayout,
    ): FrameLayout.LayoutParams {
        val anchorLocation = IntArray(2)
        val playerLocation = IntArray(2)
        anchor.getLocationOnScreen(anchorLocation)
        playerView.getLocationOnScreen(playerLocation)
        val playerWidth = playerView.width.takeIf { it > 0 } ?: layout.screenWidth
        val playerHeight = playerView.height.takeIf { it > 0 } ?: layout.screenHeight
        val anchorLeft = anchorLocation[0] - playerLocation[0]
        val anchorTop = anchorLocation[1] - playerLocation[1]
        val placement = playerPopupPlacement(
            playerWidth = playerWidth,
            playerHeight = playerHeight,
            anchorLeft = anchorLeft,
            anchorTop = anchorTop,
            anchorWidth = anchor.width,
            anchorHeight = anchor.height,
            popupWidth = layout.width,
            popupHeight = layout.height,
            margin = layout.margin,
            gap = context.playerMenuDp(10),
        )
        return FrameLayout.LayoutParams(layout.width, layout.height).apply {
            leftMargin = placement.x
            topMargin = placement.y
        }
    }

    private fun requestInitialFocus(
        items: List<PlayerPopupMenuItem>,
        adapter: PlayerPopupMenuAdapter,
        listView: ListView,
    ) {
        val selectedIndex = playerPopupInitialSelectionIndex(items.size, items.indexOfFirst { item -> item.isChecked })
        if (selectedIndex == AdapterView.INVALID_POSITION) return
        adapter.selectedIndex = selectedIndex
        listView.requestPlayerPopupFocus(selectedIndex)
        listView.post { listView.requestPlayerPopupFocus(selectedIndex) }
    }

    private fun ListView.requestPlayerPopupFocus(selectedIndex: Int) {
        setSelection(selectedIndex)
        if (isInTouchMode && !hasFocus()) {
            requestFocusFromTouch()
        }
        if (!hasFocus()) {
            requestFocus()
        }
    }

    private inner class PlayerPopupMenuAdapter(
        private val items: List<PlayerPopupMenuItem>,
        private val rowHeight: Int,
    ) : BaseAdapter() {
        var selectedIndex: Int = playerPopupInitialSelectionIndex(
            itemCount = items.size,
            checkedIndex = items.indexOfFirst { item -> item.isChecked },
        )
            set(value) {
                if (field == value) return
                field = value
                notifyDataSetChanged()
            }

        override fun getCount(): Int = items.size

        override fun getItem(position: Int): PlayerPopupMenuItem = items[position]

        override fun getItemId(position: Int): Long = getItem(position).itemId.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = convertView as? LinearLayout ?: createRow(rowHeight)
            row.bindRow(getItem(position), active = position == selectedIndex)
            return row
        }

        fun itemAt(position: Int): PlayerPopupMenuItem? = items.getOrNull(position)
    }

    private data class PlayerPopupRowViews(
        val marker: View,
        val label: TextView,
    )
}

private class PlayerPopupOverlay(
    private val playerView: PlayerView,
    private val anchor: View,
    val container: FrameLayout,
) {
    fun dismiss(): Boolean {
        if (ActivePlayerPopupOverlays[playerView] !== this) return false
        ActivePlayerPopupOverlays.remove(playerView)
        (container.parent as? ViewGroup)?.removeView(container)
        if (!anchor.isInTouchMode) {
            anchor.playerFocusableTarget()?.requestFocus()
        }
        playerView.resumePlayerControlsAutoHide()
        return true
    }
}

private val ActivePlayerPopupOverlays = WeakHashMap<PlayerView, PlayerPopupOverlay>()

internal fun PlayerView.dismissPlayerPopupMenu(): Boolean {
    return ActivePlayerPopupOverlays[this]?.dismiss() == true
}

internal fun PlayerView.hasPlayerPopupMenu(): Boolean {
    return ActivePlayerPopupOverlays.containsKey(this)
}

internal fun playerPopupPlacement(
    playerWidth: Int,
    playerHeight: Int,
    anchorLeft: Int,
    anchorTop: Int,
    anchorWidth: Int,
    anchorHeight: Int,
    popupWidth: Int,
    popupHeight: Int,
    margin: Int,
    gap: Int,
): PlayerPopupPlacement {
    val maxX = (playerWidth - popupWidth - margin).coerceAtLeast(margin)
    val x = (anchorLeft + anchorWidth / 2 - popupWidth / 2).coerceIn(margin, maxX)
    val aboveY = anchorTop - popupHeight - gap
    val belowY = anchorTop + anchorHeight + gap
    val maxY = (playerHeight - popupHeight - margin).coerceAtLeast(margin)
    val y = if (aboveY >= margin) aboveY else belowY.coerceIn(margin, maxY)
    return PlayerPopupPlacement(x = x, y = y)
}

private fun Context.playerPopupLayout(items: List<PlayerPopupMenuItem>): PlayerPopupLayout {
    val rowHeight = playerMenuDp(48)
    val verticalPadding = playerMenuDp(10)
    val margin = playerMenuDp(14)
    val screenWidth = resources.displayMetrics.widthPixels
    val screenHeight = resources.displayMetrics.heightPixels
    val maxWidth = (screenWidth - margin * 2).coerceAtLeast(playerMenuDp(120))
    val width = playerMenuContentWidth(items).coerceAtMost(maxWidth)
    val maxHeight = (screenHeight * 0.62f).toInt().coerceAtLeast(rowHeight + verticalPadding * 2)
    val height = (items.size * rowHeight + verticalPadding * 2).coerceAtMost(maxHeight)
    return PlayerPopupLayout(rowHeight, margin, screenWidth, screenHeight, width, height)
}

private fun Context.createPlayerPopupMarker(checked: Boolean): View {
    return View(this).apply {
        alpha = if (checked) 1f else 0f
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = playerMenuDp(2).toFloat()
            setColor(PLAYER_ACCENT_COLOR)
        }
        layoutParams = LinearLayout.LayoutParams(playerMenuDp(3), playerMenuDp(24)).apply {
            marginEnd = playerMenuDp(12)
        }
    }
}

private fun Context.createPlayerPopupLabel(): TextView {
    return TextView(this).apply {
        setTextColor(playerMenuTextColors())
        textSize = PLAYER_POPUP_LABEL_TEXT_SIZE_SP
        includeFontPadding = false
        isDuplicateParentStateEnabled = true
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
    }
}

internal fun playerPopupKeyAction(keyCode: Int, eventAction: Int): PlayerPopupKeyAction {
    if (eventAction != KeyEvent.ACTION_DOWN) return PlayerPopupKeyAction.Ignore
    return when (keyCode) {
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER -> PlayerPopupKeyAction.Click
        KeyEvent.KEYCODE_BACK -> PlayerPopupKeyAction.Dismiss
        KeyEvent.KEYCODE_DPAD_UP -> PlayerPopupKeyAction.Previous
        KeyEvent.KEYCODE_DPAD_DOWN -> PlayerPopupKeyAction.Next
        else -> PlayerPopupKeyAction.Ignore
    }
}

internal fun playerPopupInitialSelectionIndex(itemCount: Int, checkedIndex: Int): Int {
    if (itemCount <= 0) return AdapterView.INVALID_POSITION
    return checkedIndex.takeIf { it in 0 until itemCount } ?: 0
}

internal class PlayerPopupMenu {
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

internal class PlayerPopupMenuItem(
    val groupId: Int,
    val itemId: Int,
    val order: Int,
    val title: CharSequence,
) {
    var isCheckable: Boolean = false
    var isChecked: Boolean = false
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
            intArrayOf(android.R.attr.state_activated),
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
            intArrayOf(android.R.attr.state_activated),
            intArrayOf(android.R.attr.state_pressed),
            intArrayOf(android.R.attr.state_selected),
            intArrayOf(),
        ),
        intArrayOf(
            PLAYER_ACCENT_CONTENT_COLOR,
            PLAYER_ACCENT_CONTENT_COLOR,
            PLAYER_ACCENT_CONTENT_COLOR,
            PLAYER_ACCENT_COLOR,
            PLAYER_CONTROL_CONTENT_COLOR,
        ),
    )
}

// PlayerQualityPopupMenu
@OptIn(UnstableApi::class)
internal fun showQualityPopup(
    anchor: View,
    player: ExoPlayer,
    options: List<QualityOption>,
    selectedQualityKey: String?,
    onSelectedQualityKeyChange: (String) -> Unit,
    onSelectLocalQuality: (OfflineVideoFile) -> Unit,
    onSelectPreferredQuality: (PreferredQuality) -> Unit,
    onPlaybackSelectionStarted: () -> Unit = {},
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
            if (option.matchesSelectedQualityKey(effectiveSelectedQualityKey)) {
                return@setOnMenuItemClickListener true
            }
            onPlaybackSelectionStarted()
            when {
                option.localFile != null -> anchor.post { onSelectLocalQuality(option.localFile) }
                option.preferredQuality != null -> onSelectPreferredQuality(option.preferredQuality)
                option.hasPlayableQualityConstraint() -> player.selectQuality(option)
                else -> player.selectQuality(option)
            }
            if (option.preferredQuality == null) {
                val stableKey = option.qualityOptionIdentity()
                anchor.setTag(R.id.yummy_player_quality, stableKey)
                onSelectedQualityKeyChange(stableKey)
            }
            true
        }
        show()
    }
}

// PlayerSourcePopupMenu
internal fun showSourcePopup(
    anchor: View,
    options: List<SourceOption>,
    selectedSourceKey: String?,
    onPlaybackSelectionStarted: () -> Unit = {},
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
            if (option.key == selectedSourceKey) return@setOnMenuItemClickListener true
            onPlaybackSelectionStarted()
            anchor.post { onSelectSource(option.video) }
            true
        }
        show()
    }
}

// PlayerSpeedPopupMenu
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

// PlayerSubtitlePopupMenu
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

// PlayerVoicePopupMenu
internal data class PlayerVoiceSelectionOption(
    val key: String,
    val label: String,
    val groupKey: String,
    val replacement: VideoVariant?,
)

internal fun playerVoiceSelectionOptions(
    groups: Map<String, List<VideoVariant>>,
    preferredGroupKey: String?,
    currentVideo: VideoVariant,
    texts: PlayerControlTexts,
): List<PlayerVoiceSelectionOption> {
    return groups.entries.mapIndexed { index, entry ->
        val voiceTitle = entry.value.firstOrNull()?.matchingVoiceTitle.orEmpty()
            .ifBlank { "${texts.voice} ${index + 1}" }
        val summary = entry.value.playerVoiceSelectionSummary(
            selectedVoiceKey = entry.key,
            preferredGroupKey = preferredGroupKey,
            currentVideo = currentVideo,
        )
        val downloadedSuffix = if (summary.downloadedEpisodes > 0) {
            " \u2022 ${texts.downloaded}: ${summary.downloadedEpisodes}"
        } else {
            ""
        }
        PlayerVoiceSelectionOption(
            key = entry.key,
            label = "$voiceTitle (${summary.availableEpisodes})$downloadedSuffix",
            groupKey = summary.replacement?.groupKey ?: summary.fallbackGroupKey ?: entry.key,
            replacement = summary.replacement,
        )
    }
}

internal data class PlayerVoiceSelectionSummary(
    val availableEpisodes: Int,
    val downloadedEpisodes: Int,
    val fallbackGroupKey: String?,
    val replacement: VideoVariant?,
)

internal fun List<VideoVariant>.playerVoiceSelectionSummary(
    selectedVoiceKey: String?,
    preferredGroupKey: String?,
    currentVideo: VideoVariant,
): PlayerVoiceSelectionSummary {
    val availableEpisodeKeys = mutableSetOf<String>()
    val downloadedEpisodeKeys = mutableSetOf<String>()
    for (video in this) {
        val episodeKey = video.matchingEpisodeKey
        if (episodeKey.isBlank()) continue
        availableEpisodeKeys += episodeKey
        if (video.isOfflineAvailable) {
            downloadedEpisodeKeys += episodeKey
        }
    }
    val scopedVideos = selectedVoiceKey
        ?.takeIf { it.isNotBlank() }
        ?.let { voiceKey -> filter { it.matchingVoiceKey == voiceKey } }
        ?.takeIf { it.isNotEmpty() }
        ?: this
    return PlayerVoiceSelectionSummary(
        availableEpisodes = availableEpisodeKeys.size,
        downloadedEpisodes = downloadedEpisodeKeys.size,
        fallbackGroupKey = firstOrNull()?.groupKey,
        replacement = scopedVideos.preferredVoiceReplacement(
            preferredGroupKey = preferredGroupKey,
            currentVideo = currentVideo,
        ),
    )
}

private fun List<VideoVariant>.preferredVoiceReplacement(
    preferredGroupKey: String?,
    currentVideo: VideoVariant,
): VideoVariant? {
    if (isEmpty()) return null
    val variantComparator = playerVoiceReplacementComparator(preferredGroupKey)
    val bestByEpisode = linkedMapOf<String, VideoVariant>()
    var sameEpisode: VideoVariant? = null
    for (video in this) {
        if (video.isSameEpisodeAs(currentVideo)) {
            val previous = sameEpisode
            if (previous == null || variantComparator.compare(video, previous) < 0) {
                sameEpisode = video
            }
        }

        val episodeKey = video.matchingEpisodeKey
        val previousForEpisode = bestByEpisode[episodeKey]
        if (previousForEpisode == null || variantComparator.compare(video, previousForEpisode) < 0) {
            bestByEpisode[episodeKey] = video
        }
    }
    return sameEpisode ?: bestByEpisode.values.minWithOrNull(playerVoiceEpisodeComparator)
}

private fun playerVoiceReplacementComparator(preferredGroupKey: String?): Comparator<VideoVariant> {
    return compareBy<VideoVariant> { if (it.isOfflineAvailable) 0 else 1 }
        .thenBy { if (it.groupKey == preferredGroupKey) 0 else 1 }
        .thenBy { sourceProviderRank(it.player) }
        .thenBy { it.index }
        .thenBy { it.id }
}

private val playerVoiceEpisodeComparator: Comparator<VideoVariant> =
    compareBy<VideoVariant> { it.episodeOrderValue() ?: Double.MAX_VALUE }
        .thenBy { it.index.takeIf { index -> index > 0 } ?: Int.MAX_VALUE }
        .thenBy { if (it.isOfflineAvailable) 0 else 1 }
        .thenBy { it.id }

internal fun showVoicePopup(
    anchor: View,
    groups: Map<String, List<VideoVariant>>,
    selectedKey: String?,
    preferredGroupKey: String?,
    currentVideo: VideoVariant,
    texts: PlayerControlTexts,
    onPlaybackSelectionStarted: () -> Unit = {},
    onRememberPlayerControlFocus: (Int) -> Unit = {},
    onSelectGroup: (String, VideoVariant?) -> Unit,
) {
    val options = playerVoiceSelectionOptions(
        groups = groups,
        preferredGroupKey = preferredGroupKey,
        currentVideo = currentVideo,
        texts = texts,
    )
    anchor.rememberPlayerControlFocus(onRememberPlayerControlFocus)
    PopupMenu(anchor.context, anchor).apply {
        options.forEachIndexed { index, option ->
            menu.add(VOICE_MENU_GROUP_ID, index, index, option.label).apply {
                isCheckable = true
                isChecked = option.key == selectedKey
            }
        }
        menu.setGroupCheckable(VOICE_MENU_GROUP_ID, true, true)
        setOnMenuItemClickListener { item ->
            val option = options.getOrNull(item.itemId) ?: return@setOnMenuItemClickListener false
            anchor.rememberPlayerControlFocus(onRememberPlayerControlFocus)
            if (option.key == selectedKey) return@setOnMenuItemClickListener true
            onPlaybackSelectionStarted()
            anchor.post { onSelectGroup(option.groupKey, option.replacement) }
            true
        }
        show()
    }
}
