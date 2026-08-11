package me.yummydroid.app.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

// AllohaRuntimeStreams
internal fun String.extractAllohaRuntimeStreams(baseUrl: String): List<AllohaRuntimeStream> {
    val payload = runCatching { VIDEO_RESOLVER_JSON.parseToJsonElement(this) as? JsonObject }.getOrNull()
        ?: return emptyList()
    val sources = payload["hlsSource"] as? JsonArray ?: return emptyList()
    return sources
        .flatMap sourceMap@ { source ->
            val qualities = (source as? JsonObject)?.get("quality") as? JsonObject
                ?: return@sourceMap emptyList()
            qualities.flatMap qualityMap@ { (qualityLabel, value) ->
                val height = qualityLabel.filter(Char::isDigit).toIntOrNull()
                    ?: return@qualityMap emptyList()
                (value as? JsonPrimitive)
                    ?.contentOrNull
                    ?.extractDirectStreamUrls(baseUrl)
                    .orEmpty()
                    .mapIndexed { mirrorIndex, url ->
                        AllohaRuntimeStream(url = url, height = height, mirrorIndex = mirrorIndex)
                    }
            }
        }
        .distinctBy { it.url }
}

internal fun List<AllohaRuntimeStream>.sortedForPreferredQuality(
    preferredQuality: PreferredQuality,
): List<AllohaRuntimeStream> {
    val remaining = toMutableList()
    val sorted = mutableListOf<AllohaRuntimeStream>()
    while (remaining.isNotEmpty()) {
        val selected = remaining.selectForPreferredQuality(
            preferredQuality = preferredQuality,
            height = AllohaRuntimeStream::height,
            priority = { -it.mirrorIndex },
        ) ?: break
        sorted += selected
        remaining.remove(selected)
    }
    return sorted.distinctBy { it.url }
}

// GenericStreamResolver
internal class GenericStreamResolver(
    private val client: OkHttpClient,
    private val playbackRequestHeaders: PlaybackRequestHeaders,
    private val subtitleMetadataParser: SubtitleMetadataParser,
    private val runtimeStreamResolver: RuntimeStreamResolver,
) {
    suspend fun resolve(
        sourceUrl: String,
        siteBaseUrl: String,
        preferredQuality: PreferredQuality,
        waitForRuntimeSubtitles: Boolean,
    ): ResolvedVideoStream {
        if (sourceUrl.isDirectStreamUrl()) {
            return directStream(sourceUrl, siteBaseUrl)
        }
        if (sourceUrl.requiresRuntimePlayerDiscovery()) {
            return runtimeStreamResolver.resolve(
                sourceUrl,
                siteBaseUrl,
                preferredQuality,
                waitForRuntimeSubtitles,
            )
        }

        val request = Request.Builder()
            .url(sourceUrl)
            .headers(playbackRequestHeaders.iframe(sourceUrl, siteBaseUrl).toOkHttpHeaders())
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Player returned HTTP ${response.code}")
            }
            if (body.isHlsManifestBody()) {
                return hlsManifestStream(sourceUrl, siteBaseUrl, body)
            }
            body.extractDirectStreamUrl(sourceUrl)?.let { streamUrl ->
                return staticStream(sourceUrl, siteBaseUrl, streamUrl, body)
            }
        }
        return runtimeStreamResolver.resolve(
            sourceUrl,
            siteBaseUrl,
            preferredQuality,
            waitForRuntimeSubtitles,
        )
    }

    private fun directStream(sourceUrl: String, siteBaseUrl: String): ResolvedVideoStream {
        return ResolvedVideoStream(
            url = sourceUrl,
            mimeType = sourceUrl.mimeTypeFromUrl(),
            headers = playbackRequestHeaders.playback(sourceUrl, sourceUrl, siteBaseUrl),
            maxVideoHeight = sourceUrl.detectVideoHeight(),
            availableQualities = sourceUrl.detectSourceQualities(),
            subtitles = listOfNotNull(subtitleMetadataParser.directTrack(sourceUrl)).normalizedSubtitleTracks(),
        )
    }

    private fun hlsManifestStream(
        sourceUrl: String,
        siteBaseUrl: String,
        body: String,
    ): ResolvedVideoStream {
        val subtitles = subtitleMetadataParser.extractHlsTracks(body, sourceUrl)
        return ResolvedVideoStream(
            url = sourceUrl,
            mimeType = "application/x-mpegURL",
            headers = playbackRequestHeaders.playback(sourceUrl, sourceUrl, siteBaseUrl),
            maxVideoHeight = body.detectVideoHeight(),
            availableQualities = body.detectSourceQualities(),
            subtitles = subtitles.tracks,
            embeddedSubtitles = subtitles.embeddedSubtitles,
            hasEmbeddedSubtitles = subtitles.hasEmbeddedSubtitles,
        )
    }

    private fun staticStream(
        sourceUrl: String,
        siteBaseUrl: String,
        streamUrl: String,
        body: String,
    ): ResolvedVideoStream {
        return ResolvedVideoStream(
            url = streamUrl,
            mimeType = streamUrl.mimeTypeFromUrl(),
            headers = playbackRequestHeaders.playback(streamUrl, sourceUrl, siteBaseUrl),
            maxVideoHeight = maxOfOrNull(body.detectVideoHeight(), streamUrl.detectVideoHeight()),
            availableQualities = (body.detectSourceQualities() + streamUrl.detectSourceQualities())
                .normalizedSourceQualities(),
            subtitles = subtitleMetadataParser.extractTracks(body, sourceUrl),
        )
    }

}

