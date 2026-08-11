package me.yummydroid.app.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLDecoder
import kotlin.math.abs
import okhttp3.OkHttpClient
import okhttp3.Request

// MaterializedSubtitleTiming
internal fun List<MaterializedSubtitleSegment>.shouldShiftWebVttCueTimes(): Boolean {
    val samples = filter { it.offsetMs > 0L }
        .mapNotNull { segment ->
            val firstCueStartMs = segment.body.firstWebVttCueStartMs() ?: return@mapNotNull null
            segment to firstCueStartMs
        }
    if (samples.isEmpty()) return false

    val localCueCount = samples.count { (segment, firstCueStartMs) ->
        val localWindowMs = maxOf(segment.durationMs + 5_000L, 60_000L)
        firstCueStartMs < localWindowMs && firstCueStartMs + 10_000L < segment.offsetMs
    }
    val absoluteCueCount = samples.count { (segment, firstCueStartMs) ->
        firstCueStartMs + 10_000L >= segment.offsetMs ||
            abs(firstCueStartMs - segment.offsetMs) <= segment.durationMs + 10_000L
    }

    return localCueCount > absoluteCueCount
}

internal fun MaterializedSubtitleSegment.normalizedWebVttCueBody(shiftBySegmentOffset: Boolean): String {
    val firstCueStartMs = body.firstWebVttCueStartMs()
    val mapLocalMs = localMapMs
    val timestampMapShiftMs = if (mapLocalMs != null && firstCueStartMs != null) {
        val localWindowMs = maxOf(durationMs + 5_000L, 60_000L)
        val cueLooksLocalToMap = abs(firstCueStartMs - mapLocalMs) <= localWindowMs ||
            firstCueStartMs < localWindowMs
        if (cueLooksLocalToMap) offsetMs - mapLocalMs else 0L
    } else {
        0L
    }
    val shiftMs = if (timestampMapShiftMs != 0L || mapLocalMs != null) {
        timestampMapShiftMs
    } else if (shiftBySegmentOffset) {
        offsetMs
    } else {
        0L
    }
    return body.shiftWebVttCueTimes(shiftMs)
}

private fun String.firstWebVttCueStartMs(): Long? {
    return lineSequence()
        .mapNotNull { line ->
            SubtitleParsingPatterns.webVttTiming
                .find(line.trim())
                ?.groupValues
                ?.getOrNull(1)
                ?.webVttTimestampMs()
        }
        .firstOrNull()
}

private fun String.shiftWebVttCueTimes(offsetMs: Long): String {
    if (offsetMs == 0L) return this
    return lineSequence().joinToString("\n") { line ->
        val match = SubtitleParsingPatterns.webVttTiming.find(line.trim()) ?: return@joinToString line
        val startMs = match.groupValues.getOrNull(1)?.webVttTimestampMs() ?: return@joinToString line
        val endMs = match.groupValues.getOrNull(2)?.webVttTimestampMs() ?: return@joinToString line
        val settings = match.groupValues.getOrNull(3).orEmpty()
        "${(startMs + offsetMs).toWebVttTimestamp()} --> ${(endMs + offsetMs).toWebVttTimestamp()}$settings"
    }
}

// SubtitleCacheFiles
internal fun File.subtitleTextOrNull(): String? {
    if (!isFile || length() <= 0L) return null
    return runCatching { readText(Charsets.UTF_8) }.getOrNull()
}

internal fun File.writeVerifiedSubtitleCacheFile(text: String, mimeType: String): Boolean {
    val directory = parentFile ?: return false
    if (!directory.exists() && !directory.mkdirs()) return false
    val bytes = text.toByteArray(Charsets.UTF_8)
    val tempFile = File(directory, "$name.${System.nanoTime()}.tmp")

    return runCatching {
        FileOutputStream(tempFile).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        check(tempFile.isFile && tempFile.length() == bytes.size.toLong())
        check(tempFile.hasSubtitleCues(mimeType = mimeType))
        if (exists() && !delete()) {
            check(!exists())
        }
        if (!tempFile.renameTo(this)) {
            tempFile.copyTo(this, overwrite = true)
            check(tempFile.delete() || !tempFile.exists())
        }
        isFile &&
            length() == bytes.size.toLong() &&
            readBytes().contentEquals(bytes) &&
            hasSubtitleCues(mimeType = mimeType)
    }.getOrElse {
        runCatching { tempFile.delete() }
        false
    }
}

