package me.yummydroid.app.data

import android.webkit.CookieManager
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient

// CvhVideoSelection
internal fun buildCvhVoiceCandidates(
    iframeUrl: HttpUrl,
    video: VideoVariant,
): List<String> {
    val iframeVoices = listOf(
        "dubbing_code",
        "priority-voice",
        "translation",
        "voice",
        "voiceStudio",
        "voice_studio",
        "dubbing",
    ).mapNotNull(iframeUrl::queryParameter)

    return (iframeVoices + video.dubbing + video.groupTitle)
        .map { it.trim() }
        .filter { it.isNotBlank() && it.cvhVoiceAliases().isNotEmpty() }
        .distinctBy { it.cvhVoiceIdentity() }
}

internal fun List<CvhItemDto>.selectCvhItem(
    season: Int?,
    episode: Int,
    priorityVoices: List<String>,
): CvhItemDto? {
    val seasonItems = filter { item ->
        season == null || (item.season ?: 1) == season
    }
    val episodeItems = seasonItems.filter { item ->
        cvhPlaylistItemMatchesEpisode(
            requestedSeason = season,
            requestedEpisode = episode,
            itemSeason = item.season,
            itemEpisode = item.episode,
        )
    }
    if (episodeItems.isEmpty()) {
        val fallbackEpisode = cvhFallbackEpisodeForMissingRequestedEpisode(
            requestedEpisode = episode,
            availableEpisodes = seasonItems.map { it.episode },
        ) ?: return null
        return seasonItems
            .filter { item -> (item.episode ?: 1) == fallbackEpisode }
            .selectCvhItemForVoice(priorityVoices)
    }

    return episodeItems.selectCvhItemForVoice(priorityVoices)
}

private fun List<CvhItemDto>.selectCvhItemForVoice(
    priorityVoices: List<String>,
): CvhItemDto? {
    val requestedAliases = priorityVoices
        .flatMap { it.cvhVoiceAliases() }
        .toSet()
    if (requestedAliases.isNotEmpty()) {
        firstOrNull { item ->
            item.cvhVoiceAliases().any { it in requestedAliases }
        }?.let { return it }

        firstOrNull { item ->
            item.cvhVoiceAliases().any { itemAlias ->
                requestedAliases.any { requestedAlias ->
                    itemAlias.isMeaningfulCvhAliasMatch(requestedAlias)
                }
            }
        }?.let { return it }

        if (priorityVoices.any { it.isSubtitleCvhVoice() }) {
            firstOrNull { item ->
                item.voiceType.orEmpty().isSubtitleCvhVoice() ||
                    item.voiceStudio.orEmpty().isSubtitleCvhVoice()
            }?.let { return it }
        }

        return null
    }

    return firstOrNull { !it.voiceStudio.isNullOrBlank() }
        ?: firstOrNull()
}

private fun CvhItemDto.cvhVoiceAliases(): Set<String> {
    return buildSet {
        voiceStudio?.cvhVoiceAliases()?.let(::addAll)
        voiceType?.cvhVoiceAliases()?.let(::addAll)
        if (!voiceStudio.isNullOrBlank() && !voiceType.isNullOrBlank()) {
            addAll("${voiceStudio.orEmpty()} ${voiceType.orEmpty()}".cvhVoiceAliases())
        }
    }
}

private fun String.cvhVoiceAliases(): Set<String> {
    val identity = cvhVoiceIdentity()
    if (identity.isBlank()) return emptySet()
    return buildSet {
        add(identity)
        if (identity.endsWith("tv") && identity.length > 4) {
            add(identity.removeSuffix("tv"))
        }
    }
}

private fun String.cvhVoiceIdentity(): String {
    return trim()
        .lowercase()
        .replace('\u0451', '\u0435')
        .replace(CVH_RU_VOICE_PREFIX_KEY, "")
        .replace(CVH_RU_PLAYER_PREFIX_KEY, "")
        .replace(CVH_RU_SUBTITLES_PREFIX_KEY, "")
        .replace("subtitle", "")
        .replace("subtitles", "")
        .replace("subs", "")
        .replace("voice", "")
        .replace("dubbing", "")
        .replace("dub", "")
        .replace(Regex("[\\s./|\\u2022\\u0432\\u0402\\u045E:_+&\\-]+"), "")
        .trim()
}

private fun String.isSubtitleCvhVoice(): Boolean {
    val value = lowercase().replace('\u0451', '\u0435')
    return CVH_RU_SUBTITLE_STEM_KEY in value || "subtitle" in value
}

private fun String.isMeaningfulCvhAliasMatch(other: String): Boolean {
    if (length < 4 || other.length < 4) return false
    return startsWith(other) || other.startsWith(this)
}

private const val CVH_RU_VOICE_PREFIX_KEY = "\u043e\u0437\u0432\u0443\u0447\u043a\u0430"
private const val CVH_RU_PLAYER_PREFIX_KEY = "\u043f\u043b\u0435\u0435\u0440"
private const val CVH_RU_SUBTITLES_PREFIX_KEY = "\u0441\u0443\u0431\u0442\u0438\u0442\u0440\u044b"
private const val CVH_RU_SUBTITLE_STEM_KEY = "\u0441\u0443\u0431\u0442\u0438\u0442\u0440"

// HttpHeaders
internal fun Map<String, String>.toOkHttpHeaders(): Headers {
    return Headers.Builder().also { builder ->
        forEach { (name, value) -> builder.set(name, value) }
    }.build()
}

// PlaybackCaptureModels
internal data class SubtitleDetection(
    val tracks: List<ResolvedSubtitleTrack>,
    val embeddedSubtitles: List<ResolvedEmbeddedSubtitleTrack>,
    val hasEmbeddedSubtitles: Boolean,
)

