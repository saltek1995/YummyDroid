package me.yummydroid.app.data

import android.content.Context
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

private fun Throwable.throwIfResolutionStopped() {
    throwIfCancellation()
    if (this is DownloadSourceCoolingDown) throw this
}

// GenericStreamResolver
internal class GenericStreamResolver(
    private val client: OkHttpClient,
    private val playbackRequestHeaders: PlaybackRequestHeaders,
    private val subtitleMetadataParser: SubtitleMetadataParser,
) {
    suspend fun resolve(
        sourceUrl: String,
        siteBaseUrl: String,
    ): ResolvedVideoStream {
        if (sourceUrl.isDirectStreamUrl()) {
            return directStream(sourceUrl, siteBaseUrl)
        }

        val request = Request.Builder()
            .url(sourceUrl)
            .headers(playbackRequestHeaders.iframe(sourceUrl, siteBaseUrl).toOkHttpHeaders())
            .build()
        return client.withCancellableResponse(request) { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Player returned HTTP ${response.code}")
            }
            if (body.isHlsManifestBody()) {
                return@withCancellableResponse hlsManifestStream(sourceUrl, siteBaseUrl, body)
            }
            body.extractDirectStreamUrl(sourceUrl)?.let { streamUrl ->
                return@withCancellableResponse staticStream(sourceUrl, siteBaseUrl, streamUrl, body)
            }
            throw IOException("Generic: HLS/MP4/DASH stream was not found")
        }
    }

    private fun directStream(sourceUrl: String, siteBaseUrl: String): ResolvedVideoStream {
        return ResolvedVideoStream(
            url = sourceUrl,
            mimeType = sourceUrl.mimeTypeFromUrl(),
            headers = playbackRequestHeaders.playback(sourceUrl, sourceUrl, siteBaseUrl),
            maxVideoHeight = sourceUrl.detectVideoHeight(),
            availableQualities = sourceUrl.detectSourceQualities(),
            skipPlaybackProbe = true,
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
            skipPlaybackProbe = true,
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
            skipPlaybackProbe = true,
            subtitles = subtitleMetadataParser.extractTracks(body, sourceUrl),
        )
    }

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
    private data class DomainState(
        val candidates: List<String>,
        val cachedBaseUrl: String? = null,
        val checkedAtMs: Long = 0L,
    ) {
        val knownSiteHosts = candidates.knownSiteHosts()
    }

    private val state = AtomicReference(DomainState(candidates.normalizedSiteBaseUrls().ifEmpty { DEFAULT_SITE_DOMAINS }))

    suspend fun activeBaseUrl(): String = selectBaseUrl(useCache = true, allowFallback = true)!!

    suspend fun checkReachableBaseUrl(): String? = selectBaseUrl(useCache = false, allowFallback = false)

    suspend fun orderedBaseUrlsFor(rawUrl: String): List<String> {
        if (!rawUrl.isSiteRelativeOrKnownHost()) return listOf(cachedOrDefaultBaseUrl())
        val active = activeBaseUrl()
        val current = state.get()
        return (listOf(active) + current.candidates).filter { url ->
            current.candidates.any { it.sameUrlOrigin(url) }
        }.distinct()
    }

    fun cachedOrDefaultBaseUrl(): String = state.get().let { it.cachedBaseUrl ?: it.candidates.first() }

    fun updateCandidates(rawCandidates: List<String>) {
        val candidates = rawCandidates.normalizedSiteBaseUrls().ifEmpty { DEFAULT_SITE_DOMAINS }
        state.updateAndGet { previous ->
            if (previous.candidates == candidates) return@updateAndGet previous
            if (candidates.any { it.sameUrlOrigin(previous.cachedBaseUrl) }) {
                previous.copy(candidates = candidates)
            } else {
                DomainState(candidates)
            }
        }
    }

    fun markAvailable(baseUrl: String) {
        state.updateAndGet { previous ->
            if (previous.candidates.none { it.sameUrlOrigin(baseUrl) }) previous
            else previous.copy(cachedBaseUrl = baseUrl.toRootSiteBaseUrl(), checkedAtMs = System.currentTimeMillis())
        }
    }

    fun markUnavailable(baseUrl: String) {
        state.updateAndGet { previous ->
            if (baseUrl.sameUrlOrigin(previous.cachedBaseUrl)) previous.copy(cachedBaseUrl = null, checkedAtMs = 0L)
            else previous
        }
    }

    fun isKnownSiteHost(host: String?): Boolean {
        return !host.isNullOrBlank() && host.lowercase() in state.get().knownSiteHosts
    }

    private suspend fun selectBaseUrl(useCache: Boolean, allowFallback: Boolean): String? {
        while (true) {
            currentCoroutineContext().ensureActive()
            val snapshot = state.get()
            val now = System.currentTimeMillis()
            if (useCache && snapshot.cachedBaseUrl != null && now - snapshot.checkedAtMs < CACHE_TTL_MS) {
                return snapshot.cachedBaseUrl
            }
            val reachable = snapshot.candidates.firstOrNull { isReachable(it) }
            val selected = reachable ?: snapshot.candidates.first().takeIf { allowFallback }
            val updated = if (selected == null) snapshot else snapshot.copy(cachedBaseUrl = selected, checkedAtMs = now)
            if (state.compareAndSet(snapshot, updated)) return selected
        }
    }

    private suspend fun isReachable(baseUrl: String): Boolean {
        return request(baseUrl, "HEAD") || request(baseUrl, "GET")
    }

    private suspend fun request(baseUrl: String, method: String): Boolean {
        val request = Request.Builder()
            .url(baseUrl)
            .method(method, null)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()

        return runCatching {
            client.withCancellableResponse(request) { response -> response.code in 200..499 }
        }.onFailure { it.throwIfResolutionStopped() }.getOrDefault(false)
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
const val DEFAULT_SITE_BASE_URL = "https://ru.yummyani.me/"
const val APP_USER_AGENT = "YummyDroid Android TV"
const val BROWSER_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 10; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36"

val DEFAULT_YUMMY_SITE_DOMAINS: List<String> = listOf(
    DEFAULT_SITE_BASE_URL,
    "https://old.yummyani.me/",
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
    val playerMetadataMarkers = listOf(
        ".m3u8",
        ".mp4",
        ".mpd",
        "subtitle",
        "subtitles",
        "caption",
        "captions",
        "texttrack",
        "texttracks",
    )
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
    )

    suspend fun resolve(
        video: VideoVariant,
        preferredQuality: PreferredQuality = PreferredQuality.Auto,
        waitForRuntimeSubtitles: Boolean = true,
    ): ResolvedVideoStream {
        val stream = resolveInternal(video, preferredQuality, waitForRuntimeSubtitles)
        return withContext(Dispatchers.IO) {
            streamPostProcessor.process(stream, validateSubtitles = waitForRuntimeSubtitles)
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
            } catch (throwable: Throwable) {
                throwable.throwIfResolutionStopped()
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
            sourceUrl.isAllohaIframeUrl() -> webViewStreamResolver.resolve(
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
        } catch (throwable: Throwable) {
            throwable.throwIfResolutionStopped()
            throwable
        }
        return try {
            webViewStreamResolver.resolve(sourceUrl, siteBaseUrl, preferredQuality, waitForRuntimeSubtitles)
        } catch (runtimeFailure: Throwable) {
            runtimeFailure.throwIfResolutionStopped()
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

internal fun String.looksLikeAdaptiveStreamUrl(mimeType: String? = mimeTypeFromUrl()): Boolean {
    val lowerUrl = lowercase()
    val lowerMimeType = mimeType.orEmpty().lowercase()
    return ".m3u8" in lowerUrl ||
        ".mpd" in lowerUrl ||
        "mpegurl" in lowerMimeType ||
        "dash" in lowerMimeType
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

internal fun String.isAllohaIframeUrl(): Boolean {
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
    if (normalized.isHlsManifestBody()) return true
    val sample = normalized.take(8192).lowercase()
    return VideoStreamPatterns.playerMetadataMarkers.any(sample::contains)
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

// ProviderStreamResolver
internal class ProviderStreamResolver(
    private val client: OkHttpClient,
    private val playbackRequestHeaders: PlaybackRequestHeaders,
    private val subtitleMetadataParser: SubtitleMetadataParser,
    private val fallbackSiteBaseUrl: () -> String,
    private val json: Json,
) {
    suspend fun resolveKodik(
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

        val body = client.awaitRequiredResponseBody(request) { code -> "Kodik API returned HTTP $code" }
        val dto = json.decodeFromString<KodikFtorDto>(body)
        val stream = dto.bestStream(preferredQuality)
            ?: throw IOException("Kodik: HLS/MP4/DASH stream was not found")
        val selectedHeight = maxOfOrNull(stream.height, stream.url.detectVideoHeight())

        return ResolvedVideoStream(
            url = stream.url,
            mimeType = stream.mimeType ?: stream.url.mimeTypeFromUrl(),
            headers = playbackRequestHeaders.kodikPlayback(stream.url),
            maxVideoHeight = selectedHeight,
            availableQualities = (dto.availableQualities() + stream.url.detectSourceQualities())
                .normalizedSourceQualities(),
            selectedVideoHeight = selectedHeight,
            skipPlaybackProbe = true,
            subtitles = subtitleMetadataParser.extractTracks(body, sourceUrl),
        )
    }

    suspend fun resolveAksor(
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
        val selectedHeight = maxOfOrNull(stream.height, streamUrl.detectVideoHeight())

        return ResolvedVideoStream(
            url = streamUrl,
            mimeType = streamUrl.mimeTypeFromUrl(),
            headers = playbackRequestHeaders.playback(streamUrl, sourceUrl, siteBaseUrl),
            maxVideoHeight = selectedHeight,
            availableQualities = (video.qualities.availableQualities() + streamUrl.detectSourceQualities())
                .normalizedSourceQualities(),
            selectedVideoHeight = selectedHeight,
            skipPlaybackProbe = true,
        )
    }

    suspend fun resolveSibnet(sourceUrl: String, siteBaseUrl: String): ResolvedVideoStream {
        val html = getText(sourceUrl, playbackRequestHeaders.iframe(sourceUrl, siteBaseUrl))
        val streamUrl = html.extractSibnetStreamUrl(sourceUrl)
            ?: html.extractDirectStreamUrl(sourceUrl)
            ?: throw IOException("Sibnet: HLS/MP4/DASH stream was not found")

        return ResolvedVideoStream(
            url = streamUrl,
            mimeType = streamUrl.mimeTypeFromUrl(),
            headers = playbackRequestHeaders.playback(streamUrl, sourceUrl, siteBaseUrl),
            maxVideoHeight = streamUrl.detectVideoHeight(),
            skipPlaybackProbe = true,
        )
    }

    suspend fun resolveCvh(
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
            skipPlaybackProbe = true,
        )
    }

    suspend fun getResponse(url: String, headers: Map<String, String>): HttpResponseSnapshot {
        return client.readResponseSnapshot(url, headers)
    }

    private suspend fun getText(url: String, headers: Map<String, String>): String {
        return readRequiredResponseBody(url, headers) { code -> "Player returned HTTP $code" }
    }

    private suspend inline fun <reified T> getJson(
        url: String,
        headers: Map<String, String>,
        providerName: String,
    ): T {
        val body = readRequiredResponseBody(url, headers) { code -> "$providerName API returned HTTP $code" }
        return json.decodeFromString(body)
    }

    private suspend fun readRequiredResponseBody(
        url: String,
        headers: Map<String, String>,
        errorMessage: (Int) -> String,
    ): String {
        return client.awaitRequiredResponseBody(url, headers, errorMessage)
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
    suspend fun process(
        stream: ResolvedVideoStream,
        validateSubtitles: Boolean = true,
    ): ResolvedVideoStream {
        return stream.withFirstPlayableUrl().withDetectedSourceMetadata(validateSubtitles)
    }

    private suspend fun ResolvedVideoStream.withFirstPlayableUrl(): ResolvedVideoStream {
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
        val playable = candidates.firstNotNullOfOrNull { candidateUrl ->
            val candidate = copy(
                url = candidateUrl,
                mimeType = candidateUrl.mimeTypeFromUrl() ?: mimeType,
                maxVideoHeight = maxOfOrNull(maxVideoHeight, candidateUrl.detectVideoHeight()),
            )
            runCatching { validatePlayableStream(candidate) }
                .onFailure { throwable ->
                    throwable.throwIfResolutionStopped()
                    lastFailure = throwable
                }
                .map { candidate }
                .getOrNull()
        }
            ?: throw lastFailure ?: IOException("Player did not return a video URL")
        return playable.copy(fallbackUrls = candidates.filterNot { it == playable.url })
    }

    private suspend fun validatePlayableStream(stream: ResolvedVideoStream) {
        val url = stream.url.takeIf(String::isNotBlank)
            ?: throw IOException("Player did not return a video URL")
        if (url.startsWith("blob:", ignoreCase = true)) {
            throw IOException("Player returned a blob stream that native playback cannot use")
        }
        val requestBuilder = Request.Builder()
            .url(url)
            .headers(stream.headers.toOkHttpHeaders())
        if (!stream.looksLikeAdaptiveManifest()) requestBuilder.header("Range", "bytes=0-4095")
        client.withCancellableResponse(requestBuilder.build()) { response ->
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

    private suspend fun ResolvedVideoStream.withDetectedSourceMetadata(
        validateSubtitles: Boolean,
    ): ResolvedVideoStream {
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
        val subtitleCandidates = (subtitles + detectedSubtitles.tracks).normalizedSubtitleTracks()
        return copy(
            maxVideoHeight = resolvedHeight,
            availableQualities = resolvedQualities,
            subtitles = if (validateSubtitles) {
                subtitleTrackMaterializer.validateTracks(subtitleCandidates, headers)
            } else {
                subtitleCandidates
            },
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

    private suspend fun ResolvedVideoStream.loadAdaptiveManifestTextOrNull(): String? {
        if (!looksLikeAdaptiveManifest()) return null
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .headers(headers.toOkHttpHeaders())
                .build()
            client.withCancellableResponse(request) { response ->
                if (response.code !in listOf(200, 206)) return@withCancellableResponse null
                response.body?.string()
            }
        }.onFailure { it.throwIfResolutionStopped() }.getOrNull()
    }

    private fun ResolvedVideoStream.looksLikeAdaptiveManifest(): Boolean {
        return url.looksLikeAdaptiveStreamUrl(mimeType)
    }
}
