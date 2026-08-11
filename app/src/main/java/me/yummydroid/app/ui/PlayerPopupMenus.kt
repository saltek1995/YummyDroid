package me.yummydroid.app.ui

import android.view.View
import androidx.media3.ui.PlayerView
import me.yummydroid.app.R

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
    (rootView.findViewById<View>(R.id.yummy_player_view) as? PlayerView)?.showPlayerControls()
}
