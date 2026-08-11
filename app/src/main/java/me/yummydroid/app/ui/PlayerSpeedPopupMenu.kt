package me.yummydroid.app.ui

import android.view.View
import me.yummydroid.app.data.PlayerSpeed

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