internal data class CapturedPlayback(
    val url: String,
    val mimeType: String?,
    val headers: Map<String, String>,
    val maxVideoHeight: Int?,
    val availableQualities: List<SourceQuality> = emptyList(),
    val selectedVideoHeight: Int? = null,
    val fallbackUrls: List<String> = emptyList(),
    val skipPlaybackProbe: Boolean = false,
) {
    fun toStream(
        subtitles: List<ResolvedSubtitleTrack>,
        embeddedSubtitles: List<ResolvedEmbeddedSubtitleTrack>,
        hasEmbeddedSubtitles: Boolean,
    ): ResolvedVideoStream {
        return ResolvedVideoStream(
            url = url,
            mimeType = mimeType,
            headers = headers,
            maxVideoHeight = maxVideoHeight,
            availableQualities = availableQualities.normalizedSourceQualities(),
            selectedVideoHeight = selectedVideoHeight,
            fallbackUrls = fallbackUrls,
            skipPlaybackProbe = skipPlaybackProbe,
            subtitles = subtitles.normalizedSubtitleTracks(),
            embeddedSubtitles = embeddedSubtitles.normalizedEmbeddedSubtitleTracks(),
            hasEmbeddedSubtitles = hasEmbeddedSubtitles || embeddedSubtitles.isNotEmpty(),
        )
    }
}

internal data class PlayerMetadataCapture(
    val playback: CapturedPlayback? = null,
    val subtitles: List<ResolvedSubtitleTrack> = emptyList(),
    val embeddedSubtitles: List<ResolvedEmbeddedSubtitleTrack> = emptyList(),
    val hasEmbeddedSubtitles: Boolean = false,
)

// PlaybackRequestHeaders
internal fun interface PlaybackCookieProvider {
    fun cookieFor(url: String): String?
}

private object AndroidPlaybackCookieProvider : PlaybackCookieProvider {
    override fun cookieFor(url: String): String? {
        return runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
    }
}

internal class PlaybackRequestHeaders(
    private val fallbackSiteBaseUrl: () -> String,
    private val cookieProvider: PlaybackCookieProvider = AndroidPlaybackCookieProvider,
) {
    fun iframe(
        url: String,
        siteBaseUrl: String = fallbackSiteBaseUrl(),
    ): Map<String, String> {
        return buildMap {
            put("Accept", "*/*")
            put("Origin", siteBaseUrl.urlOrigin() ?: siteBaseUrl.trimEnd('/'))
            put("Referer", siteBaseUrl.withTrailingSlash())
            put("User-Agent", BROWSER_USER_AGENT)
            if (url.contains("alloha.yani.tv", ignoreCase = true)) {
                put("Sec-Fetch-Dest", "iframe")
                put("Sec-Fetch-Mode", "navigate")
            }
        }
    }

    fun aksorApi(sourceUrl: String): Map<String, String> {
        val origin = sourceUrl.urlOrigin() ?: AKSOR_PLAYER_ORIGIN
        return buildMap {
            put("Accept", "application/json")
            put("Origin", origin)
            put("Referer", sourceUrl)
            put("User-Agent", BROWSER_USER_AGENT)
        }
    }

    fun kodikApi(sourceUrl: String): Map<String, String> {
        return buildMap {
            put("Accept", "application/json, text/javascript, */*; q=0.01")
            put("Origin", sourceUrl.urlOrigin() ?: KODIK_PLAYER_ORIGIN)
            put("Referer", sourceUrl)
            put("User-Agent", BROWSER_USER_AGENT)
            put("X-Requested-With", "XMLHttpRequest")
        }
    }

    fun kodikPlayback(url: String): Map<String, String> {
        return buildMap {
            putAll(playback(url, "$KODIK_PLAYER_ORIGIN/"))
            put("Accept", "*/*")
            put("Origin", KODIK_PLAYER_ORIGIN)
            put("Referer", "$KODIK_PLAYER_ORIGIN/")
            put("User-Agent", BROWSER_USER_AGENT)
        }
    }

    fun playback(
        url: String,
        refererUrl: String? = null,
        siteBaseUrl: String = fallbackSiteBaseUrl(),
    ): Map<String, String> {
        val referer = refererUrl?.takeIf { it.isNotBlank() }
        val origin = referer?.urlOrigin()

        return buildMap {
            put("Accept", "*/*")
            put("Accept-Encoding", "identity")
            put("Accept-Language", PLAYBACK_ACCEPT_LANGUAGE)
            put("User-Agent", BROWSER_USER_AGENT)
            put("Sec-Fetch-Dest", "empty")
            put("Sec-Fetch-Mode", "cors")
            put("Sec-Fetch-Site", "cross-site")
            if (url.contains("vkvideo.cloud", ignoreCase = true)) {
                put("Origin", origin ?: ALLOHA_PLAYER_ORIGIN)
                put("Referer", referer ?: "$ALLOHA_PLAYER_ORIGIN/")
            } else if (referer != null && origin != null) {
                put("Origin", origin)
                put("Referer", referer)
            } else {
                put("Origin", siteBaseUrl.urlOrigin() ?: siteBaseUrl.trimEnd('/'))
                put("Referer", siteBaseUrl.withTrailingSlash())
            }
        }
    }

    fun cvhApi(sourceUrl: String): Map<String, String> {
        val origin = sourceUrl.urlOrigin() ?: DEFAULT_CVH_SITE_ORIGIN
        return buildMap {
            put("Accept", "application/json, text/plain, */*")
            put("Origin", origin)
            put("Referer", sourceUrl)
            put("User-Agent", BROWSER_USER_AGENT)
        }
    }

    fun cvhPlayback(
        url: String,
        sourceUrl: String,
        siteBaseUrl: String,
    ): Map<String, String> {
        return buildMap {
            putAll(playback(url, sourceUrl, siteBaseUrl))
            put("Accept", "*/*")
            put("Origin", CVH_PLAYER_ORIGIN)
            put("Referer", "$CVH_PLAYER_ORIGIN/")
            put("User-Agent", BROWSER_USER_AGENT)
        }
    }

    fun forwardedPlayback(
        sourceHeaders: Map<String, String>,
        streamUrl: String,
        sourceUrl: String,
        siteBaseUrl: String = fallbackSiteBaseUrl(),
    ): Map<String, String> {
        return buildMap {
            putAll(playback(streamUrl, sourceUrl, siteBaseUrl))
            sourceHeaders.forEach { (name, value) ->
                if (name.isForwardablePlaybackHeader() && value.isNotBlank()) {
                    put(name, value)
                }
            }
            putIfAbsent("Referer", sourceUrl)
            putIfAbsent("Origin", sourceUrl.urlOrigin() ?: siteBaseUrl.urlOrigin().orEmpty())
            putIfAbsent("User-Agent", BROWSER_USER_AGENT)
            putIfAbsent("Accept-Encoding", "identity")
            putIfAbsent("Accept-Language", PLAYBACK_ACCEPT_LANGUAGE)
            putIfAbsent("Sec-Fetch-Dest", "empty")
            putIfAbsent("Sec-Fetch-Mode", "cors")
            putIfAbsent("Sec-Fetch-Site", "cross-site")
            playbackCookies(streamUrl, sourceUrl)?.let { put("Cookie", it) }
        }
    }

    private fun String.isForwardablePlaybackHeader(): Boolean {
        return lowercase() !in BLOCKED_PLAYBACK_HEADERS
    }

    private fun playbackCookies(streamUrl: String, sourceUrl: String): String? {
        val streamOrigin = streamUrl.urlOrigin()
        val sourceOrigin = sourceUrl.urlOrigin()
        val cookieUrls = buildList {
            add(streamUrl)
            add(streamOrigin)
            if (streamOrigin != null && streamOrigin == sourceOrigin) {
                add(sourceUrl)
                add(sourceOrigin)
            }
        }
        return cookieUrls
            .asSequence()
            .filterNotNull()
            .mapNotNull(cookieProvider::cookieFor)
            .firstOrNull { it.isNotBlank() }
    }

    private companion object {
        const val AKSOR_PLAYER_ORIGIN = "https://player.aksor.tv"
        const val KODIK_PLAYER_ORIGIN = "https://kodikplayer.com"
        const val ALLOHA_PLAYER_ORIGIN = "https://alloha.yani.tv"
        const val CVH_PLAYER_ORIGIN = "https://player.cdnvideohub.com"
        const val DEFAULT_CVH_SITE_ORIGIN = "https://ru.yummyani.me"
        const val PLAYBACK_ACCEPT_LANGUAGE = "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7"

        val BLOCKED_PLAYBACK_HEADERS = setOf(
            "accept-encoding",
            "access-control-request-headers",
            "access-control-request-method",
            "connection",
            "host",
            "range",
        )
    }
}