internal fun File.hasSubtitleCues(mimeType: String? = null): Boolean {
    return subtitleTextOrNull()?.hasSubtitleCues(mimeType = mimeType, uri = name) == true
}

// SubtitleTrackClassifier
internal class SubtitleTrackClassifier(
    private val fallbackSiteBaseUrl: () -> String,
) {
    fun extractDirectTracks(body: String, baseUrl: String): List<ResolvedSubtitleTrack> {
        return subtitleUrlRegex
            .findAll(body)
            .mapNotNull { match ->
                match.value
                    .trim('"', '\'', ' ', '\\')
                    .let { value -> normalizeAgainst(value, baseUrl) }
                    .let(::directTrack)
            }
            .toList()
    }

    fun normalizeAgainst(value: String, baseUrl: String): String {
        return value.normalizeVideoUrlAgainstBase(baseUrl, fallbackSiteBaseUrl())
    }

    fun directTrack(url: String): ResolvedSubtitleTrack? {
        if (!url.isSubtitleUrl()) return null
        return ResolvedSubtitleTrack(
            uri = url,
            label = url.subtitleLabelFromUrl(),
            mimeType = url.subtitleMimeTypeFromUrl(),
        )
    }

    fun potentialTrack(
        url: String,
        label: String = "",
        language: String? = null,
    ): ResolvedSubtitleTrack? {
        if (!isResolvableCandidate(url)) return null
        return ResolvedSubtitleTrack(
            uri = url,
            label = label.takeIf(String::isNotBlank) ?: url.subtitleLabelFromUrl(),
            language = language?.takeIf(String::isNotBlank),
            mimeType = url.subtitleMimeTypeFromUrl(),
        )
    }

    fun isResolvableCandidate(url: String): Boolean {
        val value = url.trim()
        if (value.isBlank()) return false
        if (value.isSubtitleUrl()) return true
        return value.isUrlLike() && isPotentialRequestUrl(value)
    }

    fun isPotentialRequestUrl(value: String): Boolean {
        val lower = runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }
            .getOrDefault(value)
            .lowercase()
        return subtitleUrlMarkers.any(lower::contains)
    }

    fun isMetadataKey(value: String): Boolean {
        val lower = value.lowercase()
        return subtitleMetadataKeyMarkers.any(lower::contains) || lower in exactSubtitleMetadataKeys
    }

    fun isUrlKey(value: String): Boolean = value.lowercase() in subtitleUrlKeys

    fun isDescriptor(value: String): Boolean = value.trim().lowercase() in subtitleDescriptors

    fun looksLikeJsonPayload(value: String): Boolean {
        val trimmed = value.trim()
        return (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
            (trimmed.startsWith("[") && trimmed.endsWith("]"))
    }

    private fun String.isSubtitleUrl(): Boolean {
        val lower = substringBefore('?').substringBefore('#').lowercase()
        return subtitleExtensions.any(lower::endsWith)
    }

    private fun String.isUrlLike(): Boolean {
        val value = trim()
        if (value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)) {
            return true
        }
        if (value.startsWith("//") || value.startsWith("/")) return true
        return relativeSubtitleUrlRegex.matches(value)
    }

    private companion object {
        val subtitleUrlRegex = Regex(
            """(?:(?:https?:)?//|/)?[^"'\s<>\\]+?\.(?:vtt|srt|ass|ssa|ttml|dfxp)(?:\?[^"'\s<>\\]*)?""",
            RegexOption.IGNORE_CASE,
        )
        val relativeSubtitleUrlRegex = Regex(
            """^[\w.-]+\.(?:vtt|srt|ass|ssa|ttml|dfxp)(?:[?#].*)?$""",
            RegexOption.IGNORE_CASE,
        )
        val subtitleExtensions = setOf(".vtt", ".srt", ".ass", ".ssa", ".ttml", ".dfxp")
        val subtitleUrlMarkers = setOf(
            "subtitle",
            "subtitles",
            "caption",
            "captions",
            "texttrack",
            "texttracks",
            "/track",
            "track=",
            ".vtt",
            ".srt",
            ".ass",
            ".ssa",
            ".ttml",
            ".dfxp",
        )
        val subtitleMetadataKeyMarkers = setOf("subtitle", "caption")
        val exactSubtitleMetadataKeys = setOf("texttrack", "texttracks")
        val subtitleUrlKeys = setOf("src", "url", "file", "href", "path", "link", "track", "tracks")
        val subtitleDescriptors = setOf("subtitle", "subtitles", "caption", "captions", "sub", "subs", "texttrack")
    }
}