// ProviderStreamResolver
internal class ProviderStreamResolver(
    private val client: OkHttpClient,
    private val playbackRequestHeaders: PlaybackRequestHeaders,
    private val subtitleMetadataParser: SubtitleMetadataParser,
    private val fallbackSiteBaseUrl: () -> String,
    private val json: Json,
) {
    fun resolveKodik(
        sourceUrl: String,
        siteBaseUrl: String,
        preferredQuality: PreferredQuality,
    ): ResolvedVideoStream {
        val html = getText(sourceUrl, playbackRequestHeaders.iframe(sourceUrl, siteBaseUrl))
        val params = html.kodikParams()
        val form = FormBody.Builder()
            .add("d", params.domain)
            .add("d_sign", params.domainSign)
            .add("pd", params.playerDomain)
            .add("pd_sign", params.playerDomainSign)
            .add("ref", params.referer)
            .add("ref_sign", params.refererSign)
            .add("bad_user", "false")
            .add("cdn_is_working", "true")
            .add("type", params.type)
            .add("hash", params.hash)
            .add("id", params.id)
            .build()
        val request = Request.Builder()
            .url(KODIK_FTOR_URL)
            .headers(playbackRequestHeaders.kodikApi(sourceUrl).toOkHttpHeaders())
            .post(form)
            .build()

        val body = client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful || text.isBlank()) {
                throw IOException("Kodik API returned HTTP ${response.code}")
            }
            text
        }
        val dto = json.decodeFromString<KodikFtorDto>(body)
        val stream = dto.bestStream(preferredQuality)
            ?: throw IOException("Kodik: HLS/MP4/DASH stream was not found")

        return ResolvedVideoStream(
            url = stream.url,
            mimeType = stream.mimeType ?: stream.url.mimeTypeFromKodikUrl(),
            headers = playbackRequestHeaders.kodikPlayback(stream.url),
            maxVideoHeight = maxOfOrNull(stream.height, stream.url.detectVideoHeight()),
            availableQualities = (dto.availableQualities() + stream.url.detectSourceQualities())
                .normalizedSourceQualities(),
            subtitles = subtitleMetadataParser.extractTracks(body, sourceUrl),
        )
    }

    fun resolveAksor(
        sourceUrl: String,
        siteBaseUrl: String,
        preferredQuality: PreferredQuality,
    ): ResolvedVideoStream {
        val videoId = sourceUrl.toHttpUrlOrNull()
            ?.pathSegments
            ?.lastOrNull { it.isNotBlank() }
            ?: throw IOException("Aksor: missing video id")
        val origin = sourceUrl.urlOrigin() ?: AKSOR_ORIGIN
        val video = getJson<AksorVideoDto>(
            url = "$origin/api/video/$videoId",
            headers = playbackRequestHeaders.aksorApi(sourceUrl),
            providerName = "Aksor",
        )
        val stream = video.bestStream(preferredQuality)
            ?: throw IOException("Aksor: stream is unavailable")
        val streamUrl = stream.url.normalizeAgainst(sourceUrl)

        return ResolvedVideoStream(
            url = streamUrl,
            mimeType = streamUrl.mimeTypeFromUrl(),
            headers = playbackRequestHeaders.playback(streamUrl, sourceUrl, siteBaseUrl),
            maxVideoHeight = maxOfOrNull(stream.height, streamUrl.detectVideoHeight()),
            availableQualities = (video.qualities.availableQualities() + streamUrl.detectSourceQualities())
                .normalizedSourceQualities(),
        )
    }

    fun resolveSibnet(sourceUrl: String, siteBaseUrl: String): ResolvedVideoStream {
        val html = getText(sourceUrl, playbackRequestHeaders.iframe(sourceUrl, siteBaseUrl))
        val streamUrl = html.extractSibnetStreamUrl(sourceUrl)
            ?: html.extractDirectStreamUrl(sourceUrl)
            ?: throw IOException("Sibnet: HLS/MP4/DASH stream was not found")

        return ResolvedVideoStream(
            url = streamUrl,
            mimeType = streamUrl.mimeTypeFromUrl(),
            headers = playbackRequestHeaders.playback(streamUrl, sourceUrl, siteBaseUrl),
            maxVideoHeight = streamUrl.detectVideoHeight(),
        )
    }

    fun resolveCvh(
        sourceUrl: String,
        video: VideoVariant,
        siteBaseUrl: String,
        preferredQuality: PreferredQuality,
    ): ResolvedVideoStream {
        val iframeUrl = sourceUrl.toHttpUrlOrNull()
            ?: throw IOException("CVH: invalid iframe URL")
        val titleId = iframeUrl.queryParameter("anime_id")?.takeIf { it.isNotBlank() }
            ?: throw IOException("CVH: anime_id was not found in iframe")
        val episode = iframeUrl.queryParameter("episode")?.toIntOrNull()
            ?: video.episode.toIntOrNull()
            ?: 1
        val season = iframeUrl.queryParameter("season")?.toIntOrNull()
        val priorityVoices = buildCvhVoiceCandidates(iframeUrl, video)

        val playlistUrl = CVH_PLAYLIST_URL.newBuilder()
            .addQueryParameter("pub", CVH_PUBLISHER_ID)
            .addQueryParameter("id", titleId)
            .addQueryParameter("aggr", CVH_AGGREGATOR)
            .build()
            .toString()
        val playlist = getJson<CvhPlaylistDto>(
            url = playlistUrl,
            headers = playbackRequestHeaders.cvhApi(sourceUrl),
            providerName = "CVH",
        )
        val selectedVideo = playlist.items.selectCvhItem(
            season = season,
            episode = episode,
            priorityVoices = priorityVoices,
        ) ?: throw IOException(
            "CVH: voice is unavailable for episode $episode: ${priorityVoices.firstOrNull().orEmpty()}",
        )

        val vkId = selectedVideo.vkId.takeIf { it.isNotBlank() }
            ?: throw IOException("CVH: episode has no vkId")
        val videoUrl = "$CVH_VIDEO_URL/$vkId"
        val cvhVideo = getJson<CvhVideoDto>(
            url = videoUrl,
            headers = playbackRequestHeaders.cvhApi(sourceUrl),
            providerName = "CVH",
        )
        val source = cvhVideo.sources?.bestStream(preferredQuality)
            ?: throw IOException("CVH: HLS/DASH/MP4 stream was not found")

        val selectedHeight = maxOfOrNull(source.height, source.url.detectVideoHeight())
        return ResolvedVideoStream(
            url = source.url,
            mimeType = source.mimeType,
            headers = playbackRequestHeaders.cvhPlayback(source.url, sourceUrl, siteBaseUrl),
            maxVideoHeight = selectedHeight,
            availableQualities = (cvhVideo.sources?.availableQualities().orEmpty() + source.url.detectSourceQualities())
                .normalizedSourceQualities(),
            selectedVideoHeight = selectedHeight,
        )
    }

    fun getText(url: String, headers: Map<String, String>): String {
        return readRequiredResponseBody(url, headers) { code -> "Player returned HTTP $code" }
    }

    private inline fun <reified T> getJson(
        url: String,
        headers: Map<String, String>,
        providerName: String,
    ): T {
        val body = readRequiredResponseBody(url, headers) { code -> "$providerName API returned HTTP $code" }
        return json.decodeFromString(body)
    }

    private fun readRequiredResponseBody(
        url: String,
        headers: Map<String, String>,
        errorMessage: (Int) -> String,
    ): String {
        val request = Request.Builder()
            .url(url)
            .headers(headers.toOkHttpHeaders())
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful || body.isBlank()) {
                throw IOException(errorMessage(response.code))
            }
            return body
        }
    }

    private fun String.extractSibnetStreamUrl(baseUrl: String): String? {
        val normalized = replace("\\/", "/")
            .replace("&amp;", "&")
            .replace("\\u0026", "&")
        return SIBNET_PLAYER_SOURCE_REGEX
            .find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim('"', '\'', ' ', '\\')
            ?.normalizeAgainst(baseUrl)
            ?.takeIf { it.isCapturedPlaybackUrl() }
    }

    private fun String.normalizeAgainst(baseUrl: String): String {
        return normalizeVideoUrlAgainstBase(baseUrl, fallbackSiteBaseUrl())
    }

    private companion object {
        const val CVH_PUBLISHER_ID = "745"
        const val CVH_AGGREGATOR = "mali"
        const val CVH_VIDEO_URL = "https://plapi.cdnvideohub.com/api/v1/player/sv/video"
        val CVH_PLAYLIST_URL = "https://plapi.cdnvideohub.com/api/v1/player/sv/playlist".toHttpUrl()
        const val KODIK_FTOR_URL = "https://kodikplayer.com/ftor"
        const val AKSOR_ORIGIN = "https://player.aksor.tv"
        val SIBNET_PLAYER_SOURCE_REGEX = Regex(
            """src\s*:\s*["']([^"']+\.(?:m3u8|mp4|mpd)(?:\?[^"']*)?)["']""",
            RegexOption.IGNORE_CASE,
        )
    }
}

