package me.yummydroid.app.data


fun Int.qualityPreferenceScore(preferredQuality: PreferredQuality): Int {
    val height = coerceAtLeast(0)
    val preferredHeight = preferredQuality.height ?: return height
    return when {
        height <= 0 -> 0
        height <= preferredHeight -> 1_000_000 + height
        else -> 500_000 - (height - preferredHeight).coerceAtLeast(0)
    }
}

fun <T> Iterable<T>.selectForPreferredQuality(
    preferredQuality: PreferredQuality,
    height: (T) -> Int?,
    bitrate: (T) -> Int = { 0 },
    priority: (T) -> Int = { 0 },
): T? {
    val preferredHeight = preferredQuality.height
    return if (preferredHeight == null) {
        maxWithOrNull(
            compareBy<T> { height(it).validQualityHeight() ?: 0 }
                .thenBy { bitrate(it).coerceAtLeast(0) }
                .thenBy { priority(it) },
        )
    } else {
        minWithOrNull(
            compareBy<T> { preferredQualityBucket(height(it), preferredHeight) }
                .thenBy { preferredQualityDistance(height(it), preferredHeight) }
                .thenByDescending { bitrate(it).coerceAtLeast(0) }
                .thenByDescending { priority(it) },
        )
    }
}

private fun preferredQualityBucket(height: Int?, preferredHeight: Int): Int {
    val safeHeight = height.validQualityHeight() ?: return 2
    return if (safeHeight <= preferredHeight) 0 else 1
}

private fun preferredQualityDistance(height: Int?, preferredHeight: Int): Int {
    val safeHeight = height.validQualityHeight() ?: return Int.MAX_VALUE
    return if (safeHeight <= preferredHeight) {
        preferredHeight - safeHeight
    } else {
        safeHeight - preferredHeight
    }
}

private fun Int?.validQualityHeight(): Int? {
    return this?.takeIf { it in 100..4320 }
}