// SubtitleTrackMaterializer
internal class SubtitleTrackMaterializer(
    context: Context?,
    private val client: OkHttpClient,
    private val currentTimeMs: () -> Long = System::currentTimeMillis,
) {
    private val cacheDir = context?.applicationContext?.cacheDir

    fun validateTracks(
        tracks: List<ResolvedSubtitleTrack>,
        headers: Map<String, String>,
    ): List<ResolvedSubtitleTrack> {
        return tracks.mapNotNull { track -> track.validatedTrack(headers) }
            .normalizedSubtitleTracks()
    }

    fun materializeCapturedBody(
        url: String,
        contentType: String?,
        body: String,
    ): ResolvedSubtitleTrack? {
        val mimeType = contentType.subtitleMimeTypeFromContentType() ?: url.subtitleMimeTypeFromUrl()
        return materializeBody(
            track = ResolvedSubtitleTrack(
                uri = url,
                label = url.subtitleLabelFromUrl(),
                mimeType = mimeType,
            ),
            body = body,
        )
    }

    private fun ResolvedSubtitleTrack.validatedTrack(
        fallbackHeaders: Map<String, String>,
    ): ResolvedSubtitleTrack? {
        val subtitleHeaders = headers.ifEmpty { fallbackHeaders }
        return if (!uri.isHlsPlaylistUrl() && mimeType?.contains("mpegurl", ignoreCase = true) != true) {
            runCatching { materializeDirectTrack(this, subtitleHeaders) }.getOrNull()
        } else {
            runCatching { materializeHlsPlaylist(this, subtitleHeaders) }.getOrNull()
        }
    }

    private fun materializeDirectTrack(
        track: ResolvedSubtitleTrack,
        headers: Map<String, String>,
    ): ResolvedSubtitleTrack? {
        val body = when {
            track.uri.startsWith("file:", ignoreCase = true) -> {
                val path = runCatching { Uri.parse(track.uri).path }.getOrNull() ?: return null
                File(path).subtitleTextOrNull() ?: return null
            }
            track.uri.startsWith("content:", ignoreCase = true) -> return track
            else -> getText(track.uri, headers)
        }
        return materializeBody(track, body)
    }

    private fun materializeBody(
        track: ResolvedSubtitleTrack,
        body: String,
    ): ResolvedSubtitleTrack? {
        if (body.looksLikeStandaloneHlsWebVttSegment()) return null
        val playable = body.toPlayableSubtitleBody(mimeType = track.mimeType, uri = track.uri) ?: return null
        val outputFile = cacheDir?.let { subtitleCacheFile(it, track.uri, playable.fileExtension) }
        if (outputFile?.isFreshSubtitleCacheFile() == true) {
            if (outputFile.hasSubtitleCues(mimeType = playable.mimeType)) {
                return track.withSubtitleCacheFile(outputFile, playable.mimeType)
            }
            runCatching { outputFile.delete() }
        }
        return cachePlayableTrack(track, playable, outputFile)
    }

    private fun materializeHlsPlaylist(
        track: ResolvedSubtitleTrack,
        headers: Map<String, String>,
    ): ResolvedSubtitleTrack? {
        val outputFile = cacheDir?.let { subtitleCacheFile(it, track.uri, "vtt") }
        if (outputFile?.isFreshSubtitleCacheFile() == true) {
            if (outputFile.hasSubtitleCues(mimeType = "text/vtt")) {
                return track.withSubtitleCacheFile(outputFile, "text/vtt")
            }
            runCatching { outputFile.delete() }
        }

        val playlist = getText(track.uri, headers)
        val assembledBody = assembleHlsSubtitleBody(
            playlist = playlist,
            playlistUrl = track.uri,
            loadSegment = { url -> getText(url, headers) },
        ) ?: return null
        val playable = assembledBody.toPlayableSubtitleBody(
            mimeType = "text/vtt",
            uri = track.uri,
        ) ?: return null
        return cachePlayableTrack(track, playable, outputFile)
    }

    private fun cachePlayableTrack(
        track: ResolvedSubtitleTrack,
        playable: PlayableSubtitleBody,
        outputFile: File?,
    ): ResolvedSubtitleTrack? {
        if (outputFile == null) return track.copy(mimeType = playable.mimeType)
        outputFile.parentFile?.mkdirs()
        cleanupOldSubtitleFiles(outputFile.parentFile)
        if (!outputFile.writeVerifiedSubtitleCacheFile(playable.text, playable.mimeType)) return null
        return track.withSubtitleCacheFile(outputFile, playable.mimeType)
    }

    private fun getText(url: String, headers: Map<String, String>): String {
        val request = Request.Builder()
            .url(url)
            .headers(headers.toOkHttpHeaders())
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful || body.isBlank()) {
                throw IOException("Player returned HTTP ${response.code}")
            }
            return body
        }
    }

    private fun subtitleCacheFile(cacheDir: File, sourceUri: String, extension: String): File {
        val safeExtension = extension
            .trim()
            .trimStart('.')
            .lowercase()
            .takeIf { it.isNotBlank() }
            ?: "vtt"
        return File(
            File(cacheDir, SUBTITLE_CACHE_DIR),
            "$SUBTITLE_CACHE_FILE_PREFIX${sourceUri.sha256Hex()}.$safeExtension",
        )
    }

    private fun File.isFreshSubtitleCacheFile(): Boolean {
        if (!isFile || length() <= WEBVTT_HEADER_MIN_BYTES) return false
        return currentTimeMs() - lastModified() <= SUBTITLE_CACHE_TTL_MS
    }

    private fun cleanupOldSubtitleFiles(directory: File?) {
        val now = currentTimeMs()
        directory
            ?.listFiles { file -> file.isFile && file.name.startsWith(SUBTITLE_CACHE_FILE_PREFIX) }
            ?.forEach { file ->
                if (now - file.lastModified() > SUBTITLE_CACHE_TTL_MS) {
                    runCatching { file.delete() }
                }
            }
    }

    private fun ResolvedSubtitleTrack.withSubtitleCacheFile(
        file: File,
        mimeType: String,
    ): ResolvedSubtitleTrack {
        return copy(
            uri = Uri.fromFile(file).toString(),
            label = label.ifBlank {
                file.nameWithoutExtension
                    .takeUnless { it.startsWith(SUBTITLE_CACHE_FILE_PREFIX) }
                    .orEmpty()
            },
            mimeType = mimeType,
            headers = emptyMap(),
        )
    }

    private companion object {
        const val SUBTITLE_CACHE_DIR = "subtitle_streams"
        const val SUBTITLE_CACHE_FILE_PREFIX = "subtitle_"
        const val SUBTITLE_CACHE_TTL_MS = 6L * 60L * 60L * 1000L
        const val WEBVTT_HEADER_MIN_BYTES = 8L
    }
}