// ResolvedStreamPostProcessor
internal class ResolvedStreamPostProcessor(
    private val client: OkHttpClient,
    private val subtitleMetadataParser: SubtitleMetadataParser,
    private val subtitleTrackMaterializer: SubtitleTrackMaterializer,
) {
    fun process(stream: ResolvedVideoStream): ResolvedVideoStream {
        return stream.withFirstPlayableUrl().withDetectedSourceMetadata()
    }

    private fun ResolvedVideoStream.withFirstPlayableUrl(): ResolvedVideoStream {
        if (skipPlaybackProbe) {
            return copy(
                mimeType = url.mimeTypeFromUrl() ?: mimeType,
                maxVideoHeight = maxOfOrNull(maxVideoHeight, url.detectVideoHeight()),
            )
        }
        val candidates = (listOf(url) + fallbackUrls)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
        var lastFailure: Throwable? = null
        candidates.forEach { candidateUrl ->
            val candidate = copy(
                url = candidateUrl,
                mimeType = candidateUrl.mimeTypeFromUrl() ?: mimeType,
                maxVideoHeight = maxOfOrNull(maxVideoHeight, candidateUrl.detectVideoHeight()),
                fallbackUrls = candidates.filterNot { it == candidateUrl },
            )
            runCatching { validatePlayableStream(candidate) }
                .onSuccess { return candidate }
                .onFailure { throwable -> lastFailure = throwable }
        }
        throw lastFailure ?: IOException("Player did not return a video URL")
    }

    private fun validatePlayableStream(stream: ResolvedVideoStream) {
        val url = stream.url.takeIf(String::isNotBlank)
            ?: throw IOException("Player did not return a video URL")
        if (url.startsWith("blob:", ignoreCase = true)) {
            throw IOException("Player returned a blob stream that native playback cannot use")
        }
        val requestBuilder = Request.Builder()
            .url(url)
            .headers(stream.headers.toOkHttpHeaders())
        if (!stream.looksLikeAdaptiveManifest()) requestBuilder.header("Range", "bytes=0-4095")
        client.newCall(requestBuilder.build()).execute().use { response ->
            if (response.code !in listOf(200, 206)) {
                throw IOException("Source returned HTTP ${response.code}")
            }
            val contentType = response.header("Content-Type").orEmpty()
            val bodyPrefix = response.body?.source()?.use { source ->
                source.request(512)
                source.buffer.clone().readUtf8().take(512)
            }.orEmpty()
            if (!streamResponseLooksPlayable(stream, contentType, bodyPrefix)) {
                throw IOException("Source does not look like an HLS/DASH/MP4 stream")
            }
        }
    }

    private fun streamResponseLooksPlayable(
        stream: ResolvedVideoStream,
        contentType: String,
        bodyPrefix: String,
    ): Boolean {
        val metadata = listOf(stream.mimeType.orEmpty(), contentType)
        return metadata.any { value ->
            value.contains("mpegURL", ignoreCase = true) ||
                value.contains("dash", ignoreCase = true) ||
                value.contains("video", ignoreCase = true)
        } || bodyPrefix.trimStart().startsWith("#EXTM3U")
    }

    private fun ResolvedVideoStream.withDetectedSourceMetadata(): ResolvedVideoStream {
        val manifestText = if (skipPlaybackProbe) null else loadAdaptiveManifestTextOrNull()
        val detectedQualities = detectSourceQualities(manifestText)
        val detectedHeight = detectedQualities.mapNotNull(SourceQuality::height).maxOrNull()
        val resolvedHeight = maxOfOrNull(maxVideoHeight, detectedHeight, url.detectVideoHeight())
        val resolvedQualities = (
            availableQualities +
                detectedQualities +
                listOfNotNull(resolvedHeight?.let { SourceQuality(height = it) })
            ).normalizedSourceQualities()
        val detectedSubtitles = detectSubtitleTracks(manifestText)
        return copy(
            maxVideoHeight = resolvedHeight,
            availableQualities = resolvedQualities,
            subtitles = subtitleTrackMaterializer.validateTracks(subtitles + detectedSubtitles.tracks, headers),
            embeddedSubtitles = (embeddedSubtitles + detectedSubtitles.embeddedSubtitles)
                .normalizedEmbeddedSubtitleTracks(),
            hasEmbeddedSubtitles = hasEmbeddedSubtitles ||
                detectedSubtitles.hasEmbeddedSubtitles ||
                detectedSubtitles.embeddedSubtitles.isNotEmpty(),
        )
    }

    private fun ResolvedVideoStream.detectSourceQualities(manifestText: String?): List<SourceQuality> {
        val urlHeight = url.detectVideoHeight()
        if (!looksLikeAdaptiveManifest()) {
            return listOfNotNull(urlHeight?.let { SourceQuality(height = it) })
        }
        val manifestQualities = manifestText?.detectSourceQualities()
        return (manifestQualities.orEmpty() + listOfNotNull(urlHeight?.let { SourceQuality(height = it) }))
            .normalizedSourceQualities()
    }

    private fun ResolvedVideoStream.detectSubtitleTracks(manifestText: String?): SubtitleDetection {
        val directTrack = subtitleMetadataParser.directTrack(url)
        if (!looksLikeAdaptiveManifest() || manifestText == null) {
            return SubtitleDetection(
                tracks = listOfNotNull(directTrack).normalizedSubtitleTracks(),
                embeddedSubtitles = emptyList(),
                hasEmbeddedSubtitles = false,
            )
        }
        val hlsSubtitles = subtitleMetadataParser.extractHlsTracks(manifestText, url)
        val dashEmbeddedSubtitles = if (manifestText.isDashManifestBody()) {
            subtitleMetadataParser.extractDashEmbeddedTracks(manifestText)
        } else {
            emptyList()
        }
        return SubtitleDetection(
            tracks = (
                listOfNotNull(directTrack) +
                    hlsSubtitles.tracks +
                    subtitleMetadataParser.extractTracks(manifestText, url)
                ).normalizedSubtitleTracks(),
            embeddedSubtitles = (hlsSubtitles.embeddedSubtitles + dashEmbeddedSubtitles)
                .normalizedEmbeddedSubtitleTracks(),
            hasEmbeddedSubtitles = hlsSubtitles.hasEmbeddedSubtitles || dashEmbeddedSubtitles.isNotEmpty(),
        )
    }

    private fun ResolvedVideoStream.loadAdaptiveManifestTextOrNull(): String? {
        if (!looksLikeAdaptiveManifest()) return null
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .headers(headers.toOkHttpHeaders())
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code !in listOf(200, 206)) return@use null
                response.body?.string()
            }
        }.getOrNull()
    }

    private fun ResolvedVideoStream.looksLikeAdaptiveManifest(): Boolean {
        val lowerUrl = url.lowercase()
        val lowerMimeType = mimeType.orEmpty().lowercase()
        return ".m3u8" in lowerUrl ||
            ".mpd" in lowerUrl ||
            "mpegurl" in lowerMimeType ||
            "dash" in lowerMimeType
    }
}

