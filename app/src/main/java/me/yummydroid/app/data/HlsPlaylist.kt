package me.yummydroid.app.data

internal data class HlsVariant(
    val height: Int?,
    val bandwidth: Int,
    val url: String,
)

internal fun String.selectBestHlsVariant(
    baseUrl: String,
    preferredQuality: PreferredQuality,
): HlsVariant? {
    return hlsVariants(baseUrl).selectForQuality(preferredQuality)
}

internal fun String.hlsVariants(baseUrl: String): List<HlsVariant> {
    val variants = mutableListOf<HlsVariant>()
    var pendingVariant: HlsVariantMetadata? = null
    lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        when {
            line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true) -> {
                pendingVariant = line.toHlsVariantMetadata()
            }
            line.isNotBlank() && !line.startsWith("#") -> {
                pendingVariant?.let { metadata ->
                    variants += HlsVariant(
                        height = metadata.height,
                        bandwidth = metadata.bandwidth,
                        url = line.resolveUrlAgainst(baseUrl),
                    )
                }
                pendingVariant = null
            }
        }
    }
    return variants
}

internal fun String.hlsSourceQualities(): List<SourceQuality> {
    return lineSequence()
        .map { it.trim() }
        .filter { line -> line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true) }
        .map { line ->
            val metadata = line.toHlsVariantMetadata()
            SourceQuality(
                height = metadata.height,
                bitrate = metadata.bandwidth,
            )
        }
        .toList()
        .normalizedSourceQualities()
}

internal fun List<HlsVariant>.selectForQuality(preferredQuality: PreferredQuality): HlsVariant? {
    if (isEmpty()) return null
    val preferredHeight = preferredQuality.height
    if (preferredHeight == null) {
        return maxWithOrNull(compareBy<HlsVariant> { it.height ?: 0 }.thenBy { it.bandwidth })
    }

    return minWithOrNull(
        compareBy<HlsVariant> { variant ->
            val height = variant.height ?: 0
            when {
                height <= 0 -> 2
                height <= preferredHeight -> 0
                else -> 1
            }
        }.thenBy { variant ->
            val height = variant.height ?: 0
            when {
                height <= 0 -> Int.MAX_VALUE
                height <= preferredHeight -> preferredHeight - height
                else -> height - preferredHeight
            }
        }.thenByDescending { it.bandwidth },
    )
}

internal fun String.hlsAttribute(name: String): String? {
    val pattern = Regex("""(?i)(?:^|[:,])\s*$name=(?:"([^"]*)"|([^,]*))""")
    val match = pattern.find(this) ?: return null
    return match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }
        ?: match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }
}

private data class HlsVariantMetadata(
    val height: Int?,
    val bandwidth: Int,
)

private fun String.toHlsVariantMetadata(): HlsVariantMetadata {
    return HlsVariantMetadata(
        height = hlsResolutionHeightRegex
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull(),
        bandwidth = hlsBandwidthRegex
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: 0,
    )
}

private val hlsResolutionHeightRegex = Regex("""(?i)RESOLUTION\s*=\s*\d+\s*x\s*(\d+)""")
private val hlsBandwidthRegex = Regex("""(?i)BANDWIDTH\s*=\s*(\d+)""")
