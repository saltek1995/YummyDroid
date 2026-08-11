package me.yummydroid.app.ui

import android.view.View
import me.yummydroid.app.data.VideoVariant

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
