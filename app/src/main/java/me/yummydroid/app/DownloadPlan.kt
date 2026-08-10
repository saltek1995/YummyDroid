package me.yummydroid.app

import kotlinx.serialization.Serializable
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.normalizedDownloadQualities

@Serializable
data class DownloadPlan(
    val id: String,
    val animeId: Long,
    val animeTitle: String,
    val preferredQualityName: String = PreferredQuality.Auto.name,
    val qualityNames: List<String> = emptyList(),
    val onlyMissing: Boolean,
    val items: List<DownloadPlanItem>,
    val createdAtMs: Long = System.currentTimeMillis(),
) {
    val preferredQuality: PreferredQuality
        get() = acceptableQualities.firstOrNull() ?: PreferredQuality.Auto

    val acceptableQualities: List<PreferredQuality>
        get() = normalizedDownloadQualities(
            qualityNames.mapNotNull(PreferredQuality::fromName)
                .ifEmpty { listOf(PreferredQuality.fromName(preferredQualityName) ?: PreferredQuality.Auto) },
        )

    val qualityTitle: String
        get() = acceptableQualities.joinToString(", ") { it.title }
}

@Serializable
data class DownloadPlanItem(
    val episodeKey: String,
    val episodeTitle: String,
    val videoId: Long,
    val voiceKey: String,
    val voiceTitle: String,
    val groupKey: String,
    val qualityName: String = PreferredQuality.Auto.name,
)

val DownloadPlanItem.preferredQuality: PreferredQuality
    get() = PreferredQuality.fromName(qualityName) ?: PreferredQuality.Auto