// RuntimeStreamResolver
internal interface RuntimeStreamResolver {
    suspend fun resolve(
        sourceUrl: String,
        siteBaseUrl: String,
        preferredQuality: PreferredQuality,
        waitForRuntimeSubtitles: Boolean,
    ): ResolvedVideoStream
}

// SiteDomainResolver
class SiteDomainResolver(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(8, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build(),
    candidates: List<String> = DEFAULT_SITE_DOMAINS,
) {
    @Volatile
    private var candidates: List<String> = candidates.normalizedSiteBaseUrls().ifEmpty { DEFAULT_SITE_DOMAINS }

    @Volatile
    private var knownSiteHosts: Set<String> = candidates.knownSiteHosts()

    @Volatile
    private var cachedBaseUrl: String? = null

    @Volatile
    private var checkedAtMs: Long = 0L

    suspend fun activeBaseUrl(): String = withContext(Dispatchers.IO) {
        activeBaseUrlBlocking()
    }

    suspend fun checkReachableBaseUrl(): String? = withContext(Dispatchers.IO) {
        candidates.firstOrNull(::isReachable)?.also { baseUrl ->
            cachedBaseUrl = baseUrl
            checkedAtMs = System.currentTimeMillis()
        }
    }

    suspend fun orderedBaseUrlsFor(rawUrl: String): List<String> = withContext(Dispatchers.IO) {
        val active = activeBaseUrlBlocking()
        if (!rawUrl.isSiteRelativeOrKnownHost()) {
            return@withContext listOf(active)
        }
        (listOf(active) + candidates).distinct()
    }

    fun cachedOrDefaultBaseUrl(): String = cachedBaseUrl ?: candidates.first()

    fun updateCandidates(rawCandidates: List<String>) {
        val updatedCandidates = rawCandidates.normalizedSiteBaseUrls().ifEmpty { DEFAULT_SITE_DOMAINS }
        candidates = updatedCandidates
        knownSiteHosts = updatedCandidates.knownSiteHosts()
        if (cachedBaseUrl?.let { cached -> updatedCandidates.any { it.sameUrlOrigin(cached) } } != true) {
            cachedBaseUrl = null
            checkedAtMs = 0L
        }
    }

    fun markAvailable(baseUrl: String) {
        cachedBaseUrl = baseUrl.toRootSiteBaseUrl()
        checkedAtMs = System.currentTimeMillis()
    }

    fun markUnavailable(baseUrl: String) {
        if (baseUrl.sameUrlOrigin(cachedBaseUrl)) {
            cachedBaseUrl = null
            checkedAtMs = 0L
        }
    }

    fun isKnownSiteHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        return host.lowercase() in knownSiteHosts
    }

    private fun activeBaseUrlBlocking(): String {
        val now = System.currentTimeMillis()
        cachedBaseUrl
            ?.takeIf { now - checkedAtMs < CACHE_TTL_MS }
            ?.let { return it }

        candidates.firstOrNull(::isReachable)?.let { baseUrl ->
            cachedBaseUrl = baseUrl
            checkedAtMs = now
            return baseUrl
        }

        cachedBaseUrl = candidates.first()
        checkedAtMs = now
        return candidates.first()
    }

    private fun isReachable(baseUrl: String): Boolean {
        return request(baseUrl, "HEAD") || request(baseUrl, "GET")
    }

    private fun request(baseUrl: String, method: String): Boolean {
        val request = Request.Builder()
            .url(baseUrl)
            .method(method, null)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                response.code in 200..499
            }
        }.getOrDefault(false)
    }

    private fun String.isSiteRelativeOrKnownHost(): Boolean {
        val value = trim()
        if (value.startsWith("/")) return true
        val host = runCatching { value.toHttpUrl().host }.getOrNull()
        return isKnownSiteHost(host)
    }

    companion object {
        private const val CACHE_TTL_MS = 5 * 60 * 1000L
        private const val USER_AGENT = BROWSER_USER_AGENT

        val DEFAULT_SITE_DOMAINS: List<String> = DEFAULT_YUMMY_SITE_DOMAINS
    }
}

fun normalizeSiteBaseUrl(rawUrl: String): String? {
    val trimmed = rawUrl.trim()
    if (trimmed.isBlank()) return null
    val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
    } else {
        "https://$trimmed"
    }
    return runCatching {
        withScheme.toHttpUrl()
            .newBuilder()
            .encodedPath("/")
            .query(null)
            .fragment(null)
            .build()
            .toString()
    }.getOrNull()
}

fun Iterable<String>.normalizedSiteBaseUrls(): List<String> {
    return mapNotNull(::normalizeSiteBaseUrl)
        .distinctBy { it.trimEnd('/').lowercase() }
}

private fun Iterable<String>.knownSiteHosts(): Set<String> {
    return (this + SiteDomainResolver.DEFAULT_SITE_DOMAINS)
        .mapNotNull { runCatching { it.toHttpUrl().host.lowercase() }.getOrNull() }
        .toSet()
}

// SiteUrlUtils
const val DEFAULT_SITE_BASE_URL = "https://old.yummyani.me/"
const val APP_USER_AGENT = "YummyDroid Android TV"
const val BROWSER_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 10; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36"

val DEFAULT_YUMMY_SITE_DOMAINS: List<String> = listOf(
    DEFAULT_SITE_BASE_URL,
    "https://ru.yummyani.me/",
    "https://yummyani.me/",
    "https://yummy-ani.me/",
    "https://old.yummy-ani.me/",
    "https://yummyani.meme/",
    "https://site.yummyani.me/",
    "https://en.yummyani.me/",
    "https://uk.yummyani.me/",
    "https://yummy-anime.ru/",
)

fun String.urlOrigin(): String? {
    val url = toHttpUrlOrNull() ?: return null
    val defaultPort = when (url.scheme) {
        "http" -> 80
        "https" -> 443
        else -> -1
    }
    val port = url.port
        .takeIf { it > 0 && it != defaultPort }
        ?.let { ":$it" }
        .orEmpty()
    return "${url.scheme}://${url.host}$port"
}

fun String.withTrailingSlash(): String {
    return if (endsWith("/")) this else "$this/"
}

fun String.toRootSiteBaseUrl(): String {
    return toHttpUrlOrNull()
        ?.newBuilder()
        ?.encodedPath("/")
        ?.query(null)
        ?.fragment(null)
        ?.build()
        ?.toString()
        ?: this
}

fun String.sameUrlOrigin(other: String?): Boolean {
    if (other.isNullOrBlank()) return false
    val first = toRootSiteBaseUrl().trimEnd('/')
    val second = other.toRootSiteBaseUrl().trimEnd('/')
    return first.equals(second, ignoreCase = true)
}

fun String.resolveUrlAgainst(baseUrl: String): String {
    val clean = trim().trim('"', '\'')
    return when {
        clean.startsWith("//") -> "https:$clean"
        clean.startsWith("http://", ignoreCase = true) ||
            clean.startsWith("https://", ignoreCase = true) ||
            clean.startsWith("blob:", ignoreCase = true) -> clean
        clean.startsWith("/") -> "${baseUrl.urlOrigin() ?: baseUrl.trimEnd('/')}$clean"
        else -> baseUrl.toHttpUrlOrNull()?.resolve(clean)?.toString() ?: clean
    }
}

// VideoResolverConfiguration
internal val VIDEO_RESOLVER_JSON = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

