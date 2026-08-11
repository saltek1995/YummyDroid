package me.yummydroid.app.ui

import android.view.View
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import me.yummydroid.app.R
import me.yummydroid.app.data.OfflineVideoFile
import me.yummydroid.app.data.PlayerSpeed
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.availableVoiceEpisodeCount
import me.yummydroid.app.data.isSameEpisodeAs
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.matchingVoiceTitle

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
