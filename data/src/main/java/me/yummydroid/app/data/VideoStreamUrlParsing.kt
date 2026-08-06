package me.yummydroid.app.data

import androidx.core.net.toUri
import java.security.MessageDigest
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal fun String.mimeTypeFromUrl(): String? {
    val lower = lowercase()
    return when {
        ".m3u8" in lower -> "application/x-mpegURL"
        ".mpd" in lower -> "application/dash+xml"
        ".mp4" in lower -> "video/mp4"
        else -> null
    }
}

internal fun String.subtitleMimeTypeFromUrl(): String? {
    val lower = substringBefore('?').substringBefore('#').lowercase()
    return when {
        lower.endsWith(".vtt") -> "text/vtt"
        lower.endsWith(".srt") -> "application/x-subrip"
        lower.endsWith(".ass") || lower.endsWith(".ssa") -> "text/x-ssa"
        lower.endsWith(".ttml") || lower.endsWith(".dfxp") -> "application/ttml+xml"
        lower.endsWith(".m3u8") -> "application/x-mpegURL"
        else -> null
    }
}

internal fun String?.subtitleMimeTypeFromContentType(): String? {
    val lower = this
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase()
        .orEmpty()
    return when {
        lower == "text/vtt" || lower == "text/webvtt" -> "text/vtt"
        "subrip" in lower -> "application/x-subrip"
        "x-ssa" in lower || "x-ass" in lower -> "text/x-ssa"
        "ttml" in lower || "dfxp" in lower -> "application/ttml+xml"
        "mpegurl" in lower -> "application/x-mpegURL"
        else -> null
    }
}

internal fun String.subtitleLabelFromUrl(): String {
    val path = runCatching { toUri().lastPathSegment }.getOrNull()
        ?: substringBefore('?').substringBefore('#').substringAfterLast('/')
    return path
        .substringBeforeLast('.', path)
        .replace('_', ' ')
        .replace('-', ' ')
        .trim()
        .takeIf { it.isNotBlank() }
        ?: "Subtitles"
}

internal fun String.rewriteKnownSiteHost(siteBaseUrl: String): String {
    val targetOrigin = siteBaseUrl.urlOrigin() ?: siteBaseUrl.trimEnd('/')
    return runCatching {
        val uri = toUri()
        val path = uri.encodedPath.orEmpty()
        val query = uri.encodedQuery?.let { "?$it" }.orEmpty()
        val fragment = uri.encodedFragment?.let { "#$it" }.orEmpty()
        "$targetOrigin$path$query$fragment"
    }.getOrDefault(this)
}

internal fun String.isKodikIframeUrl(): Boolean {
    val host = runCatching { toUri().host.orEmpty() }.getOrDefault("")
    return host.equals("kodikplayer.com", ignoreCase = true) ||
        host.endsWith(".kodikplayer.com", ignoreCase = true)
}

internal fun String.isAksorIframeUrl(): Boolean {
    val uri = runCatching { toUri() }.getOrNull() ?: return false
    return uri.host.equals("player.aksor.tv", ignoreCase = true) &&
        uri.path.orEmpty().startsWith("/video/", ignoreCase = true)
}

internal fun String.isSibnetIframeUrl(): Boolean {
    val uri = runCatching { toUri() }.getOrNull() ?: return false
    return uri.host.equals("video.sibnet.ru", ignoreCase = true) &&
        uri.path.orEmpty().contains("shell.php", ignoreCase = true)
}

internal fun String.requiresRuntimePlayerDiscovery(): Boolean {
    val host = runCatching { toUri().host.orEmpty() }.getOrDefault("").lowercase()
    return "alloha" in host || "alloh" in host
}

