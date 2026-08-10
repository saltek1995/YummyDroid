package me.yummydroid.app.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request

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
