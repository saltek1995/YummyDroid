package me.yummydroid.app.data

import java.io.IOException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

internal data class HlsSingleFilePlan(
    val mediaSequence: Long,
    val initUrl: String?,
    val outputExtension: String,
    val variantBandwidth: Int,
    val segments: List<HlsMediaSegment>,
) {
    fun signature(): String {
        return buildString {
            append(mediaSequence)
            append('|').append(initUrl.orEmpty())
            append('|').append(outputExtension)
            append('|').append(variantBandwidth)
            segments.forEach { segment ->
                append('|').append(segment.url)
                append('@').append(segment.durationSeconds)
                append('@').append(segment.encryption?.method.orEmpty())
                append('@').append(segment.encryption?.keyUrl.orEmpty())
            }
        }
    }
}

internal data class HlsMediaSegment(
    val url: String,
    val encryption: HlsEncryption?,
    val durationSeconds: Double,
)

internal data class HlsEncryption(
    val method: String,
    val keyUrl: String?,
    val iv: ByteArray?,
)

internal fun String.toHlsSingleFilePlan(baseUrl: String, variantBandwidth: Int): HlsSingleFilePlan {
    val segments = mutableListOf<HlsMediaSegment>()
    var encryption: HlsEncryption? = null
    var initUrl: String? = null
    var mediaSequence = 0L
    var nextSegmentDuration = 0.0

    lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        when {
            line.startsWith("#EXT-X-MEDIA-SEQUENCE", ignoreCase = true) -> {
                mediaSequence = line.substringAfter(':', "").trim().toLongOrNull() ?: 0L
            }
            line.startsWith("#EXT-X-KEY", ignoreCase = true) -> {
                encryption = line.toHlsEncryption(baseUrl)
            }
            line.startsWith("#EXT-X-MAP", ignoreCase = true) -> {
                initUrl = line.hlsAttribute("URI")?.let { it.resolveUrlAgainst(baseUrl) }
            }
            line.startsWith("#EXTINF", ignoreCase = true) -> {
                nextSegmentDuration = line.substringAfter(':', "")
                    .substringBefore(',')
                    .trim()
                    .toDoubleOrNull()
                    ?: 0.0
            }
            line.isBlank() || line.startsWith("#") -> Unit
            else -> {
                segments += HlsMediaSegment(
                    url = line.resolveUrlAgainst(baseUrl),
                    encryption = encryption,
                    durationSeconds = nextSegmentDuration,
                )
                nextSegmentDuration = 0.0
            }
        }
    }

    val extension = when {
        initUrl != null -> "mp4"
        segments.any { it.url.fileExtensionForDownload() in setOf("m4s", "mp4") } -> "mp4"
        else -> "ts"
    }
    return HlsSingleFilePlan(
        mediaSequence = mediaSequence,
        initUrl = initUrl,
        outputExtension = extension,
        variantBandwidth = variantBandwidth,
        segments = segments,
    )
}

internal fun String.toHlsEncryption(baseUrl: String): HlsEncryption? {
    val method = hlsAttribute("METHOD").orEmpty()
    if (method.equals("NONE", ignoreCase = true)) return null
    val keyUrl = hlsAttribute("URI")?.let { it.resolveUrlAgainst(baseUrl) }
    return HlsEncryption(
        method = method,
        keyUrl = keyUrl,
        iv = hlsAttribute("IV")?.hexToBytes(),
    )
}

internal suspend fun YummyAnimeRepository.decryptHlsSegment(
    bytes: ByteArray,
    encryption: HlsEncryption,
    sequenceNumber: Long,
    headers: Map<String, String>,
    keyCache: MutableMap<String, ByteArray>,
    bandwidthLimiter: DownloadBandwidthLimiter,
): ByteArray {
    if (!encryption.method.equals("AES-128", ignoreCase = true)) {
        throw IOException("HLS ${encryption.method} is not supported for offline downloading")
    }
    val keyUrl = encryption.keyUrl ?: throw IOException("HLS encryption key was not found")
    val key = keyCache[keyUrl] ?: downloadUrlBytes(keyUrl, headers, bandwidthLimiter).also { keyCache[keyUrl] = it }
    if (key.size != 16) throw IOException("Invalid HLS encryption key")
    val iv = encryption.iv ?: sequenceNumber.toAesIv()
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
    return cipher.doFinal(bytes)
}

internal fun Long.toAesIv(): ByteArray {
    val result = ByteArray(16)
    var value = this
    for (index in 15 downTo 8) {
        result[index] = (value and 0xff).toByte()
        value = value ushr 8
    }
    return result
}

internal fun String.hexToBytes(): ByteArray? {
    val clean = removePrefix("0x").removePrefix("0X").trim()
    if (clean.length % 2 != 0) return null
    return runCatching {
        ByteArray(clean.length / 2) { index ->
            clean.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }.getOrNull()
}
