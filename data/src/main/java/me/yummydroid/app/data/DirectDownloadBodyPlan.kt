package me.yummydroid.app.data

internal data class DirectDownloadBodyPlan(
    val canAppend: Boolean,
    val startingBytes: Long,
    val totalBytes: Long,
)

internal fun directDownloadBodyPlan(
    existingBytes: Long,
    responseCode: Int,
    contentRangeTotal: Long?,
    contentLength: Long,
): DirectDownloadBodyPlan {
    val canAppend = existingBytes > 0L && responseCode == 206
    val startingBytes = if (canAppend) existingBytes else 0L
    val totalBytes = contentRangeTotal
        ?: contentLength.takeIf { it > 0L }
            ?.let { length -> if (canAppend) startingBytes + length else length }
        ?: -1L
    return DirectDownloadBodyPlan(canAppend, startingBytes, totalBytes)
}