internal fun String.detectVideoHeight(): Int? {
    val heights = buildList {
        this@detectVideoHeight.hlsSourceQualities().mapNotNull { it.height }.forEach(::add)
        VideoStreamResolver.dashHeightRegex.findAll(this@detectVideoHeight).forEach { match ->
            match.groupValues.getOrNull(1)?.toIntOrNull()?.let(::add)
        }
        VideoStreamResolver.qualityHeightRegex.findAll(this@detectVideoHeight).forEach { match ->
            match.groupValues.getOrNull(1)?.toIntOrNull()?.let(::add)
        }
    }
    return heights.mapNotNull { it.validVideoQualityHeight() }.maxOrNull()
}

internal fun maxOfOrNull(vararg values: Int?): Int? {
    return values.filterNotNull().maxOrNull()
}

internal fun String.normalizeVideoUrlAgainstBase(baseUrl: String, fallbackSiteBaseUrl: String): String {
    val value = trim()
    return when {
        value.startsWith("//") -> "https:$value"
        value.startsWith("/") -> "${baseUrl.urlOrigin() ?: fallbackSiteBaseUrl.trimEnd('/')}$value"
        value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true) -> value
        value.startsWith("blob:", ignoreCase = true) -> value
        else -> value.extractEmbeddedAbsoluteStreamUrl()
            ?: baseUrl.toHttpUrlOrNull()?.resolve(value)?.toString()
            ?: value
    }
}

internal fun String.extractEmbeddedAbsoluteStreamUrl(): String? {
    val normalized = replace("\\/", "/")
        .replace("&amp;", "&")
        .replace("\\u0026", "&")
        .trim()
    return VideoStreamResolver.embeddedAbsoluteStreamUrlRegex
        .find(normalized)
        ?.value
        ?.trim('"', '\'', ' ', '\\')
        ?.takeIf { it.isNotBlank() }
}

internal fun String.isCapturedPlaybackUrl(): Boolean {
    val lower = lowercase()
    return isDirectStreamUrl() &&
        "blank.mp4" !in lower &&
        "cdn.plyr.io" !in lower
}

internal fun String.isProgressivePlaybackUrl(): Boolean {
    val lower = substringBefore('?').substringBefore('#').lowercase()
    return lower.endsWith(".mp4")
}

internal fun String.isDirectStreamUrl(): Boolean {
    val lower = lowercase()
    return ".m3u8" in lower || ".mp4" in lower || ".mpd" in lower || lower.startsWith("blob:").not() && "#EXTM3U" in this
}

internal fun String.extractDirectStreamUrl(baseUrl: String): String? {
    return extractDirectStreamUrls(baseUrl).firstOrNull()
}

internal fun String.extractDirectStreamUrls(baseUrl: String): List<String> {
    val normalized = this
        .replace("\\/", "/")
        .replace("&amp;", "&")
        .replace("\\u0026", "&")

    return VideoStreamResolver.streamUrlRegex
        .findAll(normalized)
        .map { it.value.trim('"', '\'', ' ', '\\') }
        .map { it.normalizeVideoUrlAgainstBase(baseUrl, DEFAULT_SITE_BASE_URL) }
        .filter { it.isCapturedPlaybackUrl() }
        .distinct()
        .toList()
}

internal fun String.looksLikePlayerMetadataBody(): Boolean {
    val normalized = trimStart()
    if (normalized.startsWith("#EXTM3U", ignoreCase = true)) return true
    val sample = normalized.take(8192).lowercase()
    return ".m3u8" in sample ||
        ".mp4" in sample ||
        ".mpd" in sample ||
        "subtitle" in sample ||
        "subtitles" in sample ||
        "caption" in sample ||
        "captions" in sample ||
        "texttrack" in sample ||
        "texttracks" in sample
}

internal fun String.isHlsManifestBody(): Boolean {
    return trimStart().startsWith("#EXTM3U", ignoreCase = true)
}

internal fun String.isDashManifestBody(): Boolean {
    val normalized = trimStart()
    if (normalized.startsWith("<MPD", ignoreCase = true)) return true
    if (!normalized.startsWith("<", ignoreCase = true)) return false
    return "<MPD" in normalized.take(8192)
}

internal fun String.sha256Hex(): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