// PlaybackStreamModels
data class ResolvedVideoStream(
    val url: String,
    val mimeType: String?,
    val headers: Map<String, String>,
    val maxVideoHeight: Int? = null,
    val availableQualities: List<SourceQuality> = emptyList(),
    val selectedVideoHeight: Int? = null,
    val fallbackUrls: List<String> = emptyList(),
    val skipPlaybackProbe: Boolean = false,
    val subtitles: List<ResolvedSubtitleTrack> = emptyList(),
    val embeddedSubtitles: List<ResolvedEmbeddedSubtitleTrack> = emptyList(),
    val hasEmbeddedSubtitles: Boolean = false,
    val sourceSubtitleSourceKeys: Set<String> = emptySet(),
) {
    val hasResolvedSubtitles: Boolean
        get() = subtitles.isNotEmpty() || embeddedSubtitles.isNotEmpty()

    val hasSubtitles: Boolean
        get() = hasResolvedSubtitles || hasEmbeddedSubtitles
}

fun ResolvedVideoStream.sourceResolutionHeight(): Int {
    return (
        availableQualities.mapNotNull { it.height } +
            listOfNotNull(maxVideoHeight, selectedVideoHeight)
        )
        .mapNotNull { it.validVideoQualityHeight() }
        .maxOrNull()
        ?: 0
}