internal object VideoStreamPatterns {
    val streamUrl = Regex(
        """(?:(?:https?:)?//|/)[^"'\s<>\\]+?(?:\.m3u8|\.mp4|\.mpd)(?:\?[^"'\s<>\\]*)?""",
        RegexOption.IGNORE_CASE,
    )
    val embeddedAbsoluteStreamUrl = Regex(
        """https?://[^"'\s<>\\]+?(?:\.m3u8|\.mp4|\.mpd)(?:\?[^"'\s<>\\]*)?""",
        RegexOption.IGNORE_CASE,
    )
    val dashHeight = Regex("""(?i)\b(?:height|maxHeight)\s*=\s*["'](\d+)["']""")
    val qualityHeight = Regex(
        """(?i)(?:^|[^\d])(2160|1440|1080|720|576|540|480|360|240|144)p(?:[^\d]|$)""",
    )
}

internal object SubtitleParsingPatterns {
    val webVttTiming = Regex(
        """^(\d{2,}:\d{2}:\d{2}\.\d{3}|\d{2}:\d{2}\.\d{3})\s*-->\s*(\d{2,}:\d{2}:\d{2}\.\d{3}|\d{2}:\d{2}\.\d{3})(.*)$""",
    )
    val webVttTimestampMapLocal = Regex("""(?i)\bLOCAL:([^,\s]+)""")
    val timing = Regex(
        """^\s*(?:\d+\s+)?(?:\d{1,2}:)?\d{1,2}:\d{2}[,.]\d{1,3}\s*-->\s*(?:\d{1,2}:)?\d{1,2}:\d{2}[,.]\d{1,3}(?:\s+.*)?$""",
    )
    val timingLine = Regex(
        """^\s*(?:\d+\s+)?((?:\d{1,2}:)?\d{1,2}:\d{2}[,.]\d{1,3})\s*-->\s*((?:\d{1,2}:)?\d{1,2}:\d{2}[,.]\d{1,3})(.*)$""",
    )
    val ttmlParagraph = Regex(
        """<p\b[^>]*>(.*?)</p>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    val ttmlParagraphWithAttributes = Regex(
        """<p\b([^>]*)>(.*?)</p>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    val htmlTag = Regex("""<[^>]+>""")
    val htmlSpaceEntity = Regex("""&(?:nbsp|#160|#xA0);""", RegexOption.IGNORE_CASE)
    val assBlankEscape = Regex("""\\[Nnh]""")
    val xmlTimeAttribute = Regex("""\b([A-Za-z_:][\w:.-]*)\s*=\s*["']([^"']+)["']""")
}

// VideoStreamResolverFacade
class VideoStreamResolver(
    context: Context? = null,
    siteDomainResolver: SiteDomainResolver = SiteDomainResolver(),
    client: OkHttpClient = defaultVideoResolveClient(),
) {
    private val runtime = VideoStreamResolveRuntime(
        context = context,
        siteDomainResolver = siteDomainResolver,
        client = client,
    )

    suspend fun resolve(
        video: VideoVariant,
        preferredQuality: PreferredQuality = PreferredQuality.Auto,
        waitForRuntimeSubtitles: Boolean = true,
    ): ResolvedVideoStream {
        return runtime.resolve(video, preferredQuality, waitForRuntimeSubtitles)
    }
}

// VideoStreamResolveRuntime
internal class VideoStreamResolveRuntime(
    context: Context? = null,
    private val siteDomainResolver: SiteDomainResolver = SiteDomainResolver(),
    private val client: OkHttpClient = defaultVideoResolveClient(),
) {
    private val appContext = context?.applicationContext
    private val subtitleMetadataParser = SubtitleMetadataParser(
        fallbackSiteBaseUrl = siteDomainResolver::cachedOrDefaultBaseUrl,
        json = VIDEO_RESOLVER_JSON,
    )
    private val subtitleTrackMaterializer = SubtitleTrackMaterializer(appContext, client)
    private val playbackRequestHeaders = PlaybackRequestHeaders(siteDomainResolver::cachedOrDefaultBaseUrl)
    private val playerMetadataInspector = PlayerMetadataInspector(
        subtitleMetadataParser = subtitleMetadataParser,
        playbackRequestHeaders = playbackRequestHeaders,
        fallbackSiteBaseUrl = siteDomainResolver::cachedOrDefaultBaseUrl,
    )
    private val providerStreamResolver = ProviderStreamResolver(
        client = client,
        playbackRequestHeaders = playbackRequestHeaders,
        subtitleMetadataParser = subtitleMetadataParser,
        fallbackSiteBaseUrl = siteDomainResolver::cachedOrDefaultBaseUrl,
        json = VIDEO_RESOLVER_JSON,
    )
    private val webViewStreamResolver = WebViewStreamResolver(
        context = appContext,
        providerStreamResolver = providerStreamResolver,
        subtitleMetadataParser = subtitleMetadataParser,
        subtitleTrackMaterializer = subtitleTrackMaterializer,
        playbackRequestHeaders = playbackRequestHeaders,
        playerMetadataInspector = playerMetadataInspector,
        fallbackSiteBaseUrl = siteDomainResolver::cachedOrDefaultBaseUrl,
    )
    private val streamPostProcessor = ResolvedStreamPostProcessor(
        client = client,
        subtitleMetadataParser = subtitleMetadataParser,
        subtitleTrackMaterializer = subtitleTrackMaterializer,
    )
    private val genericStreamResolver = GenericStreamResolver(
        client = client,
        playbackRequestHeaders = playbackRequestHeaders,
        subtitleMetadataParser = subtitleMetadataParser,
        runtimeStreamResolver = webViewStreamResolver,
    )

    suspend fun resolve(
        video: VideoVariant,
        preferredQuality: PreferredQuality = PreferredQuality.Auto,
        waitForRuntimeSubtitles: Boolean = true,
    ): ResolvedVideoStream {
        val stream = resolveInternal(video, preferredQuality, waitForRuntimeSubtitles)
        return withContext(Dispatchers.IO) {
            streamPostProcessor.process(stream)
        }
    }

    private suspend fun resolveInternal(
        video: VideoVariant,
        preferredQuality: PreferredQuality,
        waitForRuntimeSubtitles: Boolean,
    ): ResolvedVideoStream = withContext(Dispatchers.IO) {
        var lastFailure: Throwable? = null
        for (siteBaseUrl in siteDomainResolver.orderedBaseUrlsFor(video.url)) {
            val sourceUrl = video.url.normalizeVideoUrl(siteBaseUrl)
            try {
                val stream = resolveInternalForBaseUrl(
                    video = video,
                    sourceUrl = sourceUrl,
                    siteBaseUrl = siteBaseUrl,
                    preferredQuality = preferredQuality,
                    waitForRuntimeSubtitles = waitForRuntimeSubtitles,
                )
                siteDomainResolver.markAvailable(siteBaseUrl)
                return@withContext stream
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                lastFailure = throwable
                siteDomainResolver.markUnavailable(siteBaseUrl)
            }
        }
        throw lastFailure ?: IOException("Could not select a working site domain")
    }

    private suspend fun resolveInternalForBaseUrl(
        video: VideoVariant,
        sourceUrl: String,
        siteBaseUrl: String,
        preferredQuality: PreferredQuality,
        waitForRuntimeSubtitles: Boolean,
    ): ResolvedVideoStream {
        return when {
            sourceUrl.isCvhIframeUrl() -> resolveCvhWithRuntimeFallback(
                video = video,
                sourceUrl = sourceUrl,
                siteBaseUrl = siteBaseUrl,
                preferredQuality = preferredQuality,
                waitForRuntimeSubtitles = waitForRuntimeSubtitles,
            )
            sourceUrl.isKodikIframeUrl() ->
                providerStreamResolver.resolveKodik(sourceUrl, siteBaseUrl, preferredQuality)
            sourceUrl.isAksorIframeUrl() ->
                providerStreamResolver.resolveAksor(sourceUrl, siteBaseUrl, preferredQuality)
            sourceUrl.isSibnetIframeUrl() -> providerStreamResolver.resolveSibnet(sourceUrl, siteBaseUrl)
            else -> genericStreamResolver.resolve(
                sourceUrl = sourceUrl,
                siteBaseUrl = siteBaseUrl,
                preferredQuality = preferredQuality,
                waitForRuntimeSubtitles = waitForRuntimeSubtitles,
            )
        }
    }

    private suspend fun resolveCvhWithRuntimeFallback(
        video: VideoVariant,
        sourceUrl: String,
        siteBaseUrl: String,
        preferredQuality: PreferredQuality,
        waitForRuntimeSubtitles: Boolean,
    ): ResolvedVideoStream {
        val cvhFailure = try {
            return providerStreamResolver.resolveCvh(sourceUrl, video, siteBaseUrl, preferredQuality)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            throwable
        }
        return try {
            webViewStreamResolver.resolve(sourceUrl, siteBaseUrl, preferredQuality, waitForRuntimeSubtitles)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (runtimeFailure: Throwable) {
            cvhFailure.addSuppressed(runtimeFailure)
            throw cvhFailure
        }
    }

    private fun String.normalizeVideoUrl(siteBaseUrl: String): String {
        val value = trim()
        return when {
            value.startsWith("//") -> {
                val absoluteUrl = "https:$value"
                if (siteDomainResolver.isKnownSiteHost(absoluteUrl.toHttpUrlOrNull()?.host)) {
                    absoluteUrl.rewriteKnownSiteHost(siteBaseUrl)
                } else {
                    absoluteUrl
                }
            }
            value.startsWith("/") -> "${siteBaseUrl.urlOrigin() ?: siteBaseUrl.trimEnd('/')}$value"
            siteDomainResolver.isKnownSiteHost(value.toHttpUrlOrNull()?.host) ->
                value.rewriteKnownSiteHost(siteBaseUrl)
            else -> value
        }
    }

    private fun String.isCvhIframeUrl(): Boolean {
        val url = toHttpUrlOrNull() ?: return false
        return siteDomainResolver.isKnownSiteHost(url.host) &&
            url.encodedPath.contains("iframeCVH", ignoreCase = true)
    }

}

// VideoStreamUrlParsing
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
    val path = toHttpUrlOrNull()?.pathSegments?.lastOrNull { it.isNotBlank() }
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
        val url = toHttpUrlOrNull() ?: return@runCatching this
        val path = url.encodedPath
        val query = url.encodedQuery?.let { "?$it" }.orEmpty()
        val fragment = url.encodedFragment?.let { "#$it" }.orEmpty()
        "$targetOrigin$path$query$fragment"
    }.getOrDefault(this)
}

