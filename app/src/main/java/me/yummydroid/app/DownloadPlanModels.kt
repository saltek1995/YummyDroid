package me.yummydroid.app

import me.yummydroid.app.data.isWholeNumber

data class DownloadVoiceCoverage(
    val voiceKey: String,
    val title: String,
    val episodeCount: Int,
    val downloadedCount: Int,
    val ranges: List<String>,
    val availableEpisodeRanges: List<IntRange>,
    val qualities: List<String>,
)

data class DownloadEpisodeSelection(
    val ranges: List<IntRange> = emptyList(),
) {
    val isRestricted: Boolean
        get() = ranges.isNotEmpty()

    fun allows(order: Double?): Boolean {
        if (!isRestricted) return true
        val episodeNumber = order
            ?.takeIf(::isWholeNumber)
            ?.toInt()
            ?: return false
        return ranges.any { range -> episodeNumber in range }
    }
}

data class DownloadEpisodeSelectionParseResult(
    val selection: DownloadEpisodeSelection,
    val error: DownloadEpisodeSelectionError? = null,
)

sealed interface DownloadEpisodeSelectionError {
    data class InvalidEpisodeNumber(val token: String) : DownloadEpisodeSelectionError
    data class InvalidEpisodeRange(val token: String) : DownloadEpisodeSelectionError
    data class MissingEpisodes(val ranges: String) : DownloadEpisodeSelectionError
}

data class DownloadPlanBuildResult(
    val plan: DownloadPlan,
    val totalEpisodes: Int,
    val selectedVoiceCount: Int,
    val alreadyDownloaded: Int,
    val missingInSelectedVoices: Int,
    val missingSelectedQuality: Int,
    val excludedByEpisodeSelection: Int = 0,
) {
    val scheduledCount: Int
        get() = plan.items.size
}

