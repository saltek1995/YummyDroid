package me.yummydroid.app.data

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import me.yummydroid.app.AppLog
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class VideoStreamResolver(
    context: Context? = null,
    private val siteDomainResolver: SiteDomainResolver = SiteDomainResolver(),
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build(),
) {
    private val appContext = context?.applicationContext

    suspend fun resolve(video: VideoVariant): ResolvedVideoStream {
        val stream = resolveInternal(video)
        return withContext(Dispatchers.IO) {
            validatePlayableStream(stream)
            stream.withDetectedMaxVideoHeight()
        }
    }

    private suspend fun resolveInternal(video: VideoVariant): ResolvedVideoStream = withContext(Dispatchers.IO) {
        var lastFailure: Throwable? = null
        siteDomainResolver.orderedBaseUrlsFor(video.url).forEach { siteBaseUrl ->
            val sourceUrl = video.url.normalizeVideoUrl(siteBaseUrl)
            runCatching {
                resolveInternalForBaseUrl(
                    video = video,
                    sourceUrl = sourceUrl,
                    siteBaseUrl = siteBaseUrl,
                )
            }.onSuccess { stream ->
                siteDomainResolver.markAvailable(siteBaseUrl)
                return@withContext stream
            }.onFailure { throwable ->
                lastFailure = throwable
                siteDomainResolver.markUnavailable(siteBaseUrl)
            }
        }
        throw lastFailure ?: IOException("Не удалось выбрать рабочий домен сайта")
    }

    private suspend fun resolveInternalForBaseUrl(
        video: VideoVariant,
        sourceUrl: String,
        siteBaseUrl: String,
    ): ResolvedVideoStream {
        val headers = iframeHeaders(sourceUrl, siteBaseUrl)

        if (sourceUrl.isCvhIframeUrl()) {
            return resolveCvh(sourceUrl, video, siteBaseUrl)
        }

        if (sourceUrl.isKodikIframeUrl()) {
            return resolveKodik(sourceUrl, siteBaseUrl)
        }

        if (sourceUrl.isAksorIframeUrl()) {
            return resolveAksor(sourceUrl, siteBaseUrl)
        }

        if (sourceUrl.isSibnetIframeUrl()) {
            return resolveSibnet(sourceUrl, siteBaseUrl)
        }

        if (sourceUrl.isDirectStreamUrl()) {
            return ResolvedVideoStream(
                url = sourceUrl,
                mimeType = sourceUrl.mimeTypeFromUrl(),
                headers = playbackHeaders(sourceUrl, sourceUrl, siteBaseUrl),
                maxVideoHeight = sourceUrl.detectVideoHeight(),
            )
        }

        val request = Request.Builder()
            .url(sourceUrl)
            .headers(headers.toOkHttpHeaders())
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Плеер вернул HTTP ${response.code}")
            }

            if (body.trimStart().startsWith("#EXTM3U")) {
                return ResolvedVideoStream(
                    url = sourceUrl,
                    mimeType = "application/x-mpegURL",
                    headers = playbackHeaders(sourceUrl, sourceUrl, siteBaseUrl),
                    maxVideoHeight = body.detectVideoHeight(),
                )
            }

            body.extractDirectStreamUrl(sourceUrl)?.let { streamUrl ->
                return ResolvedVideoStream(
                    url = streamUrl,
                    mimeType = streamUrl.mimeTypeFromUrl(),
                    headers = playbackHeaders(streamUrl, sourceUrl, siteBaseUrl),
                    maxVideoHeight = maxOfOrNull(body.detectVideoHeight(), streamUrl.detectVideoHeight()),
                )
            }
        }

        return resolveViaWebView(sourceUrl, siteBaseUrl)
    }

    private fun validatePlayableStream(stream: ResolvedVideoStream) {
        val url = stream.url.takeIf { it.isNotBlank() }
            ?: throw IOException("Плеер не вернул ссылку на видео")
        if (url.startsWith("blob:", ignoreCase = true)) {
            throw IOException("Плеер вернул blob-поток, недоступный для нативного воспроизведения")
        }

        val request = Request.Builder()
            .url(url)
            .headers(stream.headers.toOkHttpHeaders())
            .header("Range", "bytes=0-4095")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code !in listOf(200, 206)) {
                throw IOException("источник вернул HTTP ${response.code}")
            }

            val contentType = response.header("Content-Type").orEmpty()
            val bodyPrefix = response.body?.source()?.use { source ->
                source.request(512)
                source.buffer.clone().readUtf8().take(512)
            }.orEmpty()
            val isExpectedStream = stream.mimeType?.contains("mpegURL", ignoreCase = true) == true ||
                stream.mimeType?.contains("dash", ignoreCase = true) == true ||
                stream.mimeType?.contains("video", ignoreCase = true) == true ||
                contentType.contains("mpegurl", ignoreCase = true) ||
                contentType.contains("dash", ignoreCase = true) ||
                contentType.contains("video", ignoreCase = true) ||
                bodyPrefix.trimStart().startsWith("#EXTM3U")

            if (!isExpectedStream) {
                throw IOException("источник не похож на HLS/DASH/MP4 поток")
            }
        }
    }

    private fun ResolvedVideoStream.withDetectedMaxVideoHeight(): ResolvedVideoStream {
        val detectedHeight = detectMaxVideoHeight()
        val resolvedHeight = maxOfOrNull(maxVideoHeight, detectedHeight, url.detectVideoHeight())
        AppLog.w(
            "YummyDroidVideo",
            "Resolved stream host=${runCatching { Uri.parse(url).host }.getOrNull().orEmpty()}, " +
                "maxHeight=${resolvedHeight ?: 0}, mime=${mimeType.orEmpty()}",
        )
        return copy(maxVideoHeight = resolvedHeight)
    }

    private fun ResolvedVideoStream.detectMaxVideoHeight(): Int? {
        val urlHeight = url.detectVideoHeight()
        if (!looksLikeAdaptiveManifest()) return urlHeight

        val manifestHeight = runCatching {
            val request = Request.Builder()
                .url(url)
                .headers(headers.toOkHttpHeaders())
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code !in listOf(200, 206)) return@use null
                response.body?.string()?.detectVideoHeight()
            }
        }.getOrNull()

        return maxOfOrNull(maxVideoHeight, urlHeight, manifestHeight)
    }

    private fun ResolvedVideoStream.looksLikeAdaptiveManifest(): Boolean {
        val lowerUrl = url.lowercase()
        val lowerMimeType = mimeType.orEmpty().lowercase()
        return ".m3u8" in lowerUrl ||
            ".mpd" in lowerUrl ||
            "mpegurl" in lowerMimeType ||
            "dash" in lowerMimeType
    }

    private fun resolveKodik(sourceUrl: String, siteBaseUrl: String): ResolvedVideoStream {
        val html = getText(sourceUrl, iframeHeaders(sourceUrl, siteBaseUrl))
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
            .headers(kodikApiHeaders(sourceUrl).toOkHttpHeaders())
            .post(form)
            .build()

        val body = client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful || text.isBlank()) {
                throw IOException("Kodik API вернул HTTP ${response.code}")
            }
            text
        }
        val stream = json.decodeFromString<KodikFtorDto>(body).bestStream()
            ?: throw IOException("Kodik: не найден HLS/MP4/DASH поток")

        return ResolvedVideoStream(
            url = stream.url,
            mimeType = stream.mimeType ?: stream.url.mimeTypeFromKodikUrl(),
            headers = kodikPlaybackHeaders(stream.url),
            maxVideoHeight = maxOfOrNull(stream.height, stream.url.detectVideoHeight()),
        )
    }

    private fun resolveAksor(sourceUrl: String, siteBaseUrl: String): ResolvedVideoStream {
        val videoId = Uri.parse(sourceUrl).lastPathSegment?.takeIf { it.isNotBlank() }
            ?: throw IOException("Aksor: missing video id")
        val origin = sourceUrl.origin() ?: AKSOR_ORIGIN
        val video = getJson<AksorVideoDto>(
            url = "$origin/api/video/$videoId",
            headers = aksorApiHeaders(sourceUrl),
        )
        val stream = video.bestStream()
            ?: throw IOException("Aksor: stream is unavailable")
        val streamUrl = stream.url.normalizeVideoUrlAgainst(sourceUrl)

        return ResolvedVideoStream(
            url = streamUrl,
            mimeType = streamUrl.mimeTypeFromUrl(),
            headers = playbackHeaders(streamUrl, sourceUrl, siteBaseUrl),
            maxVideoHeight = maxOfOrNull(stream.height, streamUrl.detectVideoHeight()),
        )
    }

    private fun resolveSibnet(sourceUrl: String, siteBaseUrl: String): ResolvedVideoStream {
        val html = getText(sourceUrl, iframeHeaders(sourceUrl, siteBaseUrl))
        val streamUrl = html.extractSibnetStreamUrl(sourceUrl)
            ?: html.extractDirectStreamUrl(sourceUrl)
            ?: throw IOException("Sibnet: не найден HLS/MP4/DASH поток")

        return ResolvedVideoStream(
            url = streamUrl,
            mimeType = streamUrl.mimeTypeFromUrl(),
            headers = playbackHeaders(streamUrl, sourceUrl, siteBaseUrl),
            maxVideoHeight = streamUrl.detectVideoHeight(),
        )
    }

    private fun resolveCvh(sourceUrl: String, video: VideoVariant, siteBaseUrl: String): ResolvedVideoStream {
        val iframeUri = Uri.parse(sourceUrl)
        val titleId = iframeUri.getQueryParameter("anime_id")?.takeIf { it.isNotBlank() }
            ?: throw IOException("CVH: не найден anime_id в iframe")
        val episode = iframeUri.getQueryParameter("episode")?.toIntOrNull()
            ?: video.episode.toIntOrNull()
            ?: 1
        val season = iframeUri.getQueryParameter("season")?.toIntOrNull() ?: 1
        val priorityVoice = iframeUri.getQueryParameter("dubbing_code")
            ?.takeIf { it.isNotBlank() }
            ?: video.dubbing.takeIf { it.isNotBlank() }

        val playlistUrl = CVH_PLAYLIST_URL.newBuilder()
            .addQueryParameter("pub", CVH_PUBLISHER_ID)
            .addQueryParameter("id", titleId)
            .addQueryParameter("aggr", CVH_AGGREGATOR)
            .build()
            .toString()
        val playlist = getJson<CvhPlaylistDto>(playlistUrl, cvhApiHeaders(sourceUrl))
        val selectedVideo = playlist.items.selectCvhItem(
            season = season,
            episode = episode,
            priorityVoice = priorityVoice,
        ) ?: throw IOException("CVH: не найдена серия $episode для озвучки ${priorityVoice.orEmpty()}")

        val vkId = selectedVideo.vkId.takeIf { it.isNotBlank() }
            ?: throw IOException("CVH: у серии нет vkId")
        val videoUrl = "$CVH_VIDEO_URL/$vkId"
        val cvhVideo = getJson<CvhVideoDto>(videoUrl, cvhApiHeaders(sourceUrl))
        val source = cvhVideo.sources?.bestStream()
            ?: throw IOException("CVH: не найден HLS/DASH/MP4 поток")

        return ResolvedVideoStream(
            url = source.url,
            mimeType = source.mimeType,
            headers = cvhPlaybackHeaders(source.url, sourceUrl, siteBaseUrl),
            maxVideoHeight = maxOfOrNull(source.height, source.url.detectVideoHeight()),
        )
    }

    private fun getText(url: String, headers: Map<String, String>): String {
        val request = Request.Builder()
            .url(url)
            .headers(headers.toOkHttpHeaders())
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful || body.isBlank()) {
                throw IOException("Плеер вернул HTTP ${response.code}")
            }
            return body
        }
    }

    private inline fun <reified T> getJson(url: String, headers: Map<String, String>): T {
        val request = Request.Builder()
            .url(url)
            .headers(headers.toOkHttpHeaders())
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful || body.isBlank()) {
                throw IOException("CVH API вернул HTTP ${response.code}")
            }
            return json.decodeFromString(body)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun resolveViaWebView(
        sourceUrl: String,
        siteBaseUrl: String,
    ): ResolvedVideoStream = withContext(Dispatchers.Main) {
        val context = appContext ?: throw IOException("Нужен Context для JS-перехвата потока")

        suspendCancellableCoroutine { continuation ->
            val handler = Handler(Looper.getMainLooper())
            val webView = WebView(context)
            var completed = false

            fun cleanup() {
                runCatching {
                    webView.stopLoading()
                    webView.loadUrl("about:blank")
                    webView.removeAllViews()
                    webView.destroy()
                }
            }

            fun finish(result: Result<ResolvedVideoStream>) {
                if (completed) return
                completed = true
                handler.removeCallbacksAndMessages(null)
                cleanup()

                if (!continuation.isActive) return
                result
                    .onSuccess { continuation.resume(it) }
                    .onFailure { continuation.resumeWithException(it) }
            }

            val timeout = Runnable {
                finish(Result.failure(IOException("Не удалось перехватить HLS/MP4/DASH поток плеера за 35 секунд. Iframe: $sourceUrl")))
            }
            handler.postDelayed(timeout, WEBVIEW_RESOLVE_TIMEOUT_MS)

            continuation.invokeOnCancellation {
                handler.post {
                    handler.removeCallbacksAndMessages(null)
                    cleanup()
                }
            }

            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString = USER_AGENT
                loadsImagesAutomatically = false
                blockNetworkImage = true
            }

            webView.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): WebResourceResponse? {
                    val url = request?.url?.toString().orEmpty()
                    val method = request?.method.orEmpty()
                    if (method.equals("GET", ignoreCase = true) && url.isCapturedPlaybackUrl()) {
                        val requestHeaders = request?.requestHeaders.orEmpty()
                        val headerLengths = requestHeaders
                            .mapValues { (_, value) -> value.length }
                            .toSortedMap()
                        AppLog.w(
                            "YummyDroidVideo",
                            "Captured playback host=${request?.url?.host}, file=${request?.url?.lastPathSegment}, headers=$headerLengths",
                        )
                        handler.post {
                            finish(
                                Result.success(
                                    ResolvedVideoStream(
                                        url = url,
                                        mimeType = url.mimeTypeFromUrl(),
                                        headers = requestHeaders.toPlaybackHeaders(url, sourceUrl, siteBaseUrl),
                                        maxVideoHeight = url.detectVideoHeight(),
                                    ),
                                ),
                            )
                        }
                        return WebResourceResponse(
                            url.mimeTypeFromUrl() ?: "text/plain",
                            "UTF-8",
                            ByteArrayInputStream(ByteArray(0)),
                        )
                    }
                    return null
                }
            }

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
    }

    private fun iframeHeaders(
        url: String,
        siteBaseUrl: String = siteDomainResolver.cachedOrDefaultBaseUrl(),
    ): Map<String, String> {
        return buildMap {
            put("Accept", "*/*")
            put("Origin", siteBaseUrl.origin() ?: siteBaseUrl.trimEnd('/'))
            put("Referer", siteBaseUrl.ensureTrailingSlash())
            put("User-Agent", USER_AGENT)
            if (url.contains("alloha.yani.tv", ignoreCase = true)) {
                put("Sec-Fetch-Dest", "iframe")
                put("Sec-Fetch-Mode", "navigate")
            }
        }
    }

    private fun aksorApiHeaders(sourceUrl: String): Map<String, String> {
        val origin = sourceUrl.origin() ?: AKSOR_ORIGIN
        return buildMap {
            put("Accept", "application/json")
            put("Origin", origin)
            put("Referer", sourceUrl)
            put("User-Agent", USER_AGENT)
        }
    }

    private fun kodikApiHeaders(sourceUrl: String): Map<String, String> {
        return buildMap {
            put("Accept", "application/json, text/javascript, */*; q=0.01")
            put("Origin", sourceUrl.origin() ?: "https://kodikplayer.com")
            put("Referer", sourceUrl)
            put("User-Agent", USER_AGENT)
            put("X-Requested-With", "XMLHttpRequest")
        }
    }

    private fun kodikPlaybackHeaders(url: String): Map<String, String> {
        return buildMap {
            putAll(playbackHeaders(url, "https://kodikplayer.com/"))
            put("Accept", "*/*")
            put("Origin", "https://kodikplayer.com")
            put("Referer", "https://kodikplayer.com/")
            put("User-Agent", USER_AGENT)
        }
    }

    private fun playbackHeaders(
        url: String,
        refererUrl: String? = null,
        siteBaseUrl: String = siteDomainResolver.cachedOrDefaultBaseUrl(),
    ): Map<String, String> {
        val referer = refererUrl?.takeIf { it.isNotBlank() }
        val origin = referer?.origin()

        return buildMap {
            put("Accept", "*/*")
            put("Accept-Encoding", "identity")
            put("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
            put("User-Agent", USER_AGENT)
            put("Sec-Fetch-Dest", "empty")
            put("Sec-Fetch-Mode", "cors")
            put("Sec-Fetch-Site", "cross-site")
            if (url.contains("vkvideo.cloud", ignoreCase = true)) {
                put("Origin", origin ?: "https://alloha.yani.tv")
                put("Referer", referer ?: "https://alloha.yani.tv/")
            } else if (referer != null && origin != null) {
                put("Origin", origin)
                put("Referer", referer)
            } else {
                put("Origin", siteBaseUrl.origin() ?: siteBaseUrl.trimEnd('/'))
                put("Referer", siteBaseUrl.ensureTrailingSlash())
            }
        }
    }

    private fun cvhApiHeaders(sourceUrl: String): Map<String, String> {
        val origin = sourceUrl.origin() ?: "https://ru.yummyani.me"
        return buildMap {
            put("Accept", "application/json, text/plain, */*")
            put("Origin", origin)
            put("Referer", sourceUrl)
            put("User-Agent", USER_AGENT)
        }
    }

    private fun cvhPlaybackHeaders(url: String, sourceUrl: String, siteBaseUrl: String): Map<String, String> {
        return buildMap {
            putAll(playbackHeaders(url, sourceUrl, siteBaseUrl))
            put("Accept", "*/*")
            put("Origin", "https://player.cdnvideohub.com")
            put("Referer", "https://player.cdnvideohub.com/")
            put("User-Agent", USER_AGENT)
        }
    }

    private fun Map<String, String>.toPlaybackHeaders(
        streamUrl: String,
        sourceUrl: String,
        siteBaseUrl: String = siteDomainResolver.cachedOrDefaultBaseUrl(),
    ): Map<String, String> {
        val sourceHeaders = this
        return buildMap {
            putAll(playbackHeaders(streamUrl, sourceUrl, siteBaseUrl))
            sourceHeaders.forEach { (name, value) ->
                if (name.isForwardablePlaybackHeader() && value.isNotBlank()) {
                    put(name, value)
                }
            }
            putIfAbsent("Referer", sourceUrl)
            putIfAbsent("Origin", sourceUrl.origin() ?: siteBaseUrl.origin().orEmpty())
            putIfAbsent("User-Agent", USER_AGENT)
            putIfAbsent("Accept-Encoding", "identity")
            putIfAbsent("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
            putIfAbsent("Sec-Fetch-Dest", "empty")
            putIfAbsent("Sec-Fetch-Mode", "cors")
            putIfAbsent("Sec-Fetch-Site", "cross-site")
            playbackCookies(streamUrl, sourceUrl)?.let { put("Cookie", it) }
        }
    }

    private fun String.isForwardablePlaybackHeader(): Boolean {
        return when (lowercase()) {
            "accept-encoding",
            "access-control-request-headers",
            "access-control-request-method",
            "connection",
            "host",
            "range" -> false
            else -> true
        }
    }

    private fun playbackCookies(streamUrl: String, sourceUrl: String): String? {
        val cookieManager = CookieManager.getInstance()
        val streamOrigin = streamUrl.origin()
        val sourceOrigin = sourceUrl.origin()
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
            .mapNotNull { url -> runCatching { cookieManager.getCookie(url) }.getOrNull() }
            .firstOrNull { it.isNotBlank() }
    }

    private fun Map<String, String>.toOkHttpHeaders(): okhttp3.Headers {
        return okhttp3.Headers.Builder().also { builder ->
            forEach { (name, value) -> builder.set(name, value) }
        }.build()
    }

    private fun String.extractDirectStreamUrl(baseUrl: String): String? {
        val normalized = this
            .replace("\\/", "/")
            .replace("&amp;", "&")
            .replace("\\u0026", "&")

        return streamUrlRegex
            .findAll(normalized)
            .map { it.value.trim('"', '\'', ' ', '\\') }
            .map { it.normalizeVideoUrlAgainst(baseUrl) }
            .firstOrNull { it.isCapturedPlaybackUrl() }
    }

    private fun String.extractSibnetStreamUrl(baseUrl: String): String? {
        val normalized = this
            .replace("\\/", "/")
            .replace("&amp;", "&")
            .replace("\\u0026", "&")

        return sibnetPlayerSourceRegex
            .find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim('"', '\'', ' ', '\\')
            ?.normalizeVideoUrlAgainst(baseUrl)
            ?.takeIf { it.isCapturedPlaybackUrl() }
    }

    private fun String.normalizeVideoUrl(siteBaseUrl: String): String {
        val value = trim()
        return when {
            value.startsWith("//") -> {
                val absoluteUrl = "https:$value"
                if (siteDomainResolver.isKnownSiteHost(runCatching { Uri.parse(absoluteUrl).host }.getOrNull())) {
                    absoluteUrl.rewriteKnownSiteHost(siteBaseUrl)
                } else {
                    absoluteUrl
                }
            }
            value.startsWith("/") -> "${siteBaseUrl.origin() ?: siteBaseUrl.trimEnd('/')}$value"
            siteDomainResolver.isKnownSiteHost(runCatching { Uri.parse(value).host }.getOrNull()) ->
                value.rewriteKnownSiteHost(siteBaseUrl)
            else -> value
        }
    }

    private fun String.normalizeVideoUrlAgainst(baseUrl: String): String {
        val value = trim()
        return when {
            value.startsWith("//") -> "https:$value"
            value.startsWith("/") -> "${baseUrl.origin() ?: siteDomainResolver.cachedOrDefaultBaseUrl().trimEnd('/')}$value"
            else -> value
        }
    }

    private fun String.isCapturedPlaybackUrl(): Boolean {
        val lower = lowercase()
        return isDirectStreamUrl() &&
            "blank.mp4" !in lower &&
            "cdn.plyr.io" !in lower
    }

    private fun String.isDirectStreamUrl(): Boolean {
        val lower = lowercase()
        return ".m3u8" in lower || ".mp4" in lower || ".mpd" in lower || lower.startsWith("blob:").not() && "#EXTM3U" in this
    }

    private fun String.mimeTypeFromUrl(): String? {
        val lower = lowercase()
        return when {
            ".m3u8" in lower -> "application/x-mpegURL"
            ".mpd" in lower -> "application/dash+xml"
            ".mp4" in lower -> "video/mp4"
            else -> null
        }
    }

    private fun String.origin(): String? {
        return runCatching {
            val uri = Uri.parse(this)
            val scheme = uri.scheme?.takeIf { it.isNotBlank() } ?: return@runCatching null
            val host = uri.host?.takeIf { it.isNotBlank() } ?: return@runCatching null
            val port = uri.port.takeIf { it > 0 }?.let { ":$it" }.orEmpty()
            "$scheme://$host$port"
        }.getOrNull()
    }

    private fun String.ensureTrailingSlash(): String {
        return if (endsWith("/")) this else "$this/"
    }

    private fun String.rewriteKnownSiteHost(siteBaseUrl: String): String {
        val targetOrigin = siteBaseUrl.origin() ?: siteBaseUrl.trimEnd('/')
        return runCatching {
            val uri = Uri.parse(this)
            val path = uri.encodedPath.orEmpty()
            val query = uri.encodedQuery?.let { "?$it" }.orEmpty()
            val fragment = uri.encodedFragment?.let { "#$it" }.orEmpty()
            "$targetOrigin$path$query$fragment"
        }.getOrDefault(this)
    }

    private fun String.isCvhIframeUrl(): Boolean {
        val uri = runCatching { Uri.parse(this) }.getOrNull() ?: return false
        return siteDomainResolver.isKnownSiteHost(uri.host) &&
            uri.path.orEmpty().contains("iframeCVH", ignoreCase = true)
    }

    private fun String.isKodikIframeUrl(): Boolean {
        val host = runCatching { Uri.parse(this).host.orEmpty() }.getOrDefault("")
        return host.equals("kodikplayer.com", ignoreCase = true) ||
            host.endsWith(".kodikplayer.com", ignoreCase = true)
    }

    private fun String.isAksorIframeUrl(): Boolean {
        val uri = runCatching { Uri.parse(this) }.getOrNull() ?: return false
        return uri.host.equals("player.aksor.tv", ignoreCase = true) &&
            uri.path.orEmpty().startsWith("/video/", ignoreCase = true)
    }

    private fun String.isSibnetIframeUrl(): Boolean {
        val uri = runCatching { Uri.parse(this) }.getOrNull() ?: return false
        return uri.host.equals("video.sibnet.ru", ignoreCase = true) &&
            uri.path.orEmpty().contains("shell.php", ignoreCase = true)
    }

    private fun String.kodikParams(): KodikParams {
        val type = extractKodikValue("type")
            ?: extractKodikVInfoValue("type")
            ?: throw IOException("Kodik: не найден type")
        val id = extractKodikVInfoValue("id")
            ?: extractKodikValue("videoId")
            ?: throw IOException("Kodik: не найден id")
        val hash = extractKodikVInfoValue("hash")
            ?: throw IOException("Kodik: не найден hash")

        return KodikParams(
            type = type,
            id = id,
            hash = hash,
            domain = extractKodikValue("domain") ?: throw IOException("Kodik: не найден domain"),
            domainSign = extractKodikValue("d_sign") ?: throw IOException("Kodik: не найден d_sign"),
            playerDomain = extractKodikValue("pd") ?: "kodikplayer.com",
            playerDomainSign = extractKodikValue("pd_sign") ?: throw IOException("Kodik: не найден pd_sign"),
            referer = extractKodikValue("ref") ?: "https://old.yummyani.me/",
            refererSign = extractKodikValue("ref_sign") ?: throw IOException("Kodik: не найден ref_sign"),
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

    private fun List<CvhItemDto>.selectCvhItem(
        season: Int,
        episode: Int,
        priorityVoice: String?,
    ): CvhItemDto? {
        val episodeItems = filter { item ->
            (item.season ?: 1) == season && (item.episode ?: 1) == episode
        }
        if (episodeItems.isEmpty()) return null

        val normalizedVoice = priorityVoice?.normalizeVoiceName()
        if (!normalizedVoice.isNullOrBlank()) {
            episodeItems.firstOrNull { item ->
                item.voiceStudio?.normalizeVoiceName() == normalizedVoice ||
                    item.voiceType?.normalizeVoiceName() == normalizedVoice
            }?.let { return it }
        }

        return episodeItems.firstOrNull { !it.voiceStudio.isNullOrBlank() }
            ?: episodeItems.firstOrNull()
    }

    private fun String.normalizeVoiceName(): String {
        return trim().lowercase()
    }

    private fun String.detectVideoHeight(): Int? {
        val heights = buildList {
            hlsResolutionHeightRegex.findAll(this@detectVideoHeight).forEach { match ->
                match.groupValues.getOrNull(1)?.toIntOrNull()?.let(::add)
            }
            dashHeightRegex.findAll(this@detectVideoHeight).forEach { match ->
                match.groupValues.getOrNull(1)?.toIntOrNull()?.let(::add)
            }
            qualityHeightRegex.findAll(this@detectVideoHeight).forEach { match ->
                match.groupValues.getOrNull(1)?.toIntOrNull()?.let(::add)
            }
        }
        return heights.filter { it in 100..4320 }.maxOrNull()
    }

    private fun maxOfOrNull(vararg values: Int?): Int? {
        return values.filterNotNull().maxOrNull()
    }

    private companion object {
        const val WEBVIEW_RESOLVE_TIMEOUT_MS = 35_000L
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36"
        const val CVH_PUBLISHER_ID = "745"
        const val CVH_AGGREGATOR = "mali"
        const val CVH_VIDEO_URL = "https://plapi.cdnvideohub.com/api/v1/player/sv/video"
        val CVH_PLAYLIST_URL = "https://plapi.cdnvideohub.com/api/v1/player/sv/playlist".toHttpUrl()
        const val KODIK_FTOR_URL = "https://kodikplayer.com/ftor"
        const val AKSOR_ORIGIN = "https://player.aksor.tv"

        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        val streamUrlRegex = Regex(
            """(?:(?:https?:)?//|/)[^"'\s<>\\]+?(?:\.m3u8|\.mp4|\.mpd)(?:\?[^"'\s<>\\]*)?""",
            RegexOption.IGNORE_CASE,
        )
        val sibnetPlayerSourceRegex = Regex(
            """src\s*:\s*["']([^"']+\.(?:m3u8|mp4|mpd)(?:\?[^"']*)?)["']""",
            RegexOption.IGNORE_CASE,
        )
        val hlsResolutionHeightRegex = Regex("""(?i)RESOLUTION\s*=\s*\d+\s*x\s*(\d+)""")
        val dashHeightRegex = Regex("""(?i)\b(?:height|maxHeight)\s*=\s*["'](\d+)["']""")
        val qualityHeightRegex = Regex("""(?i)(?:^|[^\d])(2160|1440|1080|720|576|540|480|360|240|144)p(?:[^\d]|$)""")
    }
}

private data class KodikParams(
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

@Serializable
private data class KodikFtorDto(
    val link: String = "",
    val links: Map<String, List<KodikLinkDto>> = emptyMap(),
) {
    fun bestStream(): KodikStream? {
        links.entries
            .sortedByDescending { it.key.toIntOrNull() ?: 0 }
            .forEach { entry ->
                entry.value.firstOrNull { it.src.isNotBlank() }?.let { link ->
                    return KodikStream(
                        url = link.src.decodeKodikUrl().normalizeKodikUrl(),
                        mimeType = link.type.takeIf { it.isNotBlank() },
                        height = entry.key.toIntOrNull(),
                    )
                }
            }

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
private data class KodikLinkDto(
    val src: String = "",
    val type: String = "",
)

private data class KodikStream(
    val url: String,
    val mimeType: String?,
    val height: Int?,
)

@Serializable
private data class AksorVideoDto(
    val qualities: AksorQualitiesDto = AksorQualitiesDto(),
) {
    fun bestStream(): AksorStream? = qualities.bestStream()
}

@Serializable
private data class AksorQualitiesDto(
    val q4k: String? = null,
    val q2k: String? = null,
    val q1080: String? = null,
    val q720: String? = null,
    val q480: String? = null,
    val q360: String? = null,
) {
    fun bestStream(): AksorStream? {
        return listOf(
            AksorStream(q4k.orEmpty(), 2160),
            AksorStream(q2k.orEmpty(), 1440),
            AksorStream(q1080.orEmpty(), 1080),
            AksorStream(q720.orEmpty(), 720),
            AksorStream(q480.orEmpty(), 480),
            AksorStream(q360.orEmpty(), 360),
        ).firstOrNull { it.url.isNotBlank() }
    }
}

private data class AksorStream(
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

private fun String.mimeTypeFromKodikUrl(): String? {
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

@Serializable
private data class CvhPlaylistDto(
    val items: List<CvhItemDto> = emptyList(),
)

@Serializable
private data class CvhItemDto(
    @SerialName("vkId") val vkId: String = "",
    @SerialName("voiceStudio") val voiceStudio: String? = null,
    @SerialName("voiceType") val voiceType: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
)

@Serializable
private data class CvhVideoDto(
    val sources: CvhSourcesDto? = null,
)

@Serializable
private data class CvhSourcesDto(
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
    fun bestStream(): CvhStream? {
        val mpegStreams = listOf(
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
        val highestKnownHeight = mpegStreams
            .asSequence()
            .filter { it.url.isNotBlank() }
            .mapNotNull { it.height }
            .maxOrNull()

        return (
            listOf(
                CvhStream(hlsUrl, "application/x-mpegURL", highestKnownHeight),
                CvhStream(dashUrl, "application/dash+xml", highestKnownHeight),
            ) + mpegStreams
            )
                .filter { it.url.isNotBlank() }
                .maxWithOrNull(
                    compareBy<CvhStream> { it.height ?: 0 }
                        .thenBy { if (it.mimeType.contains("mpegURL") || it.mimeType.contains("dash")) 1 else 0 },
                )
    }
}

private data class CvhStream(
    val url: String,
    val mimeType: String,
    val height: Int?,
)