internal fun String.isKodikIframeUrl(): Boolean {
    val host = toHttpUrlOrNull()?.host.orEmpty()
    return host.equals("kodikplayer.com", ignoreCase = true) ||
        host.endsWith(".kodikplayer.com", ignoreCase = true)
}

internal fun String.isAksorIframeUrl(): Boolean {
    val url = toHttpUrlOrNull() ?: return false
    return url.host.equals("player.aksor.tv", ignoreCase = true) &&
        url.encodedPath.startsWith("/video/", ignoreCase = true)
}

internal fun String.isSibnetIframeUrl(): Boolean {
    val url = toHttpUrlOrNull() ?: return false
    return url.host.equals("video.sibnet.ru", ignoreCase = true) &&
        url.encodedPath.contains("shell.php", ignoreCase = true)
}

internal fun String.requiresRuntimePlayerDiscovery(): Boolean {
    val host = toHttpUrlOrNull()?.host.orEmpty().lowercase()
    return "alloha" in host || "alloh" in host
}

internal fun String.detectVideoHeight(): Int? {
    val heights = buildList {
        this@detectVideoHeight.hlsSourceQualities().mapNotNull { it.height }.forEach(::add)
        VideoStreamPatterns.dashHeight.findAll(this@detectVideoHeight).forEach { match ->
            match.groupValues.getOrNull(1)?.toIntOrNull()?.let(::add)
        }
        VideoStreamPatterns.qualityHeight.findAll(this@detectVideoHeight).forEach { match ->
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
    return VideoStreamPatterns.embeddedAbsoluteStreamUrl
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

    return VideoStreamPatterns.streamUrl
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

// WebViewStreamResolver
internal class WebViewStreamResolver(
    context: Context?,
    private val providerStreamResolver: ProviderStreamResolver,
    private val subtitleMetadataParser: SubtitleMetadataParser,
    private val subtitleTrackMaterializer: SubtitleTrackMaterializer,
    private val playbackRequestHeaders: PlaybackRequestHeaders,
    private val playerMetadataInspector: PlayerMetadataInspector,
    private val fallbackSiteBaseUrl: () -> String,
) : RuntimeStreamResolver {
    private val appContext = context?.applicationContext

    override suspend fun resolve(
        sourceUrl: String,
        siteBaseUrl: String,
        preferredQuality: PreferredQuality,
        waitForRuntimeSubtitles: Boolean,
    ): ResolvedVideoStream = withContext(Dispatchers.Main) {
        val context = appContext ?: throw IOException("Context is required for JS stream capture")
        suspendCancellableCoroutine { continuation ->
            WebViewCaptureSession(
                context = context,
                sourceUrl = sourceUrl,
                siteBaseUrl = siteBaseUrl,
                preferredQuality = preferredQuality,
                waitForRuntimeSubtitles = waitForRuntimeSubtitles,
                providerStreamResolver = providerStreamResolver,
                subtitleMetadataParser = subtitleMetadataParser,
                subtitleTrackMaterializer = subtitleTrackMaterializer,
                playbackRequestHeaders = playbackRequestHeaders,
                playerMetadataInspector = playerMetadataInspector,
                fallbackSiteBaseUrl = fallbackSiteBaseUrl,
                continuation = continuation,
            ).start()
        }
    }
}

private class WebViewCaptureSession(
    context: Context,
    private val sourceUrl: String,
    private val siteBaseUrl: String,
    private val preferredQuality: PreferredQuality,
    private val waitForRuntimeSubtitles: Boolean,
    private val providerStreamResolver: ProviderStreamResolver,
    private val subtitleMetadataParser: SubtitleMetadataParser,
    private val subtitleTrackMaterializer: SubtitleTrackMaterializer,
    private val playbackRequestHeaders: PlaybackRequestHeaders,
    private val playerMetadataInspector: PlayerMetadataInspector,
    private val fallbackSiteBaseUrl: () -> String,
    private val continuation: CancellableContinuation<ResolvedVideoStream>,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val webView = WebView(context)
    private val capturedRequestHeaders = ConcurrentHashMap<String, Map<String, String>>()
    private val capturedSubtitleTracks = linkedSetOf<ResolvedSubtitleTrack>()
    private val capturedEmbeddedSubtitleTracks = linkedSetOf<ResolvedEmbeddedSubtitleTrack>()
    private val requiresRuntimePlayerDiscovery = sourceUrl.requiresRuntimePlayerDiscovery()
    private val supportsDocumentStartScript = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

    private val termination = WebViewSessionTermination()
    private var capturedPlayback: CapturedPlayback? = null
    private var capturedHasEmbeddedSubtitles = false
    private var discoveryVersion = 0
    private var playerStateScriptHandler: ScriptHandler? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun start() {
        continuation.invokeOnCancellation {
            if (termination.tryTerminate()) {
                handler.post(::cleanupAfterTermination)
            }
        }
        if (!continuation.isActive || termination.isTerminated) return

        if (requiresRuntimePlayerDiscovery && !supportsDocumentStartScript) {
            finish(
                Result.failure(
                    IOException(
                        "WebView document-start script is not supported; " +
                            "runtime player discovery cannot run. Iframe: $sourceUrl",
                    ),
                ),
            )
            return
        }

        runCatching {
            installJavascriptBridge()
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString = BROWSER_USER_AGENT
                loadsImagesAutomatically = false
                blockNetworkImage = true
            }

            installDocumentStartScript()
            installRequestInterceptor()
            handler.postDelayed(::finishWithCapturedPlaybackOrFailure, STREAM_WEBVIEW_RESOLVE_TIMEOUT_MS)
            loadPlayerFrame()
        }.onFailure { failure ->
            finish(Result.failure(failure))
        }
    }

    private fun installJavascriptBridge() {
        webView.addJavascriptInterface(
            object {
                @JavascriptInterface
                fun captureResponse(rawUrl: String?, contentType: String?, rawBody: String?) {
                    captureJavascriptResponse(rawUrl, contentType, rawBody)
                }
            },
            STREAM_WEBVIEW_DISCOVERY_BRIDGE_NAME,
        )
    }

    private fun captureJavascriptResponse(
        rawUrl: String?,
        contentType: String?,
        rawBody: String?,
    ) {
        if (termination.isTerminated) return
        val url = rawUrl
            ?.takeIf { it.isNotBlank() }
            ?.normalizeAgainst(sourceUrl)
            ?: return
        val body = rawBody?.takeIf { it.isNotBlank() } ?: return
        if (
            !playerMetadataInspector.isInspectableUrl(url) &&
            !subtitleMetadataParser.isResolvableCandidate(url) &&
            !body.looksLikePlayerMetadataBody()
        ) {
            return
        }

        val playbackHeaders = playbackRequestHeaders.forwardedPlayback(
            sourceHeaders = capturedRequestHeaders[url].orEmpty(),
            streamUrl = url,
            sourceUrl = sourceUrl,
            siteBaseUrl = siteBaseUrl,
        )
        val metadataCapture = runCatching {
            playerMetadataInspector.inspect(
                url = url,
                body = body,
                requestHeaders = playbackHeaders,
                sourceUrl = sourceUrl,
                siteBaseUrl = siteBaseUrl,
                preferredQuality = preferredQuality,
            )
        }.getOrNull()
        val capturedSubtitle = runCatching {
            subtitleTrackMaterializer.materializeCapturedBody(
                url = url,
                contentType = contentType,
                body = body,
            )
        }.getOrNull()

        handler.post {
            metadataCapture?.playback?.let(::capturePlayback)
            captureSubtitleTracks(metadataCapture?.subtitles.orEmpty() + listOfNotNull(capturedSubtitle))
            metadataCapture?.let { capture ->
                captureEmbeddedSubtitleTracks(
                    tracks = capture.embeddedSubtitles,
                    hasEmbeddedSubtitles = capture.hasEmbeddedSubtitles,
                )
            }
        }
    }

    private fun installDocumentStartScript() {
        if (!requiresRuntimePlayerDiscovery || !supportsDocumentStartScript) return
        playerStateScriptHandler = WebViewCompat.addDocumentStartJavaScript(
            webView,
            STREAM_PLAYER_DISCOVERY_BRIDGE_SCRIPT,
            setOf(runtimeDocumentStartOriginRule(sourceUrl)),
        )
    }

    private fun installRequestInterceptor() {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?,
            ): WebResourceResponse? {
                return interceptRequest(request)
            }
        }
    }

    private fun interceptRequest(request: WebResourceRequest?): WebResourceResponse? {
        if (termination.isTerminated) return null
        val url = request?.url?.toString().orEmpty()
        val method = request?.method.orEmpty()
        if (method.equals("GET", ignoreCase = true)) {
            val requestHeaders = request?.requestHeaders.orEmpty()
            val playbackHeaders = playbackRequestHeaders.forwardedPlayback(
                sourceHeaders = requestHeaders,
                streamUrl = url,
                sourceUrl = sourceUrl,
                siteBaseUrl = siteBaseUrl,
            )
            capturedRequestHeaders[url] = playbackHeaders

            subtitleMetadataParser.potentialTrack(url)
                ?.copy(headers = playbackHeaders)
                ?.let { track -> handler.post { captureSubtitleTracks(listOf(track)) } }

            if (playerMetadataInspector.isInspectableUrl(url)) {
                runCatching { inspectPlayerMetadataResponse(url, playbackHeaders) }
                    .onSuccess { capture ->
                        handler.post {
                            capture.playback?.let(::capturePlayback)
                            captureSubtitleTracks(capture.subtitles)
                            captureEmbeddedSubtitleTracks(
                                tracks = capture.embeddedSubtitles,
                                hasEmbeddedSubtitles = capture.hasEmbeddedSubtitles,
                            )
                        }
                    }
            }

            if (url.isCapturedPlaybackUrl()) {
                handler.post {
                    capturePlayback(
                        CapturedPlayback(
                            url = url,
                            mimeType = url.mimeTypeFromUrl(),
                            headers = playbackHeaders,
                            maxVideoHeight = url.detectVideoHeight(),
                        ),
                    )
                }
                if (url.isProgressivePlaybackUrl()) {
                    return WebResourceResponse(
                        url.mimeTypeFromUrl() ?: "text/plain",
                        "UTF-8",
                        ByteArrayInputStream(ByteArray(0)),
                    )
                }
            }
        }
        if (!method.equals("GET", ignoreCase = true) && url.isNotBlank()) {
            capturedRequestHeaders[url] = playbackRequestHeaders.forwardedPlayback(
                sourceHeaders = request?.requestHeaders.orEmpty(),
                streamUrl = url,
                sourceUrl = sourceUrl,
                siteBaseUrl = siteBaseUrl,
            )
        }
        return null
    }

    private fun inspectPlayerMetadataResponse(
        url: String,
        requestHeaders: Map<String, String>,
    ): PlayerMetadataCapture {
        val body = providerStreamResolver.getText(url, requestHeaders)
        return playerMetadataInspector.inspect(
            url = url,
            body = body,
            requestHeaders = requestHeaders,
            sourceUrl = sourceUrl,
            siteBaseUrl = siteBaseUrl,
            preferredQuality = preferredQuality,
        )
    }

    private fun capturePlayback(playback: CapturedPlayback) {
        if (termination.isTerminated) return
        if (capturedPlayback?.url != playback.url) {
            capturedPlayback = playback
        }
        scheduleFinishAfterDiscoveryIdle()
    }

    private fun captureSubtitleTracks(tracks: List<ResolvedSubtitleTrack>) {
        if (termination.isTerminated || tracks.isEmpty()) return
        tracks.forEach(capturedSubtitleTracks::add)
        scheduleFinishAfterDiscoveryIdle()
    }

    private fun captureEmbeddedSubtitleTracks(
        tracks: List<ResolvedEmbeddedSubtitleTrack>,
        hasEmbeddedSubtitles: Boolean,
    ) {
        if (termination.isTerminated) return
        if (hasEmbeddedSubtitles) {
            capturedHasEmbeddedSubtitles = true
        }
        if (tracks.isNotEmpty()) {
            tracks.forEach(capturedEmbeddedSubtitleTracks::add)
        }
        if (tracks.isNotEmpty() || hasEmbeddedSubtitles) {
            scheduleFinishAfterDiscoveryIdle()
        }
    }

    private fun scheduleFinishAfterDiscoveryIdle() {
        if (termination.isTerminated || capturedPlayback == null) return
        discoveryVersion += 1
        val scheduledVersion = discoveryVersion
        handler.postDelayed(
            {
                if (!termination.isTerminated && scheduledVersion == discoveryVersion) {
                    finishWithCapturedPlaybackOrFailure()
                }
            },
            webViewDiscoveryIdleMs(
                waitForRuntimeSubtitles = waitForRuntimeSubtitles,
                hasCapturedSubtitles = capturedSubtitleTracks.isNotEmpty(),
                requiresRuntimePlayerDiscovery = requiresRuntimePlayerDiscovery,
            ),
        )
    }

    private fun finishWithCapturedPlaybackOrFailure() {
        val playback = capturedPlayback
        if (playback != null) {
            finish(
                Result.success(
                    playback.toStream(
                        subtitles = capturedSubtitleTracks.toList(),
                        embeddedSubtitles = capturedEmbeddedSubtitleTracks.toList(),
                        hasEmbeddedSubtitles = capturedHasEmbeddedSubtitles,
                    ),
                ),
            )
        } else {
            val timeoutSeconds = STREAM_WEBVIEW_RESOLVE_TIMEOUT_MS / 1_000L
            finish(
                Result.failure(
                    IOException(
                        "Could not capture an HLS/MP4/DASH player stream in $timeoutSeconds seconds. " +
                            "Iframe: $sourceUrl",
                    ),
                ),
            )
        }
    }

    private fun finish(result: Result<ResolvedVideoStream>) {
        if (!termination.tryTerminate()) return
        cleanupAfterTermination()
        if (!continuation.isActive) return
        result
            .onSuccess { continuation.resume(it) }
            .onFailure { continuation.resumeWithException(it) }
    }

    private fun cleanupAfterTermination() {
        handler.removeCallbacksAndMessages(null)
        cleanup()
    }

    private fun cleanup() {
        runCatching { playerStateScriptHandler?.remove() }
        playerStateScriptHandler = null
        runCatching { webView.stopLoading() }
        runCatching { webView.removeJavascriptInterface(STREAM_WEBVIEW_DISCOVERY_BRIDGE_NAME) }
        runCatching { webView.loadUrl("about:blank") }
        runCatching { webView.removeAllViews() }
        runCatching { webView.destroy() }
    }

    private fun loadPlayerFrame() {
        val html = """
            <!doctype html>
            <html>
                <head><meta name="referrer" content="no-referrer-when-downgrade"></head>
                <body style="margin:0;background:#000">
                    <iframe
                        src="$sourceUrl"
                        width="1280"
                        height="720"
                        allow="autoplay; fullscreen"
                        referrerpolicy="origin">
                    </iframe>
                </body>
            </html>
        """.trimIndent()
        webView.loadDataWithBaseURL(
            siteBaseUrl,
            html,
            "text/html",
            "UTF-8",
            null,
        )
    }

    private fun String.normalizeAgainst(baseUrl: String): String {
        return normalizeVideoUrlAgainstBase(baseUrl, fallbackSiteBaseUrl())
    }
}