// PlayerMetadataInspector
internal class PlayerMetadataInspector(
    private val subtitleMetadataParser: SubtitleMetadataParser,
    private val playbackRequestHeaders: PlaybackRequestHeaders,
    private val fallbackSiteBaseUrl: () -> String,
) {
    fun isInspectableUrl(url: String): Boolean {
        val parsedUrl = url.toHttpUrlOrNull() ?: return false
        val host = parsedUrl.host.lowercase()
        if ("alloha" !in host && "alloh" !in host) return false
        val path = parsedUrl.encodedPath.lowercase()
        return path.startsWith("/movies/") ||
            path.startsWith("/serials/") ||
            path.startsWith("/trailers/") ||
            path.startsWith("/player/") ||
            path.startsWith("/video/")
    }

    fun inspect(
        url: String,
        body: String,
        requestHeaders: Map<String, String>,
        sourceUrl: String,
        siteBaseUrl: String,
        preferredQuality: PreferredQuality,
    ): PlayerMetadataCapture {
        val bodyIsHlsManifest = body.isHlsManifestBody()
        val bodyIsDashManifest = body.isDashManifestBody()
        val playback = capturedPlayback(
            url = url,
            body = body,
            requestHeaders = requestHeaders,
            sourceUrl = sourceUrl,
            siteBaseUrl = siteBaseUrl,
            preferredQuality = preferredQuality,
            bodyIsHlsManifest = bodyIsHlsManifest,
            bodyIsDashManifest = bodyIsDashManifest,
        )
        val subtitleDetection = capturedSubtitles(
            url = url,
            body = body,
            requestHeaders = requestHeaders,
            sourceUrl = sourceUrl,
            siteBaseUrl = siteBaseUrl,
            bodyIsHlsManifest = bodyIsHlsManifest,
            bodyIsDashManifest = bodyIsDashManifest,
        )
        return PlayerMetadataCapture(
            playback = playback,
            subtitles = subtitleDetection.tracks,
            embeddedSubtitles = subtitleDetection.embeddedSubtitles,
            hasEmbeddedSubtitles = subtitleDetection.hasEmbeddedSubtitles,
        )
    }

    private fun capturedPlayback(
        url: String,
        body: String,
        requestHeaders: Map<String, String>,
        sourceUrl: String,
        siteBaseUrl: String,
        preferredQuality: PreferredQuality,
        bodyIsHlsManifest: Boolean,
        bodyIsDashManifest: Boolean,
    ): CapturedPlayback? {
        val runtimeStreams = body.extractAllohaRuntimeStreams(url)
            .sortedForPreferredQuality(preferredQuality)
        val runtimeStream = runtimeStreams.firstOrNull()
        val capturedUrl = capturedPlaybackUrl(
            url = url,
            body = body,
            runtimeStream = runtimeStream,
            bodyIsHlsManifest = bodyIsHlsManifest,
            bodyIsDashManifest = bodyIsDashManifest,
        ) ?: return null
        val playbackUrl = capturedUrl.normalizeVideoUrlAgainstBase(sourceUrl, fallbackSiteBaseUrl())
        return CapturedPlayback(
            url = playbackUrl,
            mimeType = capturedPlaybackMimeType(
                capturedMetadataUrl = capturedUrl == url,
                bodyIsHlsManifest = bodyIsHlsManifest,
                bodyIsDashManifest = bodyIsDashManifest,
                playbackUrl = playbackUrl,
            ),
            headers = playbackRequestHeaders.forwardedPlayback(
                sourceHeaders = requestHeaders,
                streamUrl = playbackUrl,
                sourceUrl = sourceUrl,
                siteBaseUrl = siteBaseUrl,
            ),
            maxVideoHeight = maxOfOrNull(
                body.detectVideoHeight(),
                runtimeStream?.height,
                playbackUrl.detectVideoHeight(),
            ),
            availableQualities = (
                runtimeStreams.map { stream -> SourceQuality(height = stream.height) } +
                    body.detectSourceQualities() +
                    playbackUrl.detectSourceQualities()
                ).normalizedSourceQualities(),
            selectedVideoHeight = runtimeStream?.height,
            fallbackUrls = runtimeStreams
                .drop(1)
                .map { stream -> stream.url.normalizeVideoUrlAgainstBase(sourceUrl, fallbackSiteBaseUrl()) },
            skipPlaybackProbe = runtimeStream != null,
        )
    }

    private fun capturedPlaybackUrl(
        url: String,
        body: String,
        runtimeStream: AllohaRuntimeStream?,
        bodyIsHlsManifest: Boolean,
        bodyIsDashManifest: Boolean,
    ): String? {
        runtimeStream?.url?.let { return it }
        body.extractDirectStreamUrl(url)?.let { return it }
        if (subtitleMetadataParser.isResolvableCandidate(url)) return null
        return url.takeIf { bodyIsHlsManifest || bodyIsDashManifest }
    }

    private fun capturedPlaybackMimeType(
        capturedMetadataUrl: Boolean,
        bodyIsHlsManifest: Boolean,
        bodyIsDashManifest: Boolean,
        playbackUrl: String,
    ): String? {
        if (!capturedMetadataUrl) return playbackUrl.mimeTypeFromUrl()
        if (bodyIsHlsManifest) return "application/x-mpegURL"
        if (bodyIsDashManifest) return "application/dash+xml"
        return playbackUrl.mimeTypeFromUrl()
    }

    private fun capturedSubtitles(
        url: String,
        body: String,
        requestHeaders: Map<String, String>,
        sourceUrl: String,
        siteBaseUrl: String,
        bodyIsHlsManifest: Boolean,
        bodyIsDashManifest: Boolean,
    ): SubtitleDetection {
        val hlsSubtitles = if (bodyIsHlsManifest) {
            subtitleMetadataParser.extractHlsTracks(body, url)
        } else {
            SubtitleDetection(tracks = emptyList(), embeddedSubtitles = emptyList(), hasEmbeddedSubtitles = false)
        }
        val dashEmbeddedSubtitles = if (bodyIsDashManifest) {
            subtitleMetadataParser.extractDashEmbeddedTracks(body)
        } else {
            emptyList()
        }
        val subtitleHeaders = playbackRequestHeaders.forwardedPlayback(
            sourceHeaders = requestHeaders,
            streamUrl = url,
            sourceUrl = sourceUrl,
            siteBaseUrl = siteBaseUrl,
        )
        val subtitles = (subtitleMetadataParser.extractTracks(body, url) + hlsSubtitles.tracks)
            .map { track -> track.withFallbackHeaders(subtitleHeaders) }
            .normalizedSubtitleTracks()
        return SubtitleDetection(
            tracks = subtitles,
            embeddedSubtitles = (hlsSubtitles.embeddedSubtitles + dashEmbeddedSubtitles)
                .normalizedEmbeddedSubtitleTracks(),
            hasEmbeddedSubtitles = hlsSubtitles.hasEmbeddedSubtitles || dashEmbeddedSubtitles.isNotEmpty(),
        )
    }

    private fun ResolvedSubtitleTrack.withFallbackHeaders(
        fallbackHeaders: Map<String, String>,
    ): ResolvedSubtitleTrack {
        if (uri.startsWith("file:", ignoreCase = true)) return this
        if (uri.startsWith("content:", ignoreCase = true)) return this
        return copy(headers = headers.ifEmpty { fallbackHeaders })
    }
}

// QualitySelection
private const val MinVideoQualityHeight = 100
private const val MaxVideoQualityHeight = 4320

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
            compareBy<T> { height(it).validVideoQualityHeight() ?: 0 }
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

fun normalizedDownloadQualities(qualities: Collection<PreferredQuality>): List<PreferredQuality> {
    val concrete = qualities
        .filter { it.height != null }
        .distinctBy { it.height }
        .sortedByDescending { it.height ?: 0 }
    if (concrete.isNotEmpty()) return concrete
    return listOf(PreferredQuality.Auto)
}

internal fun Int?.validVideoQualityHeight(): Int? {
    return this?.takeIf { it in MinVideoQualityHeight..MaxVideoQualityHeight }
}

private fun preferredQualityBucket(height: Int?, preferredHeight: Int): Int {
    val safeHeight = height.validVideoQualityHeight() ?: return 2
    return if (safeHeight <= preferredHeight) 0 else 1
}

