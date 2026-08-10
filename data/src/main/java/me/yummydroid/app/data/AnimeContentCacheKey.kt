package me.yummydroid.app.data

import java.security.MessageDigest

private const val AnimeContentCacheSchemaVersion = "poster-original-v2"
private val AnimeContentCacheHexChars = "0123456789abcdef".toCharArray()

internal fun animeContentCacheName(vararg parts: Any?): String {
    val versionedParts = listOf<Any?>(AnimeContentCacheSchemaVersion) + parts.toList()
    val raw = versionedParts.joinToString(separator = "\u001f") { it?.toString().orEmpty() }
    val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
    return digest.toAnimeContentCacheHexString()
}

internal fun Long?.animeContentCacheUserPart(): String {
    return this?.takeIf { it > 0L }?.let { "user:$it" } ?: "anonymous"
}

private fun ByteArray.toAnimeContentCacheHexString(): String {
    val chars = CharArray(size * 2)
    forEachIndexed { index, byte ->
        val value = byte.toInt() and 0xFF
        chars[index * 2] = AnimeContentCacheHexChars[value ushr 4]
        chars[index * 2 + 1] = AnimeContentCacheHexChars[value and 0x0F]
    }
    return String(chars)
}