// SubtitleTrackModels
private val GENERIC_SUBTITLE_TRACK_PATTERN =
    Regex("""(?:sub|subs|subtitle|subtitles|caption|captions)\s*[a-z]{2,3}\s*\d*""")
private val GENERIC_SUBTITLE_LABELS = setOf("subtitles", "subtitle", "captions", "caption")

data class ResolvedSubtitleTrack(
    val uri: String,
    val label: String = "",
    val language: String? = null,
    val mimeType: String? = null,
    val headers: Map<String, String> = emptyMap(),
)

data class ResolvedEmbeddedSubtitleTrack(
    val id: String = "",
    val label: String = "",
    val language: String? = null,
)

fun List<ResolvedEmbeddedSubtitleTrack>.normalizedEmbeddedSubtitleTracks(): List<ResolvedEmbeddedSubtitleTrack> {
    return asSequence()
        .map(ResolvedEmbeddedSubtitleTrack::trimmed)
        .filter(ResolvedEmbeddedSubtitleTrack::hasIdentity)
        .groupBy(ResolvedEmbeddedSubtitleTrack::identityKey)
        .values
        .map(::mergeEmbeddedSubtitleTracks)
        .toList()
}

fun List<ResolvedSubtitleTrack>.normalizedSubtitleTracks(): List<ResolvedSubtitleTrack> {
    return asSequence()
        .filter { it.uri.isNotBlank() }
        .groupBy { it.uri.trim().lowercase() }
        .values
        .map(::mergeSubtitleTracks)
        .toList()
}

