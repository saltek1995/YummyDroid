package me.yummydroid.app.ui

import android.view.View
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.availableVoiceEpisodeCount
import me.yummydroid.app.data.isSameEpisodeAs
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.matchingVoiceTitle

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
