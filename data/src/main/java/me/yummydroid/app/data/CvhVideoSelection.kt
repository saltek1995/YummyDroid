package me.yummydroid.app.data

import android.net.Uri

internal fun buildCvhVoiceCandidates(
    iframeUri: Uri,
    video: VideoVariant,
): List<String> {
    val iframeVoices = listOf(
        "dubbing_code",
        "priority-voice",
        "translation",
        "voice",
        "voiceStudio",
        "voice_studio",
        "dubbing",
    ).mapNotNull { name -> iframeUri.getQueryParameter(name) }

    return (iframeVoices + video.dubbing + video.groupTitle)
        .map { it.trim() }
        .filter { it.isNotBlank() && it.cvhVoiceAliases().isNotEmpty() }
        .distinctBy { it.cvhVoiceIdentity() }
}

internal fun List<CvhItemDto>.selectCvhItem(
    season: Int?,
    episode: Int,
    priorityVoices: List<String>,
): CvhItemDto? {
    val seasonItems = filter { item ->
        season == null || (item.season ?: 1) == season
    }
    val episodeItems = seasonItems.filter { item ->
        cvhPlaylistItemMatchesEpisode(
            requestedSeason = season,
            requestedEpisode = episode,
            itemSeason = item.season,
            itemEpisode = item.episode,
        )
    }
    if (episodeItems.isEmpty()) {
        val fallbackEpisode = cvhFallbackEpisodeForMissingRequestedEpisode(
            requestedEpisode = episode,
            availableEpisodes = seasonItems.map { it.episode },
        ) ?: return null
        return seasonItems
            .filter { item -> (item.episode ?: 1) == fallbackEpisode }
            .selectCvhItemForVoice(priorityVoices)
    }

    return episodeItems.selectCvhItemForVoice(priorityVoices)
}

private fun List<CvhItemDto>.selectCvhItemForVoice(
    priorityVoices: List<String>,
): CvhItemDto? {
    val requestedAliases = priorityVoices
        .flatMap { it.cvhVoiceAliases() }
        .toSet()
    if (requestedAliases.isNotEmpty()) {
        firstOrNull { item ->
            item.cvhVoiceAliases().any { it in requestedAliases }
        }?.let { return it }

        firstOrNull { item ->
            item.cvhVoiceAliases().any { itemAlias ->
                requestedAliases.any { requestedAlias ->
                    itemAlias.isMeaningfulCvhAliasMatch(requestedAlias)
                }
            }
        }?.let { return it }

        if (priorityVoices.any { it.isSubtitleCvhVoice() }) {
            firstOrNull { item ->
                item.voiceType.orEmpty().isSubtitleCvhVoice() ||
                    item.voiceStudio.orEmpty().isSubtitleCvhVoice()
            }?.let { return it }
        }

        return null
    }

    return firstOrNull { !it.voiceStudio.isNullOrBlank() }
        ?: firstOrNull()
}

private fun CvhItemDto.cvhVoiceAliases(): Set<String> {
    return buildSet {
        voiceStudio?.cvhVoiceAliases()?.let(::addAll)
        voiceType?.cvhVoiceAliases()?.let(::addAll)
        if (!voiceStudio.isNullOrBlank() && !voiceType.isNullOrBlank()) {
            addAll("${voiceStudio.orEmpty()} ${voiceType.orEmpty()}".cvhVoiceAliases())
        }
    }
}

private fun String.cvhVoiceAliases(): Set<String> {
    val identity = cvhVoiceIdentity()
    if (identity.isBlank()) return emptySet()
    return buildSet {
        add(identity)
        if (identity.endsWith("tv") && identity.length > 4) {
            add(identity.removeSuffix("tv"))
        }
    }
}

private fun String.cvhVoiceIdentity(): String {
    return trim()
        .lowercase()
        .replace('\u0451', '\u0435')
        .replace(CVH_RU_VOICE_PREFIX_KEY, "")
        .replace(CVH_RU_PLAYER_PREFIX_KEY, "")
        .replace(CVH_RU_SUBTITLES_PREFIX_KEY, "")
        .replace("subtitle", "")
        .replace("subtitles", "")
        .replace("subs", "")
        .replace("voice", "")
        .replace("dubbing", "")
        .replace("dub", "")
        .replace(Regex("[\\s./|\\u2022\\u0432\\u0402\\u045E:_+&\\-]+"), "")
        .trim()
}

private fun String.isSubtitleCvhVoice(): Boolean {
    val value = lowercase().replace('\u0451', '\u0435')
    return CVH_RU_SUBTITLE_STEM_KEY in value || "subtitle" in value
}

private fun String.isMeaningfulCvhAliasMatch(other: String): Boolean {
    if (length < 4 || other.length < 4) return false
    return startsWith(other) || other.startsWith(this)
}

private const val CVH_RU_VOICE_PREFIX_KEY = "\u043e\u0437\u0432\u0443\u0447\u043a\u0430"
private const val CVH_RU_PLAYER_PREFIX_KEY = "\u043f\u043b\u0435\u0435\u0440"
private const val CVH_RU_SUBTITLES_PREFIX_KEY = "\u0441\u0443\u0431\u0442\u0438\u0442\u0440\u044b"
private const val CVH_RU_SUBTITLE_STEM_KEY = "\u0441\u0443\u0431\u0442\u0438\u0442\u0440"
