package me.yummydroid.app.ui

import android.view.View
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import me.yummydroid.app.R

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
