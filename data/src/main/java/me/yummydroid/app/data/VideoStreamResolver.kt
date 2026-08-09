package me.yummydroid.app.data

import android.content.Context
import java.io.IOException
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient

class VideoStreamResolver(
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