private fun preferredQualityDistance(height: Int?, preferredHeight: Int): Int {
    val safeHeight = height.validVideoQualityHeight() ?: return Int.MAX_VALUE
    return if (safeHeight <= preferredHeight) {
        preferredHeight - safeHeight
    } else {
        safeHeight - preferredHeight
    }
}

// VideoHttpClients
fun defaultVideoResolveClient(): OkHttpClient {
    return OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .withVideoTlsCompatibility()
        .build()
}

fun defaultVideoDownloadClient(): OkHttpClient {
    return OkHttpClient.Builder()
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .withVideoTlsCompatibility()
        .build()
}

fun OkHttpClient.Builder.withVideoTlsCompatibility(): OkHttpClient.Builder {
    val trustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
    val sslContext = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
    }
    return sslSocketFactory(sslContext.socketFactory, trustManager)
        .hostnameVerifier { _, _ -> true }
}

// VideoLabelNormalization
fun sourceProviderRank(player: String): Int {
    val normalized = player.cleanVideoSourceLabel().lowercase(Locale.ROOT)
    return when {
        "cvh" in normalized || "cdnvideohub" in normalized -> 0
        "alloha" in normalized -> 1
        "kodik" in normalized -> 2
        "aksor" in normalized -> 3
        "sibnet" in normalized -> 4
        else -> 10
    }
}

fun String.cleanVideoSourceLabel(): String {
    var value = trim()
    knownVideoSourcePrefixRegexes.forEach { prefixRegex ->
        value = value.replace(
            regex = prefixRegex,
            replacement = "",
        ).trim()
    }
    return value
}

fun String.isKnownPlayerLabel(): Boolean {
    val key = cleanVideoSourceLabel().normalizedVoiceKey()
    return key in knownVideoPlayerLabelKeys
}

fun String.normalizedVoiceKey(): String {
    return lowercase(Locale.ROOT)
        .replace('\u0451', '\u0435')
        .replace(RU_VOICE_PREFIX_KEY, "")
        .replace(RU_SUBTITLES_PREFIX_KEY, "")
        .replace(RU_PLAYER_PREFIX_KEY, "")
        .replace(voiceKeySeparatorRegex, "")
        .trim()
}

private const val RU_VOICE_PREFIX_LABEL = "\u041e\u0437\u0432\u0443\u0447\u043a\u0430"
private const val RU_SUBTITLES_PREFIX_LABEL = "\u0421\u0443\u0431\u0442\u0438\u0442\u0440\u044b"
private const val RU_PLAYER_PREFIX_LABEL = "\u041f\u043b\u0435\u0435\u0440"
private const val RU_VOICE_PREFIX_KEY = "\u043e\u0437\u0432\u0443\u0447\u043a\u0430"
private const val RU_SUBTITLES_PREFIX_KEY = "\u0441\u0443\u0431\u0442\u0438\u0442\u0440\u044b"
private const val RU_PLAYER_PREFIX_KEY = "\u043f\u043b\u0435\u0435\u0440"

private val knownVideoSourcePrefixes = listOf(
    RU_VOICE_PREFIX_LABEL,
    RU_SUBTITLES_PREFIX_LABEL,
    RU_PLAYER_PREFIX_LABEL,
    "Voice",
    "Dubbing",
    "Subtitles",
    "Player",
)

private val knownVideoSourcePrefixRegexes = knownVideoSourcePrefixes.map { prefix ->
    Regex("""^\s*${Regex.escape(prefix)}\s*""", RegexOption.IGNORE_CASE)
}

internal val whitespaceRegex = Regex("""\s+""")
private val voiceKeySeparatorRegex = Regex("""(?:[\s./|\u2022:_-]+|\u0432\u0452\u045e)""")

private val knownVideoPlayerLabelKeys = setOf(
    "alloha",
    "kodik",
    "cvh",
    "sibnet",
    "aksor",
    "hls",
    "mp4",
    "videocdn",
    "cdnvideohub",
    "videoframe",
    "aniboom",
)

// VideoModels
@Serializable
data class OfflineVideoFile(
    val playbackUrl: String,
    val mimeType: String? = null,
    val bytes: Long = 0L,
    val qualityTitle: String = "",
    val voiceTitle: String = "",
    val player: String = "",
    val createdAtMs: Long = 0L,
)

@Serializable
data class SourceQuality(
    val height: Int? = null,
    val bitrate: Int = 0,
) {
    val title: String
        get() = height.validVideoQualityHeight()?.let { "${it}p" }.orEmpty()
}

fun OfflineVideoFile.qualityHeight(): Int {
    return Regex("""(\d{3,4})p""", RegexOption.IGNORE_CASE)
        .find(qualityTitle)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?: 0
}

@Serializable
data class VideoVariant(
    val id: Long,
    val animeId: Long,
    val player: String,
    val playerId: Long = 0L,
    val dubbing: String,
    val episode: String,
    val url: String,
    val index: Int,
    val durationSeconds: Int?,
    val views: Long,
    val skipSegments: List<VideoSkipSegment> = emptyList(),
    val previewUrl: String = "",
    val localPlaybackUrl: String = "",
    val localMimeType: String? = null,
    val localBytes: Long = 0L,
    val localFiles: List<OfflineVideoFile> = emptyList(),
    val sourceQualities: List<SourceQuality> = emptyList(),
    val subscribed: Boolean = false,
) {
    val groupKey: String = "$player|$dubbing"
    val groupTitle: String = listOf(player.cleanVideoLabel("Player"), dubbing.cleanVideoLabel("Voice"))
        .filter { it.isNotBlank() }
        .joinToString(" \u2022 ")

    val episodeTitle: String
        get() = if (episode.isBlank()) "Episode" else "Episode $episode"

    val isOfflineAvailable: Boolean
        get() = localPlaybackUrl.isNotBlank() || localFiles.any { it.playbackUrl.isNotBlank() }

    val offlineFiles: List<OfflineVideoFile>
        get() = localFiles.filter { it.playbackUrl.isNotBlank() }.ifEmpty(::legacyOfflineFiles)

    private fun legacyOfflineFiles(): List<OfflineVideoFile> {
        if (localPlaybackUrl.isBlank()) return emptyList()
        return listOf(
            OfflineVideoFile(
                playbackUrl = localPlaybackUrl,
                mimeType = localMimeType,
                bytes = localBytes,
                qualityTitle = "",
                voiceTitle = dubbing.cleanVideoLabel("Voice")
                    .ifBlank { player.cleanVideoLabel("Player") },
                player = player,
            ),
        )
    }
}