private fun ResolvedEmbeddedSubtitleTrack.trimmed(): ResolvedEmbeddedSubtitleTrack {
    return copy(
        id = id.trim(),
        label = label.trim(),
        language = language?.trim()?.takeIf { it.isNotBlank() },
    )
}

private fun ResolvedEmbeddedSubtitleTrack.hasIdentity(): Boolean {
    return id.isNotBlank() || label.isNotBlank() || language.orEmpty().isNotBlank()
}

private fun ResolvedEmbeddedSubtitleTrack.identityKey(): String {
    return id.takeIf { it.isNotBlank() }?.lowercase()
        ?: listOf(label.lowercase(), language.orEmpty().lowercase()).joinToString(":")
}

private fun mergeEmbeddedSubtitleTracks(
    tracks: List<ResolvedEmbeddedSubtitleTrack>,
): ResolvedEmbeddedSubtitleTrack {
    val metadata = tracks.maxWithOrNull(
        compareBy<ResolvedEmbeddedSubtitleTrack> { it.label.subtitleLabelScore() }
            .thenBy { if (it.language.orEmpty().isNotBlank()) 1 else 0 }
            .thenBy { if (it.id.isNotBlank()) 1 else 0 },
    ) ?: tracks.first()
    return metadata.copy(
        id = tracks.firstOrNull { it.id.isNotBlank() }?.id.orEmpty(),
        label = metadata.label.takeIf { it.isNotBlank() }.orEmpty(),
        language = metadata.language?.takeIf { it.isNotBlank() }
            ?: tracks.firstOrNull { it.language.orEmpty().isNotBlank() }?.language,
    )
}

private fun mergeSubtitleTracks(tracks: List<ResolvedSubtitleTrack>): ResolvedSubtitleTrack {
    val source = tracks.firstOrNull { it.uri.startsWith("file:", ignoreCase = true) }
        ?: tracks.firstOrNull { it.headers.isNotEmpty() }
        ?: tracks.first()
    val metadata = tracks.maxWithOrNull(
        compareBy<ResolvedSubtitleTrack> { it.label.subtitleLabelScore() }
            .thenBy { if (it.language.orEmpty().isNotBlank()) 1 else 0 }
            .thenBy { if (it.mimeType.orEmpty().isNotBlank()) 1 else 0 },
    )
    return source.copy(
        label = metadata?.label?.takeIf { it.isNotBlank() }.orEmpty(),
        language = metadata?.language?.takeIf { it.isNotBlank() } ?: source.language,
        mimeType = source.mimeType ?: metadata?.mimeType,
    )
}

private fun String.subtitleLabelScore(): Int {
    val normalized = trim().lowercase()
    return when {
        normalized.isOpaqueSubtitleLabel() -> 0
        GENERIC_SUBTITLE_TRACK_PATTERN.matches(normalized) -> 1
        normalized in GENERIC_SUBTITLE_LABELS -> 2
        else -> 3
    }
}

private fun String.isOpaqueSubtitleLabel(): Boolean {
    return isBlank() || all(Char::isDigit) || isShortHexToken() || isGeneratedSubtitleToken()
}

private fun String.isShortHexToken(): Boolean {
    if (length !in 4..16) return false
    if (!all { it in '0'..'9' || it in 'a'..'f' }) return false
    return any(Char::isDigit) && any { it in 'a'..'f' }
}

private fun String.isGeneratedSubtitleToken(): Boolean {
    if (!startsWith("subtitle_") || length < 24) return false
    return removePrefix("subtitle_").all { it in '0'..'9' || it in 'a'..'f' }
}
