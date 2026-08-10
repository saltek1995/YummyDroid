package me.yummydroid.app.data

import java.io.IOException

internal data class ResolvedHlsDownloadPlan(
    val plan: HlsSingleFilePlan,
    val qualityTitle: String,
)

internal fun YummyAnimeRepository.resolveHlsDownloadPlan(
    stream: ResolvedVideoStream,
    preferredQuality: PreferredQuality,
): ResolvedHlsDownloadPlan {
    val initialPlaylist = downloadText(stream.url, stream.headers)
    val variants = initialPlaylist.hlsVariants(stream.url)
    val selectedVariant = if (preferredQuality.height != null && variants.isNotEmpty()) {
        variants.selectExactQuality(preferredQuality)
            ?: throw IOException("HLS source does not contain ${preferredQuality.title} quality")
    } else {
        variants.selectForQuality(preferredQuality)
    }
    if (variants.isEmpty()) stream.requireExactDownloadQuality(preferredQuality)

    val mediaUrl = selectedVariant?.url ?: stream.url
    val mediaPlaylist = if (mediaUrl == stream.url) initialPlaylist else downloadText(mediaUrl, stream.headers)
    val plan = mediaPlaylist.toHlsSingleFilePlan(mediaUrl, selectedVariant?.bandwidth ?: 0)
    if (plan.segments.isEmpty()) {
        throw IOException("HLS playlist does not contain segments to download")
    }
    return ResolvedHlsDownloadPlan(
        plan = plan,
        qualityTitle = selectedVariant?.qualityTitle() ?: stream.qualityTitle(),
    )
}