internal class WebViewSessionTermination {
    private val terminated = AtomicBoolean(false)

    val isTerminated: Boolean
        get() = terminated.get()

    fun tryTerminate(): Boolean = terminated.compareAndSet(false, true)
}

internal fun webViewDiscoveryIdleMs(
    waitForRuntimeSubtitles: Boolean,
    hasCapturedSubtitles: Boolean,
    requiresRuntimePlayerDiscovery: Boolean,
): Long {
    return when {
        !waitForRuntimeSubtitles -> STREAM_WEBVIEW_PLAYBACK_DISCOVERY_IDLE_MS
        !hasCapturedSubtitles && requiresRuntimePlayerDiscovery -> STREAM_WEBVIEW_SUBTITLE_DISCOVERY_GRACE_MS
        else -> STREAM_WEBVIEW_DISCOVERY_IDLE_MS
    }
}

internal fun runtimeDocumentStartOriginRule(sourceUrl: String): String {
    return sourceUrl.urlOrigin()
        ?: throw IOException("Runtime player URL has no valid origin: $sourceUrl")
}

internal const val STREAM_WEBVIEW_RESOLVE_TIMEOUT_MS = 30_000L
private const val STREAM_WEBVIEW_PLAYBACK_DISCOVERY_IDLE_MS = 250L
private const val STREAM_WEBVIEW_DISCOVERY_IDLE_MS = 1_200L
private const val STREAM_WEBVIEW_SUBTITLE_DISCOVERY_GRACE_MS = 4_000L
private const val STREAM_WEBVIEW_DISCOVERY_BRIDGE_NAME = "YummyResolverBridge"

internal val STREAM_PLAYER_DISCOVERY_BRIDGE_SCRIPT = """
    (function() {
        if (window.__yummyResolverBridgeInstalled) return;
        window.__yummyResolverBridgeInstalled = true;
        var attemptsLeft = 80;
        var timer = window.setInterval(function() {
            try {
                var source = window.player && window.player.currentSource;
                var quality = source && source.quality;
                if (!quality || typeof quality !== 'object') {
                    if (--attemptsLeft <= 0) window.clearInterval(timer);
                    return;
                }
                var hasHls = Object.keys(quality).some(function(key) {
                    return /\.m3u8(?:[?#]|${'$'})/i.test(String(quality[key] || ''));
                });
                if (!hasHls) {
                    if (--attemptsLeft <= 0) window.clearInterval(timer);
                    return;
                }
                var bridge = window['$STREAM_WEBVIEW_DISCOVERY_BRIDGE_NAME'];
                if (bridge && bridge.captureResponse) {
                    bridge.captureResponse(
                        String(location.href),
                        'application/json',
                        JSON.stringify({ hlsSource: [source] })
                    );
                    window.clearInterval(timer);
                }
            } catch (error) {}
        }, 250);
    })();
""".trimIndent()