data class ResolvedPlayback(
    val video: VideoVariant,
    val stream: ResolvedVideoStream,
)

data class DownloadProgressInfo(
    val fraction: Float,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val bytesPerSecond: Long = 0L,
    val qualityTitle: String = "",
    val voiceTitle: String = "",
)

private fun String.cleanVideoLabel(prefix: String): String {
    return trim().removePrefix(prefix).trim()
}

// VideoProviderModels
internal fun String.detectSourceQualities(): List<SourceQuality> {
    val qualities = mutableListOf<SourceQuality>()
    qualities += hlsSourceQualities()
    VideoStreamPatterns.dashHeight.findAll(this).forEach { match ->
        match.groupValues.getOrNull(1)?.toIntOrNull()?.let { height ->
            qualities += SourceQuality(height = height)
        }
    }
    VideoStreamPatterns.qualityHeight.findAll(this).forEach { match ->
        match.groupValues.getOrNull(1)?.toIntOrNull()?.let { height ->
            qualities += SourceQuality(height = height)
        }
    }
    return qualities.normalizedSourceQualities()
}

internal data class KodikParams(
    val type: String,
    val id: String,
    val hash: String,
    val domain: String,
    val domainSign: String,
    val playerDomain: String,
    val playerDomainSign: String,
    val referer: String,
    val refererSign: String,
)

internal fun String.kodikParams(): KodikParams {
    val type = extractKodikValue("type")
        ?: extractKodikVInfoValue("type")
        ?: throw IOException("Kodik: type was not found")
    val id = extractKodikVInfoValue("id")
        ?: extractKodikValue("videoId")
        ?: throw IOException("Kodik: id was not found")
    val hash = extractKodikVInfoValue("hash")
        ?: throw IOException("Kodik: hash was not found")

    return KodikParams(
        type = type,
        id = id,
        hash = hash,
        domain = extractKodikValue("domain") ?: throw IOException("Kodik: domain was not found"),
        domainSign = extractKodikValue("d_sign") ?: throw IOException("Kodik: d_sign was not found"),
        playerDomain = extractKodikValue("pd") ?: "kodikplayer.com",
        playerDomainSign = extractKodikValue("pd_sign") ?: throw IOException("Kodik: pd_sign was not found"),
        referer = extractKodikValue("ref") ?: DEFAULT_SITE_BASE_URL,
        refererSign = extractKodikValue("ref_sign") ?: throw IOException("Kodik: ref_sign was not found"),
    )
}

