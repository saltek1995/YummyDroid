package me.yummydroid.app.ui

import android.view.View
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import me.yummydroid.app.R
import me.yummydroid.app.data.OfflineVideoFile
import me.yummydroid.app.data.PreferredQuality

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
