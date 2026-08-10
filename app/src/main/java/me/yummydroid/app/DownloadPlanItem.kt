package me.yummydroid.app

import kotlinx.serialization.Serializable
import me.yummydroid.app.data.PreferredQuality

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