private fun String.extractKodikValue(name: String): String? {
    val doubleQuoted = Regex("""var\s+$name\s*=\s*"([^"]*)"""").find(this)?.groupValues?.getOrNull(1)
    if (!doubleQuoted.isNullOrBlank()) return doubleQuoted
    val singleQuoted = Regex("""var\s+$name\s*=\s*'([^']*)'""").find(this)?.groupValues?.getOrNull(1)
    return singleQuoted?.takeIf { it.isNotBlank() }
}

private fun String.extractKodikVInfoValue(name: String): String? {
    return Regex("""vInfo\.$name\s*=\s*['"]([^'"]+)['"]""")
        .find(this)
        ?.groupValues
        ?.getOrNull(1)
        ?.takeIf { it.isNotBlank() }
}

@Serializable
internal data class KodikFtorDto(
    val link: String = "",
    val links: Map<String, List<KodikLinkDto>> = emptyMap(),
) {
    fun availableQualities(): List<SourceQuality> {
        val qualities = links.keys.mapNotNull { key ->
            key.toIntOrNull().validVideoQualityHeight()?.let { SourceQuality(height = it) }
        }
        return (qualities + link.detectSourceQualities()).normalizedSourceQualities()
    }

    fun bestStream(preferredQuality: PreferredQuality): KodikStream? {
        linkStreams()
            .selectForPreferredQuality(
                preferredQuality = preferredQuality,
                height = { it.height },
            )
            ?.let { return it }

        return directLinkStream()
    }

    private fun linkStreams(): List<KodikStream> {
        return links.entries.flatMap { (quality, links) ->
            val height = quality.toIntOrNull()
            links.mapNotNull { link ->
                link.src
                    .takeIf { it.isNotBlank() }
                    ?.let { src ->
                        KodikStream(
                            url = src.decodeKodikUrl().normalizeKodikUrl(),
                            mimeType = link.type.takeIf { it.isNotBlank() },
                            height = height,
                        )
                    }
            }
        }
    }

    private fun directLinkStream(): KodikStream? {
        return link.takeIf { it.isNotBlank() }?.let {
            KodikStream(
                url = it.normalizeKodikUrl(),
                mimeType = it.mimeTypeFromKodikUrl(),
                height = it.detectKodikHeight(),
            )
        }
    }
}

@Serializable
internal data class KodikLinkDto(
    val src: String = "",
    val type: String = "",
)

internal data class KodikStream(
    val url: String,
    val mimeType: String?,
    val height: Int?,
)

@Serializable
internal data class AksorVideoDto(
    val qualities: AksorQualitiesDto = AksorQualitiesDto(),
) {
    fun bestStream(preferredQuality: PreferredQuality): AksorStream? = qualities.bestStream(preferredQuality)
}

@Serializable
internal data class AksorQualitiesDto(
    val q4k: String? = null,
    val q2k: String? = null,
    val q1080: String? = null,
    val q720: String? = null,
    val q480: String? = null,
    val q360: String? = null,
) {
    fun availableQualities(): List<SourceQuality> {
        return streams().availableSourceQualities(
            url = { it.url },
            height = { it.height },
        )
    }

    fun bestStream(preferredQuality: PreferredQuality): AksorStream? {
        return streams()
            .filter { it.url.isNotBlank() }
            .selectForPreferredQuality(
                preferredQuality = preferredQuality,
                height = { it.height },
            )
    }

    private fun streams(): List<AksorStream> {
        return listOf(
            AksorStream(q4k.orEmpty(), 2160),
            AksorStream(q2k.orEmpty(), 1440),
            AksorStream(q1080.orEmpty(), 1080),
            AksorStream(q720.orEmpty(), 720),
            AksorStream(q480.orEmpty(), 480),
            AksorStream(q360.orEmpty(), 360),
        )
    }
}

internal data class AksorStream(
    val url: String,
    val height: Int,
)

private fun String.decodeKodikUrl(): String {
    val rotated = map { char ->
        when (char) {
            in 'A'..'Z' -> {
                val shifted = char.code + 18
                if (shifted <= 'Z'.code) shifted.toChar() else (shifted - 26).toChar()
            }
            in 'a'..'z' -> {
                val shifted = char.code + 18
                if (shifted <= 'z'.code) shifted.toChar() else (shifted - 26).toChar()
            }
            else -> char
        }
    }.joinToString("")
    val padded = rotated.padEnd(rotated.length + ((4 - rotated.length % 4) % 4), '=')
    return runCatching {
        String(Base64.getDecoder().decode(padded), Charsets.UTF_8)
    }.getOrDefault(this)
}

private fun String.normalizeKodikUrl(): String {
    return when {
        startsWith("//") -> "https:$this"
        startsWith("/") -> "https://kodikplayer.com$this"
        else -> this
    }
}

internal fun String.mimeTypeFromKodikUrl(): String? {
    val lower = lowercase()
    return when {
        ".m3u8" in lower -> "application/x-mpegURL"
        ".mpd" in lower -> "application/dash+xml"
        ".mp4" in lower -> "video/mp4"
        else -> null
    }
}

private fun String.detectKodikHeight(): Int? {
    return Regex("""(?i)(2160|1440|1080|720|576|540|480|360|240|144)p""")
        .find(this)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
}

internal data class AllohaRuntimeStream(
    val url: String,
    val height: Int,
    val mirrorIndex: Int,
)

internal fun cvhPlaylistItemMatchesEpisode(
    requestedSeason: Int?,
    requestedEpisode: Int,
    itemSeason: Int?,
    itemEpisode: Int?,
): Boolean {
    val episode = itemEpisode ?: 1
    if (episode != requestedEpisode) return false
    return requestedSeason == null || (itemSeason ?: 1) == requestedSeason
}

internal fun cvhFallbackEpisodeForMissingRequestedEpisode(
    requestedEpisode: Int,
    availableEpisodes: List<Int?>,
): Int? {
    val fallbackEpisode = availableEpisodes
        .map { it ?: 1 }
        .filter { it in 1 until requestedEpisode }
        .maxOrNull()
        ?: return null
    return fallbackEpisode.takeIf { requestedEpisode - it == 1 }
}

@Serializable
internal data class CvhPlaylistDto(
    val items: List<CvhItemDto> = emptyList(),
)

@Serializable
internal data class CvhItemDto(
    @SerialName("vkId") val vkId: String = "",
    @SerialName("voiceStudio") val voiceStudio: String? = null,
    @SerialName("voiceType") val voiceType: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
)

@Serializable
internal data class CvhVideoDto(
    val sources: CvhSourcesDto? = null,
)

@Serializable
internal data class CvhSourcesDto(
    @SerialName("hlsUrl") val hlsUrl: String = "",
    @SerialName("dashUrl") val dashUrl: String = "",
    @SerialName("mpeg4kUrl") val mpeg4kUrl: String = "",
    @SerialName("mpeg2kUrl") val mpeg2kUrl: String = "",
    @SerialName("mpegQhdUrl") val mpegQhdUrl: String = "",
    @SerialName("mpegFullHdUrl") val mpegFullHdUrl: String = "",
    @SerialName("mpegHighUrl") val mpegHighUrl: String = "",
    @SerialName("mpegMediumUrl") val mpegMediumUrl: String = "",
    @SerialName("mpegLowUrl") val mpegLowUrl: String = "",
    @SerialName("mpegLowestUrl") val mpegLowestUrl: String = "",
    @SerialName("mpegTinyUrl") val mpegTinyUrl: String = "",
) {
    fun availableQualities(): List<SourceQuality> {
        return (
            mpegStreams().availableSourceQualities(
                url = { it.url },
                height = { it.height },
            ) +
                hlsUrl.takeIf { it.isNotBlank() }?.detectSourceQualities().orEmpty() +
                dashUrl.takeIf { it.isNotBlank() }?.detectSourceQualities().orEmpty()
            ).normalizedSourceQualities()
    }

    fun bestStream(preferredQuality: PreferredQuality): CvhStream? {
        val mpegStreams = mpegStreams()
        val highestKnownHeight = mpegStreams
            .asSequence()
            .filter { it.url.isNotBlank() }
            .mapNotNull { it.height }
            .maxOrNull()
        adaptiveStreams(highestKnownHeight).firstOrNull()?.let { return it }

        return mpegStreams
            .filter { it.url.isNotBlank() }
            .selectForPreferredQuality(
                preferredQuality = preferredQuality,
                height = { it.height },
            )
    }

    private fun adaptiveStreams(highestKnownHeight: Int?): List<CvhStream> {
        return listOf(
            CvhStream(hlsUrl, "application/x-mpegURL", highestKnownHeight),
            CvhStream(dashUrl, "application/dash+xml", highestKnownHeight),
        ).filter { it.url.isNotBlank() }
    }

    private fun mpegStreams(): List<CvhStream> {
        return listOf(
            CvhStream(mpeg4kUrl, "video/mp4", 2160),
            CvhStream(mpeg2kUrl, "video/mp4", 1440),
            CvhStream(mpegQhdUrl, "video/mp4", 1440),
            CvhStream(mpegFullHdUrl, "video/mp4", 1080),
            CvhStream(mpegHighUrl, "video/mp4", 720),
            CvhStream(mpegMediumUrl, "video/mp4", 480),
            CvhStream(mpegLowUrl, "video/mp4", 360),
            CvhStream(mpegLowestUrl, "video/mp4", 240),
            CvhStream(mpegTinyUrl, "video/mp4", 144),
        )
    }
}

internal data class CvhStream(
    val url: String,
    val mimeType: String,
    val height: Int?,
)

private fun <T> Iterable<T>.availableSourceQualities(
    url: (T) -> String,
    height: (T) -> Int?,
): List<SourceQuality> {
    return mapNotNull { stream ->
        height(stream)
            ?.takeIf { url(stream).isNotBlank() }
            ?.let { SourceQuality(height = it) }
    }.normalizedSourceQualities()
}

// VideoSkipModels
@Serializable
enum class VideoSkipKind(
    val title: String,
) {
    Opening("opening"),
    Ending("ending"),
}

@Serializable
data class VideoSkipSegment(
    val kind: VideoSkipKind,
    val startMs: Long,
    val endMs: Long,
) {
    val key: String = "${kind.name}:$startMs:$endMs"

    fun isActive(positionMs: Long): Boolean {
        return startMs >= 0L && endMs > startMs && positionMs in startMs until endMs
    }
}

fun List<VideoSkipSegment>.normalizedSkipSegments(): List<VideoSkipSegment> {
    return asSequence()
        .filter { it.startMs >= 0L && it.endMs > it.startMs }
        .distinctBy { segment -> segment.key }
        .sortedWith(
            compareBy<VideoSkipSegment> { it.startMs }
                .thenBy { it.endMs }
                .thenBy { it.kind.ordinal },
        )
        .toList()
}

// VideoVariantMatching
val VideoVariant.matchingVoiceTitle: String
    get() = matchingDubbingTitle.ifBlank { "Voice" }

val VideoVariant.matchingDisplayVoiceTitle: String
    get() = matchingDubbingTitle
        .ifBlank { player.cleanVideoSourceLabel() }
        .ifBlank { matchingVoiceTitle }

val VideoVariant.matchingDubbingTitle: String
    get() = dubbing.cleanVideoSourceLabel()
        .takeUnless { it.isKnownPlayerLabel() }
        .orEmpty()

val VideoVariant.matchingVoiceKey: String
    get() = matchingDubbingTitle.normalizedVoiceKey()

val VideoVariant.matchingSourceKey: String
    get() = listOf(player.cleanVideoSourceLabel(), matchingVoiceKey)
        .joinToString("|")
        .normalizedVoiceKey()

val VideoVariant.matchingEpisodeKey: String
    get() = episode.normalizedEpisodeKey()
        ?: index.takeIf { it > 0 }?.let { "index:$it" }
        ?: "video:$id"

val VideoVariant.matchingPlayerKey: String
    get() = player.cleanVideoSourceLabel().normalizedVoiceKey()

fun VideoVariant.isSameEpisodeAs(other: VideoVariant): Boolean {
    return matchingEpisodeKey == other.matchingEpisodeKey
}

fun VideoVariant.hasSameVoiceAs(other: VideoVariant): Boolean {
    return matchingVoiceKey == other.matchingVoiceKey
}

fun VideoVariant.episodeOrderValue(): Double? {
    return episode
        .trim()
        .replace(',', '.')
        .toDoubleOrNull()
        ?: index.takeIf { it > 0 }?.toDouble()
}

val VideoVariant.downloadVoiceSlotKey: String
    get() = listOf(
        animeId.toString(),
        matchingEpisodeKey,
        matchingVoiceKey,
    ).joinToString("|") { it.trim().lowercase(Locale.ROOT) }

val VideoVariant.sourceSlotKey: String
    get() = listOf(
        animeId.toString(),
        matchingEpisodeKey,
        matchingPlayerKey,
        matchingVoiceKey,
    ).joinToString("|") { it.trim().lowercase(Locale.ROOT) }

val VideoVariant.downloadEpisodeSlotKey: String
    get() = matchingEpisodeKey

fun OfflineVideoFile.matchesPreferredQuality(preferredQuality: PreferredQuality): Boolean {
    val preferredHeight = preferredQuality.height ?: return true
    return qualityHeight() == preferredHeight
}

fun VideoVariant.downloadedEpisodeCountForVoice(variants: List<VideoVariant>): Int {
    val voiceKey = downloadPlanVoiceKey
    return variants
        .asSequence()
        .filter { it.downloadPlanVoiceKey == voiceKey && it.isOfflineAvailable }
        .map { it.episodeDownloadSlotKey() }
        .distinct()
        .count()
}

private fun VideoVariant.episodeDownloadSlotKey(): String = matchingEpisodeKey

private fun String.normalizedEpisodeKey(): String? {
    val raw = trim()
    if (raw.isBlank()) return null
    val numericTokens = episodeNumberRegex.findAll(raw)
        .map { it.groupValues.getOrNull(1).orEmpty().normalizedEpisodeNumber() }
        .filter { it.isNotBlank() }
        .distinct()
        .toList()
    if (numericTokens.size == 1) return numericTokens.single()
    return raw
        .lowercase(Locale.ROOT)
        .replace('\u0451', '\u0435')
        .replace(whitespaceRegex, " ")
        .trim()
        .takeIf { it.isNotBlank() }
}

private fun String.normalizedEpisodeNumber(): String {
    val normalized = replace(',', '.')
    if (!normalized.contains('.')) return normalized.trimStart('0').ifBlank { "0" }
    val compact = normalized
        .trimEnd('0')
        .trimEnd('.')
    return compact.trimStart('0').ifBlank { "0" }
}

private val episodeNumberRegex = Regex("""(?<!\d)(\d+(?:[.,]\d+)?)(?!\d)""")
